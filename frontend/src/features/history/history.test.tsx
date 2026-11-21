import type {ReactNode} from 'react';
import {beforeAll, describe, expect, it} from 'vitest';
import {fireEvent, render, screen, within} from '@testing-library/react';
import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {http, HttpResponse} from 'msw';
import type {components} from '../../api/generated/scenario-service';
import {server, problemHeaders} from '../../test/msw';
import {ids} from '../../test/fixtures/auth';
import {acmeMembers, userIds} from '../../test/fixtures/workspace';
import {payment, paymentSourceV2, scenarioProblem} from '../../test/fixtures/scenario';
import {HistoryPanel} from './HistoryPanel';
import {TestCasesPanel} from './TestCasesPanel';

type S = components['schemas'];

beforeAll(() => {
    Range.prototype.getClientRects = function getClientRects() {
        return Object.assign(Object.create(DOMRectList.prototype ?? Object.prototype), {length: 0, item: () => null});
    };
    Range.prototype.getBoundingClientRect = function getBoundingClientRect() {
        return new DOMRect(0, 0, 0, 0);
    };
});

const base = `/api/projects/${ids.checkout}/features/${payment.id}`;
const versions: S['FeatureVersionSummary'][] = [
    {version: 2, message: 'Clarify card step', createdBy: userIds.gus, createdAt: '2026-10-28T14:30:00.25Z'},
    {version: 1, message: null, createdBy: '11111111-2222-4333-8444-555555555555', createdAt: '2026-10-05T09:00:00Z'},
];

function renderWith(node: ReactNode) {
    const client = new QueryClient({defaultOptions: {queries: {retry: false}}});
    return render(<QueryClientProvider client={client}>{node}</QueryClientProvider>);
}

function serve(versionAnswer: (n: string) => Response) {
    server.use(
        http.get(`${base}/versions`, () => HttpResponse.json(versions)),
        http.get(`${base}/versions/:version`, ({params}) => versionAnswer(String(params.version))),
        http.get(`/api/orgs/${ids.acme}/members`, () => HttpResponse.json(acmeMembers)),
    );
}

// @US-7 @AC-7.1
describe('AC-7.1 version history', () => {
    it('lists versions newest first with author name, time and message; a former member is named as such', async () => {
        serve(() => HttpResponse.json({}));
        renderWith(<HistoryPanel projectId={ids.checkout} featureId={payment.id} orgId={ids.acme}/>);
        const table = await screen.findByRole('grid', {name: 'Versions'});
        const rows = within(table).getAllByRole('row').slice(1);
        expect(rows.map((r) => within(r).getAllByRole('rowheader')[0]?.textContent)).toEqual(['Version 2', 'Version 1']);
        expect(await within(rows[0] ?? table).findByText('Gus Underwood')).toBeTruthy();
        expect(within(rows[0] ?? table).getByText('Clarify card step')).toBeTruthy();
        expect(within(rows[1] ?? table).getByText('Former member')).toBeTruthy();
        expect(within(rows[0] ?? table).getByRole('time').getAttribute('datetime')).toBe('2026-10-28T14:30:00.25Z');
    });

    it('opens version 1 read-only, "Version 1 of 2"', async () => {
        serve(() => HttpResponse.json({...versions[1], source: paymentSourceV2.replace('Then', 'And')}));
        renderWith(<HistoryPanel projectId={ids.checkout} featureId={payment.id} orgId={ids.acme}/>);
        fireEvent.click(await screen.findByRole('button', {name: 'Open version 1'}));
        expect(await screen.findByText('Version 1 of 2')).toBeTruthy();
        const content = await screen.findByRole('textbox', {name: 'Version 1 source'});
        expect(content.getAttribute('aria-readonly')).toBe('true');
        expect(content.textContent).toContain('And the order is confirmed');
    });

    it('a version that answers 404 shows the not-found state', async () => {
        serve(() =>
            HttpResponse.json(scenarioProblem(404, 'VERSION_NOT_FOUND', `${base}/versions/1`), {status: 404, headers: problemHeaders}),
        );
        renderWith(<HistoryPanel projectId={ids.checkout} featureId={payment.id} orgId={ids.acme}/>);
        fireEvent.click(await screen.findByRole('button', {name: 'Open version 1'}));
        const alert = await screen.findByRole('alert');
        expect(alert.textContent).toContain("This version doesn't exist");
        expect(within(alert).getByRole('button', {name: 'Back to history'})).toBeTruthy();
    });
});

// @US-7 @AC-7.2
describe('AC-7.2 test cases', () => {
    it('lists name, kind, line and tags in the API order; an example row shows its scenario name', async () => {
        const cases: S['TestCase'][] = [
            {id: 'a0000000-0000-4000-8000-000000000001', type: 'SCENARIO', scenarioId: 'b0000000-0000-4000-8000-000000000001', exampleRowId: null, name: 'Pay by card', tags: ['@checkout'], line: 4, steps: []},
            {id: 'a0000000-0000-4000-8000-000000000002', type: 'EXAMPLE_ROW', scenarioId: 'b0000000-0000-4000-8000-000000000002', exampleRowId: 'c0000000-0000-4000-8000-000000000001', name: 'Pay with <method>', tags: ['@checkout', '@smoke'], line: 15, steps: []},
        ];
        server.use(http.get(`${base}/test-cases`, () => HttpResponse.json(cases)));
        renderWith(<TestCasesPanel projectId={ids.checkout} featureId={payment.id}/>);
        const table = await screen.findByRole('grid', {name: 'Test cases'});
        const rows = within(table).getAllByRole('row').slice(1);
        expect(rows).toHaveLength(2);
        expect(rows[0]?.textContent).toContain('Pay by card');
        expect(rows[0]?.textContent).toContain('Scenario');
        expect(rows[0]?.textContent).toContain('4');
        expect(rows[1]?.textContent).toContain('Pay with <method>');
        expect(rows[1]?.textContent).toContain('Example row');
        expect(rows[1]?.textContent).toContain('15');
        expect(rows[1]?.textContent).toContain('@smoke');
    });
});
