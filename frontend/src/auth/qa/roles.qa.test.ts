// qa (frontend phase 1): D5 / AC-3.2, AC-3.5, AC-4.4, AC-6.6, AC-8.6. The full role × action matrix, derived from the
// contracts (auth-service 1.5.0, scenario-service 1.0.7, report-service 1.0.2), checked against roles.ts and the
// organization permission helpers. A non-member is `null` (no myRole).
import {describe, expect, it} from 'vitest';
import {hasOrgRole, hasProjectRole, type OrgRole, type ProjectRole} from '../roles';
import {assignableOrgRoles, canCreateProject, canInvite, canManageMember} from '../../features/orgs/permissions';

const ORG: Array<OrgRole | null> = ['OWNER', 'ADMIN', 'MEMBER', 'READER', null];
const PROJECT: Array<ProjectRole | null> = ['ADMIN', 'MEMBER', 'TESTER', 'READER', null];

/** Minimum project role per action, from the contracts. */
const PROJECT_ACTIONS: Record<string, ProjectRole> = {
    'edit, create, archive or restore a feature': 'MEMBER',
    'plan a manual run': 'MEMBER',
    'cancel a run': 'MEMBER',
    'record a result': 'TESTER',
    'complete a run': 'TESTER',
    'manage upload tokens': 'ADMIN',
    'rename, archive or restore the project; manage its members': 'ADMIN',
    'read runs, features, history': 'READER',
};

const PROJECT_RANK = {ADMIN: 4, MEMBER: 3, TESTER: 2, READER: 1} as const;
const ORG_RANK = {OWNER: 4, ADMIN: 3, MEMBER: 2, READER: 1} as const;

describe('qa: project role × action (AC-4.4, AC-6.6, AC-8.6)', () => {
    for (const [action, minimum] of Object.entries(PROJECT_ACTIONS)) {
        it.each(PROJECT.map((role) => [role ?? 'non-member', role]))(`${action}: %s`, (_label, role) => {
            const expected = role !== null && PROJECT_RANK[role] >= PROJECT_RANK[minimum];
            expect(hasProjectRole(role, minimum)).toBe(expected);
        });
    }

    it('undefined (role not loaded yet) allows nothing', () => {
        expect(hasProjectRole(undefined, 'READER')).toBe(false);
    });
});

describe('qa: organization role × action (AC-3.2, AC-3.5)', () => {
    it.each(ORG.map((role) => [role ?? 'non-member', role]))('invite and create project: %s', (_label, role) => {
        const expected = role === 'OWNER' || role === 'ADMIN';
        expect(canInvite(role)).toBe(expected);
        expect(canCreateProject(role)).toBe(expected);
    });

    it.each([
        ['OWNER', ['OWNER', 'ADMIN', 'MEMBER', 'READER']],
        ['ADMIN', ['ADMIN', 'MEMBER', 'READER']],
        ['MEMBER', []],
        ['READER', []],
    ] as const)('%s may assign exactly %j', (role, roles) => {
        expect(assignableOrgRoles(role)).toEqual(roles);
    });

    it('nobody may manage their own row; managers may manage only roles at most their own', () => {
        for (const me of ORG) {
            for (const target of ['OWNER', 'ADMIN', 'MEMBER', 'READER'] as const) {
                expect(canManageMember(me, target, true)).toBe(false);
                const expected = me !== null && ORG_RANK[me] >= 3 && ORG_RANK[me] >= ORG_RANK[target];
                expect(canManageMember(me, target, false)).toBe(expected);
            }
        }
    });

    it('hasOrgRole is a total order on the four roles', () => {
        for (const a of ['OWNER', 'ADMIN', 'MEMBER', 'READER'] as const) {
            for (const b of ['OWNER', 'ADMIN', 'MEMBER', 'READER'] as const) {
                expect(hasOrgRole(a, b)).toBe(ORG_RANK[a] >= ORG_RANK[b]);
            }
        }
        expect(hasOrgRole(null, 'READER')).toBe(false);
    });
});
