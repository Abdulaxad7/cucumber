// F8 / D15: the dashboard's data, shared by the charts and their table alternatives so both show the same numbers.
import type {components} from '../../api/generated/report-service';

type Counts = components['schemas']['StatusCounts'];

/** DS-2 order of execution statuses (the chart series and table columns). */
export const STATUSES = ['PASSED', 'FAILED', 'SKIPPED', 'PENDING', 'UNDEFINED', 'AMBIGUOUS', 'UNKNOWN'] as const satisfies ReadonlyArray<keyof Counts>;

export interface ChartRun {
    name: string;
    counts: Counts;
    passRate: number | null;
}

export function formatRate(rate: number | null): string {
    return rate === null ? '–' : `${rate.toFixed(1)}%`;
}

/** Oldest first (the API lists newest first), one row of counts per run in STATUSES order. */
export function countsTable(runs: readonly ChartRun[]): Array<{name: string; values: number[]}> {
    return [...runs].reverse().map((run) => ({name: run.name, values: STATUSES.map((s) => run.counts[s])}));
}

export function passRateTable(runs: readonly ChartRun[]): Array<{name: string; rate: string}> {
    return [...runs].reverse().map((run) => ({name: run.name, rate: formatRate(run.passRate)}));
}
