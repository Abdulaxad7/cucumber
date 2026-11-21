import {useState} from 'react';
import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query';
import {ChevronRight} from 'lucide-react';
import {Button as AriaButton, Disclosure, DisclosurePanel, Heading} from 'react-aria-components';
import type {components} from '../../api/generated/report-service';
import {reportApi, unwrap} from '../../api/client';
import {uiErrorOf} from '../../api/problem';
import {Button} from '../../ui/Button';
import {Dialog} from '../../ui/Dialog';
import {ErrorState} from '../../ui/ErrorState';
import {InlineAlert} from '../../ui/InlineAlert';
import {SegmentedControl} from '../../ui/SegmentedControl';
import {SkeletonRows} from '../../ui/Skeleton';
import {StatusChip} from '../../ui/StatusChip';
import {TextArea} from '../../ui/TextArea';
import {cx} from '../../ui/cx';
import {errorText} from '../../ui/errorText';
import {showToast} from '../../ui/toastStore';
import {uiStrings} from '../../ui/strings';
import {runKey} from './queries';
import {runStrings as t} from './strings';

type Result = components['schemas']['TestCaseResult'];
type Recordable = components['schemas']['RecordableStatus'];
const RECORDABLE: readonly Recordable[] = ['PASSED', 'FAILED', 'SKIPPED', 'UNKNOWN'];

function isRecordable(value: string): value is Recordable {
    return RECORDABLE.some((r) => r === value);
}

function StepError({message}: { message: string }) {
    const [expanded, setExpanded] = useState(false);
    return (
        <Disclosure isExpanded={expanded} onExpandedChange={setExpanded} className="mt-1">
            <Heading className="m-0">
                <AriaButton slot="trigger" className="inline-flex items-center gap-1 rounded-sm text-xs font-medium text-danger outline-none data-focus-visible:outline-2 data-focus-visible:outline-focus-ring">
                    <ChevronRight aria-hidden="true" size={12} className={cx('transition duration-120 ease-standard', expanded && 'rotate-90')}/>
                    {expanded ? t.hideError : t.showError}
                </AriaButton>
            </Heading>
            <DisclosurePanel>
                {expanded ? (
                <pre className="mt-1 max-h-40 overflow-auto whitespace-pre-wrap rounded-md border border-border bg-surface-hover p-2 font-mono text-xs text-text">{message}</pre>
                ) : null}
            </DisclosurePanel>
        </Disclosure>
    );
}

export interface ResultDialogProps {
    projectId: string;
    runId: string;
    result: Result | null;
    /** Record mode shows the status buttons and the comment. */
    canRecord: boolean;
    executedByName: (userId: string | null) => string;
    onClose: () => void;
}

