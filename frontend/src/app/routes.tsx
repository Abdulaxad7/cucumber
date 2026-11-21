// The route tree. Later batches add routes here (additive only).
import {createRootRouteWithContext, createRoute, redirect} from '@tanstack/react-router';
import type {QueryClient} from '@tanstack/react-query';
import {getSession} from '../auth/session';
import {safeNext} from '../auth/next';
import {LoginPage} from '../features/session/LoginPage';
import {RegisterPage} from '../features/session/RegisterPage';
import {AppShell} from './AppShell';
import {Shell} from './Shell';
import {OrgsHome} from './pages/OrgsHome';
import {NotFoundPage} from './pages/Placeholders';
// Loaded eagerly: it is a landing page, and the h1 focus after navigation (Shell) expects the h1 on resolve.
import {ProjectsPage} from '../features/projects/ProjectsPage';


import {lazy, Suspense, type ReactNode} from 'react';
import type {FeaturesSearch} from '../features/tree/FeaturesPage';
import {SkeletonRows} from '../ui/Skeleton';

// Batch B: the tree and the editor load with their route, not with the login page (NFR: initial JS budget).
const FeaturesPage = lazy(() => import('../features/tree/FeaturesPage').then((m) => ({default: m.FeaturesPage})));
// Batch C pages load with their routes too.
const CreateOrgPage = lazy(() => import('../features/orgs/CreateOrgPage').then((m) => ({default: m.CreateOrgPage})));
const OrgMembersPage = lazy(() => import('../features/orgs/OrgMembersPage').then((m) => ({default: m.OrgMembersPage})));
const AcceptInvitePage = lazy(() => import('../features/invitations/AcceptInvitePage').then((m) => ({default: m.AcceptInvitePage})));
const ProjectSettingsPage = lazy(() => import('../features/projects/ProjectSettingsPage').then((m) => ({default: m.ProjectSettingsPage})));
// Batch D: runs, run detail and upload tokens.
const RunsPage = lazy(() => import('../features/runs/RunsPage').then((m) => ({default: m.RunsPage})));
const RunDetailPage = lazy(() => import('../features/runs/RunDetailPage').then((m) => ({default: m.RunDetailPage})));
const TokensPage = lazy(() => import('../features/tokens/TokensPage').then((m) => ({default: m.TokensPage})));
const ProjectMembersPage = lazy(() => import('../features/projects/ProjectMembersPage').then((m) => ({default: m.ProjectMembersPage})));

function Lazy({children}: { children: ReactNode }) {
    return <Suspense fallback={<SkeletonRows rows={4}/>}>{children}</Suspense>;
}

export interface RouterContext {
    queryClient: QueryClient;
}

function stringParam(value: unknown): string | undefined {
    return typeof value === 'string' && value.length > 0 ? value : undefined;
}

export const rootRoute = createRootRouteWithContext<RouterContext>()({
    component: Shell,
    notFoundComponent: NotFoundPage,
});

function redirectIfSignedIn(next: string | undefined) {
    if (getSession()) throw redirect({href: safeNext(next), replace: true});
}

export const loginRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: '/login',
    validateSearch: (search: Record<string, unknown>): { next?: string } => {
        const next = stringParam(search.next);
        return next ? {next} : {};
    },
    beforeLoad: ({search}) => redirectIfSignedIn(search.next),
    component: function LoginRoute() {
        return <LoginPage next={loginRoute.useSearch().next}/>;
    },
});

export const registerRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: '/register',
    beforeLoad: () => redirectIfSignedIn(undefined),
    component: RegisterPage,
});

/** Every route below needs a session; without one, go to login and come back (AC-1.1, D4). */
export const authedRoute = createRoute({
    getParentRoute: () => rootRoute,
    id: 'authed',
    component: AppShell,
    beforeLoad: ({location}) => {
        if (!getSession()) {
            throw redirect({to: '/login', search: {next: `${location.pathname}${location.searchStr}`}, replace: true});
        }
    },
});

export const homeRoute = createRoute({getParentRoute: () => authedRoute, path: '/', component: OrgsHome});
export const orgsRoute = createRoute({getParentRoute: () => authedRoute, path: '/orgs', component: OrgsHome});
export const newOrgRoute = createRoute({
    getParentRoute: () => authedRoute,
    path: '/orgs/new',
    component: function CreateOrgRoute() {
        return (
            <Lazy>
                <CreateOrgPage/>
            </Lazy>
        );
    },
});

