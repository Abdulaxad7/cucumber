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

function tokenFor(user: UserKey): string {
    const now = Math.floor(Date.now() / 1000);
    return `${base64url(JSON.stringify({alg: 'HS512', typ: 'JWT'}))}.${base64url(JSON.stringify({sub: users[user].id, email: users[user].email, exp: now + 3600}))}.c2ln`;
}

interface Seen {
    method: string;
    path: string;
    authorization: string | null;
    body: string | null;
}

async function wire(page: Page, backend: FakeBackend, loginAs: UserKey | null = backend.user) {
    const seen: Seen[] = [];
    await page.route('**/api/**', async (route) => {
        const request = route.request();
        const url = new URL(request.url());
        const raw = request.postData();
        seen.push({method: request.method(), path: url.pathname, authorization: await request.headerValue('authorization'), body: raw});
        if (url.pathname === '/api/auth/login' && loginAs) {
            return route.fulfill({status: 200, contentType: 'application/json', body: JSON.stringify({accessToken: tokenFor(loginAs)})});
        }
        const answer = backend.answer(request.method(), url, raw ? JSON.parse(raw) : null);
        if (!answer) return route.abort();
        if (answer.status === 204) return route.fulfill({status: 204});
        return route.fulfill({status: answer.status, contentType: answer.contentType, body: JSON.stringify(answer.body)});
    });
    return seen;
}

async function signIn(page: Page, user: UserKey) {
    await page.addInitScript((jwt) => sessionStorage.setItem('cucumber.session', jwt), tokenFor(user));
}

async function expectNoAxeViolations(page: Page) {
    await expect(page.locator('[data-entering]')).toHaveCount(0);
    const results = await new AxeBuilder({page}).withTags(WCAG).analyze();
    expect(results.violations.map((v) => `${v.id}: ${v.nodes.map((n) => n.target.join(' ')).join(', ')}`)).toEqual([]);
}

// @US-2 @AC-2.1
test('AC-2.1 a new person accepts: the fragment leaves the URL, no Authorization, then log in', async ({page}) => {
    const backend = new FakeBackend(null);
    backend.auth.tokens.set('tok-123', {token: 'tok-123', orgId: ids.acme, email: 'fay@example.com', role: 'MEMBER'});
    const seen = await wire(page, backend, null);
    await page.goto('/invite#tok-123');
    await expect(page).toHaveURL('/invite');
    expect(new URL(page.url()).hash).toBe('');
    await expectNoAxeViolations(page);
    await page.getByLabel('Full name').fill('Fay Lin');
    await page.getByLabel('Password').fill('Secret123!');
    await page.getByRole('button', {name: 'Accept invitation'}).click();
    await expect(page.getByText('You joined Acme as MEMBER')).toBeVisible();
    await expect(page.getByLabel('Email')).toHaveValue('fay@example.com');
    const accept = seen.find((s) => s.path === '/api/auth/invitations/tok-123/accept');
    expect(accept?.authorization).toBeNull();
    expect(JSON.parse(accept?.body ?? '{}')).toEqual({fullName: 'Fay Lin', password: 'Secret123!'});
    await expectNoAxeViolations(page);
});

// @US-2 @AC-2.2
test('AC-2.2 a logged-in person accepts after a login detour', async ({page}) => {
    const backend = new FakeBackend('gus');
    backend.auth.members.set(ids.acme, (backend.auth.members.get(ids.acme) ?? []).filter((m) => m.userId !== users.gus.id));
    backend.auth.tokens.set('tok-456', {token: 'tok-456', orgId: ids.acme, email: users.gus.email, role: 'MEMBER'});
    const seen = await wire(page, backend, 'gus');
    await page.goto('/invite#tok-456');
    await page.getByRole('link', {name: 'I have an account'}).click();
    await expect(page).toHaveURL('/login?next=%2Finvite');
    await page.getByLabel('Email').fill(users.gus.email);
    await page.getByLabel('Password').fill('Secret123!');
    await page.getByRole('button', {name: 'Log in'}).click();
    await expect(page).toHaveURL('/invite');
    await page.getByRole('button', {name: 'Accept invitation'}).click();
    await expect(page).toHaveURL(`/orgs/${ids.acme}/projects`);
    const accept = seen.find((s) => s.path === '/api/auth/invitations/tok-456/accept');
    expect(accept?.authorization).toMatch(/^Bearer /);
    expect(accept?.body).toBeNull();
    expect(await page.evaluate(() => sessionStorage.getItem('cucumber.pendingInvite'))).toBeNull();
});

// @US-2 @AC-2.3
test('AC-2.3 an expired invitation shows its message and no form', async ({page}) => {
    const backend = new FakeBackend(null);
    backend.auth.tokens.set('tok-9', {
        token: 'tok-9',
        orgId: ids.acme,
        email: 'fay@example.com',
        role: 'MEMBER',
        outcome: {
            status: 410,
            contentType: 'application/problem+json',
            body: {type: 'about:blank', title: 'Gone', status: 410, detail: 'd', instance: '/x', code: 'INVITATION_EXPIRED'},
        },
    });
    await wire(page, backend, null);
    await page.goto('/invite#tok-9');
    await page.getByLabel('Full name').fill('Fay Lin');
    await page.getByLabel('Password').fill('Secret123!');
    await page.getByRole('button', {name: 'Accept invitation'}).click();
    await expect(page.getByText('This invitation has expired. Ask for a new one.')).toBeVisible();
    await expect(page.getByLabel('Password')).toHaveCount(0);
    await expectNoAxeViolations(page);
});

