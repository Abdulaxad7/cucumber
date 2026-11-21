// qa (frontend phase 1): D9 retry policy. Queries retry once, and only for a network failure, 502, 503 or 504;
// mutations never retry.
import {describe, expect, it} from 'vitest';
import {createQueryClient} from '../queryClient';
import {ApiError, networkError, toUiError} from '../../api/problem';

const PJ = 'application/problem+json';

function apiError(status: number, code?: string) {
    return new ApiError(toUiError({status, contentType: PJ, body: {type: 'about:blank', title: 'T', status, detail: 'd', instance: '/x', ...(code ? {code} : {})}}));
}

describe('qa: D9 retries', () => {
    const client = createQueryClient();
    const retry = client.getDefaultOptions().queries?.retry;

    function retries(error: unknown, failureCount = 0): boolean {
        if (typeof retry !== 'function') throw new Error('retry is not a function');
        return retry(failureCount, error instanceof Error ? error : new Error('x'));
    }

    it.each([
        ['network', new ApiError(networkError()), true],
        ['502', apiError(502), true],
        ['503', apiError(503, 'ACCESS_CHECK_UNAVAILABLE'), true],
        ['504', apiError(504), true],
        ['500', apiError(500, 'INTERNAL_ERROR'), false],
        ['409 CONFLICT', apiError(409, 'CONFLICT'), false],
        ['403', apiError(403, 'FORBIDDEN'), false],
        ['404', apiError(404, 'NOT_FOUND'), false],
        ['401', apiError(401, 'UNAUTHENTICATED'), false],
        ['400', apiError(400, 'VALIDATION_FAILED'), false],
    ])('a query failing with %s retries: %s', (_label, error, expected) => {
        expect(retries(error)).toBe(expected);
    });

    it('retries at most once', () => {
        expect(retries(apiError(503), 1)).toBe(false);
    });

    it('mutations never retry', () => {
        expect(client.getDefaultOptions().mutations?.retry).toBe(false);
    });
});
