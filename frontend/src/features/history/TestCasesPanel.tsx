import {useQuery} from '@tanstack/react-query';
import {ListChecks} from 'lucide-react';
import {scenarioApi, unwrap} from '../../api/client';
import {Badge} from '../../ui/Badge';
import {EmptyState} from '../../ui/EmptyState';
import {QueryView} from '../../ui/QueryView';
import {Table} from '../../ui/Table';
import {historyStrings as t} from './strings';

/** F7 / AC-7.2: the feature's test cases, as Cucumber would run them, in the API's order. */
export function TestCasesPanel({projectId, featureId}: { projectId: string; featureId: string }) {
    const query = useQuery({
        queryKey: ['feature', projectId, featureId, 'test-cases'],
        queryFn: () => unwrap(scenarioApi.GET('/api/projects/{projectId}/features/{featureId}/test-cases', {params: {path: {projectId, featureId}}})),
    });
    return (
        <QueryView
            query={query}
            label={t.testCasesLabel}
            isEmpty={(list) => list.length === 0}
            empty={
                <EmptyState icon={ListChecks} title={t.noTestCases}>
                    <p>{t.noTestCasesHelp}</p>
                </EmptyState>
            }
        >
            {(list) => (
                <Table
                    label={t.testCasesLabel}
                    columns={[
                        {id: 'name', label: t.colName, isRowHeader: true},
                        {id: 'kind', label: t.colKind},
                        {id: 'line', label: t.colLine, numeric: true},
                        {id: 'tags', label: t.colTags},
                    ]}
                    rows={list.map((tc) => ({
                        id: tc.id,
                        cells: {
                            name: <span className="font-medium">{tc.name}</span>,
                            kind: <Badge>{t.kinds[tc.type]}</Badge>,
                            line: tc.line,
                            tags: (
                                <span className="flex flex-wrap gap-1">
                                    {tc.tags.map((tag) => (
                                        <span key={tag} className="rounded-sm bg-surface-hover px-1.5 py-0.5 font-mono text-xs text-text-muted">
                                            {tag}
                                        </span>
                                    ))}
                                </span>
                            ),
                        },
                    }))}
                />
            )}
        </QueryView>
    );
}
