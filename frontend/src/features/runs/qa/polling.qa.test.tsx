// qa (frontend phase 1): AC-8.7 / D9. Polling must stop when an open run closes while it is on screen, when the user
// leaves the page, and when the session ends; the runs list polls while visible and stops while hidden.
import {afterEach, describe, expect, it, vi} from 'vitest';
import {act, screen, waitFor} from '@testing-library/react';
import {focusManager} from '@tanstack/react-query';
import {server} from '../../../test/msw';
import {renderApp} from '../../../test/render';
import {tokenFor} from '../../../test/jwt';
import {ids} from '../../../test/fixtures/auth';
import {users, type UserKey} from '../../../test/fixtures/workspace';
import {FakeBackend} from '../../../test/fixtures/fakeBackend';
import {fakeBackendHandlers} from '../../../test/fixtures/handlers';
import {runIds} from '../../../test/fixtures/report';
import {endSession, startSession} from '../../../auth/session';

afterEach(() => {
    vi.useRealTimers();
    focusManager.setFocused(undefined);
});

const runsPath = `/projects/${ids.checkout}/runs`;

function open(user: UserKey, path: string) {
    const backend = new FakeBackend(user);
    server.use(...fakeBackendHandlers(backend));
    startSession(tokenFor(users[user].id, users[user].email, 3600));
    const app = renderApp(path);
    return {backend, app};
}

async function advance(ms: number) {
    await act(async () => {
        vi.advanceTimersByTime(ms);
    });
}

describe('qa: AC-8.7 polling stops', () => {
    it('an IN_PROGRESS run that becomes COMPLETED on the server stops polling after the refetch that sees it', async () => {
        vi.useFakeTimers({toFake: ['setInterval', 'clearInterval'], shouldAdvanceTime: true});
        const {backend} = open('dan', `${runsPath}/${runIds.sprint12}`);
        await screen.findByRole('region', {name: 'Run summary'});
        const gets = () => backend.report.requests.filter((r) => r.method === 'GET' && r.path.endsWith(runIds.sprint12)).length;
        const run = backend.report.runs.get(runIds.sprint12);
        if (!run) throw new Error('fixture run missing');
        run.fields = {...run.fields, status: 'COMPLETED', finishedAt: '2026-11-09T12:00:00Z'};
        const before = gets();
        await advance(30_000);
        await waitFor(() => expect(gets()).toBe(before + 1));
        const afterClose = gets();
        await advance(120_000);
        expect(gets()).toBe(afterClose);
    });

    it('the runs list polls every 30 s while visible, and not while hidden', async () => {
        vi.useFakeTimers({toFake: ['setInterval', 'clearInterval'], shouldAdvanceTime: true});
        const {backend} = open('dan', runsPath);
        await screen.findByRole('grid', {name: 'Runs'});
        const lists = () => backend.report.requests.filter((r) => r.method === 'GET' && /\/runs$/.test(r.path)).length;
        const start = lists();
        await advance(30_000);
        await waitFor(() => expect(lists()).toBe(start + 1));
        await advance(30_000);
        await waitFor(() => expect(lists()).toBe(start + 2));
        act(() => focusManager.setFocused(false));
        const hidden = lists();
        await advance(120_000);
        expect(lists()).toBe(hidden);
    });

    it('no request is sent after the session ends on an open run page', async () => {
        vi.useFakeTimers({toFake: ['setInterval', 'clearInterval'], shouldAdvanceTime: true});
        const {backend} = open('dan', `${runsPath}/${runIds.sprint12}`);
        await screen.findByRole('region', {name: 'Run summary'});
        act(() => endSession('logout'));
        await screen.findByRole('heading', {level: 1, name: /log in/i});
        const count = backend.report.requests.length;
        await advance(120_000);
        expect(backend.report.requests.length).toBe(count);
    });
});