// Batch C (F5): organization members and invitations; the public invitation page.
export const orgMembersRoute = createRoute({
    getParentRoute: () => authedRoute,
    path: '/orgs/$orgId/members',
    component: function OrgMembersRoute() {
        return (
            <Lazy>
                <OrgMembersPage orgId={orgMembersRoute.useParams().orgId}/>
            </Lazy>
        );
    },
});

export const inviteRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: '/invite',
    component: function InviteRoute() {
        return (
            <Lazy>
                <AcceptInvitePage/>
            </Lazy>
        );
    },
});
export const orgProjectsRoute = createRoute({
    getParentRoute: () => authedRoute,
    path: '/orgs/$orgId/projects',
    component: function OrgProjectsRoute() {
        return <ProjectsPage orgId={orgProjectsRoute.useParams().orgId}/>;
    },
});

// Batch C (F6): project settings and members.
export const projectSettingsRoute = createRoute({
    getParentRoute: () => authedRoute,
    path: '/projects/$projectId/settings',
    component: function ProjectSettingsRoute() {
        return (
            <Lazy>
                <ProjectSettingsPage projectId={projectSettingsRoute.useParams().projectId}/>
            </Lazy>
        );
    },
});

export const projectMembersRoute = createRoute({
    getParentRoute: () => authedRoute,
    path: '/projects/$projectId/members',
    component: function ProjectMembersRoute() {
        return (
            <Lazy>
                <ProjectMembersPage projectId={projectMembersRoute.useParams().projectId}/>
            </Lazy>
        );
    },
});
// Batch B: the feature tree and editor. The URL keeps the view, the tag and the selected feature (D10).
const CANONICAL_UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;

export const projectFeaturesRoute = createRoute({
    getParentRoute: () => authedRoute,
    path: '/projects/$projectId/features',
    validateSearch: (search: Record<string, unknown>): FeaturesSearch => {
        const tag = stringParam(search.tag);
        const feature = stringParam(search.feature);
        return {
            ...(tag ? {tag} : {}),
            ...(search.view === 'archived' ? {view: 'archived' as const} : {}),
            ...(feature && CANONICAL_UUID.test(feature) ? {feature} : {}),
        };
    },
    component: function FeaturesRoute() {
        const {projectId} = projectFeaturesRoute.useParams();
        const search = projectFeaturesRoute.useSearch();
        return (
            <Suspense fallback={<SkeletonRows rows={6}/>}>
                <FeaturesPage projectId={projectId} search={search}/>
            </Suspense>
        );
    },
});

export const projectRunsRoute = createRoute({
    getParentRoute: () => authedRoute,
    path: '/projects/$projectId/runs',
    component: function ProjectRunsRoute() {
        return (
            <Lazy>
                <RunsPage projectId={projectRunsRoute.useParams().projectId}/>
            </Lazy>
        );
    },
});

export const runDetailRoute = createRoute({
    getParentRoute: () => authedRoute,
    path: '/projects/$projectId/runs/$runId',
    component: function RunDetailRoute() {
        const {projectId, runId} = runDetailRoute.useParams();
        return (
            <Lazy>
                <RunDetailPage key={runId} projectId={projectId} runId={runId}/>
            </Lazy>
        );
    },
});

export const uploadTokensRoute = createRoute({
    getParentRoute: () => authedRoute,
    path: '/projects/$projectId/upload-tokens',
    component: function UploadTokensRoute() {
        return (
            <Lazy>
                <TokensPage projectId={uploadTokensRoute.useParams().projectId}/>
            </Lazy>
        );
    },
});

export const routeTree = rootRoute.addChildren([
    loginRoute,
    registerRoute,
    inviteRoute,
    authedRoute.addChildren([
        homeRoute,
        orgsRoute,
        newOrgRoute,
        orgMembersRoute,
        orgProjectsRoute,
        projectFeaturesRoute,
        projectSettingsRoute,
        projectMembersRoute,
        projectRunsRoute,
        runDetailRoute,
        uploadTokensRoute,
    ]),
]);
