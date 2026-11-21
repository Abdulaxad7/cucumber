// Batch B: a small in-memory backend for end-to-end journeys (wired to Playwright's page.route by the specs).
// It answers only the operations the contracts define, with contract-typed bodies.
import type {components as Scenario} from '../../api/generated/scenario-service';
import {ids} from './auth';
import {FakeAuth} from './fakeAuth';
import {FakeReport} from './fakeReport';
import {billingId, users, type UserKey} from './workspace';
import {
    address,
    addressDetail,
    detail,
    featureIds,
    legacy,
    legacyDetail,
    payment,
    paymentDetail,
    scenarioProblem,
    tags as defaultTags,
} from './scenario';

type S = Scenario['schemas'];

export interface FakeAnswer {
    status: number;
    body: unknown;
    contentType: string;
}

export type SaveOverride = (body: S['SaveFeatureRequest'], current: S['Feature']) => FakeAnswer | null;

export interface FakeRequest {
    method: string;
    path: string;
    search: string;
    body: unknown;
}

const json = (status: number, body: unknown): FakeAnswer => ({status, body, contentType: 'application/json'});
const problem = (status: number, body: unknown): FakeAnswer => ({status, body, contentType: 'application/problem+json'});

function isRecord(value: unknown): value is Record<string, unknown> {
    return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function summaryOf(f: S['Feature']): S['FeatureSummary'] {
    const {description: _d, source: _s, children: _c, ...rest} = f;
    void _d;
    void _s;
    void _c;
    return {...rest, testCaseCount: 1};
}

export class FakeBackend {
    readonly user: UserKey | null;
    readonly features = new Map<string, S['Feature']>();
    readonly requests: FakeRequest[] = [];
    tags: S['Tag'][] = defaultTags;
    saveOverride: SaveOverride | null = null;
    /** Extra summaries listed without details (for large trees). */
    extraSummaries: S['FeatureSummary'][] = [];

    /** Organizations, members, invitations, projects and project members (batch C). */
    readonly auth: FakeAuth;
    /** Runs, results and upload tokens (batch D). */
    readonly report: FakeReport;

    constructor(user: UserKey | null, features: S['Feature'][] = [paymentDetail, addressDetail, legacyDetail]) {
        this.user = user;
        this.auth = new FakeAuth(user);
        this.report = new FakeReport(this.auth, user ? users[user].id : null);
        for (const f of features) this.features.set(f.id, f);
    }

    requestsMatching(method: string, pathPart: string): FakeRequest[] {
        return this.requests.filter((r) => r.method === method && r.path.includes(pathPart));
    }

    answer(method: string, url: URL, body: unknown): FakeAnswer | null {
        const path = url.pathname;
        this.requests.push({method, path, search: url.search, body});
        if (path.startsWith('/api/orgs') || path.startsWith('/api/auth/invitations/')) return this.auth.answer(method, url, body);
        if (/^\/api\/projects\/[^/]+\/(runs|upload-tokens)(\/|$)/.test(path)) return this.report.answer(method, url, body);

        // Batch C: version history and test cases (additive).
        const sub = /^\/api\/projects\/([^/]+)\/features\/([^/]+)\/(versions|test-cases)(?:\/([^/]+))?$/.exec(path);
        if (sub && method === 'GET') return this.featureSub(path, sub[2] ?? '', sub[3] ?? '', sub[4]);

        const match = /^\/api\/projects\/([^/]+)\/(features|tags)(?:\/([^/]+))?(?:\/(archive|restore))?$/.exec(path);
        if (!match) return null;
        const [, projectId, area, featureId, action] = match;
        if (projectId !== ids.checkout && projectId !== billingId) return null;

        if (area === 'tags' && method === 'GET') return json(200, this.tags);
        if (area !== 'features') return null;

        if (!featureId && method === 'GET') {
            const archived = url.searchParams.get('archived') === 'true';
            const tag = url.searchParams.get('tag');
            const all = [...[...this.features.values()].map(summaryOf), ...this.extraSummaries];
            return json(200, all.filter((f) => (f.archivedAt !== null) === archived && (tag === null || f.tags.includes(tag))));
        }
        if (!featureId && method === 'POST') return this.create(path, body);

        const current = featureId ? this.features.get(featureId) : undefined;
        if (!current) return problem(404, scenarioProblem(404, 'FEATURE_NOT_FOUND', path));
        if (!action && method === 'GET') return json(200, current);
        if (!action && method === 'PUT') return this.save(path, current, body);
        if (action === 'archive' && method === 'POST') {
            const archived = {...current, archivedAt: '2026-11-09T10:00:00Z'};
            this.features.set(current.id, archived);
            return json(200, archived);
        }
        if (action === 'restore' && method === 'POST') {
            const taken = [...this.features.values()].some((f) => f.id !== current.id && f.archivedAt === null && f.path === current.path);
            if (taken) return problem(409, scenarioProblem(409, 'FEATURE_PATH_TAKEN', path));
            const restored = {...current, archivedAt: null};
            this.features.set(current.id, restored);
            return json(200, restored);
        }
        return null;
    }

    private featureSub(path: string, featureId: string, area: string, version: string | undefined): FakeAnswer {
        const current = this.features.get(featureId);
        if (!current) return problem(404, scenarioProblem(404, 'FEATURE_NOT_FOUND', path));
        const count = current.currentVersion;
        if (area === 'test-cases') {
            const cases: S['TestCase'][] = [
                {id: 'a0000000-0000-4000-8000-000000000001', type: 'SCENARIO', scenarioId: 'b0000000-0000-4000-8000-000000000001', exampleRowId: null, name: 'Pay by card', tags: current.tags, line: 4, steps: []},
                {id: 'a0000000-0000-4000-8000-000000000002', type: 'EXAMPLE_ROW', scenarioId: 'b0000000-0000-4000-8000-000000000002', exampleRowId: 'c0000000-0000-4000-8000-000000000001', name: 'Pay with <method>', tags: [...current.tags, '@smoke'], line: 15, steps: []},
            ];
            return json(200, cases);
        }
        const summaries: S['FeatureVersionSummary'][] = Array.from({length: count}, (_, i) => ({
            version: count - i,
            message: count - i === 1 ? null : `Change ${count - i}`,
            createdBy: current.updatedBy,
            createdAt: `2026-10-${String(5 + (count - i)).padStart(2, '0')}T09:00:00Z`,
        }));
        if (version === undefined) return json(200, summaries);
        const found = summaries.find((v) => String(v.version) === version);
        if (!found) return problem(404, scenarioProblem(404, 'VERSION_NOT_FOUND', path));
        return json(200, {...found, source: current.source});
    }

    private create(path: string, body: unknown): FakeAnswer {
        if (!isRecord(body) || typeof body.path !== 'string' || typeof body.source !== 'string') {
            return problem(400, scenarioProblem(400, 'MALFORMED_REQUEST', path));
        }
        if (!body.path.endsWith('.feature')) {
            return problem(400, scenarioProblem(400, 'VALIDATION_FAILED', path, {errors: [{field: 'path', message: 'The path must end with .feature'}]}));
        }
        const newPath = body.path;
        if ([...this.features.values()].some((f) => f.archivedAt === null && f.path === newPath)) {
            return problem(409, scenarioProblem(409, 'FEATURE_PATH_TAKEN', path));
        }
        const name = /Feature:\s*(.*)/.exec(body.source)?.[1]?.trim() ?? '';
        const created = detail({...payment, id: featureIds.refund, path: newPath, name, currentVersion: 1, tags: []}, body.source);
        this.features.set(created.id, created);
        return json(201, created);
    }

    private save(path: string, current: S['Feature'], body: unknown): FakeAnswer {
        if (!isRecord(body) || typeof body.source !== 'string' || typeof body.path !== 'string' || typeof body.baseVersion !== 'number') {
            return problem(400, scenarioProblem(400, 'MALFORMED_REQUEST', path));
        }
        const request: S['SaveFeatureRequest'] = {
            source: body.source,
            path: body.path,
            baseVersion: body.baseVersion,
            ...(typeof body.message === 'string' ? {message: body.message} : {}),
        };
        const override = this.saveOverride?.(request, current);
        if (override) return override;
        if (current.archivedAt) return problem(409, scenarioProblem(409, 'FEATURE_ARCHIVED', path));
        if (request.baseVersion !== current.currentVersion) {
            return problem(409, scenarioProblem(409, 'VERSION_CONFLICT', path, {currentVersion: current.currentVersion}));
        }
        if ([...this.features.values()].some((f) => f.id !== current.id && f.archivedAt === null && f.path === request.path)) {
            return problem(409, scenarioProblem(409, 'FEATURE_PATH_TAKEN', path));
        }
        const parseErrors = request.source
            .split('\n')
            .map((line, i) => ({line: i + 1, text: line}))
            .filter(({text}) => /^\s*Whenn\b/.test(text))
            .map(({line, text}) => ({line, column: text.search(/\S/) + 1, message: `(${line}:${text.search(/\S/) + 1}): expected: #EOF, #TableRow, ... got '${text.trim()}'`}));
        if (parseErrors.length > 0) return problem(422, scenarioProblem(422, 'GHERKIN_INVALID', path, {parseErrors}));
        if (request.source === current.source && request.path === current.path) return json(200, current);
        const saved = {...current, source: request.source, path: request.path, currentVersion: current.currentVersion + 1};
        this.features.set(current.id, saved);
        return json(200, saved);
    }
}

export {address, legacy, payment};
