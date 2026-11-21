import {useState, type FormEvent} from 'react';
import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query';
import {useRouter} from '@tanstack/react-router';
import {LogOut, UserMinus, UserPlus, Users} from 'lucide-react';
import type {components} from '../../api/generated/auth-service';
import {authApi, unwrap, unwrapEmpty} from '../../api/client';
import {splitFieldErrors, uiErrorOf} from '../../api/problem';
import type {ProjectRole} from '../../auth/roles';
import {Avatar} from '../../ui/Avatar';
import {Badge} from '../../ui/Badge';
import {Button} from '../../ui/Button';
import {Card} from '../../ui/Card';
import {ConfirmDialog, Dialog} from '../../ui/Dialog';
import {EmptyState} from '../../ui/EmptyState';
import {InlineAlert} from '../../ui/InlineAlert';
import {PageHeader} from '../../ui/PageHeader';
import {QueryView} from '../../ui/QueryView';
import {Select} from '../../ui/Select';
import {Table} from '../../ui/Table';
import {ToastRegion} from '../../ui/Toast';
import {errorText} from '../../ui/errorText';
import {showToast} from '../../ui/toastStore';
import {projectStrings as t} from './strings';
import {useProject} from './useProject';

type ProjectMember = components['schemas']['ProjectMember'];
const ROLES: readonly ProjectRole[] = ['ADMIN', 'MEMBER', 'TESTER', 'READER'];
const dateFormat = new Intl.DateTimeFormat(undefined, {dateStyle: 'medium'});

function isProjectRole(value: string): value is ProjectRole {
    return ROLES.some((r) => r === value);
}

function AddMemberDialog({orgId, projectId, members, isOpen, onOpenChange}: {
    orgId: string;
    projectId: string;
    members: readonly ProjectMember[];
    isOpen: boolean;
    onOpenChange: (open: boolean) => void;
}) {
    const queryClient = useQueryClient();
    const orgMembers = useQuery({
        queryKey: ['orgs', orgId, 'members'],
        queryFn: () => unwrap(authApi.GET('/api/orgs/{orgId}/members', {params: {path: {orgId}}})),
        enabled: isOpen,
    });
    const [userId, setUserId] = useState<string | null>(null);
    const [role, setRole] = useState<ProjectRole | null>(null);
    const [errors, setErrors] = useState<{userId?: string[]; role?: string[]}>({});
    const [formError, setFormError] = useState<string | null>(null);
    const candidates = (orgMembers.data ?? []).filter((m) => !members.some((p) => p.userId === m.userId));

    const add = useMutation({
        mutationFn: (body: {userId: string; role: ProjectRole}) =>
            unwrap(authApi.POST('/api/orgs/{orgId}/projects/{projectId}/members', {params: {path: {orgId, projectId}}, body})),
        onSuccess: () => {
            showToast('success', t.memberAdded);
            void queryClient.invalidateQueries({queryKey: ['projects', projectId, 'members']});
            setUserId(null);
            setRole(null);
            onOpenChange(false);
        },
        onError: (error) => {
            const ui = uiErrorOf(error);
            if (ui.code === 'PROJECT_ROLE_NOT_ALLOWED') setErrors({role: [t.roleNotAllowed]});
            else if (ui.code === 'ALREADY_MEMBER') setErrors({userId: [t.alreadyMember]});
            else if (ui.kind === 'validation') {
                const split = splitFieldErrors(ui, ['userId', 'role']);
                setErrors(split.fields);
                setFormError(split.form.length > 0 ? split.form.join(' ') : null);
            } else if (ui.kind !== 'archived') setFormError(errorText(ui));
        },
    });

    function onSubmit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        setFormError(null);
        const missing: {userId?: string[]; role?: string[]} = {};
        if (!userId) missing.userId = [t.choosePerson];
        if (!role) missing.role = [t.chooseRole];
        setErrors(missing);
        if (userId && role) add.mutate({userId, role});
    }

    return (
        <Dialog isOpen={isOpen} onOpenChange={onOpenChange} title={t.addTitle} size="md">
            <form noValidate onSubmit={onSubmit} className="flex flex-col gap-4">
                {formError ? (
                    <InlineAlert tone="danger" role="alert">
                        {formError}
                    </InlineAlert>
                ) : null}
                {orgMembers.isSuccess && candidates.length === 0 ? <p className="text-base text-text-muted">{t.noCandidates}</p> : null}
                <Select
                    label={t.person}
                    placeholder={t.personPlaceholder}
                    options={candidates.map((m) => ({id: m.userId, label: m.fullName, description: `${m.email} · ${m.role}`}))}
                    value={userId}
                    onChange={setUserId}
                    errors={errors.userId}
                />
                <Select
                    label={t.role}
                    placeholder={t.rolePlaceholder}
                    options={ROLES.map((r) => ({id: r, label: r}))}
                    value={role}
                    onChange={(value) => {
                        if (isProjectRole(value)) setRole(value);
                    }}
                    errors={errors.role}
                />
                <div className="flex justify-end gap-2 pt-2">
                    <Button variant="secondary" onPress={() => onOpenChange(false)}>
                        {t.cancel}
                    </Button>
                    <Button type="submit" variant="primary" loading={add.isPending}>
                        {t.addMember}
                    </Button>
                </div>
            </form>
        </Dialog>
    );
}

