import {useEffect, useState, type FormEvent} from 'react';
import {useMutation, useQueryClient} from '@tanstack/react-query';
import {Archive, ArchiveRestore} from 'lucide-react';
import {authApi, unwrap} from '../../api/client';
import {splitFieldErrors, uiErrorOf} from '../../api/problem';
import {Badge} from '../../ui/Badge';
import {Button} from '../../ui/Button';
import {Card} from '../../ui/Card';
import {ConfirmDialog} from '../../ui/Dialog';
import {EmptyState} from '../../ui/EmptyState';
import {InlineAlert} from '../../ui/InlineAlert';
import {PageHeader} from '../../ui/PageHeader';
import {SkeletonRows} from '../../ui/Skeleton';
import {TextField} from '../../ui/TextField';
import {ToastRegion} from '../../ui/Toast';
import {errorText} from '../../ui/errorText';
import {showToast} from '../../ui/toastStore';
import {projectStrings as t} from './strings';
import {useProject} from './useProject';

/** F6 / AC-4.2, AC-4.4: rename, archive and restore (effective project ADMIN only); a 640 px form page. */
export function ProjectSettingsPage({projectId}: { projectId: string }) {
    const queryClient = useQueryClient();
    const {workspace, project, archived, admin, canWrite, crumbs, loading} = useProject();
    const orgId = workspace.orgId ?? '';
    const [name, setName] = useState('');
    const [nameErrors, setNameErrors] = useState<string[] | undefined>();
    const [formError, setFormError] = useState<string | null>(null);
    const [confirmArchive, setConfirmArchive] = useState(false);

    useEffect(() => {
        if (project) setName(project.name);
    }, [project]);

    const refresh = () => queryClient.invalidateQueries({queryKey: ['projects']});

    const rename = useMutation({
        mutationFn: () => unwrap(authApi.PATCH('/api/orgs/{orgId}/projects/{projectId}', {params: {path: {orgId, projectId}}, body: {name}})),
        onSuccess: () => {
            showToast('success', t.renamed);
            void refresh();
        },
        onError: (error) => {
            const ui = uiErrorOf(error);
            if (ui.code === 'PROJECT_NAME_TAKEN') setNameErrors([t.nameTaken]);
            else if (ui.kind === 'validation') {
                const split = splitFieldErrors(ui, ['name']);
                setNameErrors(split.fields.name);
                setFormError(split.form.length > 0 ? split.form.join(' ') : null);
            } else if (ui.kind !== 'archived') setFormError(errorText(ui));
        },
    });
    const archive = useMutation({
        mutationFn: () => unwrap(authApi.POST('/api/orgs/{orgId}/projects/{projectId}/archive', {params: {path: {orgId, projectId}}})),
        onSuccess: () => {
            setConfirmArchive(false);
            showToast('success', t.archived);
            void refresh();
        },
        onError: (error) => {
            setConfirmArchive(false);
            setFormError(errorText(uiErrorOf(error)));
        },
    });
    const restore = useMutation({
        mutationFn: () => unwrap(authApi.POST('/api/orgs/{orgId}/projects/{projectId}/restore', {params: {path: {orgId, projectId}}})),
        onSuccess: () => {
            showToast('success', t.restored);
            void refresh();
        },
        onError: (error) => setFormError(errorText(uiErrorOf(error))),
    });

    if (!project) {
        return loading ? (
            <div aria-busy="true">
                <SkeletonRows rows={3}/>
            </div>
        ) : (
            <div role="alert">
                <EmptyState title={t.notFound}/>
            </div>
        );
    }

    function onRename(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        setFormError(null);
        if (name.trim() === '') return setNameErrors([t.nameRequired]);
        if ([...name].length > 100) return setNameErrors([t.nameTooLong]);
        setNameErrors(undefined);
        rename.mutate();
    }

    return (
        <>
            <PageHeader title={project.name} breadcrumbs={crumbs(t.settingsTitle)} actions={<Badge tone="primary">{t.yourRole(project.myRole)}</Badge>}/>
            <div className="flex max-w-2xl flex-col gap-6">
                {archived ? (
                    <InlineAlert
                        tone="readonly"
                        actions={
                            admin ? (
                                <Button variant="secondary" size="sm" onPress={() => restore.mutate()} loading={restore.isPending}>
                                    <ArchiveRestore aria-hidden="true" size={16}/>
                                    {t.restore}
                                </Button>
                            ) : undefined
                        }
                    >
                        {t.archivedBanner}
                    </InlineAlert>
                ) : !admin ? (
                    <InlineAlert tone="readonly">{t.readOnly(project.myRole)}</InlineAlert>
                ) : null}
                {formError ? (
                    <InlineAlert tone="danger" role="alert">
                        {formError}
                    </InlineAlert>
                ) : null}
                <Card title={t.general}>
                    {canWrite ? (
                        <form noValidate onSubmit={onRename} className="flex flex-col gap-4">
                            <TextField id="project-name" label={t.nameLabel} value={name} onChange={setName} errors={nameErrors} required/>
                            <div className="flex justify-end">
                                <Button type="submit" variant="primary" loading={rename.isPending}>
                                    {t.rename}
                                </Button>
                            </div>
                        </form>
                    ) : (
                        <dl className="flex flex-col gap-1">
                            <dt className="text-sm text-text-muted">{t.nameLabel}</dt>
                            <dd className="text-base font-medium">{project.name}</dd>
                        </dl>
                    )}
                </Card>
                {canWrite ? (
                    <Card title={t.archiveCard}>
                        <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
                            <p className="text-base text-text-muted">{t.archiveHelp}</p>
                            <Button variant="danger" onPress={() => setConfirmArchive(true)}>
                                <Archive aria-hidden="true" size={16}/>
                                {t.archive}
                            </Button>
                        </div>
                    </Card>
                ) : null}
            </div>
            <ConfirmDialog
                isOpen={confirmArchive}
                onOpenChange={setConfirmArchive}
                title={t.archiveTitle}
                confirmLabel={t.archive}
                cancelLabel={t.cancel}
                danger
                loading={archive.isPending}
                onConfirm={() => archive.mutate()}
            >
                <p>{t.archiveBody(project.name)}</p>
            </ConfirmDialog>
            <ToastRegion/>
        </>
    );
}
