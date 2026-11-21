// qa (frontend phase 1): AC-9.3 "with each dialog open, in the light and the dark theme", and AC-9.4 / D17 focus:
// on open, focus is inside the dialog; Escape closes it and focus returns to the trigger, never to <body>.
import {expect, test, type Page} from '@playwright/test';
import {expectNoAxeViolations, openAs} from '../reportHarness';
import {ids} from '../../src/test/fixtures/auth';
import {runIds} from '../../src/test/fixtures/report';
import {users, type UserKey} from '../../src/test/fixtures/workspace';
import type {FakeBackend} from '../../src/test/fixtures/fakeBackend';

const checkout = `/projects/${ids.checkout}`;

interface Case {
    name: string;
    user: UserKey;
    path: string;
    trigger: (page: Page) => ReturnType<Page['getByRole']>;
    dialog: {role: 'dialog' | 'alertdialog'; name: string};
    setup?: (backend: FakeBackend) => void;
}

const CASES: Case[] = [
    {name: 'new feature', user: 'gus', path: `${checkout}/features`, trigger: (p) => p.getByRole('button', {name: 'New feature'}), dialog: {role: 'dialog', name: 'New feature'}},
    {name: 'plan a manual run', user: 'gus', path: `${checkout}/runs`, trigger: (p) => p.getByRole('button', {name: 'Plan manual run'}), dialog: {role: 'dialog', name: 'Plan a manual run'}},
    {name: 'cancel a run', user: 'gus', path: `${checkout}/runs/${runIds.sprint12}`, trigger: (p) => p.getByRole('button', {name: 'Cancel run'}), dialog: {role: 'alertdialog', name: 'Cancel this run?'}},
    {name: 'create an upload token', user: 'ana', path: `${checkout}/upload-tokens`, trigger: (p) => p.getByRole('button', {name: 'Create token'}), dialog: {role: 'dialog', name: 'Create an upload token'}},
    {name: 'create a project', user: 'ana', path: `/orgs/${ids.acme}/projects`, trigger: (p) => p.getByRole('button', {name: 'Create project'}), dialog: {role: 'dialog', name: 'Create project'}},
    {name: 'add a project member', user: 'ana', path: `${checkout}/members`, trigger: (p) => p.getByRole('button', {name: 'Add member'}), dialog: {role: 'dialog', name: 'Add a project member'}},
    {name: 'invite a member', user: 'ana', path: `/orgs/${ids.acme}/members`, trigger: (p) => p.getByRole('button', {name: 'Invite'}), dialog: {role: 'dialog', name: 'Invite a member'},
        // free a seat (the fixture org is full, so Invite is rightly disabled)
        setup: (b) => b.auth.members.set(ids.acme, (b.auth.members.get(ids.acme) ?? []).filter((m) => m.userId !== users.dan.id))},
    {name: 'archive the project', user: 'ana', path: `${checkout}/settings`, trigger: (p) => p.getByRole('button', {name: 'Archive project'}), dialog: {role: 'alertdialog', name: 'Archive this project?'}},
];

for (const theme of ['light', 'dark'] as const) {
    test.describe(`qa: dialogs, ${theme} theme`, () => {
        test.beforeEach(async ({page}) => {
            await page.addInitScript((value) => localStorage.setItem('cucumber.ui.theme', value), theme);
        });

        for (const c of CASES) {
            test(`${c.name}: axe clean, focus inside, Escape returns focus to the trigger`, async ({page}) => {
                await openAs(page, c.user, c.path, c.setup);
                await expect(page.locator('html')).toHaveAttribute('data-theme', theme);
                const trigger = c.trigger(page).first();
                await trigger.focus();
                await page.keyboard.press('Enter');
                const dialog = page.getByRole(c.dialog.role, {name: c.dialog.name});
                await expect(dialog).toBeVisible();
                await expectNoAxeViolations(page);
                expect(await dialog.evaluate((el) => el.contains(document.activeElement))).toBe(true);
                await page.keyboard.press('Escape');
                await expect(dialog).toHaveCount(0);
                await expect.poll(() => page.evaluate(() => document.activeElement?.tagName)).not.toBe('BODY');
                await expect(trigger).toBeFocused();
            });
        }
    });
}
