import {useState, type FormEvent} from 'react';
import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query';
import {useRouter} from '@tanstack/react-router';
import type {components} from '../../api/generated/report-service';
import {reportApi, unwrap} from '../../api/client';
import {splitFieldErrors, uiErrorOf, type UiError} from '../../api/problem';
import {Button} from '../../ui/Button';
import {Checkbox} from '../../ui/Checkbox';
import {Dialog} from '../../ui/Dialog';
import {ErrorState} from '../../ui/ErrorState';
import {InlineAlert} from '../../ui/InlineAlert';
import {SkeletonRows} from '../../ui/Skeleton';
import {TextField} from '../../ui/TextField';
import {showToast} from '../../ui/toastStore';
import {flattenFiles, buildTree} from '../tree/buildTree';
import {featuresKey, listFeatures} from '../tree/queries';
import {runStrings as t} from './strings';

type PlanRequest = components['schemas']['PlanRunRequest'];

/** AC-8.3: plan a manual run from chosen features (in the order picked), with optional tag, environment and build. */
export function PlanRunDialog({projectId, isOpen, onOpenChange}: { projectId: string; isOpen: boolean; onOpenChange: (open: boolean) => void }) {
    const router = useRouter();
    const queryClient = useQueryClient();
    const features = useQuery({queryKey: featuresKey(projectId, false, undefined), queryFn: () => listFeatures(projectId, false, undefined), enabled: isOpen});
    const [name, setName] = useState('');
    const [picked, setPicked] = useState<string[]>([]);
    const [tag, setTag] = useState('');
    const [environment, setEnvironment] = useState('');
    const [build, setBuild] = useState('');
    const [fieldErrors, setFieldErrors] = useState<Partial<Record<'name' | 'featureIds' | 'tag' | 'environment' | 'build', string[]>>>({});
    const [formError, setFormError] = useState<string | null>(null);
    const [failure, setFailure] = useState<UiError | null>(null);
    const [notPlannable, setNotPlannable] = useState<string[]>([]);

    const plan = useMutation({
        mutationFn: (body: PlanRequest) => unwrap(reportApi.POST('/api/projects/{projectId}/runs', {params: {path: {projectId}}, body})),
        onSuccess: (run) => {
            void queryClient.invalidateQueries({queryKey: ['runs', projectId]});
            showToast('success', t.planned);
            onOpenChange(false);
            router.history.push(`/projects/${projectId}/runs/${run.id}`);
        },
        onError: (error) => {
            const ui = uiErrorOf(error);
            const message = ui.code ? t.planErrors[ui.code] : undefined;
            if (ui.code === 'FEATURE_NOT_PLANNABLE') setNotPlannable(ui.problem?.featureIds ?? []);
            if (message) setFormError(message);
            else if (ui.kind === 'validation') {
                const split = splitFieldErrors(ui, ['name', 'featureIds', 'tag', 'environment', 'build']);
                setFieldErrors(split.fields);
                setFormError(split.form.length > 0 ? split.form.join(' ') : null);
            } else setFailure(ui);
        },
    });

    function body(): PlanRequest {
        return {
            name,
            featureIds: picked,
            ...(tag.trim() ? {tag: tag.trim()} : {}),
            ...(environment.trim() ? {environment: environment.trim()} : {}),
            ...(build.trim() ? {build: build.trim()} : {}),
        };
    }

    function submit() {
        setFormError(null);
        setFailure(null);
        setNotPlannable([]);
        const missing: typeof fieldErrors = {};
        if (name.trim() === '') missing.name = [t.nameRequired];
        if (picked.length === 0) missing.featureIds = [t.chooseFeature];
        setFieldErrors(missing);
        if (missing.name || missing.featureIds) return;
        plan.mutate(body());
    }

    function onSubmit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        submit();
    }

    const files = features.data ? flattenFiles(buildTree(features.data)) : [];

    return (
        <Dialog isOpen={isOpen} onOpenChange={onOpenChange} title={t.planTitle} size="lg">
            <form noValidate onSubmit={onSubmit} className="flex flex-col gap-4">
                {failure ? <ErrorState error={failure} onRetry={submit}/> : null}
                {formError ? (
                    <InlineAlert tone="danger" role="alert">
                        {formError}
                    </InlineAlert>
                ) : null}
                <TextField id="run-name" label={t.runName} value={name} onChange={setName} errors={fieldErrors.name} required/>
                <fieldset className="flex flex-col gap-2">
                    <legend className="text-base font-medium text-text">{t.features}</legend>
                    <p className="text-sm text-text-muted">{t.featuresHelp}</p>
                    <div className="max-h-56 overflow-y-auto rounded-md border border-border-control bg-surface-raised p-2">
                        {features.isPending ? (
                            <SkeletonRows rows={2}/>
                        ) : files.length === 0 ? (
                            <p className="p-2 text-sm text-text-muted">{t.noFeatures}</p>
                        ) : (
                            <ul className="flex flex-col gap-1">
                                {files.map((f) => {
                                    const order = picked.indexOf(f.id);
                                    const blocked = notPlannable.includes(f.id);
                                    return (
                                        <li key={f.id} className="flex items-center gap-2 rounded-md px-2 py-1 hover:bg-surface-hover">
                                            <Checkbox
                                                isSelected={order >= 0}
                                                isInvalid={fieldErrors.featureIds !== undefined || blocked}
                                                onChange={(selected) => setPicked((list) => (selected ? [...list, f.id] : list.filter((id) => id !== f.id)))}
                                            >
                                                <span className="font-mono text-sm">{f.path}</span>
                                            </Checkbox>
                                            {blocked ? (
                                                <span className="ml-auto rounded-sm bg-status-failed-bg px-1.5 text-xs font-medium text-status-failed-fg">{t.notPlannable}</span>
                                            ) : null}
                                            {order >= 0 ? (
                                                <span className="ml-auto rounded-sm bg-primary-soft px-1.5 text-xs font-medium tabular-nums text-primary" aria-hidden="true">
                                                    {order + 1}
                                                </span>
                                            ) : null}
                                        </li>
                                    );
                                })}
                            </ul>
                        )}
                    </div>
                    <p className="text-sm text-text-muted" aria-live="polite">
                        {t.selectedCount(picked.length)}
                    </p>
                    {fieldErrors.featureIds ? <p className="text-sm font-medium text-danger">{fieldErrors.featureIds.join(' ')}</p> : null}
                </fieldset>
                <div className="grid gap-4 sm:grid-cols-3">
                    <TextField id="run-tag" label={t.tag} value={tag} onChange={setTag} errors={fieldErrors.tag}/>
                    <TextField id="run-environment" label={t.environment} value={environment} onChange={setEnvironment} errors={fieldErrors.environment}/>
                    <TextField id="run-build" label={t.build} value={build} onChange={setBuild} errors={fieldErrors.build}/>
                </div>
                <div className="flex justify-end gap-2 pt-2">
                    <Button variant="secondary" onPress={() => onOpenChange(false)}>
                        {t.close}
                    </Button>
                    <Button type="submit" variant="primary" loading={plan.isPending}>
                        {t.planRun}
                    </Button>
                </div>
            </form>
        </Dialog>
    );
}
