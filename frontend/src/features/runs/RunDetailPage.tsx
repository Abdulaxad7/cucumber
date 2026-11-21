import {useState} from 'react';
import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query';
import {Ban, CheckCheck, ClipboardPen, Eye, SearchX} from 'lucide-react';
import type {components} from '../../api/generated/report-service';
import {authApi, reportApi, unwrap} from '../../api/client';
import {uiErrorOf} from '../../api/problem';
import {hasProjectRole} from '../../auth/roles';
import {useWorkspace} from '../../app/useWorkspace';
import {AppLink} from '../../ui/AppLink';
import {Badge} from '../../ui/Badge';
import {Button} from '../../ui/Button';
import {Card} from '../../ui/Card';
import {ConfirmDialog} from '../../ui/Dialog';
import {EmptyState} from '../../ui/EmptyState';
import {ErrorState} from '../../ui/ErrorState';
import {InlineAlert} from '../../ui/InlineAlert';
import {PageHeader} from '../../ui/PageHeader';
import {Pagination} from '../../ui/Pagination';
import {QueryView} from '../../ui/QueryView';
import {Select} from '../../ui/Select';
import {SkeletonRows} from '../../ui/Skeleton';
import {StatusChip} from '../../ui/StatusChip';
import {Tab, TabList, TabPanel, Tabs} from '../../ui/Tabs';
import {Table} from '../../ui/Table';
import {ToastRegion} from '../../ui/Toast';
import {errorText} from '../../ui/errorText';
import {showToast} from '../../ui/toastStore';
import {uiStrings} from '../../ui/strings';
import {STATUSES, formatRate} from './chartData';
import {ResultDialog} from './ResultDialog';
import {RunStatusBadge} from './RunStatusBadge';
import {POLL_MS, RESULT_PAGE_SIZE, getRun, isOpen, resultsKey, runKey} from './queries';
import {runStrings as t} from './strings';

type Result = components['schemas']['TestCaseResult'];
type ExecutionStatus = components['schemas']['ExecutionStatus'];

function isExecutionStatus(value: string): value is ExecutionStatus {
    return STATUSES.some((s) => s === value);
}

function formatDuration(ms: number | null): string {
    if (ms === null) return t.dash;
    if (ms < 1000) return `${ms} ms`;
    const seconds = ms / 1000;
    return seconds < 60 ? `${seconds.toFixed(1)} s` : `${Math.floor(seconds / 60)} min ${Math.round(seconds % 60)} s`;
}

function Stat({label, value}: { label: string; value: string | number }) {
    return (
        <div className="flex flex-col gap-1 rounded-lg border border-border bg-surface-raised p-4 shadow-sm">
            <span className="text-sm text-text-muted">{label}</span>
            <span className="text-2xl font-semibold tabular-nums text-text">{value}</span>
        </div>
    );
}

