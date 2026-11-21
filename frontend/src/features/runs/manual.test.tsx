import {describe, expect, it} from 'vitest';
import {fireEvent, screen, waitFor, within} from '@testing-library/react';
import {server} from '../../test/msw';
import {renderApp} from '../../test/render';
import {tokenFor} from '../../test/jwt';
import {ids} from '../../test/fixtures/auth';
import {users, type UserKey} from '../../test/fixtures/workspace';
import {FakeBackend} from '../../test/fixtures/fakeBackend';
import {fakeBackendHandlers} from '../../test/fixtures/handlers';
import {featureIds} from '../../test/fixtures/scenario';
import {reportProblem, runIds} from '../../test/fixtures/report';
import {startSession} from '../../auth/session';

function open(user: UserKey, path: string, setup?: (backend: FakeBackend) => void) {
    const backend = new FakeBackend(user);
    setup?.(backend);
    server.use(...fakeBackendHandlers(backend));
    startSession(tokenFor(users[user].id, users[user].email, 3600));
    const app = renderApp(path);
    return {backend, app};
}

const runsPath = `/projects/${ids.checkout}/runs`;
const sprint12 = `${runsPath}/${runIds.sprint12}`;
const planPath = `/api/projects/${ids.checkout}/runs`;

async function openPlan() {
    fireEvent.click(await screen.findByRole('button', {name: 'Plan manual run'}));
    const dialog = await screen.findByRole('dialog', {name: 'Plan a manual run'});
    fireEvent.change(within(dialog).getByLabelText('Run name'), {target: {value: 'Sprint 13'}});
    fireEvent.click(await within(dialog).findByRole('checkbox', {name: /payment\.feature/}));
    fireEvent.click(within(dialog).getByRole('checkbox', {name: /address\.feature/}));
    return dialog;
}

// @US-8 @AC-8.6
describe('AC-8.6 report actions by project role', () => {
    it.each([
        ['dan', false, false],
        ['hal', true, false],
        ['gus', true, true],
    ] as const)('%s: record and complete %s, plan and cancel %s', async (user, record, planCancel) => {
        open(user, sprint12);
        fireEvent.click(await screen.findByRole('tab', {name: 'Results'}));
        await screen.findByRole('grid', {name: 'Results'});
        expect(screen.queryAllByRole('button', {name: /^Record /}).length > 0).toBe(record);
        expect(screen.queryAllByRole('button', {name: 'Complete run'}).length > 0).toBe(record);
        expect(screen.queryAllByRole('button', {name: 'Cancel run'}).length > 0).toBe(planCancel);
    });

    it.each([
        ['hal', false],
        ['gus', true],
    ] as const)('%s: plan shown=%s on the runs list', async (user, shown) => {
        open(user, runsPath);
        await screen.findByRole('grid', {name: 'Runs'});
        expect(screen.queryAllByRole('button', {name: 'Plan manual run'}).length > 0).toBe(shown);
    });

    it.each([
        ['gus', false],
        ['ana', true],
    ] as const)('%s: upload tokens shown=%s', async (user, shown) => {
        open(user, runsPath);
        await screen.findByRole('grid', {name: 'Runs'});
        expect(screen.queryAllByRole('link', {name: 'Upload tokens'}).length > 0).toBe(shown);
    });
});

