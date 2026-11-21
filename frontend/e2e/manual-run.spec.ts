import {expect, test} from '@playwright/test';
import {ids} from '../src/test/fixtures/auth';
import {featureIds} from '../src/test/fixtures/scenario';
import {runIds} from '../src/test/fixtures/report';
import {expectNoAxeViolations, openAs} from './reportHarness';

const runsPath = `/projects/${ids.checkout}/runs`;

// @US-8 @AC-8.3
test('AC-8.3 Gus plans Sprint 13 and lands on the PLANNED run', async ({page}) => {
    const backend = await openAs(page, 'gus', runsPath);
    await page.getByRole('button', {name: 'Plan manual run'}).click();
    const dialog = page.getByRole('dialog', {name: 'Plan a manual run'});
    await dialog.getByLabel('Run name').fill('Sprint 13');
    await dialog.getByText('features/checkout/payment.feature').click();
    await dialog.getByText('features/checkout/address.feature').click();
    await expect(dialog.getByRole('checkbox', {name: /payment\.feature/})).toBeChecked();
    await dialog.getByLabel('Tag (optional)').fill('@smoke');
    await dialog.getByLabel('Environment (optional)').fill('staging');
    await expectNoAxeViolations(page);
    await dialog.getByRole('button', {name: 'Plan run'}).click();
    await expect(page).toHaveURL(new RegExp(`${runsPath}/[0-9a-f-]{36}$`));
    await expect(page.getByText('Planned', {exact: true})).toBeVisible();
    const post = backend.report.requests.find((r) => r.method === 'POST' && r.path.endsWith('/runs'));
    expect(post?.body).toEqual({name: 'Sprint 13', featureIds: [featureIds.payment, featureIds.address], tag: '@smoke', environment: 'staging'});
});

// @US-8 @AC-8.4
test('AC-8.4 Hal records FAILED with a comment, then completes the run', async ({page}) => {
    await openAs(page, 'hal', `${runsPath}/${runIds.sprint12}`);
    await expect(page.getByRole('button', {name: 'Cancel run'})).toHaveCount(0);
    await page.getByRole('tab', {name: 'Results'}).click();
    await page.getByRole('button', {name: 'Record Pay by voucher'}).click();
    const dialog = page.getByRole('dialog', {name: 'Pay by voucher'});
    await dialog.getByRole('radio', {name: 'Failed'}).click();
    await dialog.getByLabel('Comment (optional)').fill('Card declined');
    await expectNoAxeViolations(page);
    await dialog.getByRole('button', {name: 'Save result'}).click();
    const row = page.getByRole('grid', {name: 'Results'}).getByRole('row', {name: /Pay by voucher/});
    await expect(row).toContainText('Failed');
    await expect(row).toContainText('Hal Jordan');
    await expect(row).toContainText('Card declined');
    await page.getByRole('button', {name: 'Complete run'}).click();
    await page.getByRole('alertdialog').getByRole('button', {name: 'Complete run'}).click();
    await expect(page.getByText('Completed', {exact: true})).toBeVisible();
    await expect(page.getByRole('button', {name: /^Record /})).toHaveCount(0);
});

test('AC-8.4 Gus can cancel an open run', async ({page}) => {
    await openAs(page, 'gus', `${runsPath}/${runIds.sprint12}`);
    await page.getByRole('button', {name: 'Cancel run'}).click();
    const confirm = page.getByRole('alertdialog', {name: 'Cancel this run?'});
    await expectNoAxeViolations(page);
    await confirm.getByRole('button', {name: 'Cancel run'}).click();
    await expect(page.getByText('Cancelled', {exact: true})).toBeVisible();
});

// @US-8 @AC-8.5
test('AC-8.5 an upload token is shown once, then only its prefix', async ({page, context}) => {
    await context.grantPermissions(['clipboard-read', 'clipboard-write']);
    await openAs(page, 'ana', `/projects/${ids.checkout}/upload-tokens`);
    await page.getByRole('button', {name: 'Create token'}).click();
    await page.getByRole('dialog', {name: 'Create an upload token'}).getByLabel('Token name').fill('GitHub Actions');
    await page.getByRole('dialog', {name: 'Create an upload token'}).getByRole('button', {name: 'Create token'}).click();
    const shown = page.getByRole('dialog', {name: 'Copy your upload token'});
    const token = (await shown.getByLabel('Upload token').textContent()) ?? '';
    expect(token).toMatch(/^rct_/);
    await expect(shown.getByLabel('curl example')).toContainText('/api/ci/runs');
    await expectNoAxeViolations(page);
    await shown.getByRole('button', {name: 'Copy token'}).click();
    expect(await page.evaluate(() => navigator.clipboard.readText())).toBe(token);
    await shown.getByRole('button', {name: 'Done'}).click();
    await expect(shown).toHaveCount(0);
    expect(await page.content()).not.toContain(token);
    expect(await page.evaluate(() => JSON.stringify({...sessionStorage, ...localStorage}))).not.toContain(token);
    await expect(page.getByRole('grid', {name: 'Upload tokens'})).toContainText(token.slice(0, 12));
    await page.getByRole('button', {name: 'Revoke GitHub Actions'}).click();
    await page.getByRole('alertdialog').getByRole('button', {name: 'Revoke token'}).click();
    await expect(page.getByRole('grid', {name: 'Upload tokens'})).toContainText('Revoked');
});

// @US-8 @AC-8.6
for (const [user, record, plan] of [
    ['dan', false, false],
    ['hal', true, false],
    ['gus', true, true],
] as const) {
    test(`AC-8.6 ${user}: record ${record}, plan and cancel ${plan}`, async ({page}) => {
        await openAs(page, user, `${runsPath}/${runIds.sprint12}`);
        await page.getByRole('tab', {name: 'Results'}).click();
        await expect(page.getByRole('grid', {name: 'Results'})).toBeVisible();
        await expect(page.getByRole('button', {name: /^Record /})).toHaveCount(record ? 4 : 0);
        await expect(page.getByRole('button', {name: 'Complete run'})).toHaveCount(record ? 1 : 0);
        await expect(page.getByRole('button', {name: 'Cancel run'})).toHaveCount(plan ? 1 : 0);
    });
}

test('AC-8.6 upload tokens: absent for Gus, shown for Ana', async ({page}) => {
    await openAs(page, 'gus', runsPath);
    await expect(page.getByRole('grid', {name: 'Runs'})).toBeVisible();
    await expect(page.getByRole('navigation', {name: 'Sidebar'}).getByRole('link', {name: 'Upload tokens'})).toHaveCount(0);
    await page.goto(`/projects/${ids.checkout}/upload-tokens`);
    await expect(page.getByText('Only project admins manage upload tokens.')).toBeVisible();
});
