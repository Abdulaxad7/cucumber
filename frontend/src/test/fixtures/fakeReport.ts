// Batch D: an in-memory report-service (runs, results, manual runs, upload tokens) following the contract's role,
// state and error rules, for component and end-to-end tests.
import type {components} from '../../api/generated/report-service';
import type {FakeAnswer} from './fakeAuth';
import type {FakeAuth} from './fakeAuth';
import {ids} from './auth';
import {featureIds} from './scenario';
import {nightlyFields, nightlyResults, reportProblem, runFields, sprint12Fields, sprint12Results, steps, summaryOf} from './report';

type S = components['schemas'];
type Role = 'ADMIN' | 'MEMBER' | 'TESTER' | 'READER';
const RANK: Record<Role, number> = {ADMIN: 4, MEMBER: 3, TESTER: 2, READER: 1};

const json = (status: number, body: unknown): FakeAnswer => ({status, body, contentType: 'application/json'});
const problem = (status: number, body: unknown): FakeAnswer => ({status, body, contentType: 'application/problem+json'});

function isRecord(value: unknown): value is Record<string, unknown> {
    return typeof value === 'object' && value !== null && !Array.isArray(value);
}

const RECORDABLE = new Set(['PASSED', 'FAILED', 'SKIPPED', 'UNKNOWN']);
const EXECUTION = new Set(['PASSED', 'FAILED', 'SKIPPED', 'PENDING', 'UNDEFINED', 'AMBIGUOUS', 'UNKNOWN']);
const KNOWN_FEATURES: Record<string, {name: string; path: string; tags: string[]}> = {
    [featureIds.payment]: {name: 'Payment', path: 'features/checkout/payment.feature', tags: ['@checkout']},
    [featureIds.address]: {name: 'Address', path: 'features/checkout/address.feature', tags: ['@smoke']},
};

let counter = 100;

export class FakeReport {
    readonly runs = new Map<string, {fields: S['TestRunFields']; results: S['TestCaseResult'][]}>();
    readonly tokens: S['UploadToken'][] = [];
    /** Forced answers for "METHOD path" keys (error cases), used once. */
    readonly overrides = new Map<string, FakeAnswer>();
    readonly requests: Array<{method: string; path: string; search: string; body: unknown}> = [];
    private readonly auth: FakeAuth;
    private readonly userId: string | null;

    constructor(auth: FakeAuth, userId: string | null) {
        this.auth = auth;
        this.userId = userId;
        this.runs.set(nightlyFields.id, {fields: {...nightlyFields}, results: nightlyResults()});
        this.runs.set(sprint12Fields.id, {fields: {...sprint12Fields}, results: sprint12Results()});
    }

    addManyRuns(count: number): void {
        for (let i = 0; i < count; i += 1) {
            const id = `d2000000-0000-4000-8000-${String(i).padStart(12, '0')}`;
            const day = String(1 + (i % 28)).padStart(2, '0');
            this.runs.set(id, {fields: runFields(id, `Nightly #${1000 + i}`, 'AUTOMATED', 'COMPLETED', `2026-10-${day}T22:00:00Z`), results: nightlyResults()});
        }
    }

