// qa (frontend phase 1): D4 / AC-1.5, AC-1.6, AC-9.6. Hand-decoded JWT claims, the expiry boundary, a long-lived token
// (beyond setTimeout's 2^31 ms limit), and where the token may and may not be stored.
import {afterEach, describe, expect, it, vi} from 'vitest';
import {decodeJwt} from '../jwt';
import {
    SESSION_STORAGE_KEY,
    endSession,
    getSession,
    lastEndReason,
    restoreSession,
    startSession,
    subscribe,
} from '../session';

afterEach(() => {
    vi.useRealTimers();
    endSession('logout');
});

function b64url(text: string): string {
    const bytes = new TextEncoder().encode(text);
    let binary = '';
    for (const byte of bytes) binary += String.fromCharCode(byte);
    return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

function jwt(payload: unknown, header: unknown = {alg: 'HS512', typ: 'JWT'}): string {
    return `${b64url(JSON.stringify(header))}.${b64url(JSON.stringify(payload))}.c2ln`;
}

const nowS = () => Math.floor(Date.now() / 1000);

describe('qa: JWT payload decoding (D2, D4)', () => {
    it.each([
        ['two parts', 'a.b'],
        ['four parts', `${jwt({sub: 's', exp: nowS() + 60})}.x`],
        ['padding characters', jwt({sub: 's', exp: nowS() + 60}).replace('.c2ln', '=.c2ln')],
        ['standard base64 alphabet (+ and /)', 'eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJ+/IiwiZXhwIjo5OTk5OTk5OTk5fQ.c2ln'],
        ['payload not JSON', `x.${b64url('not json')}.y`],
        ['payload an array', jwt([{sub: 's', exp: 1}])],
        ['invalid UTF-8', `x.${btoa('ÿþ').replace(/=+$/, '')}.y`],
        ['exp a string', jwt({sub: 's', exp: String(nowS() + 60)})],
        ['exp missing', jwt({sub: 's'})],
        ['sub missing', jwt({exp: nowS() + 60})],
        ['sub empty', jwt({sub: '', exp: nowS() + 60})],
        ['sub a number', jwt({sub: 7, exp: nowS() + 60})],
    ])('rejects %s', (_label, token) => {
        expect(decodeJwt(token)).toBeNull();
    });

    it('decodes Unicode e-mail addresses and ignores the signature and the header (alg none)', () => {
        const claims = decodeJwt(jwt({sub: 'u1', email: 'zoë@exämple.com', exp: 2_000_000_000}, {alg: 'none'}));
        expect(claims).toEqual({sub: 'u1', email: 'zoë@exämple.com', exp: 2_000_000_000});
    });

    it('a non-string email is null, never an object rendered into the header', () => {
        expect(decodeJwt(jwt({sub: 'u1', email: {html: '<b>x</b>'}, exp: 2_000_000_000}))?.email).toBeNull();
    });
});

describe('qa: expiry boundary and long-lived tokens (AC-1.5, AC-1.6)', () => {
    it('a mirror whose exp equals now is discarded on start', () => {
        const now = Date.UTC(2031, 0, 1);
        sessionStorage.setItem(SESSION_STORAGE_KEY, jwt({sub: 'u', exp: now / 1000}));
        expect(restoreSession(now)).toBeNull();
        expect(sessionStorage.getItem(SESSION_STORAGE_KEY)).toBeNull();
    });

    it('a mirror valid for one more second is kept, and ends when exp passes', () => {
        vi.useFakeTimers();
        sessionStorage.setItem(SESSION_STORAGE_KEY, jwt({sub: 'u', exp: nowS() + 1}));
        expect(restoreSession()).not.toBeNull();
        vi.advanceTimersByTime(1_100);
        expect(getSession()).toBeNull();
        expect(lastEndReason()).toBe('expired');
        expect(sessionStorage.getItem(SESSION_STORAGE_KEY)).toBeNull();
    });

    it('a token valid for 30 days is not ended early by the 2^31 ms timer limit', () => {
        vi.useFakeTimers();
        const days30 = 30 * 24 * 3600;
        expect(startSession(jwt({sub: 'u', exp: nowS() + days30}))).not.toBeNull();
        vi.advanceTimersByTime(2_147_483_647 + 1_000);
        expect(getSession()).not.toBeNull();
        vi.advanceTimersByTime(days30 * 1000);
        expect(getSession()).toBeNull();
        expect(lastEndReason()).toBe('expired');
    });

    it('a second login replaces the first session and only the new expiry ends it', () => {
        vi.useFakeTimers();
        startSession(jwt({sub: 'a', exp: nowS() + 2}));
        startSession(jwt({sub: 'b', exp: nowS() + 60}));
        vi.advanceTimersByTime(3_000);
        expect(getSession()?.userId).toBe('b');
    });

    it('ending tells listeners once, and ending again tells nobody', () => {
        const heard: Array<string | null> = [];
        const off = subscribe((_session, reason) => heard.push(reason));
        startSession(jwt({sub: 'u', exp: nowS() + 60}));
        endSession('unauthorized');
        endSession('unauthorized');
        endSession('logout');
        off();
        expect(heard).toEqual([null, 'unauthorized']);
    });
});

describe('qa: the token is stored only in the sessionStorage mirror (AC-9.6)', () => {
    it('nothing reaches localStorage, cookies or the URL across login, restore and logout', () => {
        const token = jwt({sub: 'u', email: 'ana@example.com', exp: nowS() + 600});
        startSession(token);
        restoreSession();
        expect(Object.values({...localStorage}).join()).not.toContain(token);
        expect(document.cookie).not.toContain(token);
        expect(window.location.href).not.toContain(token);
        endSession('logout');
        expect(sessionStorage.getItem(SESSION_STORAGE_KEY)).toBeNull();
        expect(getSession()).toBeNull();
    });
});
