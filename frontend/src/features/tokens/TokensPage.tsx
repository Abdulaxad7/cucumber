import {useState, type FormEvent} from 'react';
import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query';
import {CircleCheck, CircleSlash, Copy, KeyRound, Plus, ShieldBan, Trash2} from 'lucide-react';
import {reportApi, unwrap} from '../../api/client';
import {splitFieldErrors, uiErrorOf} from '../../api/problem';
import {hasProjectRole} from '../../auth/roles';
import {useWorkspace} from '../../app/useWorkspace';
import {Badge} from '../../ui/Badge';
import {Button} from '../../ui/Button';
import {Card} from '../../ui/Card';
import {ConfirmDialog, Dialog} from '../../ui/Dialog';
import {EmptyState} from '../../ui/EmptyState';
import {InlineAlert} from '../../ui/InlineAlert';
import {PageHeader} from '../../ui/PageHeader';
import {QueryView} from '../../ui/QueryView';
import {Table} from '../../ui/Table';
import {TextField} from '../../ui/TextField';
import {ToastRegion} from '../../ui/Toast';
import {errorText} from '../../ui/errorText';
import {showToast} from '../../ui/toastStore';
import {tokenStrings as t} from './strings';

const dateFormat = new Intl.DateTimeFormat(undefined, {dateStyle: 'medium'});

function curlExample(origin: string): string {
    return [
        `curl -X POST "${origin}/api/ci/runs?name=CI%20%23$BUILD_NUMBER" \\`,
        '  -H "Authorization: Bearer $CUCUMBER_UPLOAD_TOKEN" \\',
        '  -H "Content-Type: application/json" \\',
        '  --data-binary @target/cucumber.json',
    ].join('\n');
}

