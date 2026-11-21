import {describe, expect, it} from 'vitest';
import {fireEvent, screen, waitFor, within} from '@testing-library/react';
import {server} from '../../test/msw';
import {renderApp} from '../../test/render';
import {tokenFor} from '../../test/jwt';
import {FakeAuth} from '../../test/fixtures/fakeAuth';
import {fakeAuthHandlers} from '../../test/fixtures/handlers';
import {ids} from '../../test/fixtures/auth';
import {users, type UserKey} from '../../test/fixtures/workspace';
import {startSession} from '../../auth/session';

function open(user: UserKey, setup?: (auth: FakeAuth) => void) {
    const auth = new FakeAuth(user);
    setup?.(auth);
    server.use(...fakeAuthHandlers(auth));
    startSession(tokenFor(users[user].id, users[user].email, 3600));
    const app = renderApp(`/orgs/${ids.acme}/members`);
    return {auth, app};
}

async function row(name: string) {
    const table = await screen.findByRole('grid', {name: 'Members'});
    const cell = await within(table).findByText(name);
    const found = cell.closest('[role=row]');
    if (!(found instanceof HTMLElement)) throw new Error(`no row for ${name}`);
    return found;
}

// @US-3 @AC-3.2
describe('AC-3.2 change a role within the ceiling', () => {
    it('Ben (ADMIN): no menu on Ana (OWNER) or himself; Gus offers ADMIN, MEMBER, READER', async () => {
        open('ben');
        const ana = await row('Ana Ruiz');
        expect(within(ana).queryByRole('button', {name: /Role for/})).toBeNull();
        expect(within(ana).queryByRole('button', {name: /Remove/})).toBeNull();
        const ben = await row('Ben Okafor');
        expect(within(ben).queryByRole('button', {name: /Role for/})).toBeNull();
        const gus = await row('Gus Underwood');
        fireEvent.click(within(gus).getByRole('button', {name: /Role for Gus Underwood/}));
        const options = await screen.findAllByRole('option');
        expect(options.map((o) => o.textContent)).toEqual(['ADMIN', 'MEMBER', 'READER']);
    });

    it('makes Gus READER after the 200', async () => {
        open('ben');
        const gus = await row('Gus Underwood');
        fireEvent.click(within(gus).getByRole('button', {name: /Role for Gus Underwood/}));
        fireEvent.click(await screen.findByRole('option', {name: 'READER'}));
        await waitFor(async () => expect((await row('Gus Underwood')).textContent).toContain('READER'));
    });

    it('a 422 is shown next to the row, which keeps its role', async () => {
        open('ben', (auth) => {
            auth.overrides.set(`PATCH /api/orgs/${ids.acme}/members/${users.gus.id}`, {
                status: 422,
                contentType: 'application/problem+json',
                body: {type: 'about:blank', title: 'x', status: 422, detail: 'd', instance: '/x', code: 'ROLE_CEILING'},
            });
        });
        const gus = await row('Gus Underwood');
        fireEvent.click(within(gus).getByRole('button', {name: /Role for Gus Underwood/}));
        fireEvent.click(await screen.findByRole('option', {name: 'READER'}));
        expect(await within(await row('Gus Underwood')).findByText("You can't give or change a role above your own.")).toBeTruthy();
        expect(within(await row('Gus Underwood')).getByRole('button', {name: /Role for Gus Underwood/}).textContent).toContain('MEMBER');
    });
});

