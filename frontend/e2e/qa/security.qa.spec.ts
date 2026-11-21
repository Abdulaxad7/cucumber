// qa (frontend phase 1): AC-1.7, AC-2.1, AC-9.6 in a real browser. `next` must never leave the app, by any encoding,
// on submit or on the already-signed-in redirect; the invitation fragment is removed from the URL, survives hostile
// encodings without crashing the page, and no secret reaches the console, the URL, localStorage or a cookie.
import {expect, test, type Page, type Route} from '@playwright/test';

const ORIGIN = 'http://127.0.0.1:4173';
const ANA = 'a1b2c3d4-0000-4000-8000-0000000000a1';

function base64url(text: string): string {
    const bytes = new TextEncoder().encode(text);
    let binary = '';
    for (const byte of bytes) binary += String.fromCharCode(byte);
    return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

function jwt(): string {
    const now = Math.floor(Date.now() / 1000);
    return `${base64url(JSON.stringify({alg: 'HS512'}))}.${base64url(JSON.stringify({sub: ANA, email: 'ana@example.com', exp: now + 3600}))}.c2lnbmF0dXJl`;
}

async function json(route: Route, status: number, body: unknown, contentType = 'application/json') {
    await route.fulfill({status, contentType, body: JSON.stringify(body)});
}

/** Answers login and an empty org list; records every request host so a foreign one fails the test. */
async function backend(page: Page, token: string) {
    const foreign: string[] = [];
    page.on('request', (request) => {
        const host = new URL(request.url()).host;
        if (host !== '127.0.0.1:4173') foreign.push(request.url());
    });
    await page.route('**/api/**', async (route) => {
        const url = new URL(route.request().url());
        if (url.pathname === '/api/auth/login') return json(route, 200, {accessToken: token});
        if (url.pathname === '/api/orgs') return json(route, 200, []);
        return route.abort();
    });
    return foreign;
}

function captureConsole(page: Page): string[] {
    const messages: string[] = [];
    page.on('console', (message) => messages.push(message.text()));
    page.on('pageerror', (error) => messages.push(`pageerror: ${error.message}`));
    return messages;
}

/** Raw query values exactly as they appear in the address bar. */
const HOSTILE_NEXT = [
    '%2F%2Fevil.example',
    '%2F%2F%2Fevil.example',
    '%2F%5Cevil.example',
    '%5C%5Cevil.example',
    'https%3A%2F%2Fevil.example',
    'javascript%3Aalert(1)',
    'JavaScript%3Aalert(document.domain)',
    'data%3Atext%2Fhtml%2C%3Cscript%3Ealert(1)%3C%2Fscript%3E',
    '%2F%09%2Fevil.example',
    '%2F%0A%2Fevil.example',
    '%2F%0D%2Fevil.example',
    '%20%2F%2Fevil.example',
    '%252F%252Fevil.example',
    '%22%2F%2Fevil.example%22',
    '%5B%22%2F%2Fevil.example%22%5D',
    '%7B%22href%22%3A%22%2F%2Fevil.example%22%7D',
    '%2Flogin%3Fnext%3D%252F%252Fevil.example',
    'evil.example',
];

test.describe('qa: next never leaves the app (AC-1.7)', () => {
    for (const next of HOSTILE_NEXT) {
        test(`login from ?next=${next}`, async ({page}) => {
            const foreign = await backend(page, jwt());
            await page.goto(`/login?next=${next}`);
            await page.getByLabel('Email').fill('ana@example.com');
            await page.getByLabel('Password').fill('Secret123!');
            await page.getByRole('button', {name: 'Log in'}).click();
            await expect(page.getByRole('heading', {level: 1})).toBeVisible();
            const url = new URL(page.url());
            expect(url.origin).toBe(ORIGIN);
            expect(url.pathname.startsWith('//')).toBe(false);
            expect(foreign).toEqual([]);
        });
    }

    for (const next of ['%2F%2Fevil.example', 'https%3A%2F%2Fevil.example', 'javascript%3Aalert(1)', '%2F%5Cevil.example']) {
        test(`already signed in: /login?next=${next} redirects in the app`, async ({page}) => {
            const token = jwt();
            const foreign = await backend(page, token);
            await page.addInitScript((value) => sessionStorage.setItem('cucumber.session', value), token);
            await page.goto(`/login?next=${next}`);
            await expect(page.getByRole('heading', {level: 1})).toBeVisible();
            expect(new URL(page.url()).origin).toBe(ORIGIN);
            expect(new URL(page.url()).pathname).toBe('/');
            expect(foreign).toEqual([]);
        });
    }

    test('the four AC-1.7 rows land exactly where the spec says', async ({page}) => {
        const rows: Array<[string, string]> = [
            ['%2Forgs', '/orgs'],
            ['%2F%2Fevil.example', '/'],
            ['https%3A%2F%2Fevil.example', '/'],
            ['javascript%3Aalert(1)', '/'],
        ];
        await backend(page, jwt());
        for (const [next, landing] of rows) {
            await page.evaluate(() => sessionStorage.clear()).catch(() => undefined);
            await page.goto(`/login?next=${next}`);
            await page.getByLabel('Email').fill('ana@example.com');
            await page.getByLabel('Password').fill('Secret123!');
            await page.getByRole('button', {name: 'Log in'}).click();
            await expect(page).toHaveURL(`${ORIGIN}${landing}`);
        }
    });
});

test.describe('qa: invitation fragment (AC-2.1, D14)', () => {
    // FE-QA-2 (fixed in F13): a malformed escape is an invalid link, not a crash, and the fragment is dropped.
    for (const fragment of ['%E0%A4%A', '%', '%ZZ', '%%', '%C0%AF', '%ED%A0%80', 'tok%E2%82', '%FF%FE']) {
        test(`/invite#${fragment} is the invalid-link state, with no request and no fragment left`, async ({page}) => {
            const messages = captureConsole(page);
            const calls: string[] = [];
            await page.route('**/api/**', (route) => {
                calls.push(route.request().url());
                return route.abort();
            });
            await page.goto(`/invite#${fragment}`);
            await expect(page.getByText("This invitation link isn't valid.")).toBeVisible();
            await expect(page.getByRole('heading', {level: 1})).toBeVisible();
            await expect.poll(() => new URL(page.url()).hash).toBe('');
            expect(new URL(page.url()).pathname).toBe('/invite');
            expect(calls).toEqual([]);
            expect(await page.evaluate(() => sessionStorage.getItem('cucumber.pendingInvite'))).toBeNull();
            await expect(page.getByRole('button', {name: 'Accept invitation'})).toHaveCount(0);
            await expect(page.getByText(/URI malformed|URIError|Something went wrong/)).toHaveCount(0);
            expect(messages.filter((m) => m.startsWith('pageerror'))).toEqual([]);
        });
    }

    test('a malformed link after a valid pending invitation forgets the pending one', async ({page}) => {
        await page.route('**/api/**', (route) => route.abort());
        await page.addInitScript(() => {
            if (!sessionStorage.getItem('qa.seeded')) {
                sessionStorage.setItem('qa.seeded', '1');
                sessionStorage.setItem('cucumber.pendingInvite', 'old-token');
            }
        });
        await page.goto('/invite#%ZZ');
        await expect(page.getByText("This invitation link isn't valid.")).toBeVisible();
        expect(await page.evaluate(() => sessionStorage.getItem('cucumber.pendingInvite'))).toBeNull();
    });

    for (const fragment of ['tok%2F..%2F..%2Forgs', 'a#b', '<img src=x onerror=alert(1)>']) {
        test(`/invite#${fragment} never crashes and never keeps the fragment`, async ({page}) => {
            const messages = captureConsole(page);
            const accepted: string[] = [];
            await page.route('**/api/**', (route) => {
                accepted.push(new URL(route.request().url()).pathname);
                return json(route, 404, {
                    type: 'about:blank', title: 'Not Found', status: 404, detail: 'd', instance: '/x', code: 'INVITATION_NOT_FOUND',
                }, 'application/problem+json');
            });
            await page.goto(`/invite#${fragment}`);
            await expect(page.getByRole('heading', {level: 1})).toBeVisible();
            await expect.poll(() => new URL(page.url()).hash).toBe('');
            expect(messages.filter((m) => m.startsWith('pageerror'))).toEqual([]);
            // A traversal attempt stays inside the accept path (the token is one encoded path segment).
            for (const path of accepted) expect(path.startsWith('/api/auth/invitations/')).toBe(true);
        });
    }

    test('the token reaches neither the URL, localStorage, cookies nor the console, and is sent once, encoded', async ({page}) => {
        const secret = 'inv-QaSecret-7Kq2';
        const messages = captureConsole(page);
        const sent: string[] = [];
        await page.route('**/api/**', (route) => {
            sent.push(route.request().url());
            return json(route, 201, {
                user: {id: ANA, email: 'fay@example.com', fullName: 'Fay Lin', status: 'PENDING_VERIFICATION', createdAt: '2026-11-01T00:00:00Z'},
                organization: {id: 'b1b2c3d4-0000-4000-8000-0000000000b1', name: 'Acme', seatLimit: 5, createdAt: '2026-11-01T00:00:00Z', myRole: 'MEMBER'},
            });
        });
        await page.goto(`/invite#${secret}`);
        await expect.poll(() => new URL(page.url()).hash).toBe('');
        await page.getByLabel('Full name').fill('Fay Lin');
        await page.getByLabel('Password').fill('Secret123!');
        await page.getByRole('button', {name: /accept/i}).click();
        await expect(page.getByText(/You joined Acme as MEMBER/)).toBeVisible();
        expect(sent.filter((u) => u.includes(secret))).toHaveLength(1);
        expect(page.url()).not.toContain(secret);
        expect(await page.evaluate(() => JSON.stringify({...localStorage}) + document.cookie)).not.toContain(secret);
        expect(await page.evaluate(() => sessionStorage.getItem('cucumber.pendingInvite'))).toBeNull();
        for (const m of messages) expect(m).not.toContain(secret);
        expect(await page.content()).not.toContain(secret);
    });
});
