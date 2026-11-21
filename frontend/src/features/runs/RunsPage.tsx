import {useState} from 'react';
import {keepPreviousData, useQuery} from '@tanstack/react-query';
import {Link} from '@tanstack/react-router';
import {CirclePlay, Plus} from 'lucide-react';
import {hasProjectRole} from '../../auth/roles';
import {useWorkspace} from '../../app/useWorkspace';
import {AppLink} from '../../ui/AppLink';
import {Badge} from '../../ui/Badge';
import {Button} from '../../ui/Button';
import {Card} from '../../ui/Card';
import {EmptyState} from '../../ui/EmptyState';
import {InlineAlert} from '../../ui/InlineAlert';
import {PageHeader} from '../../ui/PageHeader';
import {Pagination} from '../../ui/Pagination';
import {QueryView} from '../../ui/QueryView';
import {Table} from '../../ui/Table';
import {ToastRegion} from '../../ui/Toast';
import {formatRate} from './chartData';
import {PlanRunDialog} from './PlanRunDialog';
import {RunCharts} from './RunCharts';
import {RunStatusBadge} from './RunStatusBadge';
import {POLL_MS, listRuns, runsKey} from './queries';
import {runStrings as t} from './strings';

const timeFormat = new Intl.DateTimeFormat(undefined, {dateStyle: 'medium', timeStyle: 'short'});

/** F8 / AC-8.1: the project's runs, newest first, 20 per page, with the dashboard for the current page. */
export function RunsPage({projectId}: { projectId: string }) {
    const workspace = useWorkspace();
    const project = workspace.project;
    const archived = project?.archivedAt != null;
    const canPlan = !archived && hasProjectRole(project?.myRole, 'MEMBER');
    const isAdmin = hasProjectRole(project?.myRole, 'ADMIN');
    const [page, setPage] = useState(0);
    const [planning, setPlanning] = useState(false);
    const query = useQuery({
        queryKey: runsKey(projectId, page),
        queryFn: () => listRuns(projectId, page),
        placeholderData: keepPreviousData,
        refetchInterval: POLL_MS,
        refetchIntervalInBackground: false,
    });

    const crumbs = [
        ...(workspace.org && workspace.orgId ? [{label: workspace.org.name, href: `/orgs/${workspace.orgId}/projects`}] : []),
        ...(project ? [{label: project.name, href: `/projects/${project.id}/features`}] : []),
        {label: t.title},
    ];

    return (
        <>
            <PageHeader
                title={t.title}
                breadcrumbs={crumbs}
                actions={
                    canPlan ? (
                        <Button variant="primary" onPress={() => setPlanning(true)}>
                            <Plus aria-hidden="true" size={16}/>
                            {t.plan}
                        </Button>
                    ) : undefined
                }
            />
            <div className="flex flex-col gap-6">
                {archived ? <InlineAlert tone="readonly">{t.archivedReadOnly}</InlineAlert> : null}
                <QueryView
                    query={query}
                    label={t.listLabel}
                    isEmpty={(data) => data.totalItems === 0}
                    empty={
                        <Card padding="none">
                            <EmptyState icon={CirclePlay} title={t.noRuns} action={isAdmin ? <AppLink href={`/projects/${projectId}/upload-tokens`}>{t.createToken}</AppLink> : undefined}>
                                <p>{t.noRunsHelp}</p>
                                {isAdmin ? <p>{t.ciHint}</p> : null}
                            </EmptyState>
                        </Card>
                    }
                >
                    {(data) => (
                        <div className="flex flex-col gap-6">
                            <section aria-label={t.dashboard} className="flex flex-col gap-2">
                                <p className="text-sm text-text-muted">{t.dashboardHelp}</p>
                                <RunCharts runs={data.items}/>
                            </section>
                            <Card padding="none">
                                <Table
                                    label={t.listLabel}
                                    columns={[
                                        {id: 'name', label: t.colRun, isRowHeader: true},
                                        {id: 'status', label: t.colStatus},
                                        {id: 'source', label: t.colSource},
                                        {id: 'total', label: t.colTotal, numeric: true},
                                        {id: 'rate', label: t.colPassRate, numeric: true},
                                        {id: 'environment', label: t.colEnvironment},
                                        {id: 'build', label: t.colBuild},
                                        {id: 'created', label: t.colCreated},
                                    ]}
                                    rows={data.items.map((run) => ({
                                        id: run.id,
                                        cells: {
                                            name: (
                                                <Link to="/projects/$projectId/runs/$runId" params={{projectId, runId: run.id}} className="rounded-sm font-medium text-text hover:text-primary">
                                                    {run.name}
                                                </Link>
                                            ),
                                            status: <RunStatusBadge status={run.status}/>,
                                            source: <span className="text-text-muted">{t.sources[run.source]}</span>,
                                            total: run.total,
                                            rate: <span className="font-medium">{formatRate(run.passRate)}</span>,
                                            environment: run.environment ? <Badge>{run.environment.name}</Badge> : <span className="text-text-muted">{t.dash}</span>,
                                            build: run.build ? <span className="font-mono text-xs">{run.build.name}</span> : <span className="text-text-muted">{t.dash}</span>,
                                            created: (
                                                <time dateTime={run.createdAt} className="whitespace-nowrap text-text-muted">
                                                    {timeFormat.format(new Date(run.createdAt))}
                                                </time>
                                            ),
                                        },
                                    }))}
                                />
                                <Pagination page={data.page} size={data.size} total={data.totalItems} onPage={setPage}/>
                            </Card>
                        </div>
                    )}
                </QueryView>
            </div>
            {canPlan ? <PlanRunDialog projectId={projectId} isOpen={planning} onOpenChange={setPlanning}/> : null}
            <ToastRegion/>
        </>
    );
}