/** AC-8.2 and AC-8.4: a result's steps (errors collapsed behind "Show error"), and recording for testers. */
export function ResultDialog({projectId, runId, result, canRecord, executedByName, onClose}: ResultDialogProps) {
    const queryClient = useQueryClient();
    const resultId = result?.id ?? '';
    const detail = useQuery({
        queryKey: ['run', projectId, runId, 'result', resultId],
        queryFn: () => unwrap(reportApi.GET('/api/projects/{projectId}/runs/{runId}/results/{resultId}', {params: {path: {projectId, runId, resultId}}})),
        enabled: result !== null,
    });
    const [status, setStatus] = useState<Recordable | null>(null);
    const [comment, setComment] = useState('');
    const [formError, setFormError] = useState<string | null>(null);
    const chosen = status ?? (result && isRecordable(result.status) ? result.status : null);

    const record = useMutation({
        mutationFn: (body: components['schemas']['RecordResultRequest']) =>
            unwrap(reportApi.PUT('/api/projects/{projectId}/runs/{runId}/results/{resultId}', {params: {path: {projectId, runId, resultId}}, body})),
        onSuccess: () => {
            showToast('success', t.saved);
            void queryClient.invalidateQueries({queryKey: ['run', projectId, runId]});
            void queryClient.invalidateQueries({queryKey: ['runs', projectId]});
            setStatus(null);
            setComment('');
            onClose();
        },
        onError: (error) => {
            const ui = uiErrorOf(error);
            if (ui.code === 'RUN_CLOSED') {
                setFormError(t.closed);
                void queryClient.invalidateQueries({queryKey: runKey(projectId, runId)});
            } else setFormError(errorText(ui));
        },
    });

    return (
        <Dialog
            isOpen={result !== null}
            onOpenChange={(open) => {
                if (!open) {
                    setStatus(null);
                    setComment('');
                    setFormError(null);
                    onClose();
                }
            }}
            title={result?.name ?? ''}
            size="lg"
        >
            {result ? (
                <div className="flex flex-col gap-4 text-text">
                    <div className="flex flex-wrap items-center gap-3 text-sm">
                        <StatusChip status={result.status}/>
                        <span className="font-mono text-xs text-text-muted">{t.featureLine(result.featurePath, result.line)}</span>
                        {result.executedBy ? <span className="text-text-muted">{executedByName(result.executedBy)}</span> : null}
                    </div>
                    {result.comment ? <p className="rounded-md bg-surface-hover px-3 py-2 text-sm">{result.comment}</p> : null}
                    <section aria-label={t.stepsLabel}>
                        <h3 className="mb-2 text-sm font-semibold">{t.stepsLabel}</h3>
                        {detail.isPending ? (
                            <SkeletonRows rows={2}/>
                        ) : detail.isError ? (
                            <ErrorState error={uiErrorOf(detail.error)} onRetry={() => void detail.refetch()}/>
                        ) : (
                            <ol className="flex flex-col divide-y divide-border rounded-md border border-border">
                                {[...detail.data.steps]
                                    .sort((a, b) => a.position - b.position)
                                    .map((step) => (
                                        <li key={step.position} className="flex flex-col gap-1 px-3 py-2">
                                            <div className="flex items-start gap-3">
                                                <span className="min-w-0 flex-1 text-sm">
                                                    {step.kind === 'STEP' ? (
                                                        <>
                                                            <span className="font-semibold text-primary">{step.keyword?.trim()}</span> {step.text}
                                                        </>
                                                    ) : (
                                                        <span className="text-text-muted">{step.kind === 'BEFORE_HOOK' ? t.beforeHook : t.afterHook}</span>
                                                    )}
                                                </span>
                                                {step.durationMs !== null ? <span className="text-xs tabular-nums text-text-muted">{step.durationMs} ms</span> : null}
                                                <StatusChip status={step.status}/>
                                            </div>
                                            {step.errorMessage ? <StepError message={step.errorMessage}/> : null}
                                        </li>
                                    ))}
                            </ol>
                        )}
                    </section>
                    {canRecord ? (
                        <form
                            noValidate
                            className="flex flex-col gap-3 border-t border-border pt-4"
                            onSubmit={(event) => {
                                event.preventDefault();
                                if (!chosen) return;
                                setFormError(null);
                                record.mutate({status: chosen, ...(comment.trim() ? {comment: comment.trim()} : {})});
                            }}
                        >
                            {formError ? (
                                <InlineAlert tone="danger" role="alert">
                                    {formError}
                                </InlineAlert>
                            ) : null}
                            <div className="flex flex-col gap-1.5">
                                <span className="text-base font-medium">{t.recordLabel}</span>
                                <SegmentedControl
                                    label={t.recordLabel}
                                    segments={RECORDABLE.map((s) => ({id: s, label: uiStrings.status[s] ?? s}))}
                                    value={chosen}
                                    onChange={(value) => {
                                        if (isRecordable(value)) setStatus(value);
                                    }}
                                />
                            </div>
                            <TextArea id="result-comment" label={t.comment} value={comment} onChange={setComment}/>
                            <div className="flex justify-end gap-2">
                                <Button variant="secondary" onPress={onClose}>
                                    {t.close}
                                </Button>
                                <Button type="submit" variant="primary" loading={record.isPending} isDisabled={!chosen}>
                                    {t.save}
                                </Button>
                            </div>
                        </form>
                    ) : (
                        <div className="flex justify-end">
                            <Button variant="secondary" onPress={onClose}>
                                {t.close}
                            </Button>
                        </div>
                    )}
                </div>
            ) : null}
        </Dialog>
    );
}
