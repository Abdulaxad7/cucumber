import {describe, expect, it, vi} from 'vitest';
import {screen} from '@testing-library/react';
import {renderApp} from '../test/render';
import {tokenFor} from '../test/jwt';
import {users} from '../test/fixtures/workspace';
import {startSession} from '../auth/session';
import {server} from '../test/msw';
import {workspaceHandlers} from '../test/fixtures/handlers';

// A page that throws while rendering (simulates any render bug).
vi.mock('../features/orgs/CreateOrgPage', () => ({
    CreateOrgPage: () => {
        throw new Error('secret internal detail: stack and state');
    },
}));

// FE-QA-2 / F13: an unexpected render error shows the styled app error page, with no error text.
describe('app-wide error page', () => {
    it('shows an h1, a safe message, Retry and Home, and never the error text', async () => {
        vi.spyOn(console, 'error').mockImplementation(() => undefined);
        server.use(...workspaceHandlers('gus'));
        startSession(tokenFor(users.gus.id, users.gus.email, 3600));
        renderApp('/orgs/new');
        expect(await screen.findByRole('heading', {level: 1, name: 'Something went wrong'})).toBeTruthy();
        expect(screen.getByRole('button', {name: 'Retry'})).toBeTruthy();
        expect(screen.getByRole('link', {name: 'Go to home'}).getAttribute('href')).toBe('/');
        expect(document.body.textContent).not.toContain('secret internal detail');
    });
});
