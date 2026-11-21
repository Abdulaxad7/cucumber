// qa (frontend fix round 1): F12 / FE-QA-3 tree rows (name first, file name secondary, tags at every width) and
// F13 / FE-QA-2 the router's error page for a render error on several routes: safe text only, axe clean in both
// themes, focus on its h1 after an in-app navigation (FE-QA-5).
import {expect, test, type Page} from '@playwright/test';
import {expectNoAxeViolations, openAs} from '../reportHarness';
import type {FakeBackend} from '../../src/test/fixtures/fakeBackend';
import {ids} from '../../src/test/fixtures/auth';
import {summary} from '../../src/test/fixtures/scenario';

const checkout = `/projects/${ids.checkout}`;
const ERROR_TEXT = /RangeError|TypeError|Invalid time value|Cannot read|undefined is not|null is not|\bat \S+ \(|\.tsx?:\d+/;

function tree(page: Page) {
    return page.getByRole('treegrid', {name: 'Features'});
}

function row(page: Page, name: string | RegExp) {
    return tree(page).getByRole('row', {name});
}

async function noSidewaysScroll(page: Page) {
    const {scroll, client} = await page.evaluate(() => ({scroll: document.documentElement.scrollWidth, client: document.documentElement.clientWidth}));
    expect(scroll).toBeLessThanOrEqual(client + 1);
}

/** After openAs, answers one API path from the FakeBackend with its JSON body changed. */
async function corrupt(page: Page, backend: FakeBackend, path: RegExp, change: (body: unknown) => unknown, delayMs = 0) {
    await page.route('**/api/**', async (route) => {
        const request = route.request();
        const url = new URL(request.url());
        if (!path.test(url.pathname) || request.method() !== 'GET') return route.fallback();
        const answer = backend.answer('GET', url, null);
        if (!answer) return route.abort();
        if (delayMs > 0) await new Promise((resolve) => setTimeout(resolve, delayMs));
        await route.fulfill({status: answer.status, contentType: answer.contentType, body: JSON.stringify(change(JSON.parse(JSON.stringify(answer.body))))});
    });
}

function mapItems(body: unknown, change: (item: Record<string, unknown>) => void): unknown {
    const list = Array.isArray(body) ? body : (typeof body === 'object' && body !== null && 'items' in body && Array.isArray(body.items) ? body.items : []);
    for (const item of list) if (typeof item === 'object' && item !== null) change(item);
    return body;
}

const id = (n: number) => `00000000-0000-4000-8000-00000000e${String(n).padStart(3, '0')}`;

test.describe('qa: F12 tree rows (FE-QA-3, AC-5.1)', () => {
    for (const [width, height] of [[1440, 900], [390, 844]] as const) {
        test(`name first, file name second, tags with +N at ${width} px`, async ({page}) => {
            await page.setViewportSize({width, height});
            await openAs(page, 'gus', `${checkout}/features`, (b) => {
                b.extraSummaries = [
                    summary(id(1), 'features/qa/many.feature', 'Many tags', 1, ['@a', '@b', '@c', '@d', '@e']),
                    summary(id(2), 'features/qa/empty.feature', '', 1, []),
                    summary(id(3), 'features/qa/blank.feature', '   ', 1, []),
                    summary(id(4), 'features/qa/unicode.feature', 'دفع بالبطاقة 💳 Zoë', 1, ['@ünï']),
                    summary(id(5), `features/qa/${'l'.repeat(180)}.feature`, 'L'.repeat(255), 1, ['@' + 't'.repeat(120)]),
                ];
            });
            await expect(row(page, 'Payment (payment.feature)')).toBeVisible();
            await expect(row(page, 'Address (address.feature)')).toBeVisible();
            const many = row(page, 'Many tags (many.feature)');
            await expect(many).toContainText('@a');
            await expect(many).toContainText('+4');
            await expect(many.getByText('@a', {exact: true})).toBeVisible();
            await expect(row(page, 'empty.feature', )).toBeVisible();
            await expect(row(page, 'blank.feature')).toBeVisible();
            await expect(row(page, 'دفع بالبطاقة 💳 Zoë (unicode.feature)')).toContainText('@ünï');
            const long = row(page, new RegExp(`^L{255} \\(`));
            await expect(long).toBeVisible();
            expect(await long.locator('[title]').evaluateAll((els) => els.map((e) => e.getAttribute('title')))).toContain(`features/qa/${'l'.repeat(180)}.feature`);
            await noSidewaysScroll(page);
            await expectNoAxeViolations(page);
        });
    }

    test('type-ahead follows the name, and Enter opens it', async ({page}) => {
        await openAs(page, 'gus', `${checkout}/features`, (b) => {
            b.extraSummaries = [summary(id(6), 'features/qa/zz.feature', 'Quokka refunds', 1, [])];
        });
        await row(page, 'Payment (payment.feature)').focus();
        await page.keyboard.type('quo');
        await expect(tree(page).locator('[role=row]:focus')).toContainText('Quokka refunds');
        // react-aria's type-ahead buffer resets after about 1 s of no typing
        await page.waitForTimeout(1_200);
        await row(page, 'Payment (payment.feature)').focus();
        await page.keyboard.type('addr');
        await expect(tree(page).locator('[role=row]:focus')).toContainText('Address');
        await page.keyboard.press('Enter');
        await expect(page).toHaveURL(/feature=/);
    });
});

interface Broken {
    name: string;
    user: 'gus' | 'ana';
    /** A healthy page to start on, and the sidebar link that navigates (in the app) to the broken one. */
    from: string;
    link: string;
    api: RegExp;
    change: (body: unknown) => unknown;
}

const BROKEN: Broken[] = [
    {name: 'features tree (tags null)', user: 'gus', from: `${checkout}/runs`, link: 'Features', api: /\/features$/, change: (b) => mapItems(b, (f) => { f.tags = null; })},
    {name: 'runs list (invalid createdAt)', user: 'gus', from: `${checkout}/features`, link: 'Runs', api: /\/runs$/, change: (b) => mapItems(b, (r) => { r.createdAt = 'not-a-time'; })},
    {name: 'upload tokens (invalid createdAt)', user: 'ana', from: `${checkout}/features`, link: 'Upload tokens', api: /\/upload-tokens$/, change: () => [{id: id(9), name: 'CI', tokenPrefix: 'rct_abcdefgh', createdBy: ids.ana, createdAt: 'not-a-time', expiresAt: null, lastUsedAt: null, revokedAt: null, revokedBy: null}]},
];

for (const theme of ['light', 'dark'] as const) {
    test.describe(`qa: F13 error page, ${theme} theme (FE-QA-2)`, () => {
        test.beforeEach(async ({page}) => {
            await page.addInitScript((value) => localStorage.setItem('cucumber.ui.theme', value), theme);
        });

        for (const c of BROKEN) {
            test(`${c.name}: safe page, no error text, focus on its h1, axe clean`, async ({page}) => {
                const backend = await openAs(page, c.user, c.from);
                await expect(page.getByRole('heading', {level: 1})).toBeVisible();
                await corrupt(page, backend, c.api, c.change);
                // D17: focus moves to the h1 after an in-app navigation (not on a full page load)
                await page.getByRole('navigation', {name: 'Sidebar'}).getByRole('link', {name: c.link, exact: true}).click();
                const heading = page.getByRole('heading', {level: 1, name: 'Something went wrong'});
                await expect(heading).toBeVisible();
                await expect(page.getByText('This page could not be shown. Try again, or go back to the start.')).toBeVisible();
                await expect(page.getByRole('button', {name: 'Retry'})).toBeVisible();
                await expect(page.getByRole('heading', {level: 1})).toHaveCount(1);
                expect(await page.locator('body').innerText()).not.toMatch(ERROR_TEXT);
                expect(await page.content()).not.toMatch(/Invalid time value|TypeError|RangeError/);
                await expect(page.getByRole('button', {name: /Show Error/})).toHaveCount(0);
                // FE-QA-5 (fixed): after an in-app navigation, focus moves to the error page's h1.
                await expect(heading).toBeFocused();
                await expectNoAxeViolations(page);
            });
        }
    });
}

test.describe('qa: FE-QA-5 focus edge cases', () => {
    const breakRuns = (b: unknown) => mapItems(b, (r) => { r.createdAt = 'not-a-time'; });
    const sidebar = (page: Page) => page.getByRole('navigation', {name: 'Sidebar'});
    const errorHeading = (page: Page) => page.getByRole('heading', {level: 1, name: 'Something went wrong'});

    test('focus the user moved elsewhere before the error is never taken', async ({page}) => {
        const backend = await openAs(page, 'gus', `${checkout}/features`);
        await expect(page.getByRole('heading', {level: 1})).toBeVisible();
        await corrupt(page, backend, /\/runs$/, breakRuns, 1_500);
        await sidebar(page).getByRole('link', {name: 'Runs', exact: true}).click();
        const theme = page.getByRole('button', {name: 'Theme'});
        await page.waitForTimeout(300);
        await theme.focus();
        await expect(errorHeading(page)).toBeVisible();
        await page.waitForTimeout(500);
        await expect(theme).toBeFocused();
    });

    test('a full page load straight to a failing route leaves focus as designed (not moved)', async ({page}) => {
        const backend = await openAs(page, 'gus', '/orgs');
        await expect(page.getByRole('heading', {level: 1})).toBeVisible();
        await corrupt(page, backend, /\/runs$/, breakRuns);
        await page.goto(`${checkout}/runs`);
        await expect(errorHeading(page)).toBeVisible();
        await page.waitForTimeout(500);
        expect(await page.evaluate(() => document.activeElement === document.body)).toBe(true);
    });

    test('a second navigation after the error page focuses the new page h1', async ({page}) => {
        const backend = await openAs(page, 'gus', `${checkout}/features`);
        await expect(page.getByRole('heading', {level: 1})).toBeVisible();
        await corrupt(page, backend, /\/runs$/, breakRuns);
        await sidebar(page).getByRole('link', {name: 'Runs', exact: true}).click();
        await expect(errorHeading(page)).toBeFocused();
        await sidebar(page).getByRole('link', {name: 'Settings', exact: true}).click();
        const settingsHeading = page.getByRole('heading', {level: 1});
        await expect(settingsHeading).not.toHaveText('Something went wrong');
        await expect(settingsHeading).toBeFocused();
    });

    test('Retry that fails again shows a fresh error page with focus on its h1, never <body>', async ({page}) => {
        const backend = await openAs(page, 'gus', `${checkout}/features`);
        await expect(page.getByRole('heading', {level: 1})).toBeVisible();
        await corrupt(page, backend, /\/runs$/, breakRuns);
        await sidebar(page).getByRole('link', {name: 'Runs', exact: true}).click();
        await expect(errorHeading(page)).toBeFocused();
        await page.getByRole('button', {name: 'Retry'}).focus();
        await page.keyboard.press('Enter');
        await expect(errorHeading(page)).toBeVisible();
        await expect.poll(() => page.evaluate(() => document.activeElement?.tagName)).not.toBe('BODY');
        await expect(errorHeading(page)).toBeFocused();
    });

    // FE-QA-6 (open, S4): Retry after the server data is good again still shows the error page, because the retry
    // re-renders from the cached bad data and sends no request. When fixed, assert that Retry shows the Runs grid.
});
