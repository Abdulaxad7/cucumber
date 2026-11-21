// D5 and the auth-service role ceiling: an actor may give, change or remove only roles ranked at most their own,
// never their own role. Role comparisons go through src/auth/roles.ts.
import {hasOrgRole, type OrgRole} from '../../auth/roles';

const ORG_ROLES: readonly OrgRole[] = ['OWNER', 'ADMIN', 'MEMBER', 'READER'];

export function canInvite(myRole: OrgRole | null | undefined): boolean {
    return hasOrgRole(myRole, 'ADMIN');
}

export function assignableOrgRoles(myRole: OrgRole | null | undefined): OrgRole[] {
    if (!canInvite(myRole)) return [];
    return ORG_ROLES.filter((role) => hasOrgRole(myRole, role));
}

export function canManageMember(myRole: OrgRole | null | undefined, targetRole: OrgRole, isSelf: boolean): boolean {
    return !isSelf && canInvite(myRole) && hasOrgRole(myRole, targetRole);
}

export function canCreateProject(myRole: OrgRole | null | undefined): boolean {
    return hasOrgRole(myRole, 'ADMIN');
}

/** Seats used = members + PENDING invitations whose expiresAt is in the future (instants, not strings). */
export function seatsUsed(memberCount: number, invitations: ReadonlyArray<{status: string; expiresAt: string}>, now = Date.now()): number {
    return memberCount + invitations.filter((i) => i.status === 'PENDING' && Date.parse(i.expiresAt) > now).length;
}
