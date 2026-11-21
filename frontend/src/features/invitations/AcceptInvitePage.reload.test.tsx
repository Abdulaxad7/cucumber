import {describe, expect, it} from 'vitest';
import {fireEvent, screen, waitFor} from '@testing-library/react';
import {server} from '../../test/msw';
import {renderApp} from '../../test/render';
import {tokenFor} from '../../test/jwt';
import {FakeAuth} from '../../test/fixtures/fakeAuth';
import {fakeAuthHandlers} from '../../test/fixtures/handlers';
import {ids} from '../../test/fixtures/auth';
import {users} from '../../test/fixtures/workspace';
import {startSession} from '../../auth/session';

// @US-2 @AC-2.2 with D14: the detour survives a full reload (a new app instance reading sessionStorage).
describe('AC-2.2 pending invitation across a reload', () => {
    it('keeps the token in sessionStorage during the detour and removes it after the accept', async () => {
        const auth = new FakeAuth('gus');
        auth.members.set(ids.acme, (auth.members.get(ids.acme) ?? []).filter((m) => m.userId !== users.gus.id));
        auth.tokens.set('tok-456', {token: 'tok-456', orgId: ids.acme, email: users.gus.email, role: 'MEMBER'});
        server.use(...fakeAuthHandlers(auth));

        const first = renderApp('/invite#tok-456');
        await screen.findByRole('heading', {level: 1, name: 'Accept your invitation'});
        await waitFor(() => expect(sessionStorage.getItem('cucumber.pendingInvite')).toBe('tok-456'));
        first.unmount();

        // The reload: a new app instance, now signed in, with nothing in the URL.
        startSession(tokenFor(users.gus.id, users.gus.email, 3600));
        const second = renderApp('/invite');
        fireEvent.click(await screen.findByRole('button', {name: 'Accept invitation'}));
        await waitFor(() => expect(second.router.state.location.pathname).toBe(`/orgs/${ids.acme}/projects`));
        expect(sessionStorage.getItem('cucumber.pendingInvite')).toBeNull();
    });

    it('an unusable invitation is forgotten too', async () => {
        const auth = new FakeAuth(null);
        server.use(...fakeAuthHandlers(auth));
        renderApp('/invite#tok-gone');
        fireEvent.change(await screen.findByLabelText('Full name'), {target: {value: 'Fay Lin'}});
        fireEvent.change(screen.getByLabelText('Password'), {target: {value: 'Secret123!'}});
        fireEvent.click(screen.getByRole('button', {name: 'Accept invitation'}));
        expect(await screen.findByText("This invitation link isn't valid.")).toBeTruthy();
        expect(sessionStorage.getItem('cucumber.pendingInvite')).toBeNull();
    });
});
