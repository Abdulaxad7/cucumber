import type {components} from '../../api/generated/report-service';
import {reportApi, unwrap} from '../../api/client';

type RunStatus = components['schemas']['TestRunStatus'];

export const PAGE_SIZE = 20;
export const RESULT_PAGE_SIZE = 100;
/** D9 / AC-8.7: open runs and the run list refresh every 30 s while the tab is visible. */
export const POLL_MS = 30_000;

export function isOpen(status: RunStatus | undefined): boolean {
    return status === 'PLANNED' || status === 'IN_PROGRESS';
}

export const runsKey = (projectId: string, page: number) => ['runs', projectId, {page}] as const;
export const runKey = (projectId: string, runId: string) => ['run', projectId, runId] as const;
export const resultsKey = (projectId: string, runId: string, status: string | null) => ['run', projectId, runId, 'results', {status}] as const;

export function listRuns(projectId: string, page: number) {
    return unwrap(reportApi.GET('/api/projects/{projectId}/runs', {params: {path: {projectId}, query: {page, size: PAGE_SIZE}}}));
}

export function getRun(projectId: string, runId: string) {
    return unwrap(reportApi.GET('/api/projects/{projectId}/runs/{runId}', {params: {path: {projectId, runId}}}));
}
