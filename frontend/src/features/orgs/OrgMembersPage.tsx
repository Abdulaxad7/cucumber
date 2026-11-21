import {useState} from 'react';
import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query';
import {Mail, UserMinus, UserPlus, Users} from 'lucide-react';
import type {components} from '../../api/generated/auth-service';
import {authApi, unwrap, unwrapEmpty} from '../../api/client';
import {uiErrorOf} from '../../api/problem';
import type {OrgRole} from '../../auth/roles';
import {useWorkspace} from '../../app/useWorkspace';
import {Avatar} from '../../ui/Avatar';
import {Badge} from '../../ui/Badge';
import {Button} from '../../ui/Button';
import {Card} from '../../ui/Card';
import {ConfirmDialog} from '../../ui/Dialog';
import {EmptyState} from '../../ui/EmptyState';
import {PageHeader} from '../../ui/PageHeader';
import {QueryView} from '../../ui/QueryView';
import {Select} from '../../ui/Select';
import {Table} from '../../ui/Table';
import {ToastRegion} from '../../ui/Toast';
import {errorText} from '../../ui/errorText';
import {showToast} from '../../ui/toastStore';
import {InviteDialog, TokenDialog} from '../invitations/InviteDialogs';
import {assignableOrgRoles, canInvite, canManageMember, seatsUsed} from './permissions';
import {orgStrings as t} from './strings';

type Member = components['schemas']['Member'];
type Invitation = components['schemas']['Invitation'];

const dateFormat = new Intl.DateTimeFormat(undefined, {dateStyle: 'medium'});

function isOrgRole(value: string): value is OrgRole {
    return value === 'OWNER' || value === 'ADMIN' || value === 'MEMBER' || value === 'READER';
}

