import {describe, expect, it} from 'vitest';
import {act, screen, waitFor} from '@testing-library/react';
import {server} from '../test/msw';
import {renderApp} from '../test/render';
import {tokenFor} from '../test/jwt';
import {ids} from '../test/fixtures/auth';
import {users} from '../test/fixtures/workspace';
import {featureWorld, scenarioReadHandlers, workspaceHandlers} from '../test/fixtures/handlers';
import {address, addressDetail, payment, paymentDetail} from '../test/fixtures/scenario';
import {startSession} from '../auth/session';

// D17 (user 2026-11-09): after navigating to a lazy route, focus moves to its h1 once it renders, not to <main>.
describe('focus after navigation to a lazy route', () => {
    it('focuses the features page h1', async () => {
        startSession(tokenFor(users.gus.id, users.gus.email, 3600));
        server.use(...workspaceHandlers('gus'), ...scenarioReadHandlers(featureWorld([payment, address], [paymentDetail, addressDetail])));
        const app = renderApp('/orgs');
        await screen.findByRole('heading', {level: 1, name: 'Organizations'});
        act(() => app.router.history.push(`/projects/${ids.checkout}/features`));
        const h1 = await screen.findByRole('heading', {level: 1, name: 'Features'}, {timeout: 3000});
        await waitFor(() => expect(document.activeElement).toBe(h1), {timeout: 2000});
    });
});

describe('focus after navigation never takes focus from a user who moved it', () => {
    it('keeps focus on a control the user focused while the lazy page loaded', async () => {
        startSession(tokenFor(users.gus.id, users.gus.email, 3600));
        server.use(...workspaceHandlers('gus'), ...scenarioReadHandlers(featureWorld([payment, address], [paymentDetail, addressDetail])));
        const app = renderApp('/orgs');
        await screen.findByRole('heading', {level: 1, name: 'Organizations'});
        const theme = screen.getByRole('button', {name: 'Theme'});
        act(() => {
            app.router.history.push(`/projects/${ids.checkout}/features`);
            theme.focus();
        });
        await screen.findByRole('heading', {level: 1, name: 'Features'}, {timeout: 3000});
        await new Promise((resolve) => setTimeout(resolve, 200));
        expect(document.activeElement).toBe(theme);
    });
});