    answer(method: string, url: URL, body: unknown): FakeAnswer | null {
        const path = url.pathname;
        const m = /^\/api\/projects\/([^/]+)\/(runs|upload-tokens)(?:\/([^/]+))?(?:\/([a-z]+))?(?:\/([^/]+))?$/.exec(path);
        if (!m) return null;
        this.requests.push({method, path, search: url.search, body});
        const forced = this.overrides.get(`${method} ${path}`);
        if (forced) {
            this.overrides.delete(`${method} ${path}`);
            return forced;
        }
        const [, projectId = '', area, id, action, childId] = m;
        const role = this.auth.projectRole(projectId);
        if (!role) return problem(403, reportProblem(403, 'FORBIDDEN', path));
        const can = (min: Role) => RANK[role] >= RANK[min];
        const archived = this.auth.isProjectArchived(projectId);
        const forbidden = () => problem(403, reportProblem(403, 'FORBIDDEN', path));

        if (area === 'upload-tokens') {
            if (!can('ADMIN')) return forbidden();
            if (!id && method === 'GET') return json(200, this.tokens);
            if (!id && method === 'POST') {
                if (archived) return problem(409, reportProblem(409, 'PROJECT_ARCHIVED', path));
                if (!isRecord(body) || typeof body.name !== 'string' || body.name.trim() === '') {
                    return problem(400, reportProblem(400, 'VALIDATION_FAILED', path, {errors: [{field: 'name', message: 'Enter a name'}]}));
                }
                if (this.tokens.filter((t) => t.revokedAt === null).length >= 10) return problem(409, reportProblem(409, 'UPLOAD_TOKEN_LIMIT', path));
                counter += 1;
                const secret = `rct_${'Q2hlY2tvdXQ'}${counter}x7Gm2pLq9VwZr4TfYb8NcKd3HsJe6Ua`;
                const token: S['UploadToken'] = {
                    id: `a9000000-0000-4000-8000-${String(counter).padStart(12, '0')}`,
                    name: body.name.trim(),
                    tokenPrefix: secret.slice(0, 12),
                    createdBy: this.userId ?? ids.ana,
                    createdAt: '2026-11-09T10:00:00Z',
                    expiresAt: null,
                    lastUsedAt: null,
                    revokedAt: null,
                    revokedBy: null,
                };
                this.tokens.push(token);
                return json(201, {...token, token: secret});
            }
            if (id && action === 'revoke' && method === 'POST') {
                const token = this.tokens.find((t) => t.id === id);
                if (!token) return problem(404, reportProblem(404, 'UPLOAD_TOKEN_NOT_FOUND', path));
                token.revokedAt ??= '2026-11-09T11:00:00Z';
                token.revokedBy ??= this.userId;
                return json(200, token);
            }
            return null;
        }

        if (!id && method === 'GET') {
            const page = Number(url.searchParams.get('page') ?? '0');
            const size = Number(url.searchParams.get('size') ?? '20');
            const all = [...this.runs.values()]
                .filter((r) => r.fields.projectId === projectId || projectId === ids.checkout)
                .sort((a, b) => Date.parse(b.fields.createdAt) - Date.parse(a.fields.createdAt));
            const items = all.slice(page * size, page * size + size).map((r) => {
                const summary = summaryOf(r.results);
                return {...r.fields, total: summary.total, counts: summary.counts, passRate: summary.passRate};
            });
            return json(200, {items, page, size, totalItems: all.length});
        }
        if (!id && method === 'POST') return this.plan(path, projectId, archived, can('MEMBER'), body);

        const run = id ? this.runs.get(id) : undefined;
        if (!run) return problem(404, reportProblem(404, 'RUN_NOT_FOUND', path));
        const view = () => ({...run.fields, summary: summaryOf(run.results)});
        const closed = run.fields.status === 'COMPLETED' || run.fields.status === 'CANCELLED';

        if (!action && method === 'GET') return json(200, view());
        if ((action === 'complete' || action === 'cancel') && method === 'POST') {
            if (!can(action === 'complete' ? 'TESTER' : 'MEMBER')) return forbidden();
            if (archived) return problem(409, reportProblem(409, 'PROJECT_ARCHIVED', path));
            if (run.fields.source !== 'MANUAL') return problem(409, reportProblem(409, 'RUN_NOT_MANUAL', path));
            const target = action === 'complete' ? 'COMPLETED' : 'CANCELLED';
            if (run.fields.status === target) return json(200, view());
            if (closed) return problem(409, reportProblem(409, 'RUN_CLOSED', path));
            run.fields = {...run.fields, status: target, finishedAt: '2026-11-09T12:00:00Z'};
            return json(200, view());
        }
        if (action === 'results' && !childId && method === 'GET') {
            const status = url.searchParams.get('status');
            if (status !== null && !EXECUTION.has(status)) {
                return problem(400, reportProblem(400, 'VALIDATION_FAILED', path, {errors: [{field: 'status', message: 'Unknown status'}]}));
            }
            const page = Number(url.searchParams.get('page') ?? '0');
            const size = Number(url.searchParams.get('size') ?? '100');
            const all = run.results.filter((r) => status === null || r.status === status);
            return json(200, {items: all.slice(page * size, page * size + size), page, size, totalItems: all.length});
        }
        if (action === 'results' && childId) {
            const row = run.results.find((r) => r.id === childId);
            if (!row) return problem(404, reportProblem(404, 'RESULT_NOT_FOUND', path));
            if (method === 'GET') return json(200, {...row, steps: steps(row)});
            if (method === 'PUT') {
                if (!can('TESTER')) return forbidden();
                if (archived) return problem(409, reportProblem(409, 'PROJECT_ARCHIVED', path));
                if (run.fields.source !== 'MANUAL') return problem(409, reportProblem(409, 'RUN_NOT_MANUAL', path));
                if (closed) return problem(409, reportProblem(409, 'RUN_CLOSED', path));
                if (!isRecord(body) || typeof body.status !== 'string' || !RECORDABLE.has(body.status)) {
                    return problem(400, reportProblem(400, 'VALIDATION_FAILED', path, {errors: [{field: 'status', message: 'Choose a status'}]}));
                }
                const status = body.status === 'PASSED' || body.status === 'FAILED' || body.status === 'SKIPPED' ? body.status : 'UNKNOWN';
                Object.assign(row, {
                    status,
                    comment: typeof body.comment === 'string' && body.comment !== '' ? body.comment : null,
                    executedBy: this.userId,
                    finishedAt: '2026-11-09T11:30:00Z',
                    updatedAt: '2026-11-09T11:30:00Z',
                });
                if (run.fields.status === 'PLANNED') run.fields = {...run.fields, status: 'IN_PROGRESS', startedAt: '2026-11-09T11:30:00Z'};
                return json(200, row);
            }
        }
        return null;
    }

