// F10: the Success journey in "Problem and goal", against the real stack through the gateway.
// Ana registers, creates Acme and Checkout, and invites Gus, Hal and Dan, who accept. Gus creates
// features/checkout/payment.feature, saves an invalid edit and sees the marker at its line and column, fixes it and
// saves version 2. Gus plans a manual run, Hal records results and completes it, and Dan reads the run summary.
// Screenshots go to review-screenshots/real-*.png.
import {fileURLToPath} from 'node:url';
import {expect, test, type Browser, type Page} from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

const outDir = fileURLToPath(new URL('../../review-screenshots/', import.meta.url));
const password = 'Journey-Secret-123!';

interface Person {
    name: string;
    email: string;
}

const ana: Person = {name: 'Ana Ruiz', email: 'ana@example.com'};
const gus: Person = {name: 'Gus Underwood', email: 'gus@example.com'};
const hal: Person = {name: 'Hal Jordan', email: 'hal@example.com'};
const dan: Person = {name: 'Dan Brook', email: 'dan@example.com'};

async function shot(page: Page, name: string) {
    await expect(page.locator('[data-entering]')).toHaveCount(0);
    await page.waitForTimeout(300);
    await page.screenshot({path: `${outDir}real-${name}.png`, fullPage: true, animations: 'disabled', caret: 'hide'});
}

async function axe(page: Page) {
    await expect(page.locator('[data-entering]')).toHaveCount(0);
    const results = await new AxeBuilder({page}).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
    expect(results.violations.map((v) => v.id)).toEqual([]);
}

async function newPage(browser: Browser): Promise<Page> {
    const context = await browser.newContext({viewport: {width: 1440, height: 900}});
    await context.addInitScript(() => localStorage.setItem('cucumber.ui.theme', 'light'));
    return context.newPage();
}

async function invite(page: Page, orgId: string, email: string, role: 'MEMBER' | 'READER'): Promise<string> {
    await page.goto(`/orgs/${orgId}/members`);
    await page.getByRole('button', {name: 'Invite'}).click();
    const dialog = page.getByRole('dialog', {name: 'Invite a member'});
    await dialog.getByLabel('Email').fill(email);
    if (role !== 'MEMBER') {
        await dialog.getByRole('button', {name: /Role/}).click();
        await page.getByRole('option', {name: role}).click();
    }
    await dialog.getByRole('button', {name: 'Send invitation'}).click();
    const tokenDialog = page.getByRole('dialog', {name: 'Share the invitation link'});
    const link = await tokenDialog.getByLabel('Invitation link').inputValue();
    await tokenDialog.getByRole('button', {name: 'Done'}).click();
    await expect(tokenDialog).toHaveCount(0);
    return link;
}

async function accept(browser: Browser, link: string, person: Person, orgName: string, role: string, screenshot?: string): Promise<Page> {
    const page = await newPage(browser);
    await page.goto(new URL(link).pathname + new URL(link).hash);
    await expect(page).toHaveURL('/invite');
    await page.getByLabel('Full name').fill(person.name);
    await page.getByLabel('Password').fill(password);
    if (screenshot) await shot(page, screenshot);
    await page.getByRole('button', {name: 'Accept invitation'}).click();
    await expect(page.getByText(`You joined ${orgName} as ${role}`)).toBeVisible();
    await page.getByLabel('Password').fill(password);
    await page.getByRole('button', {name: 'Log in'}).click();
    await expect(page).toHaveURL(/\/orgs\/[0-9a-f-]{36}\/projects$/);
    return page;
}

async function addToProject(page: Page, projectId: string, person: Person, role: 'MEMBER' | 'TESTER' | 'READER') {
    await page.goto(`/projects/${projectId}/members`);
    await page.getByRole('button', {name: 'Add member'}).click();
    const dialog = page.getByRole('dialog', {name: 'Add a project member'});
    await dialog.getByRole('button', {name: /Person/}).click();
    await page.getByRole('option', {name: new RegExp(person.name)}).click();
    await dialog.getByRole('button', {name: /Role/}).click();
    await page.getByRole('option', {name: role, exact: true}).click();
    await dialog.getByRole('button', {name: 'Add member'}).click();
    await expect(dialog).toHaveCount(0);
    await expect(page.getByRole('grid', {name: 'Project members'})).toContainText(person.name);
}

async function replaceDoc(page: Page, text: string) {
    await page.getByRole('textbox', {name: 'Gherkin source'}).click();
    await page.keyboard.press('ControlOrMeta+a');
    await page.keyboard.insertText(text);
}

async function createFeature(page: Page, path: string) {
    await page.getByRole('button', {name: 'New feature'}).first().click();
    const dialog = page.getByRole('dialog', {name: 'New feature'});
    await dialog.getByLabel('Path').fill(path);
    await dialog.getByRole('button', {name: 'Create feature'}).click();
    await expect(dialog).toHaveCount(0);
    await expect(page.getByText('Version 1')).toBeVisible();
}

