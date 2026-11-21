// Batch C: an in-memory auth-service for end-to-end journeys (organizations, members, invitations, projects and
// project members), following the contract's role ceiling, seats, project role cap and visibility rules.
import type {components} from '../../api/generated/auth-service';
import {acme, ids} from './auth';
import {billingId, users, type UserKey} from './workspace';

type S = components['schemas'];
type OrgRole = S['OrganizationRole'];
type ProjectRole = S['ProjectRole'];

export interface FakeAnswer {
    status: number;
    body: unknown;
    contentType: string;
}

const ORG_RANK: Record<OrgRole, number> = {OWNER: 4, ADMIN: 3, MEMBER: 2, READER: 1};
const json = (status: number, body: unknown): FakeAnswer => ({status, body, contentType: 'application/json'});
const empty = (): FakeAnswer => ({status: 204, body: null, contentType: 'application/json'});

export function authProblemAnswer(status: number, code: S['ErrorCode'], instance: string, errors?: S['InvalidField'][]): FakeAnswer {
    return {
        status,
        contentType: 'application/problem+json',
        body: {type: 'about:blank', title: 'Error', status, detail: `Problem ${code}`, instance, code, ...(errors ? {errors} : {})},
    };
}

function isRecord(value: unknown): value is Record<string, unknown> {
    return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function isOrgRole(value: unknown): value is OrgRole {
    return value === 'OWNER' || value === 'ADMIN' || value === 'MEMBER' || value === 'READER';
}

function isProjectRole(value: unknown): value is ProjectRole {
    return value === 'ADMIN' || value === 'MEMBER' || value === 'TESTER' || value === 'READER';
}

const joined = '2026-10-01T08:30:00Z';
let counter = 0;
function newId(prefix: number): string {
    counter += 1;
    return `${prefix.toString(16).padStart(8, '0')}-0000-4000-8000-${counter.toString(16).padStart(12, '0')}`;
}

export interface PendingToken {
    token: string;
    orgId: string;
    email: string;
    role: OrgRole;
    outcome?: FakeAnswer;
}

export class FakeAuth {
    readonly orgs: Array<Omit<S['Organization'], 'myRole'>> = [{...acme}];
    readonly members = new Map<string, S['Member'][]>();
    readonly invitations = new Map<string, S['Invitation'][]>();
    readonly projects: Array<Omit<S['Project'], 'myRole'>> = [
        {id: ids.checkout, orgId: ids.acme, name: 'Checkout', createdAt: '2026-10-02T09:00:00Z', archivedAt: null},
        {id: billingId, orgId: ids.acme, name: 'Billing', createdAt: '2026-09-02T09:00:00Z', archivedAt: '2026-10-20T10:00:00Z'},
    ];
    readonly projectMembers = new Map<string, S['ProjectMember'][]>();
    readonly tokens = new Map<string, PendingToken>();
    /** Answers to force for "METHOD path" keys (error cases). */
    readonly overrides = new Map<string, FakeAnswer>();
    user: UserKey | null;

    constructor(user: UserKey | null) {
        this.user = user;
        this.members.set(
            ids.acme,
            (['ana', 'ben', 'gus', 'hal', 'dan'] as const).map((key) => ({
                userId: users[key].id,
                email: users[key].email,
                fullName: users[key].fullName,
                role: users[key].orgRole,
                joinedAt: joined,
            })),
        );
        this.invitations.set(ids.acme, []);
        const member = (key: UserKey, role: ProjectRole): S['ProjectMember'] => ({
            userId: users[key].id,
            email: users[key].email,
            fullName: users[key].fullName,
            role,
            joinedAt: joined,
        });
        this.projectMembers.set(ids.checkout, [member('dan', 'READER'), member('gus', 'MEMBER'), member('hal', 'TESTER')]);
        this.projectMembers.set(billingId, []);
    }

    private me(): {id: string; email: string} | null {
        return this.user ? users[this.user] : null;
    }

    private orgRoleOf(orgId: string): OrgRole | null {
        const me = this.me();
        return (me && this.members.get(orgId)?.find((m) => m.userId === me.id)?.role) ?? null;
    }

    private projectRoleOf(project: Omit<S['Project'], 'myRole'>): ProjectRole | null {
        const orgRole = this.orgRoleOf(project.orgId);
        if (!orgRole) return null;
        if (orgRole === 'OWNER' || orgRole === 'ADMIN') return 'ADMIN';
        const me = this.me();
        return (me && this.projectMembers.get(project.id)?.find((m) => m.userId === me.id)?.role) ?? null;
    }

    /** The caller's effective role in a project (batch D's fake report-service checks roles with it). */
    projectRole(projectId: string): ProjectRole | null {
        const project = this.projects.find((p) => p.id === projectId);
        return project ? this.projectRoleOf(project) : null;
    }

    isProjectArchived(projectId: string): boolean {
        return this.projects.find((p) => p.id === projectId)?.archivedAt != null;
    }

    seatsUsed(orgId: string, now = Date.now()): number {
        const pending = (this.invitations.get(orgId) ?? []).filter((i) => i.status === 'PENDING' && Date.parse(i.expiresAt) > now);
        return (this.members.get(orgId)?.length ?? 0) + pending.length;
    }

    answer(method: string, url: URL, body: unknown): FakeAnswer | null {
        const path = url.pathname;
        const forced = this.overrides.get(`${method} ${path}`);
        if (forced) {
            this.overrides.delete(`${method} ${path}`);
            return forced;
        }
        let m = /^\/api\/auth\/invitations\/([^/]+)\/accept$/.exec(path);
        if (m && method === 'POST') return this.accept(path, decodeURIComponent(m[1] ?? ''), body);

        if (path === '/api/orgs' && method === 'GET') {
            return json(
                200,
                this.orgs.flatMap((o) => {
                    const role = this.orgRoleOf(o.id);
                    return role ? [{...o, myRole: role}] : [];
                }),
            );
        }
        if (path === '/api/orgs' && method === 'POST') {
            if (!isRecord(body) || typeof body.name !== 'string' || body.name.trim() === '') {
                return authProblemAnswer(400, 'VALIDATION_FAILED', path, [{field: 'name', message: 'Enter a name'}]);
            }
            const me = this.me();
            if (!me) return authProblemAnswer(401, 'UNAUTHENTICATED', path);
            const org = {id: newId(0x7a1b2c3d), name: body.name.trim(), plan: 'FREE' as const, seatLimit: 5, createdAt: '2026-11-09T10:00:00Z'};
            this.orgs.push(org);
            this.members.set(org.id, [{userId: me.id, email: me.email, fullName: this.user ? users[this.user].fullName : '', role: 'OWNER', joinedAt: org.createdAt}]);
            this.invitations.set(org.id, []);
            return json(201, {...org, myRole: 'OWNER'});
        }

        m = /^\/api\/orgs\/([^/]+)(\/.*)?$/.exec(path);
        if (!m) return null;
        const orgId = m[1] ?? '';
        const rest = m[2] ?? '';
        const org = this.orgs.find((o) => o.id === orgId);
        const myRole = this.orgRoleOf(orgId);
        if (!org || !myRole) return authProblemAnswer(403, 'FORBIDDEN', path);
        const myRank = ORG_RANK[myRole];

        if (rest === '' && method === 'GET') return json(200, {...org, myRole});
        if (rest === '/members' && method === 'GET') return json(200, this.members.get(orgId) ?? []);
        m = /^\/members\/([^/]+)$/.exec(rest);
        if (m) {
            const list = this.members.get(orgId) ?? [];
            const target = list.find((x) => x.userId === m?.[1]);
            const me = this.me();
            if (method === 'PATCH') {
                if (myRank < 3) return authProblemAnswer(403, 'FORBIDDEN', path);
                if (!target) return authProblemAnswer(404, 'MEMBER_NOT_FOUND', path);
                if (target.userId === me?.id) return authProblemAnswer(422, 'SELF_ROLE_CHANGE', path);
                if (!isRecord(body) || !isOrgRole(body.role)) return authProblemAnswer(400, 'VALIDATION_FAILED', path, [{field: 'role', message: 'Choose a role'}]);
                if (ORG_RANK[target.role] > myRank || ORG_RANK[body.role] > myRank) return authProblemAnswer(422, 'ROLE_CEILING', path);
                target.role = body.role;
                return json(200, target);
            }
            if (method === 'DELETE') {
                if (!target) return authProblemAnswer(404, 'MEMBER_NOT_FOUND', path);
                if (target.userId !== me?.id && (myRank < 3 || ORG_RANK[target.role] > myRank)) return authProblemAnswer(422, 'ROLE_CEILING', path);
                this.members.set(orgId, list.filter((x) => x !== target));
                return empty();
            }
        }
        if (rest === '/invitations' && method === 'GET') return json(200, (this.invitations.get(orgId) ?? []).filter((i) => i.status === 'PENDING'));
        if (rest === '/invitations' && method === 'POST') {
            if (myRank < 3) return authProblemAnswer(403, 'FORBIDDEN', path);
            if (!isRecord(body) || typeof body.email !== 'string' || !body.email.includes('@') || !isOrgRole(body.role)) {
                return authProblemAnswer(400, 'VALIDATION_FAILED', path, [{field: 'email', message: 'Enter a valid email'}]);
            }
            if (ORG_RANK[body.role] > myRank) return authProblemAnswer(422, 'ROLE_CEILING', path);
            if (org.seatLimit !== null && this.seatsUsed(orgId) >= org.seatLimit) return authProblemAnswer(409, 'SEAT_LIMIT_REACHED', path);
            const invitation: S['Invitation'] = {
                id: newId(0x1c2d3e4f),
                email: body.email,
                role: body.role,
                status: 'PENDING',
                createdAt: '2026-11-09T10:00:00Z',
                expiresAt: '2099-11-16T10:00:00Z',
            };
            this.invitations.get(orgId)?.push(invitation);
            const token = `tok-${invitation.id.slice(-6)}`;
            this.tokens.set(token, {token, orgId, email: body.email, role: body.role});
            return json(201, {...invitation, token});
        }
        m = /^\/invitations\/([^/]+)$/.exec(rest);
        if (m && method === 'DELETE') {
            const list = this.invitations.get(orgId) ?? [];
            const invitation = list.find((i) => i.id === m?.[1]);
            if (!invitation) return authProblemAnswer(404, 'INVITATION_NOT_FOUND', path);
            if (ORG_RANK[invitation.role] > myRank) return authProblemAnswer(422, 'ROLE_CEILING', path);
            invitation.status = 'REVOKED';
            return empty();
        }

        if (rest === '/projects' && method === 'GET') {
            return json(
                200,
                this.projects
                    .filter((p) => p.orgId === orgId)
                    .flatMap((p) => {
                        const role = this.projectRoleOf(p);
                        return role ? [{...p, myRole: role}] : [];
                    })
                    .sort((a, b) => a.name.toLowerCase().localeCompare(b.name.toLowerCase())),
            );
        }
        if (rest === '/projects' && method === 'POST') {
            if (myRank < 3) return authProblemAnswer(403, 'FORBIDDEN', path);
            if (!isRecord(body) || typeof body.name !== 'string' || body.name.trim() === '') {
                return authProblemAnswer(400, 'VALIDATION_FAILED', path, [{field: 'name', message: 'Enter a name'}]);
            }
            const name = body.name.trim();
            if (this.projects.some((p) => p.orgId === orgId && p.name.toLowerCase() === name.toLowerCase())) {
                return authProblemAnswer(409, 'PROJECT_NAME_TAKEN', path);
            }
            const project = {id: newId(0x2e3f4a5b), orgId, name, createdAt: '2026-11-09T10:00:00Z', archivedAt: null};
            this.projects.push(project);
            this.projectMembers.set(project.id, []);
            return json(201, {...project, myRole: 'ADMIN'});
        }
        m = /^\/projects\/([^/]+)(\/[a-z]+)?(\/[^/]+)?$/.exec(rest);
        if (!m) return null;
        const project = this.projects.find((p) => p.id === m?.[1] && p.orgId === orgId);
        const projectRole = project ? this.projectRoleOf(project) : null;
        if (!project || !projectRole) return authProblemAnswer(403, 'FORBIDDEN', path);
        const action = m[2] ?? '';
        const memberId = m[3]?.slice(1);
        const isAdmin = projectRole === 'ADMIN';
        const view = () => ({...project, myRole: projectRole});
        if (action === '' && method === 'GET') return json(200, view());
        if (action === '' && method === 'PATCH') {
            if (!isAdmin) return authProblemAnswer(403, 'FORBIDDEN', path);
            if (project.archivedAt) return authProblemAnswer(409, 'PROJECT_ARCHIVED', path);
            if (!isRecord(body) || typeof body.name !== 'string' || body.name.trim() === '') {
                return authProblemAnswer(400, 'VALIDATION_FAILED', path, [{field: 'name', message: 'Enter a name'}]);
            }
            project.name = body.name.trim();
            return json(200, view());
        }
        if ((action === '/archive' || action === '/restore') && method === 'POST') {
            if (!isAdmin) return authProblemAnswer(403, 'FORBIDDEN', path);
            project.archivedAt = action === '/archive' ? (project.archivedAt ?? '2026-11-09T10:00:00Z') : null;
            return json(200, view());
        }
        if (action === '/members') {
            const list = this.projectMembers.get(project.id) ?? [];
            if (!memberId && method === 'GET') return json(200, list);
            if (project.archivedAt && method !== 'GET') return authProblemAnswer(409, 'PROJECT_ARCHIVED', path);
            const me = this.me();
            if (!memberId && method === 'POST') {
                if (!isAdmin) return authProblemAnswer(403, 'FORBIDDEN', path);
                if (!isRecord(body) || typeof body.userId !== 'string' || !isProjectRole(body.role)) {
                    return authProblemAnswer(400, 'VALIDATION_FAILED', path, [{field: 'role', message: 'Choose a role'}]);
                }
                const orgMember = this.members.get(orgId)?.find((x) => x.userId === body.userId);
                if (!orgMember) return authProblemAnswer(422, 'NOT_ORG_MEMBER', path);
                if (orgMember.role === 'READER' && body.role !== 'READER') return authProblemAnswer(422, 'PROJECT_ROLE_NOT_ALLOWED', path);
                if (list.some((x) => x.userId === body.userId)) return authProblemAnswer(409, 'ALREADY_MEMBER', path);
                const added = {userId: orgMember.userId, email: orgMember.email, fullName: orgMember.fullName, role: body.role, joinedAt: '2026-11-09T10:00:00Z'};
                this.projectMembers.set(project.id, [...list, added].sort((a, b) => a.email.localeCompare(b.email)));
                return json(201, added);
            }
            const target = list.find((x) => x.userId === memberId);
            if (!target) return authProblemAnswer(404, 'MEMBER_NOT_FOUND', path);
            if (method === 'PATCH') {
                if (!isAdmin) return authProblemAnswer(403, 'FORBIDDEN', path);
                if (target.userId === me?.id) return authProblemAnswer(422, 'SELF_ROLE_CHANGE', path);
                if (!isRecord(body) || !isProjectRole(body.role)) return authProblemAnswer(400, 'VALIDATION_FAILED', path, [{field: 'role', message: 'Choose a role'}]);
                const orgMember = this.members.get(orgId)?.find((x) => x.userId === target.userId);
                if (orgMember?.role === 'READER' && body.role !== 'READER') return authProblemAnswer(422, 'PROJECT_ROLE_NOT_ALLOWED', path);
                target.role = body.role;
                return json(200, target);
            }
            if (method === 'DELETE') {
                if (!isAdmin && target.userId !== me?.id) return authProblemAnswer(403, 'FORBIDDEN', path);
                this.projectMembers.set(project.id, list.filter((x) => x !== target));
                return empty();
            }
        }
        return null;
    }

    private accept(path: string, token: string, body: unknown): FakeAnswer {
        const pending = this.tokens.get(token);
        if (!pending) return authProblemAnswer(404, 'INVITATION_NOT_FOUND', path);
        if (pending.outcome) return pending.outcome;
        const org = this.orgs.find((o) => o.id === pending.orgId);
        if (!org) return authProblemAnswer(404, 'INVITATION_NOT_FOUND', path);
        const me = this.me();
        let user: S['User'];
        let status = 200;
        if (me && this.user) {
            if (me.email !== pending.email) return authProblemAnswer(403, 'INVITATION_EMAIL_MISMATCH', path);
            user = {id: me.id, email: me.email, fullName: users[this.user].fullName, status: 'ACTIVE', mfaEnabled: false, createdAt: joined};
        } else {
            if (!isRecord(body) || typeof body.password !== 'string' || typeof body.fullName !== 'string' || body.fullName.trim() === '') {
                return authProblemAnswer(400, 'VALIDATION_FAILED', path, [{field: 'fullName', message: 'Enter your full name'}]);
            }
            user = {id: newId(0x3f4a5b6c), email: pending.email, fullName: body.fullName, status: 'ACTIVE', mfaEnabled: false, createdAt: '2026-11-09T10:00:00Z'};
            status = 201;
        }
        this.members.get(org.id)?.push({userId: user.id, email: user.email, fullName: user.fullName, role: pending.role, joinedAt: '2026-11-09T10:00:00Z'});
        this.tokens.delete(token);
        return json(status, {user, organization: {...org, myRole: pending.role}});
    }
}
