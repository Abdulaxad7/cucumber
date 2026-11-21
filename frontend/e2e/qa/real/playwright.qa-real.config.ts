import {defineConfig, devices} from '@playwright/test';

// qa: the real-stack Success journey (a copy of e2e/real), run by qa per standards §6. Not part of "npm run e2e".
// Expects the Vite dev server on 127.0.0.1:5173 proxying /api to the gateway on 8080.
export default defineConfig({
    testDir: '.',
    testMatch: /.*\.qa\.journey\.ts/,
    workers: 1,
    retries: 0,
    timeout: 300_000,
    reporter: 'list',
    outputDir: '/tmp/qa-fe/real-results',
    use: {baseURL: 'http://127.0.0.1:5173', trace: 'retain-on-failure', viewport: {width: 1440, height: 900}},
    projects: [{name: 'real', use: {...devices['Desktop Chrome'], viewport: {width: 1440, height: 900}}}],
});