    private plan(path: string, projectId: string, archived: boolean, allowed: boolean, body: unknown): FakeAnswer {
        if (!allowed) return problem(403, reportProblem(403, 'FORBIDDEN', path));
        if (archived) return problem(409, reportProblem(409, 'PROJECT_ARCHIVED', path));
        if (!isRecord(body) || typeof body.name !== 'string' || !Array.isArray(body.featureIds)) {
            return problem(400, reportProblem(400, 'VALIDATION_FAILED', path, {errors: [{field: 'name', message: 'Enter a name'}]}));
        }
        if (body.name.trim() === '') return problem(400, reportProblem(400, 'VALIDATION_FAILED', path, {errors: [{field: 'name', message: 'Enter a name'}]}));
        const wanted = body.featureIds.filter((x): x is string => typeof x === 'string');
        const unknown = wanted.filter((f) => !KNOWN_FEATURES[f]);
        if (unknown.length > 0) return problem(422, reportProblem(422, 'FEATURE_NOT_PLANNABLE', path, {featureIds: unknown}));
        const tag = typeof body.tag === 'string' && body.tag !== '' ? body.tag : null;
        const chosen = wanted.flatMap((f) => {
            const feature = KNOWN_FEATURES[f];
            return feature && (tag === null || feature.tags.includes(tag)) ? [{id: f, ...feature}] : [];
        });
        if (chosen.length === 0) return problem(422, reportProblem(422, 'RUN_EMPTY', path));
        counter += 1;
        const id = `d3000000-0000-4000-8000-${String(counter).padStart(12, '0')}`;
        const environment = typeof body.environment === 'string' && body.environment !== '' ? {id: 'f1000000-0000-4000-8000-000000000009', name: body.environment} : null;
        const fields = runFields(id, body.name.trim(), 'MANUAL', 'PLANNED', '2026-11-09T12:00:00Z', {environment, tagExpression: tag, projectId});
        const [template] = sprint12Results();
        if (!template) return problem(500, reportProblem(500, 'INTERNAL_ERROR', path));
        const results = chosen.map((f, i): S['TestCaseResult'] => ({
            ...template,
            id: `e3000000-0000-4000-8000-${String(counter * 10 + i).padStart(12, '0')}`,
            testRunId: id,
            position: i + 1,
            featureId: f.id,
            featurePath: f.path,
            featureName: f.name,
            name: `${f.name} scenario`,
            tags: f.tags,
            status: 'UNKNOWN',
            executedBy: null,
            finishedAt: null,
        }));
        this.runs.set(id, {fields, results});
        return json(201, {...fields, summary: summaryOf(results)});
    }
}
