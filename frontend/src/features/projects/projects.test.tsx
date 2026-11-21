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

function open(user: UserKey, path: string, setup?: (auth: FakeAuth) => void) {
    const auth = new FakeAuth(user);
    setup?.(auth);
    server.use(...fakeAuthHandlers(auth));
    startSession(tokenFor(users[user].id, users[user].email, 3600));
    const app = renderApp(path);
    return {auth, app};
}

const settings = `/projects/${ids.checkout}/settings`;
const membersPath = `/projects/${ids.checkout}/members`;

// @US-4 @AC-4.1
describe('AC-4.1 list and create', () => {
    it('Gus sees Checkout only, with MEMBER, and no Create project', async () => {
        open('gus', `/orgs/${ids.acme}/projects`);
        expect(await screen.findByRole('link', {name: /Checkout/})).toBeTruthy();
        expect(screen.queryByText('Billing')).toBeNull();
        expect(screen.getByText('MEMBER')).toBeTruthy();
        expect(screen.queryByRole('button', {name: 'Create project'})).toBeNull();
    });

    it('Ana creates Payments and lands on it as ADMIN; a taken name is shown at the field', async () => {
        const {app} = open('ana', `/orgs/${ids.acme}/projects`);
        fireEvent.click(await screen.findByRole('button', {name: 'Create project'}));
        const dialog = await screen.findByRole('dialog', {name: 'Create project'});
        fireEvent.change(within(dialog).getByLabelText('Project name'), {target: {value: 'checkout'}});
        fireEvent.click(within(dialog).getByRole('button', {name: 'Create project'}));
        const field = within(dialog).getByLabelText('Project name');
        await waitFor(() => expect(field.getAttribute('aria-invalid')).toBe('true'));
        expect(within(dialog).getByText('A project with this name already exists')).toBeTruthy();

        fireEvent.change(field, {target: {value: 'Payments'}});
        fireEvent.click(within(dialog).getByRole('button', {name: 'Create project'}));
        await waitFor(() => expect(app.router.state.location.pathname).toMatch(/^\/projects\/[^/]+\/settings$/));
        expect(await screen.findByRole('heading', {level: 1, name: 'Payments'})).toBeTruthy();
        expect(await screen.findByText('Your role: ADMIN')).toBeTruthy();
    });
});

// @US-4 @AC-4.4
describe('AC-4.4 project actions by role', () => {
    it.each([
        ['ana', true],
        ['gus', false],
        ['hal', false],
        ['dan', false],
    ] as const)('%s: rename, archive and member changes shown=%s', async (user, shown) => {
        open(user, settings);
        await screen.findByRole('heading', {level: 1, name: 'Checkout'});
        await screen.findByText(/Your role: /);
        expect(screen.queryAllByRole('button', {name: 'Rename project'}).length > 0).toBe(shown);
        expect(screen.queryAllByRole('button', {name: 'Archive project'}).length > 0).toBe(shown);
        if (!shown) expect(screen.getByText(/Read only: your role is/)).toBeTruthy();
    });

    it.each([
        ['ana', true],
        ['gus', false],
        ['hal', false],
        ['dan', false],
    ] as const)('%s on members: add, role and remove shown=%s', async (user, shown) => {
        open(user, membersPath);
        await screen.findByText('Hal Jordan');
        expect(screen.queryAllByRole('button', {name: 'Add member'}).length > 0).toBe(shown);
        expect(screen.queryAllByRole('button', {name: /Role for /}).length > 0).toBe(shown);
        expect(screen.queryAllByRole('button', {name: /^Remove /}).length > 0).toBe(shown);
    });
});