// @US-8 @AC-8.3
describe('AC-8.3 plan a manual run', () => {
    it('sends the featureIds in the order picked, with the tag and environment, then opens the PLANNED run', async () => {
        const {backend, app} = open('gus', runsPath);
        const dialog = await openPlan();
        fireEvent.change(within(dialog).getByLabelText('Tag (optional)'), {target: {value: '@smoke'}});
        fireEvent.change(within(dialog).getByLabelText('Environment (optional)'), {target: {value: 'staging'}});
        fireEvent.click(within(dialog).getByRole('button', {name: 'Plan run'}));
        await waitFor(() => expect(app.router.state.location.pathname).toMatch(new RegExp(`${runsPath}/[0-9a-f-]{36}$`)));
        const post = backend.report.requests.find((r) => r.method === 'POST' && r.path === planPath);
        expect(post?.body).toEqual({name: 'Sprint 13', featureIds: [featureIds.payment, featureIds.address], tag: '@smoke', environment: 'staging'});
        expect(await screen.findByText('Planned')).toBeTruthy();
    });

    it.each([
        ['RUN_EMPTY', 'No test case matches'],
        ['RUN_TOO_LARGE', 'More than 2,000 test cases'],
    ] as const)('422 %s says "%s"', async (code, message) => {
        open('gus', runsPath, (b) => b.report.overrides.set(`POST ${planPath}`, {status: 422, contentType: 'application/problem+json', body: reportProblem(422, code, planPath)}));
        const dialog = await openPlan();
        fireEvent.click(within(dialog).getByRole('button', {name: 'Plan run'}));
        expect(await within(dialog).findByText(message)).toBeTruthy();
    });

    it('422 FEATURE_NOT_PLANNABLE marks each id from featureIds in the picker', async () => {
        open('gus', runsPath, (b) =>
            b.report.overrides.set(`POST ${planPath}`, {
                status: 422,
                contentType: 'application/problem+json',
                body: reportProblem(422, 'FEATURE_NOT_PLANNABLE', planPath, {featureIds: [featureIds.payment]}),
            }),
        );
        const dialog = await openPlan();
        fireEvent.click(within(dialog).getByRole('button', {name: 'Plan run'}));
        const payment = await within(dialog).findByRole('checkbox', {name: /payment\.feature/});
        await waitFor(() => expect(payment.getAttribute('aria-invalid')).toBe('true'));
        expect(within(dialog).getByRole('checkbox', {name: /address\.feature/}).getAttribute('aria-invalid')).not.toBe('true');
        expect(within(dialog).getByRole('checkbox', {name: /payment\.feature/}).closest('li')?.textContent).toContain("Can't be planned");
    });

    it('503 SCENARIO_SERVICE_UNAVAILABLE shows the error state with Retry', async () => {
        open('gus', runsPath, (b) => b.report.overrides.set(`POST ${planPath}`, {status: 503, contentType: 'application/problem+json', body: reportProblem(503, 'SCENARIO_SERVICE_UNAVAILABLE', planPath)}));
        const dialog = await openPlan();
        fireEvent.click(within(dialog).getByRole('button', {name: 'Plan run'}));
        const alert = await within(dialog).findByRole('alert');
        expect(alert.textContent).toContain('The service is unavailable. Try again.');
        expect(within(alert).getByRole('button', {name: 'Retry'})).toBeTruthy();
    });
});

// @US-8 @AC-8.4
describe('AC-8.4 record, complete and cancel', () => {
    it('Hal records FAILED with a comment, then completes; controls go away', async () => {
        open('hal', sprint12);
        fireEvent.click(await screen.findByRole('tab', {name: 'Results'}));
        const results = await screen.findByRole('grid', {name: 'Results'});
        fireEvent.click(within(results).getByRole('button', {name: 'Record Pay by voucher'}));
        const dialog = await screen.findByRole('dialog', {name: 'Pay by voucher'});
        fireEvent.click(within(dialog).getByRole('radio', {name: 'Failed'}));
        fireEvent.change(within(dialog).getByLabelText('Comment (optional)'), {target: {value: 'Card declined'}});
        fireEvent.click(within(dialog).getByRole('button', {name: 'Save result'}));
        await waitFor(() => expect(screen.queryByRole('dialog', {name: 'Pay by voucher'})).toBeNull());
        const row = within(await screen.findByRole('grid', {name: 'Results'})).getAllByRole('row').find((r) => (r.textContent ?? '').includes('Pay by voucher'));
        await waitFor(() => expect(row?.textContent).toContain('Failed'));
        await waitFor(() => expect(within(screen.getByRole('grid', {name: 'Results'})).getAllByRole('row').find((r) => (r.textContent ?? '').includes('Pay by voucher'))?.textContent).toContain('Hal Jordan'));
        expect(screen.getByText('In progress')).toBeTruthy();

        fireEvent.click(screen.getByRole('button', {name: 'Complete run'}));
        fireEvent.click(within(await screen.findByRole('alertdialog')).getByRole('button', {name: 'Complete run'}));
        expect(await screen.findByText('Completed')).toBeTruthy();
        await waitFor(() => expect(screen.queryAllByRole('button', {name: /^Record /})).toHaveLength(0));
    });

    it('a 409 RUN_CLOSED refetches the run', async () => {
        const {backend} = open('hal', sprint12);
        fireEvent.click(await screen.findByRole('tab', {name: 'Results'}));
        const results = await screen.findByRole('grid', {name: 'Results'});
        const run = backend.report.runs.get(runIds.sprint12);
        if (run) run.fields = {...run.fields, status: 'CANCELLED'};
        fireEvent.click(within(results).getByRole('button', {name: 'Record Pay by voucher'}));
        const dialog = await screen.findByRole('dialog', {name: 'Pay by voucher'});
        fireEvent.click(within(dialog).getByRole('radio', {name: 'Passed'}));
        fireEvent.click(within(dialog).getByRole('button', {name: 'Save result'}));
        expect(await screen.findByText('Cancelled')).toBeTruthy();
    });
});
