// qa (frontend phase 1): AC-9.1 rendered. Each response row on a real data view (the org list), with its sentence,
// Retry where the spec says so, and "Reference: <requestId>" when the Problem carries one; a hostile requestId or
// detail is never rendered.
import {expect, test, type Route} from '@playwright/test';
import {openAs} from '../reportHarness';

const PJ = 'application/problem+json';

function problem(status: number, extra: Record<string, unknown> = {}) {
    return JSON.stringify({type: 'about:blank', title: 'T', status, detail: 'QaServerDetail', instance: '/api/orgs', ...extra});
}

type Answer = (route: Route) => Promise<void>;

const ROWS: Array<{name: string; answer: Answer; text: string; retry: boolean; reference: string | null}> = [
    {name: '403 without code', answer: (r) => r.fulfill({status: 403, contentType: PJ, body: problem(403, {requestId: 'rid-403'})}), text: 'The request was refused', retry: false, reference: 'rid-403'},
    {name: '409 CONFLICT', answer: (r) => r.fulfill({status: 409, contentType: PJ, body: problem(409, {code: 'CONFLICT', requestId: 'rid-409'})}), text: 'Someone else is changing this. Try again.', retry: true, reference: 'rid-409'},
    {name: '400 MALFORMED_REQUEST', answer: (r) => r.fulfill({status: 400, contentType: PJ, body: problem(400, {code: 'MALFORMED_REQUEST', requestId: 'rid-400'})}), text: 'The request could not be read.', retry: false, reference: 'rid-400'},
    {name: '502 without code', answer: (r) => r.fulfill({status: 502, contentType: PJ, body: problem(502, {requestId: 'rid-502'})}), text: 'The service is unavailable. Try again.', retry: true, reference: 'rid-502'},
    {name: '504 HTML body', answer: (r) => r.fulfill({status: 504, contentType: 'text/html', body: '<html>gateway timeout</html>'}), text: 'The service is unavailable. Try again.', retry: true, reference: null},
    {name: '500 INTERNAL_ERROR', answer: (r) => r.fulfill({status: 500, contentType: PJ, body: problem(500, {code: 'INTERNAL_ERROR', requestId: 'rid-500'})}), text: 'Something went wrong', retry: false, reference: 'rid-500'},
    {name: 'network failure', answer: (r) => r.abort('connectionrefused'), text: "Can't reach the server", retry: true, reference: null},
    {name: 'non-Problem body', answer: (r) => r.fulfill({status: 418, contentType: 'text/plain', body: 'teapot'}), text: "Can't reach the server", retry: true, reference: null},
    {name: 'hostile requestId', answer: (r) => r.fulfill({status: 500, contentType: PJ, body: problem(500, {requestId: '<img src=x onerror=alert(1)>'})}), text: 'Something went wrong', retry: false, reference: null},
];

for (const row of ROWS) {
    test(`qa: AC-9.1 ${row.name} on the org list`, async ({page}) => {
        const dialogs: string[] = [];
        page.on('dialog', (d) => {
            dialogs.push(d.message());
            void d.dismiss();
        });
        await openAs(page, 'gus', '/orgs');
        await expect(page.getByText('Acme').first()).toBeVisible();
        let calls = 0;
        await page.route('**/api/orgs', (route) => {
            calls += 1;
            return row.answer(route);
        });
        await page.reload();
        const alert = page.getByRole('alert').filter({hasText: row.text});
        await expect(alert).toBeVisible();
        if (row.reference) await expect(page.getByText(`Reference: ${row.reference}`)).toBeVisible();
        else await expect(page.getByText(/Reference:/)).toHaveCount(0);
        await expect(page.getByRole('button', {name: 'Retry'})).toHaveCount(row.retry ? 1 : 0);
        await expect(page.getByText('QaServerDetail')).toHaveCount(0);
        expect(dialogs).toEqual([]);
        // D9: queries retry once, and only for a network failure, 502, 503 or 504
        expect(calls).toBe(row.retry && row.name !== '409 CONFLICT' ? 2 : 1);
    });
}
