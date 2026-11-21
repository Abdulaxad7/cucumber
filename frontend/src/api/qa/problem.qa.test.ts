// qa (frontend phase 1): AC-9.1 / D7. Every response row of the one Problem mapper, plus hostile and malformed
// bodies: a Problem without `code`, an unknown code, `errors[]` by field and pointer, `parseErrors` and
// `currentVersion` that aren't well-formed, and `requestId` values that mustn't reach the screen.
import {describe, expect, it} from 'vitest';
import {parseProblem, splitFieldErrors, toUiError, type UiErrorKind} from '../problem';
import {errorText} from '../../ui/errorText';

const PJ = 'application/problem+json';

function problem(status: number, extra: Record<string, unknown> = {}) {
    return {type: 'about:blank', title: 'T', status, detail: 'server detail', instance: '/x', ...extra};
}

function ui(status: number, body: unknown, contentType: string | null = PJ) {
    return toUiError({status, body, contentType});
}

describe('qa: AC-9.1 rows', () => {
    it.each<[string, number, Record<string, unknown> | null, UiErrorKind, string, boolean]>([
        ['401 any call but login', 401, {code: 'UNAUTHENTICATED'}, 'unauthenticated', 'Your session ended. Log in again.', false],
        ['401 from the gateway (no code)', 401, {}, 'unauthenticated', 'Your session ended. Log in again.', false],
        ['403 FORBIDDEN', 403, {code: 'FORBIDDEN'}, 'forbidden', "You don't have access to this", false],
        ['403 without code (gateway CORS)', 403, {}, 'refused', 'The request was refused', false],
        ['404 NOT_FOUND', 404, {code: 'NOT_FOUND'}, 'not-found', "This doesn't exist", false],
        ['404 FEATURE_NOT_FOUND', 404, {code: 'FEATURE_NOT_FOUND'}, 'not-found', "This doesn't exist", false],
        ['404 RUN_NOT_FOUND', 404, {code: 'RUN_NOT_FOUND'}, 'not-found', "This doesn't exist", false],
        ['404 VERSION_NOT_FOUND', 404, {code: 'VERSION_NOT_FOUND'}, 'not-found', "This doesn't exist", false],
        ['404 gateway (no code)', 404, {}, 'not-found', "This doesn't exist", false],
        ['409 PROJECT_ARCHIVED', 409, {code: 'PROJECT_ARCHIVED'}, 'archived', 'Project archived. It is read only.', false],
        ['409 CONFLICT (lock timeout)', 409, {code: 'CONFLICT'}, 'lock-conflict', 'Someone else is changing this. Try again.', true],
        ['400 VALIDATION_FAILED', 400, {code: 'VALIDATION_FAILED', errors: [{field: 'name', message: 'm'}]}, 'validation', 'Some fields need attention.', false],
        ['400 MALFORMED_REQUEST', 400, {code: 'MALFORMED_REQUEST'}, 'malformed', 'The request could not be read.', false],
        ['400 gateway (no code; firewall or bad query encoding)', 400, {}, 'malformed', 'The request could not be read.', false],
        ['502 with code', 502, {code: 'SERVICE_UNAVAILABLE'}, 'unavailable', 'The service is unavailable. Try again.', true],
        ['503 with code', 503, {code: 'ACCESS_CHECK_UNAVAILABLE'}, 'unavailable', 'The service is unavailable. Try again.', true],
        ['503 without code', 503, {}, 'unavailable', 'The service is unavailable. Try again.', true],
        ['504 without code', 504, {}, 'unavailable', 'The service is unavailable. Try again.', true],
        ['500', 500, {code: 'INTERNAL_ERROR'}, 'server', 'Something went wrong', false],
        ['500 gateway (no code)', 500, {}, 'server', 'Something went wrong', false],
    ])('%s', (_label, status, extra, kind, text, retryable) => {
        const mapped = ui(status, problem(status, extra ?? {}));
        expect(mapped.kind).toBe(kind);
        expect(errorText(mapped)).toBe(text);
        expect(mapped.retryable).toBe(retryable);
        expect(mapped.status).toBe(status);
        // detail is shown only for codes the UI has no own text for
        expect(mapped.detail).toBeNull();
    });

    it('login 401 INVALID_CREDENTIALS is specific, not an ended session', () => {
        expect(ui(401, problem(401, {code: 'INVALID_CREDENTIALS'})).kind).toBe('specific');
    });

    it.each([
        ['503 HTML body', 503, '<html>busy</html>', 'text/html', 'unavailable', true],
        ['502 empty body', 502, undefined, null, 'unavailable', true],
        ['500 HTML body', 500, '<html>oops</html>', 'text/html', 'network', true],
        ['200-shaped JSON that is not a Problem', 400, {message: 'x'}, 'application/json', 'network', true],
        ['Problem body with a wrong content type', 409, problem(409, {code: 'CONFLICT'}), 'text/plain', 'network', true],
    ] as const)('non-Problem body: %s', (_label, status, body, contentType, kind, retryable) => {
        const mapped = ui(status, body, contentType);
        expect(mapped.kind).toBe(kind);
        expect(mapped.retryable).toBe(retryable);
        expect(mapped.problem).toBeNull();
    });

    it('accepts content-type parameters and letter case', () => {
        expect(ui(409, problem(409, {code: 'CONFLICT'}), 'Application/Problem+JSON; charset=utf-8').kind).toBe('lock-conflict');
    });

    it('a specific code shows the server detail', () => {
        const mapped = ui(409, problem(409, {code: 'FEATURE_PATH_TAKEN'}));
        expect(mapped.kind).toBe('specific');
        expect(mapped.detail).toBe('server detail');
    });

    it('an unknown code is treated as no code (status decides), and its detail is not trusted for known states', () => {
        expect(ui(403, problem(403, {code: 'SOMETHING_NEW'})).code).toBeNull();
        expect(ui(404, problem(404, {code: 'SOMETHING_NEW'})).kind).toBe('not-found');
        expect(ui(503, problem(503, {code: 'SOMETHING_NEW'})).kind).toBe('unavailable');
    });
});

