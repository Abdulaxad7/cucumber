// F10 / AC-9.2, AC-9.3: axe (WCAG 2.1 A and AA) on every route and view state, in both themes and at both widths.
// Writes review-screenshots/axe-report.json (gitignored) with the violation count per route, for the user.
import {mkdirSync, writeFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import {expect, test, type Page} from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import {ids} from '../src/test/fixtures/auth';
import {FakeBackend} from '../src/test/fixtures/fakeBackend';
import type {FakeAnswer} from '../src/test/fixtures/fakeAuth';
import {users, type UserKey} from '../src/test/fixtures/workspace';
import {payment} from '../src/test/fixtures/scenario';
import {runIds} from '../src/test/fixtures/report';

const outDir = fileURLToPath(new URL('../review-screenshots/', import.meta.url));
const WCAG = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'];
const checkout = `/projects/${ids.checkout}`;

interface Visit {
    name: string;
    user: UserKey | null;
    path: string;
    /** What must be visible before axe runs. */
    ready: (page: Page) => Promise<void>;
    setup?: (backend: FakeBackend) => void;
    /** Answers for "METHOD path" keys, to show error and loading states. */
    force?: Record<string, FakeAnswer | 'hang'>;
}

const problem = (status: number, code: string | null): FakeAnswer => ({
    status,
    contentType: 'application/problem+json',
    body: {type: 'about:blank', title: 'x', status, detail: 'd', instance: '/x', requestId: 'rid-sweep', ...(code ? {code} : {})},
});

const h1 = (name: string | RegExp) => async (page: Page) => {
    await expect(page.getByRole('heading', {level: 1, name})).toBeVisible();
};
const text = (value: string) => async (page: Page) => {
    await expect(page.getByText(value).first()).toBeVisible();
};

const visits: Visit[] = [
    {name: 'login', user: null, path: '/login', ready: h1('Log in')},
    {name: 'register', user: null, path: '/register', ready: h1('Create an account')},
    {name: 'invite-invalid', user: null, path: '/invite', ready: text("This invitation link isn't valid.")},
    {name: 'not-found', user: 'gus', path: '/nowhere', ready: h1('Page not found')},
    {name: 'orgs-ready', user: 'gus', path: '/orgs', ready: text('Acme')},
    {name: 'orgs-loading', user: 'gus', path: '/orgs', ready: async (p) => expect(p.locator('[data-skeleton]').first()).toBeVisible(), force: {'GET /api/orgs': 'hang'}},
    {name: 'orgs-error', user: 'gus', path: '/orgs', ready: text('The service is unavailable. Try again.'), force: {'GET /api/orgs': problem(503, null)}},
    {name: 'orgs-forbidden', user: 'gus', path: '/orgs', ready: text("You don't have access to this"), force: {'GET /api/orgs': problem(403, 'FORBIDDEN')}},
    {name: 'org-create', user: 'gus', path: '/orgs/new', ready: h1('Create organization')},
    {name: 'org-members-admin', user: 'ana', path: `/orgs/${ids.acme}/members`, ready: text('Hal Jordan')},
    {name: 'org-members-member', user: 'gus', path: `/orgs/${ids.acme}/members`, ready: text('Hal Jordan')},
    {name: 'projects', user: 'ana', path: `/orgs/${ids.acme}/projects`, ready: text('Checkout')},
    {name: 'projects-empty', user: 'ana', path: `/orgs/${ids.acme}/projects`, ready: text('No projects yet'), setup: (b) => b.auth.projects.splice(0)},
    {name: 'features-tree', user: 'gus', path: `${checkout}/features`, ready: async (p) => expect(p.getByRole('treegrid', {name: 'Features'})).toBeVisible()},
    {name: 'features-empty', user: 'gus', path: `${checkout}/features`, ready: text('No features yet'), setup: (b) => b.features.clear()},
    {name: 'features-error', user: 'gus', path: `${checkout}/features`, ready: text('The service is unavailable. Try again.'), force: {[`GET /api/projects/${ids.checkout}/features`]: problem(503, 'ACCESS_CHECK_UNAVAILABLE')}},
    {name: 'editor', user: 'gus', path: `${checkout}/features?feature=${payment.id}`, ready: text('Version 2')},
    {name: 'editor-read-only', user: 'dan', path: `${checkout}/features?feature=${payment.id}`, ready: text('Read only: your role is READER')},
    {name: 'editor-not-found', user: 'gus', path: `${checkout}/features?feature=00000000-0000-4000-8000-00000000abcd`, ready: text("This feature doesn't exist")},
    {name: 'project-settings', user: 'ana', path: `${checkout}/settings`, ready: h1('Checkout')},
    {name: 'project-settings-read-only', user: 'hal', path: `${checkout}/settings`, ready: text('Read only: your role is TESTER')},
    {name: 'project-members', user: 'ana', path: `${checkout}/members`, ready: text('Hal Jordan')},
    {name: 'runs', user: 'gus', path: `${checkout}/runs`, ready: async (p) => expect(p.getByRole('grid', {name: 'Runs'})).toBeVisible()},
    {name: 'runs-empty-admin', user: 'ana', path: `${checkout}/runs`, ready: text('No runs yet'), setup: (b) => b.report.runs.clear()},
    {name: 'runs-error', user: 'gus', path: `${checkout}/runs`, ready: text('The service is unavailable. Try again.'), force: {[`GET /api/projects/${ids.checkout}/runs`]: problem(503, 'ACCESS_CHECK_UNAVAILABLE')}},
    {name: 'run-detail', user: 'hal', path: `${checkout}/runs/${runIds.sprint12}`, ready: async (p) => expect(p.getByRole('region', {name: 'Run summary'})).toBeVisible()},
    {name: 'run-not-found', user: 'gus', path: `${checkout}/runs/00000000-0000-4000-8000-00000000abcd`, ready: text("This run doesn't exist")},
    {name: 'upload-tokens', user: 'ana', path: `${checkout}/upload-tokens`, ready: text('No upload tokens yet')},
    {name: 'upload-tokens-not-admin', user: 'gus', path: `${checkout}/upload-tokens`, ready: text('Only project admins manage upload tokens.')},
];

function base64url(value: string): string {
    const bytes = new TextEncoder().encode(value);
    let binary = '';
    for (const byte of bytes) binary += String.fromCharCode(byte);
    return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

async function open(page: Page, visit: Visit) {
    const backend = new FakeBackend(visit.user);
    visit.setup?.(backend);
    if (visit.user) {
        const now = Math.floor(Date.now() / 1000);
        const jwt = `${base64url(JSON.stringify({alg: 'HS512', typ: 'JWT'}))}.${base64url(JSON.stringify({sub: users[visit.user].id, email: users[visit.user].email, exp: now + 3600}))}.c2ln`;
        await page.addInitScript((value) => sessionStorage.setItem('cucumber.session', value), jwt);
    }
    await page.route('**/api/**', async (route) => {
        const request = route.request();
        const url = new URL(request.url());
        const forced = visit.force?.[`${request.method()} ${url.pathname}`];
        if (forced === 'hang') return;
        if (forced) return route.fulfill({status: forced.status, contentType: forced.contentType, body: JSON.stringify(forced.body)});
        const raw = request.postData();
        const answer = backend.answer(request.method(), url, raw ? JSON.parse(raw) : null);
        if (!answer) return route.abort();
        if (answer.status === 204) return route.fulfill({status: 204});
        return route.fulfill({status: answer.status, contentType: answer.contentType, body: JSON.stringify(answer.body)});
    });
    await page.goto(visit.path);
    await visit.ready(page);
}

const report: Record<string, Record<string, number>> = {};

for (const theme of ['light', 'dark'] as const) {
    for (const [width, height] of [[1440, 900], [390, 844]] as const) {
        test.describe(`axe sweep, ${theme}, ${width}`, () => {
            for (const visit of visits) {
                test(visit.name, async ({page}) => {
                    await page.setViewportSize({width, height});
                    await page.addInitScript((value) => localStorage.setItem('cucumber.ui.theme', value), theme);
                    await open(page, visit);
                    await expect(page.locator('html')).toHaveAttribute('data-theme', theme);
                    await expect(page.locator('[data-entering]')).toHaveCount(0);
                    const results = await new AxeBuilder({page}).withTags(WCAG).analyze();
                    const key = `${theme}-${width}`;
                    report[key] = {...report[key], [visit.name]: results.violations.length};
                    expect(results.violations.map((v) => `${v.id}: ${v.nodes.map((n) => n.target.join(' ')).join(', ')}`)).toEqual([]);
                });
            }
        });
    }
}

test.afterAll(() => {
    mkdirSync(outDir, {recursive: true});
    const file = `${outDir}axe-report-${process.pid}.json`;
    writeFileSync(file, `${JSON.stringify(report, null, 2)}\n`);
});
