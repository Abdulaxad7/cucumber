import {createBrowserHistory, createRouter, type RouterHistory} from '@tanstack/react-router';
import type {QueryClient} from '@tanstack/react-query';
import {routeTree} from './routes';
import {ErrorPage} from './ErrorPage';

export function createAppRouter({queryClient, history}: { queryClient: QueryClient; history?: RouterHistory }) {
    return createRouter({
        routeTree,
        context: {queryClient},
        history: history ?? createBrowserHistory(),
        defaultPreload: false,
        // FE-QA-2: a styled, safe page instead of the router's bare default when a route throws.
        defaultErrorComponent: ({reset}) => <ErrorPage reset={reset}/>,
    });
}

export type AppRouter = ReturnType<typeof createAppRouter>;

declare module '@tanstack/react-router' {
    interface Register {
        router: AppRouter;
    }
}