/** F6 / AC-4.3: explicit project members; admins add, change and remove; any explicit member can leave. */
export function ProjectMembersPage({projectId}: { projectId: string }) {
    const queryClient = useQueryClient();
    const router = useRouter();
    const {workspace, project, archived, canWrite, crumbs} = useProject();
    const orgId = workspace.orgId ?? '';
    const me = workspace.session?.userId ?? null;
    const [adding, setAdding] = useState(false);
    const [removing, setRemoving] = useState<ProjectMember | null>(null);
    const [rowErrors, setRowErrors] = useState<Record<string, string>>({});

    const members = useQuery({
        queryKey: ['projects', projectId, 'members'],
        queryFn: () => unwrap(authApi.GET('/api/orgs/{orgId}/projects/{projectId}/members', {params: {path: {orgId, projectId}}})),
        enabled: orgId !== '',
    });

    const changeRole = useMutation({
        mutationFn: ({userId, role}: {userId: string; role: ProjectRole}) =>
            unwrap(authApi.PATCH('/api/orgs/{orgId}/projects/{projectId}/members/{userId}', {params: {path: {orgId, projectId, userId}}, body: {role}})),
        onMutate: ({userId}) => setRowErrors((e) => ({...e, [userId]: ''})),
        onSuccess: () => {
            showToast('success', t.roleChanged);
            void queryClient.invalidateQueries({queryKey: ['projects', projectId, 'members']});
        },
        onError: (error, {userId}) => {
            const ui = uiErrorOf(error);
            setRowErrors((e) => ({...e, [userId]: ui.code === 'PROJECT_ROLE_NOT_ALLOWED' ? t.roleNotAllowed : errorText(ui)}));
        },
    });

    const remove = useMutation({
        mutationFn: (userId: string) =>
            unwrapEmpty(authApi.DELETE('/api/orgs/{orgId}/projects/{projectId}/members/{userId}', {params: {path: {orgId, projectId, userId}}})),
        onSuccess: (_data, userId) => {
            setRemoving(null);
            if (userId === me) {
                void queryClient.invalidateQueries({queryKey: ['projects']});
                router.history.push(`/orgs/${orgId}/projects`);
                return;
            }
            showToast('success', t.memberRemoved);
            void queryClient.invalidateQueries({queryKey: ['projects', projectId, 'members']});
        },
        onError: (error, userId) => {
            setRemoving(null);
            setRowErrors((e) => ({...e, [userId]: errorText(uiErrorOf(error))}));
        },
    });

    const leaving = removing !== null && removing.userId === me;

    return (
        <>
            <PageHeader
                title={t.membersTitle}
                breadcrumbs={crumbs(t.membersTitle)}
                actions={
                    canWrite ? (
                        <Button variant="primary" onPress={() => setAdding(true)}>
                            <UserPlus aria-hidden="true" size={16}/>
                            {t.addMember}
                        </Button>
                    ) : undefined
                }
            />
            <div className="flex flex-col gap-4">
                {archived ? <InlineAlert tone="readonly">{t.archivedBanner}</InlineAlert> : null}
                <Card padding="none">
                    <QueryView
                        query={members}
                        label={t.membersLabel}
                        isEmpty={(list) => list.length === 0}
                        empty={
                            <EmptyState icon={Users} title={t.noMembers}>
                                <p>{t.noMembersHelp}</p>
                            </EmptyState>
                        }
                    >
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
                                                    {canWrite && !self ? (
                                                        <div className="w-36">
                                                            <Select
                                                                label={t.roleFor(m.fullName)}
                                                                hideLabel
                                                                size="sm"
                                                                options={ROLES.map((r) => ({id: r, label: r}))}
                                                                value={m.role}
                                                                onChange={(value) => {
                                                                    if (isProjectRole(value) && value !== m.role) changeRole.mutate({userId: m.userId, role: value});
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
                                            actions: (
                                                <div className="flex justify-end">
                                                    {self && !archived ? (
                                                        <Button variant="ghost" size="sm" onPress={() => setRemoving(m)}>
                                                            <LogOut aria-hidden="true" size={16}/>
                                                            {t.leave}
                                                        </Button>
                                                    ) : canWrite && !self ? (
                                                        <Button variant="ghost" size="sm" iconOnly aria-label={t.remove(m.fullName)} onPress={() => setRemoving(m)}>
                                                            <UserMinus aria-hidden="true" size={16}/>
                                                        </Button>
                                                    ) : null}
                                                </div>
                                            ),
                                        },
                                    };
                                })}
                            />
                        )}
                    </QueryView>
                </Card>
            </div>
            {project && canWrite ? (
                <AddMemberDialog orgId={orgId} projectId={projectId} members={members.data ?? []} isOpen={adding} onOpenChange={setAdding}/>
            ) : null}
            <ConfirmDialog
                isOpen={removing !== null}
                onOpenChange={(open) => {
                    if (!open) setRemoving(null);
                }}
                title={leaving ? t.leaveTitle : removing ? t.removeTitle(removing.fullName) : ''}
                confirmLabel={leaving ? t.leaveConfirm : t.removeConfirm}
                cancelLabel={t.cancel}
                danger
                loading={remove.isPending}
                onConfirm={() => {
                    if (removing) remove.mutate(removing.userId);
                }}
            >
                {leaving ? <p>{t.leaveBody}</p> : null}
            </ConfirmDialog>
            <ToastRegion/>
        </>
    );
}
