import {defineConfig, devices} from '@playwright/test';

// F10: the Success journey against the real stack (standards §6). Not part of "npm run e2e".
// Expects the Vite dev server on 127.0.0.1:5173, proxying /api to the gateway on 8080. Run with:
//   npx playwright test --config e2e/real/playwright.real.config.ts
export default defineConfig({
    testDir: '.',
    testMatch: /.*\.journey\.ts/,
    workers: 1,
    retries: 0,
    timeout: 300_000,
    reporter: 'list',
    use: {baseURL: 'http://127.0.0.1:5173', trace: 'retain-on-failure', viewport: {width: 1440, height: 900}},
    projects: [{name: 'real', use: {...devices['Desktop Chrome'], viewport: {width: 1440, height: 900}}}],
});
