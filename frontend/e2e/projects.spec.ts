import {expect, test, type Page} from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import {ids} from '../src/test/fixtures/auth';
import {FakeBackend} from '../src/test/fixtures/fakeBackend';
import {users, type UserKey} from '../src/test/fixtures/workspace';

const WCAG = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'];

function base64url(text: string): string {
    const bytes = new TextEncoder().encode(text);
    let binary = '';
    for (const byte of bytes) binary += String.fromCharCode(byte);
    return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

async function open(page: Page, user: UserKey, path: string, backend = new FakeBackend(user)) {
    const now = Math.floor(Date.now() / 1000);
    const jwt = `${base64url(JSON.stringify({alg: 'HS512', typ: 'JWT'}))}.${base64url(JSON.stringify({sub: users[user].id, email: users[user].email, exp: now + 3600}))}.c2ln`;
    await page.addInitScript((value) => sessionStorage.setItem('cucumber.session', value), jwt);
    await page.route('**/api/**', async (route) => {
        const request = route.request();
        const raw = request.postData();
        const answer = backend.answer(request.method(), new URL(request.url()), raw ? JSON.parse(raw) : null);
        if (!answer) return route.abort();
        if (answer.status === 204) return route.fulfill({status: 204});
        return route.fulfill({status: answer.status, contentType: answer.contentType, body: JSON.stringify(answer.body)});
    });
    await page.goto(path);
    return backend;
}

async function expectNoAxeViolations(page: Page) {
    await expect(page.locator('[data-entering]')).toHaveCount(0);
    const results = await new AxeBuilder({page}).withTags(WCAG).analyze();
    expect(results.violations.map((v) => `${v.id}: ${v.nodes.map((n) => n.target.join(' ')).join(', ')}`)).toEqual([]);
}

const settings = `/projects/${ids.checkout}/settings`;
const members = `/projects/${ids.checkout}/members`;

// @US-4 @AC-4.1
test('AC-4.1 Gus sees his projects; Ana creates Payments; a taken name stays at the field', async ({page}) => {
    await open(page, 'gus', `/orgs/${ids.acme}/projects`);
    await expect(page.getByRole('link', {name: 'Checkout'})).toBeVisible();
    await expect(page.getByText('Billing')).toHaveCount(0);
    await expect(page.getByRole('button', {name: 'Create project'})).toHaveCount(0);
});

test('AC-4.1 Ana creates a project', async ({page}) => {
    await open(page, 'ana', `/orgs/${ids.acme}/projects`);
    await page.getByRole('button', {name: 'Create project'}).click();
    const dialog = page.getByRole('dialog', {name: 'Create project'});
    await dialog.getByLabel('Project name').fill('checkout');
    await dialog.getByRole('button', {name: 'Create project'}).click();
    await expect(dialog.getByLabel('Project name')).toHaveAccessibleDescription(/A project with this name already exists/);
    await expectNoAxeViolations(page);
    await dialog.getByLabel('Project name').fill('Payments');
    await dialog.getByRole('button', {name: 'Create project'}).click();
    await expect(page.getByRole('heading', {level: 1, name: 'Payments'})).toBeVisible();
    await expect(page.getByText('Your role: ADMIN')).toBeVisible();
});

// @US-4 @AC-4.2
test('AC-4.2 rename, archive (banner, writes gone) and restore', async ({page}) => {
    await open(page, 'ana', settings);
    await page.getByLabel('Project name').fill('Checkout Web');
    await page.getByRole('button', {name: 'Rename project'}).click();
    await expect(page.getByRole('heading', {level: 1, name: 'Checkout Web'})).toBeVisible();
    await page.getByRole('button', {name: 'Archive project'}).click();
    const confirm = page.getByRole('alertdialog', {name: 'Archive this project?'});
    await expectNoAxeViolations(page);
    await confirm.getByRole('button', {name: 'Archive project'}).click();
    await expect(page.getByText('Project archived: read only')).toBeVisible();
    await expect(page.getByRole('button', {name: 'Rename project'})).toHaveCount(0);
    await expectNoAxeViolations(page);
    await page.goto(members);
    await expect(page.getByText('Project archived: read only')).toBeVisible();
    await expect(page.getByRole('button', {name: 'Add member'})).toHaveCount(0);
    await page.goto(settings);
    await page.getByRole('button', {name: 'Restore project'}).click();
    await expect(page.getByRole('button', {name: 'Rename project'})).toBeVisible();
});

// @US-4 @AC-4.3
test('AC-4.3 project members: add, change, remove; a READER can only be READER', async ({page}) => {
    await open(page, 'ana', members);
    await page.getByRole('button', {name: 'Add member'}).click();
    const dialog = page.getByRole('dialog', {name: 'Add a project member'});
    await dialog.getByRole('button', {name: /Person/}).click();
    await expect(page.getByRole('option', {name: /Gus Underwood/})).toHaveCount(0);
    await page.getByRole('option', {name: /Ben Okafor/}).click();
    await dialog.getByRole('button', {name: /Role/}).click();
    await page.getByRole('option', {name: 'TESTER'}).click();
    await expectNoAxeViolations(page);
    await dialog.getByRole('button', {name: 'Add member'}).click();
    await expect(page.getByRole('grid', {name: 'Project members'})).toContainText('Ben Okafor');
    await page.getByRole('button', {name: /Role for Hal Jordan/}).click();
    await page.getByRole('option', {name: 'MEMBER'}).click();
    await expect(page.getByRole('button', {name: /Role for Hal Jordan/})).toContainText('MEMBER');
    await page.getByRole('button', {name: 'Remove Dan Brook'}).click();
    await page.getByRole('alertdialog').getByRole('button', {name: 'Remove member'}).click();
    await expect(page.getByRole('grid', {name: 'Project members'})).not.toContainText('Dan Brook');

    await page.getByRole('button', {name: 'Add member'}).click();
    await dialog.getByRole('button', {name: /Person/}).click();
    await page.getByRole('option', {name: /Dan Brook/}).click();
    await dialog.getByRole('button', {name: /Role/}).click();
    await page.getByRole('option', {name: 'MEMBER'}).click();
    await dialog.getByRole('button', {name: 'Add member'}).click();
    await expect(dialog.getByText("This person's organization role allows only READER")).toBeVisible();
});

test('AC-4.3 Gus sees "Leave project" on his own row only', async ({page}) => {
    await open(page, 'gus', members);
    await expect(page.getByRole('button', {name: 'Leave project'})).toHaveCount(1);
    await expect(page.getByRole('row', {name: /Gus Underwood/}).getByRole('button', {name: 'Leave project'})).toBeVisible();
});

// @US-4 @AC-4.4 and @AC-9.3 in both themes.
for (const theme of ['light', 'dark'] as const) {
    for (const user of ['gus', 'hal', 'dan'] as const) {
        test(`AC-4.4 ${user} sees no project changes, ${theme}`, async ({page}) => {
            await page.addInitScript((value) => localStorage.setItem('cucumber.ui.theme', value), theme);
            await open(page, user, settings);
            await expect(page.getByText(/Read only: your role is/)).toBeVisible();
            await expect(page.getByRole('button', {name: 'Rename project'})).toHaveCount(0);
            await expect(page.getByRole('button', {name: 'Archive project'})).toHaveCount(0);
            await expectNoAxeViolations(page);
        });
    }

    test(`AC-9.3 project pages pass axe for an admin, ${theme}`, async ({page}) => {
        await page.addInitScript((value) => localStorage.setItem('cucumber.ui.theme', value), theme);
        await open(page, 'ana', `/orgs/${ids.acme}/projects`);
        await expect(page.getByRole('link', {name: 'Checkout'})).toBeVisible();
        await expectNoAxeViolations(page);
        await page.goto(settings);
        await expect(page.getByLabel('Project name')).toBeVisible();
        await expectNoAxeViolations(page);
        await page.goto(members);
        await expect(page.getByText('Hal Jordan', {exact: true})).toBeVisible();
        await expectNoAxeViolations(page);
    });
}