/** F8 + F9 / AC-8.2, AC-8.4, AC-8.7: a run's summary and results, with recording, complete and cancel. */
export function RunDetailPage({projectId, runId}: { projectId: string; runId: string }) {
    const queryClient = useQueryClient();
    const workspace = useWorkspace();
    const project = workspace.project;
    const archived = project?.archivedAt != null;
    const [statusFilter, setStatusFilter] = useState<ExecutionStatus | null>(null);
    const [page, setPage] = useState(0);
    const [open, setOpen] = useState<{result: Result; record: boolean} | null>(null);
    const [confirm, setConfirm] = useState<'complete' | 'cancel' | null>(null);
    const [actionError, setActionError] = useState<string | null>(null);

    const run = useQuery({
        queryKey: runKey(projectId, runId),
        queryFn: () => getRun(projectId, runId),
        refetchInterval: (query) => (isOpen(query.state.data?.status) ? POLL_MS : false),
        refetchIntervalInBackground: false,
    });
    const results = useQuery({
        queryKey: [...resultsKey(projectId, runId, statusFilter), page],
        queryFn: () =>
            unwrap(
                reportApi.GET('/api/projects/{projectId}/runs/{runId}/results', {
                    params: {path: {projectId, runId}, query: {page, size: RESULT_PAGE_SIZE, ...(statusFilter ? {status: statusFilter} : {})}},
                }),
            ),
    });
    const members = useQuery({
        queryKey: ['orgs', workspace.orgId, 'members'],
        queryFn: () => unwrap(authApi.GET('/api/orgs/{orgId}/members', {params: {path: {orgId: workspace.orgId ?? ''}}})),
        enabled: workspace.orgId !== null,
    });
    const nameOf = (userId: string | null) => {
        if (!userId) return t.nobody;
        return members.data?.find((m) => m.userId === userId)?.fullName ?? t.formerMember;
    };

    const transition = useMutation({
        mutationFn: (action: 'complete' | 'cancel') =>
            action === 'complete'
                ? unwrap(reportApi.POST('/api/projects/{projectId}/runs/{runId}/complete', {params: {path: {projectId, runId}}}))
                : unwrap(reportApi.POST('/api/projects/{projectId}/runs/{runId}/cancel', {params: {path: {projectId, runId}}})),
        onSuccess: (updated, action) => {
            setConfirm(null);
            queryClient.setQueryData(runKey(projectId, runId), updated);
            void queryClient.invalidateQueries({queryKey: ['runs', projectId]});
            showToast('success', action === 'complete' ? t.completed : t.cancelled);
        },
        onError: (error) => {
            setConfirm(null);
            const ui = uiErrorOf(error);
            if (ui.code === 'RUN_CLOSED' || ui.kind === 'archived') void queryClient.invalidateQueries({queryKey: runKey(projectId, runId)});
            setActionError(ui.code === 'RUN_CLOSED' ? t.closed : errorText(ui));
        },
    });

    const crumbs = [
        ...(workspace.org && workspace.orgId ? [{label: workspace.org.name, href: `/orgs/${workspace.orgId}/projects`}] : []),
        ...(project ? [{label: project.name, href: `/projects/${project.id}/features`}] : []),
        {label: t.title, href: `/projects/${projectId}/runs`},
        {label: run.data?.name ?? t.dash},
    ];

    if (run.isPending) {
        return (
            <div aria-busy="true">
                <SkeletonRows rows={4}/>
            </div>
        );
    }
    if (run.isError) {
        const error = uiErrorOf(run.error);
        if (error.kind === 'not-found') {
            return (
                <div role="alert">
                    <EmptyState icon={SearchX} title={t.notFound} action={<AppLink href={`/projects/${projectId}/runs`}>{t.backToRuns}</AppLink>}/>
                </div>
            );
        }
        return <ErrorState error={error} onRetry={() => void run.refetch()}/>;
    }

    const data = run.data;
    const manual = data.source === 'MANUAL';
    const writable = manual && isOpen(data.status) && !archived;
    const canRecord = writable && hasProjectRole(project?.myRole, 'TESTER');
    const canCancel = writable && hasProjectRole(project?.myRole, 'MEMBER');
    const summary = data.summary;

    return (
        <>
            <PageHeader
                title={data.name}
                breadcrumbs={crumbs}
                actions={
                    <>
                        <RunStatusBadge status={data.status}/>
                        {canCancel ? (
                            <Button variant="ghost" onPress={() => setConfirm('cancel')}>
                                <Ban aria-hidden="true" size={16}/>
                                {t.cancel}
                            </Button>
                        ) : null}
                        {canRecord ? (
                            <Button variant="primary" onPress={() => setConfirm('complete')}>
                                <CheckCheck aria-hidden="true" size={16}/>
                                {t.complete}
                            </Button>
                        ) : null}
                    </>
                }
            />
            <div className="mb-4 flex flex-wrap items-center gap-2 text-sm text-text-muted">
                <Badge>{t.sources[data.source]}</Badge>
                {data.environment ? <Badge>{data.environment.name}</Badge> : null}
                {data.build ? <span className="font-mono text-xs">{data.build.name}</span> : null}
                {data.tagExpression ? <span className="font-mono text-xs">{data.tagExpression}</span> : null}
            </div>
            {archived ? (
                <div className="mb-4">
                    <InlineAlert tone="readonly">{t.archivedReadOnly}</InlineAlert>
                </div>
            ) : null}
            {actionError ? (
                <div className="mb-4">
                    <InlineAlert tone="danger" role="alert">
                        {actionError}
                    </InlineAlert>
                </div>
            ) : null}
            <Tabs>
                <TabList label={t.tabsLabel}>
                    <Tab id="summary">{t.tabSummary}</Tab>
                    <Tab id="results">{t.tabResults}</Tab>
                </TabList>
                <TabPanel id="summary">
                    <section aria-label={t.summaryLabel} className="flex flex-col gap-4 pt-4">
                        <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
                            <Stat label={t.total} value={summary.total}/>
                            <Stat label={t.executed} value={summary.executed}/>
                            <Stat label={t.passRate} value={formatRate(summary.passRate)}/>
                            <Stat label={t.duration} value={formatDuration(summary.durationMs)}/>
                        </div>
                        <ul aria-label={t.statusCounts} className="flex flex-wrap gap-2">
                            {STATUSES.map((status) => (
                                <li key={status} className="flex items-center gap-2 rounded-md border border-border bg-surface-raised px-2 py-1">
                                    <StatusChip status={status}/>
                                    <span className="text-sm font-semibold tabular-nums">{summary.counts[status]}</span>
                                </li>
                            ))}
                        </ul>
                        <Card padding="none" title={t.byFeature}>
                            <Table
                                label={t.byFeature}
                                columns={[
                                    {id: 'feature', label: t.colFeature, isRowHeader: true},
                                    {id: 'total', label: t.colTotal, numeric: true},
                                    ...STATUSES.map((s) => ({id: s, label: uiStrings.status[s] ?? s, numeric: true})),
                                ]}
                                rows={summary.byFeature.map((f) => ({
                                    id: f.featurePath,
                                    cells: {
                                        feature: (
                                            <span className="flex flex-col">
                                                <span className="font-medium">{f.featureName}</span>
                                                <span className="font-mono text-xs text-text-muted">{f.featurePath}</span>
                                            </span>
                                        ),
                                        total: f.total,
                                        ...Object.fromEntries(STATUSES.map((s) => [s, f.counts[s]])),
                                    },
                                }))}
                            />
                        </Card>
                    </section>
                </TabPanel>
                <TabPanel id="results">
                    <div className="flex flex-col gap-4 pt-4">
                        <div className="w-56">
                            <Select
                                label={t.statusFilter}
                                options={[{id: 'ALL', label: t.allStatuses}, ...STATUSES.map((s) => ({id: s, label: uiStrings.status[s] ?? s}))]}
                                value={statusFilter ?? 'ALL'}
                                onChange={(value) => {
                                    setPage(0);
                                    setStatusFilter(isExecutionStatus(value) ? value : null);
                                }}
                            />
                        </div>
                        <Card padding="none">
                            <QueryView query={results} label={t.resultsLabel} isEmpty={(r) => r.totalItems === 0} empty={<EmptyState title={t.noResults}/>}>
                                {(list) => (
                                    <>
                                        <Table
                                            label={t.resultsLabel}
                                            columns={[
                                                {id: 'position', label: t.colPosition, numeric: true},
                                                {id: 'name', label: t.colName, isRowHeader: true},
                                                {id: 'status', label: t.colStatus},
                                                {id: 'executedBy', label: t.colExecutedBy},
                                                {id: 'comment', label: t.colComment},
                                                {id: 'actions', label: t.colActions, hideLabel: true},
                                            ]}
                                            rows={list.items.map((r) => ({
                                                id: r.id,
                                                cells: {
                                                    position: r.position,
                                                    name: (
                                                        <span className="flex min-w-0 flex-col">
                                                            <span className="truncate font-medium">{r.name}</span>
                                                            <span className="truncate font-mono text-xs text-text-muted">{t.featureLine(r.featurePath, r.line)}</span>
                                                        </span>
                                                    ),
                                                    status: <StatusChip status={r.status}/>,
                                                    executedBy: <span className={r.executedBy ? undefined : 'text-text-muted'}>{nameOf(r.executedBy)}</span>,
                                                    comment: r.comment ? <span className="line-clamp-2 text-sm">{r.comment}</span> : <span className="text-text-muted">{t.dash}</span>,
                                                    actions: (
                                                        <div className="flex justify-end gap-1">
                                                            {canRecord ? (
                                                                <Button variant="ghost" size="sm" iconOnly aria-label={t.record(r.name)} onPress={() => setOpen({result: r, record: true})}>
                                                                    <ClipboardPen aria-hidden="true" size={16}/>
                                                                </Button>
                                                            ) : null}
                                                            <Button variant="ghost" size="sm" iconOnly aria-label={t.open(r.name)} onPress={() => setOpen({result: r, record: false})}>
                                                                <Eye aria-hidden="true" size={16}/>
                                                            </Button>
                                                        </div>
                                                    ),
                                                },
                                            }))}
                                        />
                                        <Pagination page={list.page} size={list.size} total={list.totalItems} onPage={setPage}/>
                                    </>
                                )}
                            </QueryView>
                        </Card>
                    </div>
                </TabPanel>
            </Tabs>
            <ResultDialog
                projectId={projectId}
                runId={runId}
                result={open?.result ?? null}
                canRecord={(open?.record ?? false) && canRecord}
                executedByName={nameOf}
                onClose={() => setOpen(null)}
            />
            <ConfirmDialog
                isOpen={confirm !== null}
                onOpenChange={(value) => {
                    if (!value) setConfirm(null);
                }}
                title={confirm === 'cancel' ? t.cancelTitle : t.completeTitle}
                confirmLabel={confirm === 'cancel' ? t.cancel : t.complete}
                cancelLabel={t.keep}
                danger={confirm === 'cancel'}
                loading={transition.isPending}
                onConfirm={() => {
                    if (confirm) {
                        setActionError(null);
                        transition.mutate(confirm);
                    }
                }}
            >
                <p>{confirm === 'cancel' ? t.cancelBody : t.completeBody}</p>
            </ConfirmDialog>
            <ToastRegion/>
        </>
    );
}