/** F9 / AC-8.5: project upload tokens (ADMIN): create (shown once), list by prefix, revoke. */
export function TokensPage({projectId}: { projectId: string }) {
    const queryClient = useQueryClient();
    const workspace = useWorkspace();
    const project = workspace.project;
    const admin = hasProjectRole(project?.myRole, 'ADMIN');
    const archived = project?.archivedAt != null;
    const [creating, setCreating] = useState(false);
    const [name, setName] = useState('');
    const [nameErrors, setNameErrors] = useState<string[] | undefined>();
    const [formError, setFormError] = useState<string | null>(null);
    const [secret, setSecret] = useState<string | null>(null);
    const [revoking, setRevoking] = useState<{id: string; name: string} | null>(null);

    const tokens = useQuery({
        queryKey: ['upload-tokens', projectId],
        queryFn: () => unwrap(reportApi.GET('/api/projects/{projectId}/upload-tokens', {params: {path: {projectId}}})),
        enabled: admin,
    });

    const create = useMutation({
        // The response carries the secret: never keep it in the mutation cache.
        gcTime: 0,
        mutationFn: () => unwrap(reportApi.POST('/api/projects/{projectId}/upload-tokens', {params: {path: {projectId}}, body: {name}})),
        onSuccess: (created) => {
            void queryClient.invalidateQueries({queryKey: ['upload-tokens', projectId]});
            setCreating(false);
            setName('');
            setSecret(created.token);
            create.reset();
        },
        onError: (error) => {
            const ui = uiErrorOf(error);
            if (ui.code === 'UPLOAD_TOKEN_LIMIT') setFormError(t.limit);
            else if (ui.kind === 'validation') {
                const split = splitFieldErrors(ui, ['name']);
                setNameErrors(split.fields.name);
                setFormError(split.form.length > 0 ? split.form.join(' ') : null);
            } else setFormError(errorText(ui));
        },
    });

    const revoke = useMutation({
        mutationFn: (tokenId: string) => unwrap(reportApi.POST('/api/projects/{projectId}/upload-tokens/{tokenId}/revoke', {params: {path: {projectId, tokenId}}})),
        onSuccess: () => {
            setRevoking(null);
            showToast('success', t.revokedToast);
            void queryClient.invalidateQueries({queryKey: ['upload-tokens', projectId]});
        },
        onError: (error) => {
            setRevoking(null);
            showToast('error', errorText(uiErrorOf(error)));
        },
    });

    function onCreate(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        setFormError(null);
        if (name.trim() === '') return setNameErrors([t.nameRequired]);
        setNameErrors(undefined);
        create.mutate();
    }

    const crumbs = [
        ...(workspace.org && workspace.orgId ? [{label: workspace.org.name, href: `/orgs/${workspace.orgId}/projects`}] : []),
        ...(project ? [{label: project.name, href: `/projects/${project.id}/features`}] : []),
        {label: t.title},
    ];
    const hasTokens = (tokens.data?.length ?? 0) > 0;

    return (
        <>
            <PageHeader
                title={t.title}
                description={t.intro}
                breadcrumbs={crumbs}
                actions={
                    admin && !archived && hasTokens ? (
                        <Button variant="primary" onPress={() => setCreating(true)}>
                            <Plus aria-hidden="true" size={16}/>
                            {t.create}
                        </Button>
                    ) : undefined
                }
            />
            {!admin && project ? (
                <Card padding="none">
                    <EmptyState icon={ShieldBan} title={t.adminsOnly}/>
                </Card>
            ) : (
                <Card padding="none">
                    <QueryView
                        query={tokens}
                        label={t.listLabel}
                        isEmpty={(list) => list.length === 0}
                        empty={
                            <EmptyState
                                icon={KeyRound}
                                title={t.noTokens}
                                action={
                                    !archived ? (
                                        <Button variant="primary" onPress={() => setCreating(true)}>
                                            <Plus aria-hidden="true" size={16}/>
                                            {t.create}
                                        </Button>
                                    ) : undefined
                                }
                            >
                                <p>{t.noTokensHelp}</p>
                            </EmptyState>
                        }
                    >
                        {(list) => (
                            <Table
                                label={t.listLabel}
                                columns={[
                                    {id: 'name', label: t.colName, isRowHeader: true},
                                    {id: 'prefix', label: t.colPrefix},
                                    {id: 'status', label: t.colStatus},
                                    {id: 'created', label: t.colCreated},
                                    {id: 'lastUsed', label: t.colLastUsed},
                                    {id: 'actions', label: t.colActions, hideLabel: true},
                                ]}
                                rows={list.map((token) => ({
                                    id: token.id,
                                    cells: {
                                        name: <span className="font-medium">{token.name}</span>,
                                        prefix: (
                                            <span className="font-mono text-xs">
                                                <span>{token.tokenPrefix}</span>
                                                <span aria-hidden="true" className="text-text-muted">
                                                    …
                                                </span>
                                            </span>
                                        ),
                                        status: token.revokedAt ? (
                                            <Badge icon={<CircleSlash aria-hidden="true" size={12}/>}>{t.revoked}</Badge>
                                        ) : (
                                            <Badge tone="success" icon={<CircleCheck aria-hidden="true" size={12}/>}>
                                                {t.active}
                                            </Badge>
                                        ),
                                        created: <span className="text-text-muted">{dateFormat.format(new Date(token.createdAt))}</span>,
                                        lastUsed: <span className="text-text-muted">{token.lastUsedAt ? dateFormat.format(new Date(token.lastUsedAt)) : t.never}</span>,
                                        actions: token.revokedAt ? null : (
                                            <div className="flex justify-end">
                                                <Button variant="ghost" size="sm" iconOnly aria-label={t.revoke(token.name)} onPress={() => setRevoking({id: token.id, name: token.name})}>
                                                    <Trash2 aria-hidden="true" size={16}/>
                                                </Button>
                                            </div>
                                        ),
                                    },
                                }))}
                            />
                        )}
                    </QueryView>
                </Card>
            )}

            <Dialog
                isOpen={creating}
                onOpenChange={(value) => {
                    setCreating(value);
                    if (!value) setFormError(null);
                }}
                title={t.createTitle}
                size="md"
            >
                <form noValidate onSubmit={onCreate} className="flex flex-col gap-4">
                    {formError ? (
                        <InlineAlert tone="danger" role="alert">
                            {formError}
                        </InlineAlert>
                    ) : null}
                    <TextField id="token-name" label={t.nameLabel} hint={t.nameHint} value={name} onChange={setName} errors={nameErrors} required/>
                    <div className="flex justify-end gap-2 pt-2">
                        <Button variant="secondary" onPress={() => setCreating(false)}>
                            {t.cancel}
                        </Button>
                        <Button type="submit" variant="primary" loading={create.isPending}>
                            {t.create}
                        </Button>
                    </div>
                </form>
            </Dialog>

            <Dialog
                isOpen={secret !== null}
                onOpenChange={(value) => {
                    if (!value) setSecret(null);
                }}
                title={t.shownTitle}
                size="lg"
                footer={
                    <>
                        <Button
                            variant="secondary"
                            onPress={() => {
                                if (secret) void navigator.clipboard?.writeText(secret).then(() => showToast('success', t.copied));
                            }}
                        >
                            <Copy aria-hidden="true" size={16}/>
                            {t.copy}
                        </Button>
                        <Button variant="primary" onPress={() => setSecret(null)}>
                            {t.done}
                        </Button>
                    </>
                }
            >
                <div className="flex flex-col gap-4">
                    <InlineAlert tone="info">{t.shownBody}</InlineAlert>
                    <code aria-label={t.tokenLabel} className="block break-all rounded-md border border-border bg-surface-hover px-3 py-2 font-mono text-sm text-text">
                        {secret}
                    </code>
                    <div className="flex flex-col gap-1.5">
                        <span className="text-sm font-medium text-text">{t.curlTitle}</span>
                        <pre aria-label={t.curlLabel} className="overflow-x-auto rounded-md border border-border bg-surface-hover px-3 py-2 font-mono text-xs text-text">
                            {curlExample(window.location.origin)}
                        </pre>
                    </div>
                </div>
            </Dialog>

            <ConfirmDialog
                isOpen={revoking !== null}
                onOpenChange={(value) => {
                    if (!value) setRevoking(null);
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
                <p>{revoking ? t.revokeBody(revoking.name) : null}</p>
            </ConfirmDialog>
            <ToastRegion/>
        </>
    );
}
