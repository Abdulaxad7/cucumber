import {describe, expect, it} from 'vitest';
import {fireEvent, screen, waitFor} from '@testing-library/react';
import {server} from '../../test/msw';
import {renderApp} from '../../test/render';
import {FakeAuth, authProblemAnswer} from '../../test/fixtures/fakeAuth';
import {fakeAuthHandlers} from '../../test/fixtures/handlers';
import {ids} from '../../test/fixtures/auth';

function world(token: string) {
    const auth = new FakeAuth(null);
    auth.tokens.set(token, {token, orgId: ids.acme, email: 'fay@example.com', role: 'MEMBER'});
    server.use(...fakeAuthHandlers(auth));
    return auth;
}

// @US-2 @AC-2.1
describe('AC-2.1 a new person accepts', () => {
    it('removes the fragment from the address bar and keeps the token only in sessionStorage', async () => {
        world('tok-123');
        const app = renderApp('/invite#tok-123');
        await screen.findByRole('heading', {level: 1, name: 'Accept your invitation'});
        await waitFor(() => expect(app.router.state.location.hash).toBe(''));
        expect(app.href()).toBe('/invite');
        expect(document.body.innerHTML).not.toContain('tok-123');
        // D14 as approved 2026-11-09: only the pending-invite key, in sessionStorage; nothing in localStorage.
        expect(Object.keys({...sessionStorage})).toEqual(['cucumber.pendingInvite']);
        expect(localStorage.length).toBe(0);
    });

    it('creates the account without Authorization, then offers the login with the email filled in', async () => {
        world('tok-123');
        let seenAuthorization: string | null = 'unset';
        server.events.on('request:start', ({request}) => {
            if (request.url.includes('/accept')) seenAuthorization = request.headers.get('Authorization');
        });
        renderApp('/invite#tok-123');
        fireEvent.change(await screen.findByLabelText('Full name'), {target: {value: 'Fay Lin'}});
        fireEvent.change(screen.getByLabelText('Password'), {target: {value: 'Secret123!'}});
        fireEvent.click(screen.getByRole('button', {name: 'Accept invitation'}));
        expect(await screen.findByText('You joined Acme as MEMBER')).toBeTruthy();
        expect(screen.getByLabelText<HTMLInputElement>('Email').value).toBe('fay@example.com');
        expect(seenAuthorization).toBeNull();
        server.events.removeAllListeners();
    });
});

// @US-2 @AC-2.3
describe('AC-2.3 the invitation cannot be used', () => {
    it.each([
        [404, 'INVITATION_NOT_FOUND', "This invitation link isn't valid."],
        [410, 'INVITATION_EXPIRED', 'This invitation has expired. Ask for a new one.'],
        [410, 'INVITATION_REVOKED', 'This invitation was withdrawn.'],
        [410, 'INVITATION_ALREADY_ACCEPTED', 'This invitation was already used.'],
        [403, 'INVITATION_EMAIL_MISMATCH', 'This invitation is for a different email. Log out and try again.'],
        [409, 'ALREADY_MEMBER', "You're already a member of this organization."],
        [409, 'SEAT_LIMIT_REACHED', 'The organization has no free seat. Ask an admin.'],
    ] as const)('%i %s', async (status, code, message) => {
        const auth = world('tok-9');
        const pending = auth.tokens.get('tok-9');
        if (pending) pending.outcome = authProblemAnswer(status, code, '/api/auth/invitations/tok-9/accept');
        renderApp('/invite#tok-9');
        fireEvent.change(await screen.findByLabelText('Full name'), {target: {value: 'Fay Lin'}});
        fireEvent.change(screen.getByLabelText('Password'), {target: {value: 'Secret123!'}});
        fireEvent.click(screen.getByRole('button', {name: 'Accept invitation'}));
        expect(await screen.findByText(message)).toBeTruthy();
        expect(screen.queryByRole('button', {name: 'Accept invitation'})).toBeNull();
        expect(screen.queryByLabelText('Password')).toBeNull();
    });
});

// FE-QA-2 / F13: a malformed fragment shows the invalid-link state, never a crash, and leaves the URL.
describe('malformed invitation fragments', () => {
    it.each(['%', '%ZZ', '%E0%A4%A'])('#%s shows "This invitation link isn\'t valid."', async (fragment) => {
        world('tok-unused');
        const app = renderApp(`/invite#${fragment}`);
        expect(await screen.findByRole('heading', {level: 1, name: 'Accept your invitation'})).toBeTruthy();
        expect(await screen.findByText("This invitation link isn't valid.")).toBeTruthy();
        await waitFor(() => expect(app.router.state.location.hash).toBe(''));
        expect(app.href()).toBe('/invite');
        expect(sessionStorage.getItem('cucumber.pendingInvite')).toBeNull();
    });
});
