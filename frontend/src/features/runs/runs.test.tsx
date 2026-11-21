import {afterEach, describe, expect, it, vi} from 'vitest';
import {act, fireEvent, screen, waitFor, within} from '@testing-library/react';
import {focusManager} from '@tanstack/react-query';
import {server} from '../../test/msw';
import {renderApp} from '../../test/render';
import {tokenFor} from '../../test/jwt';
import {ids} from '../../test/fixtures/auth';
import {users, type UserKey} from '../../test/fixtures/workspace';
import {FakeBackend} from '../../test/fixtures/fakeBackend';
import {fakeBackendHandlers} from '../../test/fixtures/handlers';
import {runIds} from '../../test/fixtures/report';
import {startSession} from '../../auth/session';
import {countsTable, passRateTable} from './chartData';

afterEach(() => {
    vi.useRealTimers();
    focusManager.setFocused(undefined);
});

function open(user: UserKey, path: string, setup?: (backend: FakeBackend) => void) {
    const backend = new FakeBackend(user);
    setup?.(backend);
    server.use(...fakeBackendHandlers(backend));
    startSession(tokenFor(users[user].id, users[user].email, 3600));
    const app = renderApp(path);
    return {backend, app};
}

const runsPath = `/projects/${ids.checkout}/runs`;

// @US-8 @AC-8.1
describe('AC-8.1 runs list and dashboard', () => {
    it('lists runs newest first with status, total and pass rate', async () => {
        open('dan', runsPath);
        const table = await screen.findByRole('grid', {name: 'Runs'});
        const rows = within(table).getAllByRole('row').slice(1);
        expect(rows.map((r) => within(r).getAllByRole('rowheader')[0]?.textContent)).toEqual(['Sprint 12', 'Nightly']);
        expect(rows[1]?.textContent).toContain('Completed');
        expect(rows[1]?.textContent).toContain('50.0%');
        expect(rows[1]?.textContent).toContain('4');
    });

    it('the chart tables hold the same numbers as the data', async () => {
        open('dan', runsPath);
        await screen.findByRole('grid', {name: 'Runs'});
        fireEvent.click(screen.getByRole('button', {name: 'Show as a table: results by run'}));
        const counts = await screen.findByRole('grid', {name: 'Results by run'});
        const nightlyRow = within(counts).getAllByRole('row').find((r) => (r.textContent ?? '').startsWith('Nightly'));
        expect(within(nightlyRow ?? counts).getAllByRole('gridcell').map((c) => c.textContent)).toEqual(['2', '1', '0', '1', '0', '0', '0']);

        fireEvent.click(screen.getByRole('button', {name: 'Show as a table: pass rate'}));
        const rate = await screen.findByRole('grid', {name: 'Pass rate'});
        const rateRow = within(rate).getAllByRole('row').find((r) => (r.textContent ?? '').startsWith('Nightly'));
        expect(rateRow?.textContent).toContain('50.0%');
    });

    it('the chart data helpers match the runs exactly (oldest first, null rate shown as a dash)', () => {
        const items = [
            {name: 'B', counts: {PASSED: 1, FAILED: 0, SKIPPED: 0, PENDING: 0, UNDEFINED: 0, AMBIGUOUS: 0, UNKNOWN: 3}, passRate: null},
            {name: 'A', counts: {PASSED: 2, FAILED: 1, SKIPPED: 0, PENDING: 1, UNDEFINED: 0, AMBIGUOUS: 0, UNKNOWN: 0}, passRate: 50},
        ];
        expect(countsTable(items).map((r) => [r.name, r.values])).toEqual([
            ['A', [2, 1, 0, 1, 0, 0, 0]],
            ['B', [1, 0, 0, 0, 0, 0, 3]],
        ]);
        expect(passRateTable(items)).toEqual([
            {name: 'A', rate: '50.0%'},
            {name: 'B', rate: '–'},
        ]);
    });

    it('"Next page" requests page 1 with size 20', async () => {
        const {backend} = open('dan', runsPath, (b) => b.report.addManyRuns(25));
        await screen.findByRole('grid', {name: 'Runs'});
        expect(screen.getByText('1–20 of 27')).toBeTruthy();
        fireEvent.click(screen.getByRole('button', {name: 'Next page'}));
        await screen.findByText('21–27 of 27');
        expect(backend.report.requests.some((r) => r.method === 'GET' && r.path.endsWith('/runs') && new URLSearchParams(r.search).get('page') === '1' && new URLSearchParams(r.search).get('size') === '20')).toBe(true);
    });

    it('empty: "No runs yet", with the CI hint for an ADMIN only', async () => {
        open('ana', runsPath, (b) => b.report.runs.clear());
        expect(await screen.findByText('No runs yet')).toBeTruthy();
        expect(screen.getByRole('link', {name: 'Create an upload token'})).toBeTruthy();
    });
});

