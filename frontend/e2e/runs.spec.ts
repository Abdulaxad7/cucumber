import {expect, test} from '@playwright/test';
import {ids} from '../src/test/fixtures/auth';
import {runIds} from '../src/test/fixtures/report';
import {expectNoAxeViolations, openAs} from './reportHarness';

const runsPath = `/projects/${ids.checkout}/runs`;

// @US-8 @AC-8.1
test('AC-8.1 runs list, charts with table alternatives, next page', async ({page}) => {
    const backend = await openAs(page, 'dan', runsPath, (b) => b.report.addManyRuns(25));
    const table = page.getByRole('grid', {name: 'Runs'});
    await expect(table.getByRole('row').nth(1)).toContainText('Sprint 12');
    await expect(table.getByRole('row').nth(2)).toContainText('Nightly');
    await expect(table.getByRole('row').nth(2)).toContainText('50.0%');
    await expect(page.getByRole('img', {name: /Stacked bar chart/})).toBeVisible();
    await expect(page.locator('[role=img] svg').first()).toBeVisible();
    await page.getByRole('button', {name: /^Show as a table\s*:\s*results by run$/}).click();
    await expect(page.getByRole('grid', {name: 'Results by run'}).getByRole('row', {name: 'Nightly', exact: true})).toContainText('2101000');
    await page.getByRole('button', {name: /^Show as a table\s*:\s*pass rate$/}).click();
    await expect(page.getByRole('grid', {name: 'Pass rate'}).getByRole('row', {name: 'Nightly', exact: true})).toContainText('50.0%');
    await page.getByRole('button', {name: 'Next page'}).click();
    await expect(page.getByText('21–27 of 27')).toBeVisible();
    expect(backend.report.requests.some((r) => r.path.endsWith('/runs') && r.search.includes('page=1') && r.search.includes('size=20'))).toBe(true);
});

// @US-8 @AC-8.2
test('AC-8.2 run detail, FAILED filter and a result with a collapsed error', async ({page}) => {
    const backend = await openAs(page, 'dan', `${runsPath}/${runIds.nightly}`);
    const summary = page.getByRole('region', {name: 'Run summary'});
    await expect(summary).toContainText('50.0%');
    await expect(page.getByRole('grid', {name: 'Results by feature'})).toContainText('features/checkout/payment.feature');
    await page.getByRole('tab', {name: 'Results'}).click();
    await page.getByRole('button', {name: /Status filter/}).click();
    await page.getByRole('option', {name: 'Failed'}).click();
    await expect(page.getByRole('grid', {name: 'Results'}).getByRole('row')).toHaveCount(2);
    expect(backend.report.requests.some((r) => r.path.endsWith('/results') && r.search.includes('status=FAILED'))).toBe(true);
    await page.getByRole('button', {name: 'Open Pay by voucher'}).click();
    const dialog = page.getByRole('dialog', {name: 'Pay by voucher'});
    await expect(dialog.getByRole('listitem')).toHaveCount(4);
    await expect(dialog.getByText(/AssertionError/)).toHaveCount(0);
    await expectNoAxeViolations(page);
    await dialog.getByRole('button', {name: 'Show error'}).click();
    await expect(dialog.getByText(/AssertionError/)).toBeVisible();
});

// @US-8 @AC-8.6 and @AC-9.3 in both themes.
for (const theme of ['light', 'dark'] as const) {
    test(`AC-9.3 run pages pass axe, ${theme}`, async ({page}) => {
        await page.addInitScript((value) => localStorage.setItem('cucumber.ui.theme', value), theme);
        await openAs(page, 'gus', runsPath);
        await expect(page.getByRole('grid', {name: 'Runs'})).toBeVisible();
        await expect(page.locator('[role=img] svg').first()).toBeVisible();
        await expectNoAxeViolations(page);
        await page.getByRole('button', {name: /^Show as a table\s*:\s*results by run$/}).click();
        await expectNoAxeViolations(page);
        await page.goto(`${runsPath}/${runIds.sprint12}`);
        await expect(page.getByRole('region', {name: 'Run summary'})).toBeVisible();
        await expectNoAxeViolations(page);
        await page.getByRole('tab', {name: 'Results'}).click();
        await expect(page.getByRole('grid', {name: 'Results'})).toBeVisible();
        await expectNoAxeViolations(page);
    });

    test(`AC-9.2 the empty runs list, with the CI hint for an admin, ${theme}`, async ({page}) => {
        await page.addInitScript((value) => localStorage.setItem('cucumber.ui.theme', value), theme);
        await openAs(page, 'ana', runsPath, (b) => b.report.runs.clear());
        await expect(page.getByText('No runs yet')).toBeVisible();
        await expect(page.getByRole('link', {name: 'Create an upload token'})).toBeVisible();
        await expectNoAxeViolations(page);
    });
}
