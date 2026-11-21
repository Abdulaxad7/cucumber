// Batch D: Checkout's runs (spec glossary: "Nightly" AUTOMATED COMPLETED 2/1/1, "Sprint 12" MANUAL IN_PROGRESS with 4
// results), typed against the generated report-service schemas.
import type {components} from '../../api/generated/report-service';
import {ids} from './auth';
import {featureIds} from './scenario';
import {userIds} from './workspace';

type S = components['schemas'];

export const runIds = {
    nightly: 'd1000000-0000-4000-8000-000000000001',
    sprint12: 'd1000000-0000-4000-8000-000000000002',
} as const;

const ZERO: S['StatusCounts'] = {PASSED: 0, FAILED: 0, SKIPPED: 0, PENDING: 0, UNDEFINED: 0, AMBIGUOUS: 0, UNKNOWN: 0};

export function countsOf(results: ReadonlyArray<{status: S['ExecutionStatus']}>): S['StatusCounts'] {
    const counts = {...ZERO};
    for (const r of results) counts[r.status] += 1;
    return counts;
}

/** D12 of report-service: PASSED / executed × 100, half-up to one decimal; null when nothing was executed. */
export function passRateOf(counts: S['StatusCounts']): S['PassRate'] {
    const total = Object.values(counts).reduce((a, b) => a + b, 0);
    const executed = total - counts.UNKNOWN;
    if (executed === 0) return null;
    return Math.round((counts.PASSED / executed) * 1000) / 10;
}

export function summaryOf(results: S['TestCaseResult'][]): S['RunSummary'] {
    const counts = countsOf(results);
    const total = results.length;
    const durations = results.map((r) => r.durationMs).filter((d): d is number => d !== null);
    const byFeature: S['RunSummary']['byFeature'] = [];
    for (const r of results) {
        let entry = byFeature.find((f) => f.featurePath === r.featurePath);
        if (!entry) {
            entry = {featurePath: r.featurePath, featureName: r.featureName, total: 0, counts: {...ZERO}};
            byFeature.push(entry);
        }
        entry.total += 1;
        entry.counts[r.status] += 1;
    }
    return {
        total,
        counts,
        executed: total - counts.UNKNOWN,
        passRate: passRateOf(counts),
        durationMs: durations.length > 0 ? durations.reduce((a, b) => a + b, 0) : null,
        byFeature,
    };
}

function result(runId: string, position: number, name: string, feature: 'payment' | 'address', status: S['ExecutionStatus'], extra: Partial<S['TestCaseResult']> = {}): S['TestCaseResult'] {
    const path = feature === 'payment' ? 'features/checkout/payment.feature' : 'features/checkout/address.feature';
    return {
        id: `e${runId.slice(1, 8)}-0000-4000-8000-${String(position).padStart(12, '0')}`,
        testRunId: runId,
        position,
        featureId: featureIds[feature],
        featureVersion: feature === 'payment' ? 2 : 1,
        scenarioId: null,
        exampleRowId: null,
        featurePath: path,
        featureName: feature === 'payment' ? 'Payment' : 'Address',
        name,
        tags: feature === 'payment' ? ['@checkout'] : ['@smoke'],
        line: 4 + position,
        status,
        attempt: 1,
        executedBy: null,
        comment: null,
        finishedAt: null,
        durationMs: null,
        createdAt: '2026-11-08T22:00:00Z',
        updatedAt: '2026-11-08T22:00:00Z',
        ...extra,
    };
}

export function nightlyResults(): S['TestCaseResult'][] {
    const done = {finishedAt: '2026-11-08T22:05:00Z'};
    return [
        result(runIds.nightly, 1, 'Pay by card', 'payment', 'PASSED', {...done, durationMs: 1200}),
        result(runIds.nightly, 2, 'Pay by voucher', 'payment', 'FAILED', {...done, durationMs: 900}),
        result(runIds.nightly, 3, 'Add an address', 'address', 'PASSED', {...done, durationMs: 700}),
        result(runIds.nightly, 4, 'Edit an address', 'address', 'PENDING', {...done, durationMs: null}),
    ];
}

export function sprint12Results(): S['TestCaseResult'][] {
    return [
        result(runIds.sprint12, 1, 'Pay by card', 'payment', 'PASSED', {executedBy: userIds.hal, finishedAt: '2026-11-09T09:10:00Z'}),
        result(runIds.sprint12, 2, 'Pay by voucher', 'payment', 'UNKNOWN'),
        result(runIds.sprint12, 3, 'Add an address', 'address', 'UNKNOWN'),
        result(runIds.sprint12, 4, 'Edit an address', 'address', 'UNKNOWN'),
    ];
}

export function steps(resultRow: S['TestCaseResult']): S['StepResult'][] {
    const failed = resultRow.status === 'FAILED';
    return [
        {position: 1, kind: 'BEFORE_HOOK', keyword: null, text: null, dataTable: null, docString: null, status: 'PASSED', durationMs: 5, errorMessage: null},
        {position: 2, kind: 'STEP', keyword: 'Given ', text: 'a cart with 2 items', dataTable: null, docString: null, status: 'PASSED', durationMs: 300, errorMessage: null},
        {
            position: 3,
            kind: 'STEP',
            keyword: 'When ',
            text: 'I pay by voucher',
            dataTable: null,
            docString: null,
            status: failed ? 'FAILED' : resultRow.status === 'PENDING' ? 'PENDING' : 'PASSED',
            durationMs: 400,
            errorMessage: failed ? 'AssertionError: expected voucher to be accepted\n    at PaymentSteps.pay(PaymentSteps.java:42)' : null,
        },
        {position: 4, kind: 'STEP', keyword: 'Then ', text: 'the order is confirmed', dataTable: null, docString: null, status: failed ? 'SKIPPED' : 'PASSED', durationMs: 0, errorMessage: null},
    ];
}

export function runFields(id: string, name: string, source: S['TestRunSource'], status: S['TestRunStatus'], createdAt: string, extra: Partial<S['TestRunFields']> = {}): S['TestRunFields'] {
    return {
        id,
        projectId: ids.checkout,
        name,
        source,
        status,
        environment: null,
        build: null,
        tagExpression: null,
        createdBy: source === 'MANUAL' ? userIds.gus : null,
        startedAt: status === 'PLANNED' ? null : createdAt,
        finishedAt: status === 'COMPLETED' || status === 'CANCELLED' ? createdAt : null,
        createdAt,
        updatedAt: createdAt,
        ...extra,
    };
}

export const nightlyFields = runFields(runIds.nightly, 'Nightly', 'AUTOMATED', 'COMPLETED', '2026-11-08T22:00:00Z', {
    environment: {id: 'f1000000-0000-4000-8000-000000000001', name: 'ci'},
    build: {id: 'f2000000-0000-4000-8000-000000000001', name: '1042', branch: 'main', commitSha: '9f2c1ab', ciUrl: null},
});
export const sprint12Fields = runFields(runIds.sprint12, 'Sprint 12', 'MANUAL', 'IN_PROGRESS', '2026-11-09T09:00:00Z', {
    environment: {id: 'f1000000-0000-4000-8000-000000000002', name: 'staging'},
});

export function reportProblem(status: number, code: S['ErrorCode'], instance: string, extra: Partial<Pick<S['Problem'], 'featureIds' | 'errors'>> = {}): S['Problem'] {
    return {type: 'about:blank', title: 'Error', status, detail: `Problem ${code}`, instance, code, ...extra};
}