// @US-3 @AC-3.1
test('AC-3.1 list and create an organization', async ({page}) => {
    const backend = new FakeBackend('ana');
    await signIn(page, 'ana');
    await wire(page, backend);
    await page.goto('/orgs');
    await expect(page.getByRole('link', {name: 'Acme'})).toBeVisible();
    await expect(page.getByText('Your role: OWNER')).toBeVisible();
    await page.getByRole('link', {name: 'Create organization'}).click();
    await page.getByLabel('Organization name').fill('Globex');
    await page.getByRole('button', {name: 'Create organization'}).click();
    await expect(page).toHaveURL(/\/orgs\/[^/]+\/projects$/);
    await expect(page.getByText('Organization role: OWNER')).toBeVisible();
    await page.goto('/orgs');
    await expect(page.getByRole('link', {name: 'Acme'})).toBeVisible();
    await expect(page.getByRole('link', {name: 'Globex'})).toBeVisible();
});

// @US-3 @AC-3.2, @AC-3.4
test('AC-3.2 / AC-3.4 Ben manages roles within his ceiling; seats are full', async ({page}) => {
    const backend = new FakeBackend('ben');
    await signIn(page, 'ben');
    await wire(page, backend);
    await page.goto(`/orgs/${ids.acme}/members`);
    await expect(page.getByText('Seats: 5 of 5 used')).toBeVisible();
    await expect(page.getByText('No pending invitations')).toBeVisible();
    await expect(page.getByRole('button', {name: 'Invite'})).toBeDisabled();
    await expect(page.getByText('No free seat')).toBeVisible();
    await expect(page.getByRole('button', {name: /Role for Ana Ruiz/})).toHaveCount(0);
    await expect(page.getByRole('button', {name: /Role for Ben Okafor/})).toHaveCount(0);
    await page.getByRole('button', {name: /Role for Gus Underwood/}).click();
    await expect(page.getByRole('option')).toHaveText(['ADMIN', 'MEMBER', 'READER']);
    await expectNoAxeViolations(page);
    await page.getByRole('option', {name: 'READER'}).click();
    await expect(page.getByRole('button', {name: /Role for Gus Underwood/})).toContainText('READER');
});

// @US-3 @AC-3.3
test('AC-3.3 invite and share the link once', async ({page, context}) => {
    await context.grantPermissions(['clipboard-read', 'clipboard-write']);
    const backend = new FakeBackend('ben');
    backend.auth.members.set(ids.acme, (backend.auth.members.get(ids.acme) ?? []).filter((m) => m.userId !== users.dan.id));
    await signIn(page, 'ben');
    await wire(page, backend);
    await page.goto(`/orgs/${ids.acme}/members`);
    await page.getByRole('button', {name: 'Invite'}).click();
    const dialog = page.getByRole('dialog', {name: 'Invite a member'});
    await expectNoAxeViolations(page);
    await dialog.getByLabel('Email').fill('fay@example.com');
    await dialog.getByRole('button', {name: 'Send invitation'}).click();
    const tokenDialog = page.getByRole('dialog', {name: 'Share the invitation link'});
    const link = await tokenDialog.getByLabel('Invitation link').inputValue();
    expect(link).toMatch(new RegExp(`^${new URL(page.url()).origin}/invite#tok-`));
    await expectNoAxeViolations(page);
    await tokenDialog.getByRole('button', {name: 'Copy link'}).click();
    expect(await page.evaluate(() => navigator.clipboard.readText())).toBe(link);
    await tokenDialog.getByRole('button', {name: 'Done'}).click();
    await expect(tokenDialog).toHaveCount(0);
    const token = link.split('#')[1] ?? '';
    expect(await page.content()).not.toContain(token);
    expect(await page.evaluate(() => JSON.stringify({...sessionStorage, ...localStorage}))).not.toContain(token);
    await expect(page.getByText('fay@example.com')).toBeVisible();
    await expect(page.getByText('Seats: 5 of 5 used')).toBeVisible();
});

// @US-3 @AC-3.5 and @AC-9.3 in both themes.
for (const theme of ['light', 'dark'] as const) {
    test(`AC-3.5 / AC-9.3 a MEMBER sees the list without actions, ${theme}`, async ({page}) => {
        await page.addInitScript((value) => localStorage.setItem('cucumber.ui.theme', value), theme);
        const backend = new FakeBackend('gus');
        await signIn(page, 'gus');
        await wire(page, backend);
        await page.goto(`/orgs/${ids.acme}/members`);
        await expect(page.getByText('Ana Ruiz')).toBeVisible();
        await expect(page.getByRole('button', {name: 'Invite'})).toHaveCount(0);
        await expect(page.getByRole('button', {name: /Role for/})).toHaveCount(0);
        await expect(page.getByRole('button', {name: /^Remove /})).toHaveCount(0);
        await expectNoAxeViolations(page);
    });

    test(`AC-9.3 the admin members page passes axe, ${theme}`, async ({page}) => {
        await page.addInitScript((value) => localStorage.setItem('cucumber.ui.theme', value), theme);
        const backend = new FakeBackend('ana');
        backend.auth.members.set(ids.acme, (backend.auth.members.get(ids.acme) ?? []).filter((m) => m.userId !== users.dan.id));
        backend.auth.invitations.set(ids.acme, [
            {id: '0c0c0c0c-0000-4000-8000-000000000001', email: 'ivy@example.com', role: 'MEMBER', status: 'PENDING', createdAt: '2026-11-01T10:00:00Z', expiresAt: '2099-11-08T10:00:00Z'},
        ]);
        await signIn(page, 'ana');
        await wire(page, backend);
        await page.goto(`/orgs/${ids.acme}/members`);
        await expect(page.getByText('ivy@example.com')).toBeVisible();
        await expectNoAxeViolations(page);
    });
}