/** F5 / US-3: an organization's members, pending invitations and seats. */
export function OrgMembersPage({orgId}: { orgId: string }) {
    const queryClient = useQueryClient();
    const workspace = useWorkspace();
    const org = workspace.org;
    const myRole = org?.myRole ?? null;
    const admin = canInvite(myRole);
    const me = workspace.session?.userId ?? null;

    const members = useQuery({
        queryKey: ['orgs', orgId, 'members'],
        queryFn: () => unwrap(authApi.GET('/api/orgs/{orgId}/members', {params: {path: {orgId}}})),
    });
    const invitations = useQuery({
        queryKey: ['orgs', orgId, 'invitations'],
        queryFn: () => unwrap(authApi.GET('/api/orgs/{orgId}/invitations', {params: {path: {orgId}}})),
        enabled: admin,
    });

    const [rowErrors, setRowErrors] = useState<Record<string, string>>({});
    const [removing, setRemoving] = useState<Member | null>(null);
    const [revoking, setRevoking] = useState<Invitation | null>(null);
    const [inviting, setInviting] = useState(false);
    const [token, setToken] = useState<string | null>(null);

    const used = seatsUsed(members.data?.length ?? 0, invitations.data ?? []);
    const limit = org?.seatLimit ?? null;
    const full = limit !== null && used >= limit;

    const changeRole = useMutation({
        mutationFn: ({userId, role}: {userId: string; role: OrgRole}) =>
            unwrap(authApi.PATCH('/api/orgs/{orgId}/members/{userId}', {params: {path: {orgId, userId}}, body: {role}})),
        onMutate: ({userId}) => setRowErrors((e) => ({...e, [userId]: ''})),
        onSuccess: () => {
            showToast('success', t.roleChanged);
            void queryClient.invalidateQueries({queryKey: ['orgs', orgId, 'members']});
        },
        onError: (error, {userId}) => {
            const ui = uiErrorOf(error);
            setRowErrors((e) => ({...e, [userId]: (ui.code ? t.errors[ui.code] : undefined) ?? errorText(ui)}));
        },
    });

    const removeMember = useMutation({
        mutationFn: (userId: string) => unwrapEmpty(authApi.DELETE('/api/orgs/{orgId}/members/{userId}', {params: {path: {orgId, userId}}})),
        onSuccess: () => {
            setRemoving(null);
            showToast('success', t.memberRemoved);
            void queryClient.invalidateQueries({queryKey: ['orgs', orgId, 'members']});
        },
        onError: (error, userId) => {
            setRemoving(null);
            const ui = uiErrorOf(error);
            setRowErrors((e) => ({...e, [userId]: (ui.code ? t.errors[ui.code] : undefined) ?? errorText(ui)}));
        },
    });

    const revoke = useMutation({
        mutationFn: (invitationId: string) =>
            unwrapEmpty(authApi.DELETE('/api/orgs/{orgId}/invitations/{invitationId}', {params: {path: {orgId, invitationId}}})),
        onSuccess: () => {
            setRevoking(null);
            showToast('success', t.invitationRevoked);
            void queryClient.invalidateQueries({queryKey: ['orgs', orgId, 'invitations']});
        },
        onError: (error) => {
            setRevoking(null);
            showToast('error', errorText(uiErrorOf(error)));
        },
    });

    const roles = assignableOrgRoles(myRole);
    const crumbs = org ? [{label: org.name, href: `/orgs/${orgId}/projects`}, {label: t.membersTitle}] : [{label: t.membersTitle}];

    return (
        <>
            <PageHeader
                title={t.membersTitle}
                breadcrumbs={crumbs}
                actions={
                    admin ? (
                        <div className="flex items-center gap-3">
                            {full ? (
                                <span id="invite-disabled-reason" className="text-sm text-text-muted">
                                    {t.noFreeSeat}
                                </span>
                            ) : null}
                            <Button variant="primary" isDisabled={full} aria-describedby={full ? 'invite-disabled-reason' : undefined} onPress={() => setInviting(true)}>
                                <UserPlus aria-hidden="true" size={16}/>
                                {t.invite}
                            </Button>
                        </div>
                    ) : undefined
                }
            />
            <div className="flex flex-col gap-6">
                <div className="flex flex-wrap items-center gap-3">
                    <Badge tone={full ? 'warning' : 'neutral'} icon={<Users aria-hidden="true" size={12}/>}>
                        {t.seats(used, limit)}
                    </Badge>
                    {!admin && myRole ? <span className="text-sm text-text-muted">{t.readOnlyMembers}</span> : null}
                </div>
                <Card padding="none" title={t.membersTitle}>
                    <QueryView query={members} label={t.membersLabel}>
                        {(list) => (
                            <Table
                                label={t.membersLabel}
                                columns={[
                                    {id: 'name', label: t.colName, isRowHeader: true},
                                    {id: 'role', label: t.colRole},
                                    {id: 'joined', label: t.colJoined},
                                    {id: 'actions', label: t.colActions, hideLabel: true},
                                ]}
                                rows={list.map((m) => {
                                    const self = m.userId === me;
                                    const manageable = canManageMember(myRole, m.role, self);
                                    return {
                                        id: m.userId,
                                        cells: {
                                            name: (
                                                <span className="flex items-center gap-3">
                                                    <Avatar userId={m.userId} fullName={m.fullName} email={m.email} size="sm"/>
                                                    <span className="flex min-w-0 flex-col">
                                                        <span className="truncate font-medium">
                                                            {m.fullName} {self ? <span className="text-text-muted">{t.you}</span> : null}
                                                        </span>
                                                        <span className="truncate text-xs text-text-muted">{m.email}</span>
                                                    </span>
                                                </span>
                                            ),
                                            role: (
                                                <div className="flex flex-col items-start gap-1">
                                                    {manageable ? (
                                                        <div className="w-36">
                                                            <Select
                                                                label={t.roleFor(m.fullName)}
                                                                hideLabel
                                                                size="sm"
                                                                options={roles.map((r) => ({id: r, label: r}))}
                                                                value={m.role}
                                                                onChange={(value) => {
                                                                    if (isOrgRole(value) && value !== m.role) changeRole.mutate({userId: m.userId, role: value});
                                                                }}
                                                            />
                                                        </div>
                                                    ) : (
                                                        <Badge>{m.role}</Badge>
                                                    )}
                                                    {rowErrors[m.userId] ? (
                                                        <span role="alert" className="text-xs font-medium text-danger">
                                                            {rowErrors[m.userId]}
                                                        </span>
                                                    ) : null}
                                                </div>
                                            ),
                                            joined: <span className="text-text-muted">{dateFormat.format(new Date(m.joinedAt))}</span>,
                                            actions: manageable ? (
                                                <div className="flex justify-end">
                                                    <Button variant="ghost" size="sm" iconOnly aria-label={t.remove(m.fullName)} onPress={() => setRemoving(m)}>
                                                        <UserMinus aria-hidden="true" size={16}/>
                                                    </Button>
                                                </div>
                                            ) : null,
                                        },
                                    };
                                })}
                            />
                        )}
                    </QueryView>
                </Card>
                {admin ? (
                    <Card padding="none" title={t.invitationsTitle}>
                        <QueryView
                            query={invitations}
                            label={t.invitationsLabel}
                            isEmpty={(list) => list.length === 0}
                            empty={<EmptyState icon={Mail} title={t.noInvitations}/>}
                        >
                            {(list) => (
                                <Table
                                    label={t.invitationsLabel}
                                    columns={[
                                        {id: 'email', label: t.colEmail, isRowHeader: true},
                                        {id: 'role', label: t.colRole},
                                        {id: 'expires', label: t.colExpires},
                                        {id: 'actions', label: t.colActions, hideLabel: true},
                                    ]}
                                    rows={list.map((i) => ({
                                        id: i.id,
                                        cells: {
                                            email: <span className="font-medium">{i.email}</span>,
                                            role: <Badge>{i.role}</Badge>,
                                            expires: <span className="text-text-muted">{dateFormat.format(new Date(i.expiresAt))}</span>,
                                            actions: (
                                                <div className="flex justify-end">
                                                    <Button variant="ghost" size="sm" aria-label={t.revoke(i.email)} onPress={() => setRevoking(i)}>
                                                        {t.revokeConfirm}
                                                    </Button>
                                                </div>
                                            ),
                                        },
                                    }))}
                                />
                            )}
                        </QueryView>
                    </Card>
                ) : null}
            </div>

            <InviteDialog
                orgId={orgId}
                isOpen={inviting}
                onOpenChange={setInviting}
                roles={roles}
                seats={{used, limit}}
                onInvited={(created) => {
                    setInviting(false);
                    setToken(created.token);
                }}
            />
            <TokenDialog token={token} onClose={() => setToken(null)}/>
            <ConfirmDialog
                isOpen={removing !== null}
                onOpenChange={(open) => {
                    if (!open) setRemoving(null);
                }}
                title={removing ? t.removeTitle(removing.fullName) : ''}
                confirmLabel={t.removeConfirm}
                cancelLabel={t.cancel}
                danger
                loading={removeMember.isPending}
                onConfirm={() => {
                    if (removing) removeMember.mutate(removing.userId);
                }}
            >
                <p>{t.removeBody(org?.name ?? '')}</p>
            </ConfirmDialog>
            <ConfirmDialog
                isOpen={revoking !== null}
                onOpenChange={(open) => {
                    if (!open) setRevoking(null);
                }}
                title={t.revokeTitle}
                confirmLabel={t.revokeConfirm}
                cancelLabel={t.cancel}
                danger
                loading={revoke.isPending}
                onConfirm={() => {
                    if (revoking) revoke.mutate(revoking.id);
                }}
            >
                <p>{revoking ? t.revokeBody(revoking.email) : null}</p>
            </ConfirmDialog>
            <ToastRegion/>
        </>
    );
}