// @US-8 @AC-8.2
describe('AC-8.2 run detail and results', () => {
    it('shows the summary and the per-feature table', async () => {
        open('dan', `${runsPath}/${runIds.nightly}`);
        const summary = await screen.findByRole('region', {name: 'Run summary'});
        await waitFor(() => expect(summary.textContent).toContain('50.0%'));
        expect(summary.textContent).toMatch(/Executed\s*4/);
        const counts = within(summary).getByRole('list', {name: 'Results by status'});
        expect(within(counts).getByText('Passed').closest('li')?.textContent).toContain('2');
        expect(within(counts).getByText('Failed').closest('li')?.textContent).toContain('1');
        expect(within(counts).getByText('Pending').closest('li')?.textContent).toContain('1');
        const byFeature = screen.getByRole('grid', {name: 'Results by feature'});
        expect(within(byFeature).getAllByRole('row')).toHaveLength(3);
    });

    it('filters by FAILED and opens a result with its steps; the error is behind "Show error"', async () => {
        const {backend} = open('dan', `${runsPath}/${runIds.nightly}`);
        fireEvent.click(await screen.findByRole('tab', {name: 'Results'}));
        fireEvent.click(await screen.findByRole('button', {name: /Status filter/}));
        fireEvent.click(await screen.findByRole('option', {name: 'Failed'}));
        await waitFor(() => expect(backend.report.requests.some((r) => r.path.endsWith('/results') && new URLSearchParams(r.search).get('status') === 'FAILED')).toBe(true));
        const results = await screen.findByRole('grid', {name: 'Results'});
        await waitFor(() => expect(within(results).getAllByRole('row')).toHaveLength(2));
        fireEvent.click(within(results).getByRole('button', {name: 'Open Pay by voucher'}));
        const dialog = await screen.findByRole('dialog', {name: 'Pay by voucher'});
        const stepTexts = (await within(dialog).findAllByRole('listitem')).map((li) => li.textContent ?? '');
        expect(stepTexts[0]).toContain('Before hook');
        expect(stepTexts[1]).toContain('a cart with 2 items');
        expect(stepTexts[2]).toContain('I pay by voucher');
        expect(within(dialog).queryByText(/AssertionError/)).toBeNull();
        fireEvent.click(within(dialog).getByRole('button', {name: 'Show error'}));
        expect(await within(dialog).findByText(/AssertionError/)).toBeTruthy();
    });
});

// @US-8 @AC-8.7
describe('AC-8.7 open runs refresh', () => {
    it('refetches an IN_PROGRESS run every 30 s, and not while the tab is hidden', async () => {
        vi.useFakeTimers({toFake: ['setInterval', 'clearInterval'], shouldAdvanceTime: true});
        const {backend} = open('dan', `${runsPath}/${runIds.sprint12}`);
        await screen.findByRole('region', {name: 'Run summary'});
        const gets = () => backend.report.requests.filter((r) => r.method === 'GET' && r.path.endsWith(runIds.sprint12)).length;
        const before = gets();
        await act(async () => {
            vi.advanceTimersByTime(30_000);
        });
        await waitFor(() => expect(gets()).toBe(before + 1));
        act(() => focusManager.setFocused(false));
        const hidden = gets();
        await act(async () => {
            vi.advanceTimersByTime(90_000);
        });
        expect(gets()).toBe(hidden);
    });

    it('does not poll a COMPLETED run', async () => {
        vi.useFakeTimers({toFake: ['setInterval', 'clearInterval'], shouldAdvanceTime: true});
        const {backend} = open('dan', `${runsPath}/${runIds.nightly}`);
        await screen.findByRole('region', {name: 'Run summary'});
        const gets = () => backend.report.requests.filter((r) => r.method === 'GET' && r.path.endsWith(runIds.nightly)).length;
        const before = gets();
        await act(async () => {
            vi.advanceTimersByTime(90_000);
        });
        expect(gets()).toBe(before);
    });
});
