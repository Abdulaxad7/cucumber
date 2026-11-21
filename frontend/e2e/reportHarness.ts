// Shared by the batch D specs: a signed-in page backed by the in-memory FakeBackend.
import {expect, type Page} from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import {FakeBackend} from '../src/test/fixtures/fakeBackend';
import {users, type UserKey} from '../src/test/fixtures/workspace';

function base64url(text: string): string {
    const bytes = new TextEncoder().encode(text);
    let binary = '';
    for (const byte of bytes) binary += String.fromCharCode(byte);
    return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

export async function openAs(page: Page, user: UserKey, path: string, setup?: (backend: FakeBackend) => void) {
    const backend = new FakeBackend(user);
    setup?.(backend);
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

export async function expectNoAxeViolations(page: Page) {
    await expect(page.locator('[data-entering]')).toHaveCount(0);
    const results = await new AxeBuilder({page}).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
    expect(results.violations.map((v) => `${v.id}: ${v.nodes.map((n) => n.target.join(' ')).join(', ')}`)).toEqual([]);
}
