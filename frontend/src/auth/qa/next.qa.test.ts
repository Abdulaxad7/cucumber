// qa (frontend phase 1): D4 / AC-1.7. The `next` validator is judged by where the browser would actually go, not by
// the string's shape: whatever it returns must resolve, against the app origin, to the app origin.
import {describe, expect, it} from 'vitest';
import {safeNext} from '../next';

const ORIGIN = 'https://app.example';

function landsInApp(next: unknown): boolean {
    return new URL(safeNext(next), ORIGIN).origin === ORIGIN;
}

/** Values as the router hands them to the login page: already percent-decoded once. */
const HOSTILE: unknown[] = [
    '//evil.example',
    '///evil.example',
    '////evil.example',
    '/\\evil.example',
    '\\\\evil.example',
    '\\/evil.example',
    '/\\/evil.example',
    'https://evil.example',
    'HTTPS://evil.example',
    'http:evil.example',
    'javascript:alert(1)',
    'JaVaScRiPt:alert(1)',
    ' javascript:alert(1)',
    'data:text/html,<script>alert(1)</script>',
    'vbscript:msgbox(1)',
    '/\t/evil.example',
    '/\n/evil.example',
    '/\r/evil.example',
    '\t//evil.example',
    '/\u0000/evil.example',
    ' //evil.example',
    ' //evil.example',
    'evil.example',
    './/evil.example',
    '',
    null,
    undefined,
    42,
    ['//evil.example'],
    {href: '//evil.example'},
];

describe('qa: next never leaves the app (AC-1.7, D4)', () => {
    it.each(HOSTILE.map((value) => [JSON.stringify(value) ?? String(value), value]))(
        '%s resolves to the app origin',
        (_label, value) => {
            expect(landsInApp(value)).toBe(true);
        },
    );

    it.each(['//evil.example', '/\\evil.example', 'https://evil.example', 'javascript:alert(1)', '/\n/evil'])(
        '%j is replaced by "/"',
        (value) => {
            expect(safeNext(value)).toBe('/');
        },
    );

    it.each([
        ['/orgs', '/orgs'],
        ['/projects/p/features?tag=%40smoke', '/projects/p/features?tag=%40smoke'],
        ['/orgs#members', '/orgs#members'],
        // Encoded slashes stay a path on this origin: the browser doesn't decode %2F into a scheme-relative URL.
        ['/%2F%2Fevil.example', '/%2F%2Fevil.example'],
        ['/%5Cevil.example', '/%5Cevil.example'],
    ])('keeps the in-app path %j', (value, expected) => {
        expect(safeNext(value)).toBe(expected);
        expect(landsInApp(value)).toBe(true);
    });

    it.each(['/login', '/login?next=%2F%2Fevil', '/register', '/register?x=1'])('never loops back to %j', (value) => {
        expect(safeNext(value)).toBe('/');
    });

    it('accepts no control character anywhere (URL parsers strip tab and newline)', () => {
        for (let code = 0; code < 0x20; code += 1) {
            expect(safeNext(`/a${String.fromCharCode(code)}/b`)).toBe('/');
        }
        expect(safeNext('/a\u007f')).toBe('/');
    });
});