describe('qa: Problem members are read defensively', () => {
    it('requires status, title and detail of the right types', () => {
        expect(parseProblem({status: '400', title: 't', detail: 'd'})).toBeNull();
        expect(parseProblem({status: 400.5, title: 't', detail: 'd'})).toBeNull();
        expect(parseProblem({status: 400, title: 't'})).toBeNull();
        expect(parseProblem([problem(400)])).toBeNull();
        expect(parseProblem(null)).toBeNull();
        expect(parseProblem('Bad Request')).toBeNull();
    });

    it.each([
        ['r-77', 'r-77'],
        ['9f8e7d6c-5b4a-4321-8fed-cba987654321', '9f8e7d6c-5b4a-4321-8fed-cba987654321'],
        ['<img src=x onerror=alert(1)>', null],
        ['a b', null],
        ['x'.repeat(129), null],
        ['', null],
        [42, null],
    ])('requestId %j shows as %j', (requestId, expected) => {
        const mapped = ui(500, problem(500, {requestId}));
        expect(mapped.requestId).toBe(expected);
    });

    it('keeps only well-formed parseErrors entries', () => {
        const parsed = parseProblem(problem(422, {
            code: 'GHERKIN_INVALID',
            parseErrors: [
                {line: 4, column: 3, message: 'ok'},
                {line: '4', column: 3, message: 'string line'},
                {line: 4.5, column: 3, message: 'fraction'},
                {line: 4, column: 3},
                null,
                'x',
                {line: 2, column: 1, message: 'ok2'},
            ],
        }));
        expect(parsed?.parseErrors).toEqual([
            {line: 4, column: 3, message: 'ok'},
            {line: 2, column: 1, message: 'ok2'},
        ]);
        expect(parseProblem(problem(422, {parseErrors: {line: 1}}))?.parseErrors).toEqual([]);
    });

    it.each([
        [3, 3],
        [0, 0],
        ['3', null],
        [3.5, null],
        [null, null],
        [undefined, null],
    ])('currentVersion %j reads as %j', (value, expected) => {
        expect(parseProblem(problem(409, {code: 'VERSION_CONFLICT', currentVersion: value}))?.currentVersion).toBe(expected);
    });

    it('featureIds keeps canonical lower-case UUIDs only', () => {
        const ok = 'f0000000-0000-4000-8000-0000000000a1';
        const parsed = parseProblem(problem(422, {
            code: 'FEATURE_NOT_PLANNABLE',
            featureIds: [ok, ok.toUpperCase(), 'not-a-uuid', 5, null],
        }));
        expect(parsed?.featureIds).toEqual([ok]);
    });

    it('splits errors[] by field and by JSON pointer; everything else is form-level', () => {
        const mapped = ui(400, problem(400, {
            code: 'VALIDATION_FAILED',
            errors: [
                {field: 'name', message: 'name bad'},
                {field: 'name', message: 'name worse'},
                {pointer: '/path', message: 'path bad'},
                {pointer: '/path/0', message: 'nested path'},
                {field: 'unknownField', message: 'form 1'},
                {pointer: '$[0].uri', message: 'form 2'},
                {message: 'no field or pointer'},
                {field: 'name'},
                'garbage',
            ],
        }));
        const split = splitFieldErrors(mapped, ['name', 'path']);
        expect(split.fields).toEqual({name: ['name bad', 'name worse'], path: ['path bad', 'nested path']});
        expect(split.form).toEqual(['form 1', 'form 2']);
    });

    it('splitFieldErrors on a non-validation error is empty', () => {
        expect(splitFieldErrors(ui(503, problem(503)), ['name'])).toEqual({fields: {}, form: []});
    });
});