test('Success journey against the real stack', async ({browser}) => {
    // 1. Ana registers (and is logged in), creates Acme and Checkout.
    const anaPage = await newPage(browser);
    await anaPage.goto('/register');
    await anaPage.getByLabel('Full name').fill(ana.name);
    await anaPage.getByLabel('Email').fill(ana.email);
    await anaPage.getByLabel('Password').fill(password);
    await shot(anaPage, '01-register');
    await anaPage.getByRole('button', {name: 'Create account'}).click();
    await expect(anaPage).toHaveURL('/orgs');
    await expect(anaPage.getByText("You're not in an organization yet")).toBeVisible();
    await shot(anaPage, '02-orgs-empty');

    await anaPage.getByRole('link', {name: 'Create organization'}).first().click();
    await anaPage.getByLabel('Organization name').fill('Acme');
    await anaPage.getByRole('button', {name: 'Create organization'}).click();
    await expect(anaPage).toHaveURL(/\/orgs\/[0-9a-f-]{36}\/projects$/);
    const orgId = new URL(anaPage.url()).pathname.split('/')[2] ?? '';

    await anaPage.getByRole('button', {name: 'Create project'}).first().click();
    const createProject = anaPage.getByRole('dialog', {name: 'Create project'});
    await createProject.getByLabel('Project name').fill('Checkout');
    await createProject.getByRole('button', {name: 'Create project'}).click();
    await expect(anaPage).toHaveURL(/\/projects\/[0-9a-f-]{36}\/settings$/);
    await expect(anaPage.getByRole('heading', {level: 1, name: 'Checkout'})).toBeVisible();
    await expect(anaPage.getByText('Your role: ADMIN')).toBeVisible();
    const projectId = new URL(anaPage.url()).pathname.split('/')[2] ?? '';
    await shot(anaPage, '03-project-created');

    // 2. Ana invites Gus, Hal (MEMBER) and Dan (READER); they accept.
    const gusLink = await invite(anaPage, orgId, gus.email, 'MEMBER');
    const halLink = await invite(anaPage, orgId, hal.email, 'MEMBER');
    const danLink = await invite(anaPage, orgId, dan.email, 'READER');
    await expect(anaPage.getByText('Seats: 4 of 5 used')).toBeVisible();
    await shot(anaPage, '04-invitations-pending');

    const gusPage = await accept(browser, gusLink, gus, 'Acme', 'MEMBER', '05-invite-accept');
    const halPage = await accept(browser, halLink, hal, 'Acme', 'MEMBER');
    const danPage = await accept(browser, danLink, dan, 'Acme', 'READER');

    await anaPage.goto(`/orgs/${orgId}/members`);
    await expect(anaPage.getByText('Seats: 4 of 5 used')).toBeVisible();
    await expect(anaPage.getByText('No pending invitations')).toBeVisible();
    await shot(anaPage, '06-org-members');

    // 3. Ana gives them project roles: Gus MEMBER, Hal TESTER, Dan READER.
    await addToProject(anaPage, projectId, gus, 'MEMBER');
    await addToProject(anaPage, projectId, hal, 'TESTER');
    await addToProject(anaPage, projectId, dan, 'READER');
    await shot(anaPage, '07-project-members');

    // 4. Gus creates payment.feature, saves an invalid edit, sees the marker at its line and column, fixes it.
    await gusPage.goto(`/projects/${projectId}/features`);
    await expect(gusPage.getByText('No features yet')).toBeVisible();
    await createFeature(gusPage, 'features/checkout/payment.feature');
    await replaceDoc(gusPage, ['@checkout', 'Feature: Payment', '', '  Scenario: Pay by card', '    Given a cart with 2 items', '    Whenn I pay by card', '    Then the order is confirmed', ''].join('\n'));
    await gusPage.keyboard.press('ControlOrMeta+s');
    const marked = gusPage.locator('.cm-lintRange-error');
    await expect(marked).toHaveCount(1);
    await expect(gusPage.locator('.cm-line').nth(5).locator('.cm-lintRange-error')).toHaveCount(1);
    const problems = gusPage.getByRole('region', {name: 'Gherkin problems'});
    await expect(problems).toContainText('1 problem');
    await expect(problems).toContainText('Line 6, column 5');
    await expect(gusPage.getByRole('status').filter({hasText: '1 Gherkin error; first on line 6'})).toBeAttached();
    await expect(gusPage.getByText('Version 1')).toBeVisible();
    await shot(gusPage, '08-editor-parse-error');
    await axe(gusPage);

    await replaceDoc(gusPage, ['@checkout', 'Feature: Payment', '', '  Scenario: Pay by card', '    Given a cart with 2 items', '    When I pay by card', '    Then the order is confirmed', ''].join('\n'));
    await gusPage.keyboard.press('ControlOrMeta+s');
    await expect(gusPage.getByText('Version 2')).toBeVisible();
    await expect(marked).toHaveCount(0);
    await expect(gusPage.getByText('Unsaved changes')).toHaveCount(0);
    await shot(gusPage, '09-editor-saved-v2');

    // A second feature for the run.
    await gusPage.goto(`/projects/${projectId}/features`);
    await createFeature(gusPage, 'features/checkout/address.feature');
    await replaceDoc(gusPage, ['@smoke', 'Feature: Address', '', '  Scenario: Add an address', '    Given I am signed in', '    When I add an address', '    Then it is saved', ''].join('\n'));
    await gusPage.keyboard.press('ControlOrMeta+s');
    await expect(gusPage.getByText('Version 2')).toBeVisible();
    await gusPage.goto(`/projects/${projectId}/features`);
    const tree = gusPage.getByRole('treegrid', {name: 'Features'});
    await expect(tree).toContainText('address.feature');
    await expect(tree).toContainText('payment.feature');
    await shot(gusPage, '10-feature-tree');

    // 5. Gus plans a manual run of Payment and Address.
    await gusPage.goto(`/projects/${projectId}/runs`);
    await expect(gusPage.getByText('No runs yet')).toBeVisible();
    await gusPage.getByRole('button', {name: 'Plan manual run'}).click();
    const plan = gusPage.getByRole('dialog', {name: 'Plan a manual run'});
    await plan.getByLabel('Run name').fill('Sprint 12');
    await plan.getByText('features/checkout/payment.feature').click();
    await plan.getByText('features/checkout/address.feature').click();
    await plan.getByLabel('Environment (optional)').fill('staging');
    await shot(gusPage, '11-plan-run');
    await plan.getByRole('button', {name: 'Plan run'}).click();
    await expect(gusPage).toHaveURL(new RegExp(`/projects/${projectId}/runs/[0-9a-f-]{36}$`));
    await expect(gusPage.getByText('Planned', {exact: true})).toBeVisible();
    const runUrl = new URL(gusPage.url()).pathname;
    await expect(gusPage.getByRole('region', {name: 'Run summary'})).toContainText('2');

    // 6. Hal records results and completes the run.
    await halPage.goto(runUrl);
    await halPage.getByRole('tab', {name: 'Results'}).click();
    const results = halPage.getByRole('grid', {name: 'Results'});
    await expect(results.getByRole('row')).toHaveCount(3);
    await halPage.getByRole('button', {name: 'Record Pay by card'}).click();
    let dialog = halPage.getByRole('dialog', {name: 'Pay by card'});
    await dialog.getByRole('radio', {name: 'Passed'}).click();
    await dialog.getByRole('button', {name: 'Save result'}).click();
    await expect(dialog).toHaveCount(0);
    await halPage.getByRole('button', {name: 'Record Add an address'}).click();
    dialog = halPage.getByRole('dialog', {name: 'Add an address'});
    await dialog.getByRole('radio', {name: 'Failed'}).click();
    await dialog.getByLabel('Comment (optional)').fill('Address form rejects the postcode');
    await shot(halPage, '12-record-result');
    await dialog.getByRole('button', {name: 'Save result'}).click();
    await expect(dialog).toHaveCount(0);
    await expect(results.getByRole('row', {name: /Add an address/})).toContainText('Failed');
    await expect(results.getByRole('row', {name: /Add an address/})).toContainText(hal.name);
    await expect(halPage.getByText('In progress', {exact: true})).toBeVisible();
    await halPage.getByRole('button', {name: 'Complete run'}).click();
    await halPage.getByRole('alertdialog').getByRole('button', {name: 'Complete run'}).click();
    await expect(halPage.getByText('Completed', {exact: true})).toBeVisible();
    await expect(halPage.getByRole('button', {name: /^Record /})).toHaveCount(0);

    // 7. Dan reads the summary.
    await danPage.goto(runUrl);
    const summary = danPage.getByRole('region', {name: 'Run summary'});
    await expect(summary).toContainText('50.0%');
    const counts = summary.getByRole('list', {name: 'Results by status'});
    await expect(counts.getByRole('listitem').filter({hasText: 'Passed'})).toContainText('1');
    await expect(counts.getByRole('listitem').filter({hasText: 'Failed'})).toContainText('1');
    await expect(danPage.getByRole('button', {name: 'Complete run'})).toHaveCount(0);
    await shot(danPage, '13-run-summary');
    await axe(danPage);
    await danPage.goto(`/projects/${projectId}/runs`);
    await expect(danPage.getByRole('grid', {name: 'Runs'})).toContainText('Sprint 12');
    await expect(danPage.getByRole('grid', {name: 'Runs'})).toContainText('50.0%');
    await expect(danPage.locator('[role=img] svg').first()).toBeVisible();
    await shot(danPage, '14-runs-dashboard');
});
