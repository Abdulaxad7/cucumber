// qa (frontend phase 1): robustness. Long names at both widths don't push the page sideways; markup in names is shown
// as text and never runs; a 2,000-feature tree with deep folders stays small in the DOM at 390 px and is ready fast
// (NFR: interactive within 1 s, informational; asserted with headroom).
import {expect, test, type Page} from '@playwright/test';
import {openAs} from '../reportHarness';
import {ids} from '../../src/test/fixtures/auth';
import {runIds} from '../../src/test/fixtures/report';
import {detail, manyFeatures, summary} from '../../src/test/fixtures/scenario';

const checkout = `/projects/${ids.checkout}`;
const LONG = 'Ü'.repeat(40) + 'x'.repeat(160);
const XSS = '<img src=x onerror="window.__qaXss=1">';

async function noSidewaysScroll(page: Page) {
    const {scroll, client} = await page.evaluate(() => ({
        scroll: document.documentElement.scrollWidth,
        client: document.documentElement.clientWidth,
    }));
    expect(scroll).toBeLessThanOrEqual(client + 1);
}

for (const [width, height] of [[1440, 900], [390, 844]] as const) {
    test.describe(`qa: long names at ${width}`, () => {
        test.beforeEach(async ({page}) => page.setViewportSize({width, height}));

        test('features tree and editor header', async ({page}) => {
            await openAs(page, 'gus', `${checkout}/features`, (b) => {
                const project = b.auth.projects.find((p) => p.id === ids.checkout);
                if (project) project.name = 'P'.repeat(100);
                b.extraSummaries = [summary('00000000-0000-4000-8000-00000000f001', `features/${LONG}/${LONG}.feature`, LONG.slice(0, 255), 1, ['@' + 't'.repeat(120)])];
            });
            await expect(page.getByRole('treegrid', {name: 'Features'})).toBeVisible();
            await noSidewaysScroll(page);
        });

        test('runs list and run detail with a 255-character run name', async ({page}) => {
            await openAs(page, 'gus', `${checkout}/runs`, (b) => {
                const run = b.report.runs.get(runIds.nightly);
                if (run) run.fields = {...run.fields, name: 'N'.repeat(255)};
            });
            await expect(page.getByRole('grid', {name: 'Runs'})).toBeVisible();
            await noSidewaysScroll(page);
            await page.goto(`${checkout}/runs/${runIds.nightly}`);
            await expect(page.getByRole('region', {name: 'Run summary'})).toBeVisible();
            await noSidewaysScroll(page);
        });
    });
}

test('qa: markup in a feature name, a tag and a run name is text, never executed', async ({page}) => {
    const id = '00000000-0000-4000-8000-00000000f002';
    const tag = '@x' + XSS.replace(/ /g, '');
    await openAs(page, 'gus', `${checkout}/features?feature=${id}`, (b) => {
        b.features.set(id, detail(summary(id, 'features/xss.feature', XSS, 1, [tag]), 'Feature: x\n'));
        const run = b.report.runs.get(runIds.nightly);
        if (run) run.fields = {...run.fields, name: XSS};
    });
    await expect(page.getByRole('treegrid', {name: 'Features'})).toContainText(tag);
    await expect(page.getByText(XSS).first()).toBeVisible();
    await page.goto(`${checkout}/runs`);
    await expect(page.getByRole('grid', {name: 'Runs'})).toContainText(XSS);
    expect(await page.evaluate(() => Reflect.get(window, '__qaXss'))).toBeUndefined();
    expect(await page.locator('img[src="x"]').count()).toBe(0);
});

test('qa: 2,000 features plus a 30-level folder chain at 390 px: few rows in the DOM, ready quickly', async ({page}) => {
    await page.setViewportSize({width: 390, height: 844});
    const deep = `features/${Array.from({length: 30}, (_, i) => `level-${i}`).join('/')}/deep.feature`;
    const start = Date.now();
    await openAs(page, 'gus', `${checkout}/features`, (b) => {
        b.extraSummaries = [...manyFeatures(), summary('00000000-0000-4000-8000-00000000f003', deep, 'Deep', 1, [])];
    });
    const rows = page.getByRole('treegrid', {name: 'Features'}).getByRole('row');
    await expect(rows.first()).toBeVisible();
    const elapsed = Date.now() - start;
    test.info().annotations.push({type: 'tree-ready-ms', description: String(elapsed)});
    expect(await rows.count()).toBeLessThan(200);
    expect(elapsed).toBeLessThan(5_000);
    await noSidewaysScroll(page);
});
