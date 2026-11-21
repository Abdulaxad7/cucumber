# Cucumber frontend

The web UI of the Cucumber BDD test-management platform. It is a single-page React app that talks only to the API
gateway, through relative `/api/...` URLs.

## Stack

- React 19, Vite 8 and TypeScript 5.9 (strict); 4-space indentation.
- Tailwind CSS 4 with the design tokens in `src/ui/theme.css`, which is the only stylesheet. The components are built on react-aria-components; icons come from lucide-react.
- TanStack Query for data and TanStack Router for routes.
- A typed client generated from `docs/api/*.openapi.yaml` (openapi-typescript and openapi-fetch).
- CodeMirror 6 for the Gherkin editor, and ECharts (SVG renderer) for the run dashboard.
- Vitest, Testing Library and MSW for unit and component tests; Playwright with axe for end-to-end and accessibility tests.

## Requirements

- Node 24 LTS (`.nvmrc`); run `nvm use` in this folder.
- npm, using the committed `package-lock.json`.
- For end-to-end tests: Playwright's Chromium. Install it once with `npx playwright install chromium` if it's missing.

## Commands

| Command | What it does |
|---|---|
| `npm ci` | Installs the exact locked dependencies |
| `npm run dev` | Starts Vite on http://localhost:5173, proxying `/api` to the gateway on http://localhost:8080 |
| `npm run build` | Type-checks, then builds `dist/` |
| `npm run preview` | Serves `dist/`, with the same `/api` proxy |
| `npm run lint` | Runs ESLint (typescript-eslint strict, jsx-a11y strict, and the project guardrails below) with zero warnings allowed |
| `npm run typecheck` | Runs `tsc --noEmit` for the app and for the config and e2e files |
| `npm test` | Runs `gen:api:check`, then Vitest |
| `npm run e2e` | Runs the Playwright tests against `vite preview`, with the API faked by `page.route` and axe on every screen |
| `npm run screenshots` | Writes review screenshots to `review-screenshots/` (gitignored): every screen and state, light and dark, at 1440 and 390 px |
| `npm run gen:api` | Regenerates `src/api/generated/` from `docs/api/` |
| `npm run gen:api:check` | Fails if `src/api/generated/` differs from a fresh generation |

`npm run e2e` also runs the accessibility sweep (`e2e/a11y-sweep.spec.ts`). It visits every route and view state in both themes and at both widths, and writes the violation counts to `review-screenshots/axe-report-*.json`.

## Running against the real backend

1. Start the gateway on port 8080, with auth-service, scenario-service and report-service behind it (see each service's README).
2. Run `npm run dev` and open http://localhost:5173.

The app is served from the same origin as the API through the dev proxy, so the gateway needs no CORS setting in development.

## Layout

```text
src/
  api/        typed clients (client.ts), the auth middleware, problem.ts (the only parser of error bodies), generated/
  auth/       the session (sessionStorage), JWT decoding, role ranks (roles.ts), safe "next" paths
  app/        router, routes, app shell (top bar, sidebar, drawer), focus management
  ui/         design-system components and theme.css
  features/   session, orgs, invitations, projects, tree, editor, history, runs, tokens
  test/       MSW server, render helpers, contract-typed fixtures and in-memory fake services
e2e/          Playwright specs (design, session, tree, editor, orgs, projects, runs, manual-run, a11y sweep, screenshots)
scripts/      gen-api.mjs
```

## Guardrails

These are checked by ESLint or by tests:

- Only `src/api` calls `fetch`, and only `src/api/problem.ts` reads error bodies (RFC 9457 Problems).
- Only `src/auth/session.ts` touches `sessionStorage` (`cucumber.session`, `cucumber.pendingInvite`), and only `src/ui/prefs.ts` touches `localStorage` (the theme and sidebar preferences; never a secret).
- No `any`, no `as` casts, no non-null assertions, no `console`, and no `dangerouslySetInnerHTML`.
- No inline styles, arbitrary Tailwind values, palette colours or extra stylesheets (DS-12; see `src/ui/design-rules.test.js`). The token contrast pairs are checked by `src/ui/theme.contrast.test.js`.
- All text lives in each area's `strings.ts`.

## Security notes

- The JWT is kept in memory and mirrored to `sessionStorage` (one tab, one session). It never goes into `localStorage`, a cookie, a URL or the console.
- Invitation tokens are read from the URL fragment and removed from the address bar at once. Upload tokens and invitation links are shown once in a dialog, and are gone from the page and storage once it closes.
- Any 401, except login's own, ends the session and returns the user to the page they were on after logging in. The `next` parameter only accepts in-app paths.
