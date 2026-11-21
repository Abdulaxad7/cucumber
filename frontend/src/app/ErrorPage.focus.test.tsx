import {useEffect, useState} from 'react';
import {describe, expect, it, vi} from 'vitest';
import {act, screen, waitFor} from '@testing-library/react';
import {renderApp} from '../test/render';
import {tokenFor} from '../test/jwt';
import {users} from '../test/fixtures/workspace';
import {startSession} from '../auth/session';
import {server} from '../test/msw';
import {workspaceHandlers} from '../test/fixtures/handlers';

// qa's FE-QA-5 case: the page renders its own h1 (which the shell focuses), then throws after its first render,
// so the error boundary replaces the focused h1.
vi.mock('../features/orgs/CreateOrgPage', () => ({
    CreateOrgPage: function CrashAfterRender() {
        const [crash, setCrash] = useState(false);
        useEffect(() => {
            const timer = setTimeout(() => setCrash(true), 150);
            return () => clearTimeout(timer);
        }, []);
        if (crash) throw new Error('late render failure');
        return <h1 tabIndex={-1}>Create organization</h1>;
    },
}));

function signIn() {
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
    server.use(...workspaceHandlers('gus'));
    startSession(tokenFor(users.gus.id, users.gus.email, 3600));
}

// FE-QA-5 / AC-9.2 (D17): after an in-app navigation that ends on the error page, focus moves to its h1.
describe('focus on the error page', () => {
    it('in-app navigation: the error page h1 takes focus after the page h1 was replaced', async () => {
        signIn();
        const app = renderApp('/orgs');
        await screen.findByRole('heading', {level: 1, name: 'Organizations'});
        act(() => app.router.history.push('/orgs/new'));
        const h1 = await screen.findByRole('heading', {level: 1, name: 'Something went wrong'}, {timeout: 3000});
        await waitFor(() => expect(document.activeElement).toBe(h1), {timeout: 2000});
        expect(h1.getAttribute('tabindex')).toBe('-1');
    });

    it('never steals focus the user moved after navigating', async () => {
        signIn();
        const app = renderApp('/orgs');
        await screen.findByRole('heading', {level: 1, name: 'Organizations'});
        act(() => app.router.history.push('/orgs/new'));
        await screen.findByRole('heading', {level: 1, name: 'Create organization'});
        const theme = screen.getByRole('button', {name: 'Theme'});
        act(() => theme.focus());
        await screen.findByRole('heading', {level: 1, name: 'Something went wrong'}, {timeout: 3000});
        await new Promise((resolve) => setTimeout(resolve, 200));
        expect(document.activeElement).toBe(theme);
    });

    it('full page load: focus behaviour is unchanged (the error page does not grab focus)', async () => {
        signIn();
        renderApp('/orgs/new');
        await screen.findByRole('heading', {level: 1, name: 'Something went wrong'}, {timeout: 3000});
        await new Promise((resolve) => setTimeout(resolve, 200));
        expect(document.activeElement).toBe(document.body);
    });
});
