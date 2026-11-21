// Batch B: MSW handlers for the workspace (auth-service) and a project's features (scenario-service).
import {http, HttpResponse} from 'msw';
import type {components} from '../../api/generated/scenario-service';
import {ids} from './auth';
import {acmeMembers, orgFor, projectsFor, type UserKey} from './workspace';
import {tags as defaultTags} from './scenario';
import type {FakeAuth} from './fakeAuth';
import type {FakeBackend} from './fakeBackend';

type S = components['schemas'];

export function workspaceHandlers(user: UserKey) {
    return [
        http.get('/api/orgs', () => HttpResponse.json([orgFor(user)])),
        http.get(`/api/orgs/${ids.acme}/projects`, () => HttpResponse.json(projectsFor(user))),
        http.get(`/api/orgs/${ids.acme}/members`, () => HttpResponse.json(acmeMembers)),
    ];
}

export interface FeatureWorld {
    features: S['FeatureSummary'][];
    details: Map<string, S['Feature']>;
    tags?: S['Tag'][];
    /** Every request the handlers saw, as "METHOD path?query" with the parsed JSON body. */
    requests: Array<{key: string; body: unknown}>;
}

export function featureWorld(features: S['FeatureSummary'][], details: S['Feature'][], tags: S['Tag'][] = defaultTags): FeatureWorld {
    return {features, details: new Map(details.map((d) => [d.id, d])), tags, requests: []};
}

/** listFeatures (with archived and tag filters), getFeature and listTags for any project. */
export function scenarioReadHandlers(world: FeatureWorld) {
    return [
        http.get('/api/projects/:projectId/features', ({request}) => {
            const url = new URL(request.url);
            world.requests.push({key: `GET ${url.pathname}${url.search}`, body: null});
            const archived = url.searchParams.get('archived') === 'true';
            const tag = url.searchParams.get('tag');
            return HttpResponse.json(
                world.features.filter((f) => (f.archivedAt !== null) === archived && (tag === null || f.tags.includes(tag))),
            );
        }),
        http.get('/api/projects/:projectId/features/:featureId', ({params}) => {
            const found = world.details.get(String(params.featureId));
            if (!found) {
                return HttpResponse.json(
                    {type: 'about:blank', title: 'Not Found', status: 404, detail: 'No feature.', instance: '/x', code: 'FEATURE_NOT_FOUND'},
                    {status: 404, headers: {'Content-Type': 'application/problem+json'}},
                );
            }
            return HttpResponse.json(found);
        }),
        http.get('/api/projects/:projectId/tags', () => HttpResponse.json(world.tags ?? [])),
    ];
}

/** Batch C: every /api/orgs and invitation call answered by an in-memory auth-service (FakeAuth). */
export function fakeAuthHandlers(auth: FakeAuth) {
    return [
        http.all('/api/orgs*', async ({request}) => answerWith(auth, request)),
        http.all('/api/orgs/*', async ({request}) => answerWith(auth, request)),
        http.post('/api/auth/invitations/:token/accept', async ({request}) => answerWith(auth, request)),
    ];
}

async function answerWith(auth: FakeAuth, request: Request) {
    const text = await request.text();
    let body: unknown = null;
    try {
        body = text ? JSON.parse(text) : null;
    } catch {
        body = text;
    }
    const answer = auth.answer(request.method, new URL(request.url), body);
    if (!answer) return HttpResponse.json({type: 'about:blank', title: 'Not Found', status: 404, detail: 'x', instance: '/'}, {status: 404});
    if (answer.status === 204) return new HttpResponse(null, {status: 204});
    return new HttpResponse(JSON.stringify(answer.body), {status: answer.status, headers: {'Content-Type': answer.contentType}});
}

/** Batch D: a whole backend (auth, scenario and report) behind MSW, for component tests. */
export function fakeBackendHandlers(backend: FakeBackend) {
    return [
        http.all('/api/*', async ({request}) => {
            const text = await request.text();
            let body: unknown = null;
            try {
                body = text ? JSON.parse(text) : null;
            } catch {
                body = text;
            }
            const answer = backend.answer(request.method, new URL(request.url), body);
            if (!answer) return HttpResponse.json({type: 'about:blank', title: 'Not Found', status: 404, detail: 'x', instance: '/'}, {status: 404});
            if (answer.status === 204) return new HttpResponse(null, {status: 204});
            return new HttpResponse(JSON.stringify(answer.body), {status: answer.status, headers: {'Content-Type': answer.contentType}});
        }),
    ];
}

