import {useWorkspace} from '../../app/useWorkspace';
import {hasProjectRole} from '../../auth/roles';

/** The current project from auth-service (D5), with what the caller may change. */
export function useProject() {
    const workspace = useWorkspace();
    const project = workspace.project;
    const archived = project?.archivedAt != null;
    const admin = hasProjectRole(project?.myRole, 'ADMIN');
    const crumbs = (area: string) => {
        const list: Array<{label: string; href?: string}> = [];
        if (workspace.org && workspace.orgId) list.push({label: workspace.org.name, href: `/orgs/${workspace.orgId}/projects`});
        if (project) list.push({label: project.name, href: `/projects/${project.id}/features`});
        list.push({label: area});
        return list;
    };
    const loading = workspace.orgsLoading || workspace.projectsLoading;
    return {workspace, project, archived, admin, canWrite: admin && !archived, crumbs, loading};
}
