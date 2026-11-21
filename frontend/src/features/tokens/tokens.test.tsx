import {describe, expect, it} from 'vitest';
import {fireEvent, screen, waitFor, within} from '@testing-library/react';
import {server} from '../../test/msw';
import {renderApp} from '../../test/render';
import {tokenFor} from '../../test/jwt';
import {ids} from '../../test/fixtures/auth';
import {users} from '../../test/fixtures/workspace';
import {FakeBackend} from '../../test/fixtures/fakeBackend';
import {fakeBackendHandlers} from '../../test/fixtures/handlers';
import {startSession} from '../../auth/session';

function open(setup?: (backend: FakeBackend) => void) {
    const backend = new FakeBackend('ana');
    setup?.(backend);
    server.use(...fakeBackendHandlers(backend));
    startSession(tokenFor(users.ana.id, users.ana.email, 3600));
    renderApp(`/projects/${ids.checkout}/upload-tokens`);
    return backend;
}

async function create(name: string) {
    fireEvent.click(await screen.findByRole('button', {name: 'Create token'}));
    const dialog = await screen.findByRole('dialog', {name: 'Create an upload token'});
    fireEvent.change(within(dialog).getByLabelText('Token name'), {target: {value: name}});
    fireEvent.click(within(dialog).getByRole('button', {name: 'Create token'}));
    return dialog;
}

// @US-8 @AC-8.5
describe('AC-8.5 upload tokens', () => {
    it('shows the token once with Copy and a curl example; after closing it is nowhere, and the list shows the prefix', async () => {
        open();
        await create('GitHub Actions');
        const shown = await screen.findByRole('dialog', {name: 'Copy your upload token'});
        const code = within(shown).getByLabelText('Upload token');
        const token = code.textContent ?? '';
        expect(token).toMatch(/^rct_/);
        expect(within(shown).getByRole('button', {name: 'Copy token'})).toBeTruthy();
        expect(within(shown).getByLabelText('curl example').textContent).toContain('/api/ci/runs');
        fireEvent.click(within(shown).getByRole('button', {name: 'Done'}));
        await waitFor(() => expect(screen.queryByRole('dialog', {name: 'Copy your upload token'})).toBeNull());
        expect(document.documentElement.innerHTML).not.toContain(token);
        expect(JSON.stringify({...sessionStorage, ...localStorage})).not.toContain(token);
        const table = await screen.findByRole('grid', {name: 'Upload tokens'});
        expect(within(table).getByText('GitHub Actions')).toBeTruthy();
        expect(within(table).getByText(token.slice(0, 12))).toBeTruthy();
    });

    it('revokes after confirming', async () => {
        open();
        await create('GitHub Actions');
        fireEvent.click(within(await screen.findByRole('dialog', {name: 'Copy your upload token'})).getByRole('button', {name: 'Done'}));
        fireEvent.click(await screen.findByRole('button', {name: 'Revoke GitHub Actions'}));
        fireEvent.click(within(await screen.findByRole('alertdialog')).getByRole('button', {name: 'Revoke token'}));
        expect(await screen.findByText('Revoked')).toBeTruthy();
    });

    it('409 UPLOAD_TOKEN_LIMIT says "At most 10 active tokens"', async () => {
        open((b) => {
            for (let i = 0; i < 10; i += 1) {
                b.report.tokens.push({
                    id: `a9100000-0000-4000-8000-${String(i).padStart(12, '0')}`,
                    name: `CI ${i}`,
                    tokenPrefix: `rct_tok${i}abcd`,
                    createdBy: users.ana.id,
                    createdAt: '2026-11-01T10:00:00Z',
                    expiresAt: null,
                    lastUsedAt: null,
                    revokedAt: null,
                    revokedBy: null,
                });
            }
        });
        const dialog = await create('One more');
        expect(await within(dialog).findByText('At most 10 active tokens')).toBeTruthy();
    });
});
