import {describe, expect, it} from 'vitest';
import {assignableOrgRoles, canInvite, canManageMember} from './permissions';

// @US-3 @AC-3.2: the role ceiling (auth-service: rank at most the actor's own; never your own role).
describe('AC-3.2 role ceiling', () => {
    it('an ADMIN may assign ADMIN, MEMBER and READER; an OWNER any role', () => {
        expect(assignableOrgRoles('ADMIN')).toEqual(['ADMIN', 'MEMBER', 'READER']);
        expect(assignableOrgRoles('OWNER')).toEqual(['OWNER', 'ADMIN', 'MEMBER', 'READER']);
        expect(assignableOrgRoles('MEMBER')).toEqual([]);
        expect(assignableOrgRoles('READER')).toEqual([]);
    });

    it.each([
        ['ADMIN', 'OWNER', false, false],
        ['ADMIN', 'ADMIN', false, true],
        ['ADMIN', 'MEMBER', false, true],
        ['ADMIN', 'ADMIN', true, false],
        ['OWNER', 'OWNER', false, true],
        ['OWNER', 'OWNER', true, false],
        ['MEMBER', 'READER', false, false],
    ] as const)('%s managing a %s (self: %s): %s', (me, target, self, allowed) => {
        expect(canManageMember(me, target, self)).toBe(allowed);
    });

    it('only OWNER and ADMIN invite', () => {
        expect([canInvite('OWNER'), canInvite('ADMIN'), canInvite('MEMBER'), canInvite('READER')]).toEqual([true, true, false, false]);
    });
});
