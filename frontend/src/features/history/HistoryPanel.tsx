import {useState} from 'react';
import {useQuery} from '@tanstack/react-query';
import {ArrowLeft, History, SearchX} from 'lucide-react';
import {authApi, scenarioApi, unwrap} from '../../api/client';
import {uiErrorOf} from '../../api/problem';
import {Avatar} from '../../ui/Avatar';
import {Badge} from '../../ui/Badge';
import {Button} from '../../ui/Button';
import {EmptyState} from '../../ui/EmptyState';
import {ErrorState} from '../../ui/ErrorState';
import {QueryView} from '../../ui/QueryView';
import {Skeleton} from '../../ui/Skeleton';
import {Table} from '../../ui/Table';
import {GherkinEditor} from '../editor/GherkinEditor';
import {historyStrings as t} from './strings';

const timeFormat = new Intl.DateTimeFormat(undefined, {dateStyle: 'medium', timeStyle: 'short'});

function VersionView({projectId, featureId, version, total, onBack}: { projectId: string; featureId: string; version: number; total: number; onBack: () => void }) {
    const query = useQuery({
        queryKey: ['feature', projectId, featureId, 'version', version],
        queryFn: () =>
            unwrap(
                scenarioApi.GET('/api/projects/{projectId}/features/{featureId}/versions/{version}', {
                    params: {path: {projectId, featureId, version: String(version)}},
                }),
            ),
    });
    const back = (
        <Button variant="ghost" size="sm" onPress={onBack}>
            <ArrowLeft aria-hidden="true" size={16}/>
            {t.back}
        </Button>
    );
    if (query.isPending) {
        return (
            <div aria-busy="true" className="flex flex-col gap-3 p-4">
                <Skeleton width="third"/>
                <Skeleton shape="block"/>
            </div>
        );
    }
    if (query.isError) {
        const error = uiErrorOf(query.error);
        if (error.kind === 'not-found') {
            return (
                <div role="alert">
                    <EmptyState icon={SearchX} title={t.versionNotFound} action={back}/>
                </div>
            );
        }
        return <ErrorState error={error} onRetry={() => void query.refetch()}/>;
    }
    return (
        <div className="flex h-full min-h-0 flex-col">
            <div className="flex flex-wrap items-center gap-2 border-b border-border px-4 py-2">
                {back}
                <Badge>{t.of(version, total)}</Badge>
                {query.data.message ? <span className="truncate text-sm text-text-muted">{query.data.message}</span> : null}
            </div>
            <div className="min-h-64 flex-1">
                <GherkinEditor
                    docKey={`version-${version}`}
                    initialDoc={query.data.source}
                    readOnly
                    diagnostics={[]}
                    label={t.sourceLabel(version)}
                    onChange={() => undefined}
                    onSave={() => undefined}
                />
            </div>
        </div>
    );
}

/** F7 / AC-7.1: the feature's versions, newest first, with author names from the org's members (D13). */
export function HistoryPanel({projectId, featureId, orgId}: { projectId: string; featureId: string; orgId: string | null }) {
    const [open, setOpen] = useState<number | null>(null);
    const versions = useQuery({
        queryKey: ['feature', projectId, featureId, 'versions'],
        queryFn: () => unwrap(scenarioApi.GET('/api/projects/{projectId}/features/{featureId}/versions', {params: {path: {projectId, featureId}}})),
    });
    const members = useQuery({
        queryKey: ['orgs', orgId, 'members'],
        queryFn: () => unwrap(authApi.GET('/api/orgs/{orgId}/members', {params: {path: {orgId: orgId ?? ''}}})),
        enabled: orgId !== null,
    });
    const total = versions.data?.length ?? 0;
    if (open !== null) return <VersionView projectId={projectId} featureId={featureId} version={open} total={total} onBack={() => setOpen(null)}/>;
    return (
        <QueryView query={versions} label={t.versionsLabel} isEmpty={(list) => list.length === 0} empty={<EmptyState icon={History} title={t.versionsLabel}/>}>
            {(list) => (
                <Table
                    label={t.versionsLabel}
                    columns={[
                        {id: 'version', label: t.colVersion, isRowHeader: true},
                        {id: 'author', label: t.colAuthor},
                        {id: 'time', label: t.colTime},
                        {id: 'message', label: t.colMessage},
                    ]}
                    rows={[...list]
                        .sort((a, b) => b.version - a.version)
                        .map((v) => {
                            const author = members.data?.find((m) => m.userId === v.createdBy);
                            return {
                                id: String(v.version),
                                cells: {
                                    version: (
                                        <Button variant="ghost" size="sm" aria-label={t.open(v.version)} onPress={() => setOpen(v.version)}>
                                            {t.version(v.version)}
                                        </Button>
                                    ),
                                    author: (
                                        <span className="flex items-center gap-2">
                                            <Avatar userId={v.createdBy} fullName={author?.fullName} email={author?.email} size="sm"/>
                                            <span className={author ? undefined : 'text-text-muted'}>{author?.fullName ?? t.formerMember}</span>
                                        </span>
                                    ),
                                    time: (
                                        <time dateTime={v.createdAt} className="whitespace-nowrap text-text-muted">
                                            {timeFormat.format(new Date(v.createdAt))}
                                        </time>
                                    ),
                                    message: v.message ? <span>{v.message}</span> : <span className="text-text-muted">{t.noMessage}</span>,
                                },
                            };
                        })}
                />
            )}
        </QueryView>
    );
}