// @US-4 @AC-4.2
describe('AC-4.2 rename, archive and restore', () => {
    it('renames, then archives with a banner and Restore', async () => {
        open('ana', settings);
        const name = await screen.findByLabelText('Project name');
        fireEvent.change(name, {target: {value: 'Checkout Web'}});
        fireEvent.click(screen.getByRole('button', {name: 'Rename project'}));
        expect(await screen.findByRole('heading', {level: 1, name: 'Checkout Web'})).toBeTruthy();

        fireEvent.click(screen.getByRole('button', {name: 'Archive project'}));
        const confirm = await screen.findByRole('alertdialog', {name: 'Archive this project?'});
        fireEvent.click(within(confirm).getByRole('button', {name: 'Archive project'}));
        expect(await screen.findByText('Project archived: read only')).toBeTruthy();
        expect(screen.getByRole('button', {name: 'Restore project'})).toBeTruthy();
        expect(screen.queryByRole('button', {name: 'Rename project'})).toBeNull();
    });

    it('a 409 PROJECT_ARCHIVED from a write refetches the project and shows the banner', async () => {
        const {auth} = open('ana', settings);
        const name = await screen.findByLabelText('Project name');
        const checkout = auth.projects.find((p) => p.id === ids.checkout);
        if (checkout) checkout.archivedAt = '2026-11-09T10:00:00Z';
        fireEvent.change(name, {target: {value: 'Checkout Web'}});
        fireEvent.click(screen.getByRole('button', {name: 'Rename project'}));
        expect(await screen.findByText('Project archived: read only')).toBeTruthy();
        await waitFor(() => expect(screen.queryByRole('button', {name: 'Rename project'})).toBeNull());
    });
});

// @US-4 @AC-4.3
describe('AC-4.3 project members', () => {
    it('Ana adds Ben as TESTER, changes Hal to MEMBER and removes Dan', async () => {
        open('ana', membersPath);
        await screen.findByText('Hal Jordan');
        fireEvent.click(screen.getByRole('button', {name: 'Add member'}));
        const dialog = await screen.findByRole('dialog', {name: 'Add a project member'});
        fireEvent.click(within(dialog).getByRole('button', {name: /Person/}));
        const people = await screen.findAllByRole('option');
        expect(people.map((o) => o.textContent)).toEqual(expect.arrayContaining([expect.stringContaining('Ben Okafor'), expect.stringContaining('Ana Ruiz')]));
        expect(people.some((o) => (o.textContent ?? '').includes('Gus Underwood'))).toBe(false);
        fireEvent.click(people.find((o) => (o.textContent ?? '').includes('Ben Okafor')) ?? people[0] ?? document.body);
        fireEvent.click(within(dialog).getByRole('button', {name: /Role/}));
        fireEvent.click(await screen.findByRole('option', {name: 'TESTER'}));
        fireEvent.click(within(dialog).getByRole('button', {name: 'Add member'}));
        expect(await screen.findByText('Ben Okafor')).toBeTruthy();

        fireEvent.click(screen.getByRole('button', {name: /Role for Hal Jordan/}));
        fireEvent.click(await screen.findByRole('option', {name: 'MEMBER'}));
        fireEvent.click(screen.getByRole('button', {name: 'Remove Dan Brook'}));
        fireEvent.click(within(await screen.findByRole('alertdialog')).getByRole('button', {name: 'Remove member'}));
        await waitFor(() => expect(screen.queryByText('Dan Brook')).toBeNull());
        const grid = screen.getByRole('grid', {name: 'Project members'});
        await waitFor(() => expect(within(grid).getAllByRole('row').map((r) => r.textContent ?? '').join('|')).toMatch(/Hal Jordan.*MEMBER/));
    });

    it('adding an org READER as MEMBER shows 422 PROJECT_ROLE_NOT_ALLOWED at the role field', async () => {
        open('ana', membersPath, (auth) => {
            auth.projectMembers.set(ids.checkout, (auth.projectMembers.get(ids.checkout) ?? []).filter((m) => m.userId !== users.dan.id));
        });
        await screen.findByText('Hal Jordan');
        fireEvent.click(screen.getByRole('button', {name: 'Add member'}));
        const dialog = await screen.findByRole('dialog', {name: 'Add a project member'});
        fireEvent.click(within(dialog).getByRole('button', {name: /Person/}));
        fireEvent.click((await screen.findAllByRole('option')).find((o) => (o.textContent ?? '').includes('Dan Brook')) ?? document.body);
        fireEvent.click(within(dialog).getByRole('button', {name: /Role/}));
        fireEvent.click(await screen.findByRole('option', {name: 'MEMBER'}));
        fireEvent.click(within(dialog).getByRole('button', {name: 'Add member'}));
        expect(await within(dialog).findByText("This person's organization role allows only READER")).toBeTruthy();
    });

    it('Gus sees "Leave project" on his own row only', async () => {
        open('gus', membersPath);
        await screen.findByText('Hal Jordan');
        const leave = screen.getAllByRole('button', {name: 'Leave project'});
        expect(leave).toHaveLength(1);
        expect(leave[0]?.closest('[role=row]')?.textContent).toContain('Gus Underwood');
    });
});
