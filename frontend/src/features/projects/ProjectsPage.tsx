import {useState, type FormEvent} from 'react';
import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query';
import {Link, useRouter} from '@tanstack/react-router';
import {Archive, ArrowRight, FolderKanban, Plus} from 'lucide-react';
import {authApi, unwrap} from '../../api/client';
import {splitFieldErrors, uiErrorOf} from '../../api/problem';
import {useWorkspace} from '../../app/useWorkspace';
import {Badge} from '../../ui/Badge';
import {Button} from '../../ui/Button';
import {Dialog} from '../../ui/Dialog';
import {EmptyState} from '../../ui/EmptyState';
import {InlineAlert} from '../../ui/InlineAlert';
import {PageHeader} from '../../ui/PageHeader';
import {QueryView} from '../../ui/QueryView';
import {TextField} from '../../ui/TextField';
import {errorText} from '../../ui/errorText';
import {canCreateProject} from '../orgs/permissions';
import {projectStrings as t} from './strings';

function CreateProjectDialog({orgId, isOpen, onOpenChange}: { orgId: string; isOpen: boolean; onOpenChange: (open: boolean) => void }) {
    const router = useRouter();
    const queryClient = useQueryClient();
    const [name, setName] = useState('');
    const [nameErrors, setNameErrors] = useState<string[] | undefined>();
    const [formError, setFormError] = useState<string | null>(null);
    const create = useMutation({
        mutationFn: () => unwrap(authApi.POST('/api/orgs/{orgId}/projects', {params: {path: {orgId}}, body: {name}})),
        onSuccess: async (project) => {
            await queryClient.invalidateQueries({queryKey: ['projects', orgId]});
            onOpenChange(false);
            setName('');
            router.history.push(`/projects/${project.id}/settings`);
        },
        onError: (error) => {
            const ui = uiErrorOf(error);
            if (ui.code === 'PROJECT_NAME_TAKEN') setNameErrors([t.nameTaken]);
            else if (ui.kind === 'validation') {
                const split = splitFieldErrors(ui, ['name']);
                setNameErrors(split.fields.name);
                setFormError(split.form.length > 0 ? split.form.join(' ') : null);
            } else setFormError(errorText(ui));
        },
    });
    function onSubmit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        setFormError(null);
        if (name.trim() === '') return setNameErrors([t.nameRequired]);
        if ([...name].length > 100) return setNameErrors([t.nameTooLong]);
        setNameErrors(undefined);
        create.mutate();
    }
    return (
        <Dialog isOpen={isOpen} onOpenChange={onOpenChange} title={t.createTitle} size="md">
            <form noValidate onSubmit={onSubmit} className="flex flex-col gap-4">
                {formError ? (
                    <InlineAlert tone="danger" role="alert">
                        {formError}
                    </InlineAlert>
                ) : null}
                <TextField id="project-name-new" label={t.nameLabel} value={name} onChange={setName} errors={nameErrors} required/>
                <div className="flex justify-end gap-2 pt-2">
                    <Button variant="secondary" onPress={() => onOpenChange(false)}>
                        {t.cancel}
                    </Button>
                    <Button type="submit" variant="primary" loading={create.isPending}>
                        {t.create}
                    </Button>
                </div>
            </form>
        </Dialog>
    );
}

/** F6 / AC-4.1: the organization's projects the caller can open, with `myRole`. */
export function ProjectsPage({orgId}: { orgId: string }) {
    const workspace = useWorkspace();
    const org = workspace.org;
    const canCreate = canCreateProject(org?.myRole);
    const [creating, setCreating] = useState(false);
    const query = useQuery({
        queryKey: ['projects', orgId],
        queryFn: () => unwrap(authApi.GET('/api/orgs/{orgId}/projects', {params: {path: {orgId}}})),
    });
    const crumbs = org ? [{label: org.name, href: `/orgs/${orgId}/projects`}, {label: t.listTitle}] : [{label: t.listTitle}];
    const hasProjects = (query.data?.length ?? 0) > 0;
    return (
        <>
            <PageHeader
                title={t.listTitle}
                breadcrumbs={crumbs}
                actions={
                    <>
                        {org ? <Badge>{t.orgRole(org.myRole)}</Badge> : null}
                        {canCreate && hasProjects ? (
                            <Button variant="primary" onPress={() => setCreating(true)}>
                                <Plus aria-hidden="true" size={16}/>
                                {t.create}
                            </Button>
                        ) : null}
                    </>
                }
            />
            <QueryView
                query={query}
                label={t.listLabel}
                isEmpty={(list) => list.length === 0}
                empty={
                    <EmptyState
                        icon={FolderKanban}
                        title={t.noProjects}
                        action={
                            canCreate ? (
                                <Button variant="primary" onPress={() => setCreating(true)}>
                                    <Plus aria-hidden="true" size={16}/>
                                    {t.create}
                                </Button>
                            ) : undefined
                        }
                    >
                        <p>{canCreate ? t.noProjectsAdmin : t.noProjectsMember}</p>
                    </EmptyState>
                }
            >
                {(projects) => (
                    <ul className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
                        {projects.map((project) => (
                            <li key={project.id}
                                className="group relative flex flex-col gap-3 rounded-lg border border-border bg-surface-raised p-4 shadow-sm transition duration-120 ease-standard hover:border-border-control">
                                <div className="flex items-start gap-3">
                                    <span aria-hidden="true"
                                          className="inline-flex size-9 shrink-0 items-center justify-center rounded-md bg-primary-soft text-primary">
                                        <FolderKanban size={18}/>
                                    </span>
                                    <div className="flex min-w-0 flex-1 flex-col gap-1">
                                        <Link
                                            to="/projects/$projectId/features"
                                            params={{projectId: project.id}}
                                            className="truncate rounded-sm text-md font-semibold text-text after:absolute after:inset-0"
                                        >
                                            {project.name}
                                        </Link>
                                        <div className="flex flex-wrap items-center gap-1.5">
                                            <Badge tone="primary">{project.myRole}</Badge>
                                            {project.archivedAt ? (
                                                <Badge tone="warning" icon={<Archive aria-hidden="true" size={12}/>}>
                                                    {t.archivedBadge}
                                                </Badge>
                                            ) : null}
                                        </div>
                                    </div>
                                    <ArrowRight aria-hidden="true" size={16} className="mt-1 text-text-muted transition duration-120 ease-standard group-hover:translate-x-0.5"/>
                                </div>
                            </li>
                        ))}
                    </ul>
                )}
            </QueryView>
            <CreateProjectDialog orgId={orgId} isOpen={creating} onOpenChange={setCreating}/>
        </>
    );
}