// @US-3 @AC-3.3
describe('AC-3.3 invite and share the link once', () => {
    it('shows the token once; after the dialog closes it is nowhere in the DOM or storage', async () => {
        open('ben', (auth) => {
            auth.members.set(ids.acme, (auth.members.get(ids.acme) ?? []).slice(0, 4));
        });
        fireEvent.click(await screen.findByRole('button', {name: 'Invite'}));
        const dialog = await screen.findByRole('dialog', {name: 'Invite a member'});
        fireEvent.change(within(dialog).getByLabelText('Email'), {target: {value: 'fay@example.com'}});
        fireEvent.click(within(dialog).getByRole('button', {name: 'Send invitation'}));
        const tokenDialog = await screen.findByRole('dialog', {name: 'Share the invitation link'});
        const link = within(tokenDialog).getByLabelText<HTMLInputElement>('Invitation link');
        expect(link.value).toMatch(/\/invite#tok-/);
        const token = link.value.split('#')[1] ?? '';
        expect(token).not.toBe('');
        fireEvent.click(within(tokenDialog).getByRole('button', {name: 'Done'}));
        await waitFor(() => expect(screen.queryByRole('dialog', {name: 'Share the invitation link'})).toBeNull());
        expect(document.documentElement.innerHTML).not.toContain(token);
        expect(JSON.stringify({...sessionStorage})).not.toContain(token);
        expect(JSON.stringify({...localStorage})).not.toContain(token);
    });

    it('409 SEAT_LIMIT_REACHED shows the seat count at the form', async () => {
        open('ben', (auth) => {
            auth.overrides.set(`POST /api/orgs/${ids.acme}/invitations`, {
                status: 409,
                contentType: 'application/problem+json',
                body: {type: 'about:blank', title: 'x', status: 409, detail: 'd', instance: '/x', code: 'SEAT_LIMIT_REACHED'},
            });
            auth.members.set(ids.acme, (auth.members.get(ids.acme) ?? []).slice(0, 4));
        });
        fireEvent.click(await screen.findByRole('button', {name: 'Invite'}));
        const dialog = await screen.findByRole('dialog', {name: 'Invite a member'});
        fireEvent.change(within(dialog).getByLabelText('Email'), {target: {value: 'fay@example.com'}});
        fireEvent.click(within(dialog).getByRole('button', {name: 'Send invitation'}));
        expect(await within(dialog).findByText('No free seat (4 of 5 used)')).toBeTruthy();
    });
});

// @US-3 @AC-3.4
describe('AC-3.4 pending invitations and seats', () => {
    it('5 of 5 used: no pending, Invite disabled with the reason', async () => {
        open('ben');
        expect(await screen.findByText('Seats: 5 of 5 used')).toBeTruthy();
        expect(await screen.findByText('No pending invitations')).toBeTruthy();
        const invite = screen.getByRole('button', {name: 'Invite'});
        expect(invite.hasAttribute('disabled') || invite.getAttribute('aria-disabled') === 'true').toBe(true);
        expect(screen.getByText('No free seat')).toBeTruthy();
    });

    it('revoking a pending invitation frees its seat', async () => {
        open('ben', (auth) => {
            auth.members.set(ids.acme, (auth.members.get(ids.acme) ?? []).slice(0, 4));
            auth.invitations.set(ids.acme, [
                {id: '0c0c0c0c-0000-4000-8000-000000000001', email: 'ivy@example.com', role: 'MEMBER', status: 'PENDING', createdAt: '2026-11-01T10:00:00Z', expiresAt: '2099-11-08T10:00:00Z'},
            ]);
        });
        expect(await screen.findByText('Seats: 5 of 5 used')).toBeTruthy();
        fireEvent.click(await screen.findByRole('button', {name: 'Revoke invitation for ivy@example.com'}));
        fireEvent.click(await screen.findByRole('button', {name: 'Revoke invitation'}));
        expect(await screen.findByText('Seats: 4 of 5 used')).toBeTruthy();
        await waitFor(() => expect(screen.queryByText('ivy@example.com')).toBeNull());
    });
});

// @US-3 @AC-3.5
describe('AC-3.5 organization actions by role', () => {
    it.each([
        ['ana', true],
        ['ben', true],
        ['gus', false],
        ['dan', false],
    ] as const)('%s: invite, roles and remove shown=%s; the member list is always shown', async (user, shown) => {
        open(user);
        await row('Gus Underwood');
        expect(screen.queryAllByRole('button', {name: 'Invite'}).length > 0).toBe(shown);
        expect(screen.queryAllByRole('button', {name: /Role for/}).length > 0).toBe(shown);
        expect(screen.queryAllByRole('button', {name: /^Remove /}).length > 0).toBe(shown);
    });
});
