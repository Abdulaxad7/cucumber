# QA report: frontend phase 1

**Verdict: Not signed off.**
- 52 ACs (25 P0, 26 P1, 1 P2): **51 pass, 1 fails**.
- **AC-5.1 (P0) fails:** feature-tree rows don't show the feature's `name` (FE-QA-3).
- No S1 or S2 bug. Open: two S3 (FE-QA-2, FE-QA-3) and two S4 (FE-QA-1, FE-QA-4).
- **Path to sign-off:** fix FE-QA-3, or pm amends AC-5.1 as a named deviation. The verdict then becomes **Signed off with known issues**.

- **Code under test:** HEAD `2844565` plus the working tree (frontend batch E files, `Shell.tsx` focus fix, spec at review point E).
- **Contracts:** gateway 0.3.4, auth 1.5.0, scenario 1.0.7, report 1.0.2.
- **Tools:** Node v24.22.2, every command prefixed with `source ~/.nvm/nvm.sh && nvm use 24`.
- **Writes:** only `frontend/src/**/qa/**` and `frontend/e2e/qa/**`. No production code, no packages, no git, no `.env`.

## Commands and counts
| Run | Command | Result |
|---|---|---|
| Baseline lint | `npm run lint` | exit 0 |
| Baseline typecheck | `npm run typecheck` | exit 0 |
| Baseline unit + contract drift | `npm test` (gen:api:check + vitest) | "gen:api:check: generated client matches docs/api."; vitest **437/0/0** (27 files) |
| Baseline e2e | `npx playwright test --project=chromium` | **196 passed, 1 failed** of 197: an intermittent axe failure (FE-QA-1) |
| FE-QA-1 repeat | `-g "login, register and not-found" --repeat-each=10` | 20/20 passed |
| Final lint | `npm run lint` | exit 0 (includes qa files) |
| Final typecheck | `npm run typecheck` | exit 0 |
| Final unit | `npm test` | gen:api:check matches; vitest **637/0/0** (34 files; 437 existing + 200 qa) |
| Final e2e | `npx playwright test --project=chromium` | **255/0/0** (13 files; 197 existing + 58 qa) |
| Real-stack journey | `npx playwright test --config e2e/qa/real/playwright.qa-real.config.ts` | **1/1 passed** (37.8 s), after the `Shell.tsx` fix |
| Mutation | 23 mutants, full vitest suite, /tmp copy | **23/23 killed** (2 equivalent originals replaced; see below) |

**Real-stack run (standards §6):**
- **Setup:**
  - my own `postgres:17` (`qa-fe-95415`) on a free port, with 3 databases;
  - the 4 jars from 2026-11-21, none older than its sources;
  - the gateway on 8080 (the dev proxy's fixed target), the services on free ports;
  - Vite dev on 5173;
  - throwaway secrets, never printed.
- **Cleanup:** PIDs 95448, 95449, 95450, 95451 and 95822 were killed by PID. The container was removed with its volume. Nothing is left listening on 8080, 5173 or 4173.
- **Backend logs:** 0 ERROR lines. 2 WARN lines, both SpringDoc start-up notices. No match in any log for the journey password, the JWT secret, the service key or `Bearer ey`.
- **Screenshots:** written to /tmp, then deleted. The existing `review-screenshots/real-*.png` are untouched.

**Other measurements:**
- **Initial JS of the login route:** 200.3 KB gzip, against a 250 KB budget.
- **2,000 features plus a 30-level folder chain at 390 px:** the tree was ready in 455 ms including navigation, with fewer than 200 rows in the DOM.
- **No third-party requests:** the bundle contains only SVG namespace strings, react.dev error links, and the router's `http://localhost` parsing fallback.

## Per-AC results
Legend: **P** pass, **F** fail. "B" means the existing tests were audited against the Gherkin; "Q" means a qa test was added.

| AC | Pri | Result | Evidence and notes |
|---|---|---|---|
| 1.1 | P0 | P | B. Q: the token never reaches localStorage, cookies or the URL |
| 1.2 | P0 | P | B |
| 1.3 | P0 | P | B |
| 1.4 | P0 | P | B; real journey |
| 1.5 | P0 | P | B. Q: `exp == now` is discarded; 12 malformed-JWT rows |
| 1.6 | P0 | P | B. Q: a 30-day token isn't ended early by the 2³¹ ms timer limit; logout empties the cache (M23b) |
| 1.7 | P1 | P | B (1 e2e row). Q, in a real browser: 18 hostile `next` values on submit and 4 on the already-signed-in redirect, including `//`, `/\`, `\\`, `javascript:`, `data:`, `%09`, `%0A`, `%0D`, double-encoded, JSON-shaped and login-loop values. All stay on the origin and no request goes to a foreign host. Unit: 30 values checked by resolved origin |
| 2.1 | P1 | P | B. Q: the token is sent once, encoded, and never reaches the URL, storage, the console or the DOM. Malformed fragments: see FE-QA-2 |
| 2.2 | P1 | P | B; real journey |
| 2.3 | P1 | P | B (7 rows) |
| 3.1–3.4 | P1 | P | B |
| 3.5 | P1 | P | B. Q: the full org role matrix (`canInvite`, `assignableOrgRoles`, `canManageMember`, `canCreateProject`) checked against auth 1.5.0 |
| 4.1–4.3 | P1 | P | B |
| 4.4 | P1 | P | B. Q: the project role × action matrix (5 roles, non-member included, × 8 actions) |
| **5.1** | **P0** | **F** | **FE-QA-3:** rows show file name, tags and count, but not `name`; tags are also hidden below 640 px |
| 5.2–5.4 | P0 | P | B |
| 5.5 | P1 | P | B. Q: 2,000 features plus deep folders at 390 px |
| 6.1, 6.2 | P0 | P | B |
| 6.3 | P1 | P | B. Q: precomposed and decomposed `é`, 💳, ZWJ family emoji, a flag, astral letters, ZWSP; never a split surrogate; clamping |
| 6.4–6.6 | P0 | P | B |
| 6.7 | P1 | P | B |
| 7.1, 7.2 | P1 | P | B |
| 8.1 | P1 | P | B. The button reads "Show <chart> as a table" (FE-QA-4) |
| 8.2–8.6 | P1 | P | B. Q (8.6): role matrix |
| 8.7 | P2 | P | B. Q: polling stops when a run closes on the server, stops while hidden, sends nothing after logout; the list polls every 30 s |
| 9.1 | P0 | P | B. Q: 20 mapper rows, non-Problem bodies, hostile `requestId`, `parseErrors`, `currentVersion` and `featureIds` validation, `errors[]` by field and pointer. Q e2e: 9 rows rendered, with sentence, Retry, "Reference: <id>", and D9's retry count |
| 9.2 | P0 | P | B (sweep states) |
| 9.3 | P0 | P | B: 116-run sweep. Q: 8 dialogs × 2 themes, axe clean. Intermittent test failure FE-QA-1 |
| 9.4 | P0 | P | B. Q: each dialog opens with Enter, focus is inside it, and Escape returns focus to the trigger, never `<body>` |
| 9.5 | P0 | P | gen:api:check matches; tsc strict and eslint exit 0 |
| 9.6 | P0 | P | B. Q: invite, session and journey logs |
| 10.1–10.4 | P0 | P | B |
| 10.5 | P1 | P | Not re-run by qa. 275 files present (screenshots, `axe-report-*.json` per RV-E2, 14 `real-*`), including drawer, user-menu and theme-menu states; the folder is gitignored |
| 10.6 | P1 | P | B. The reduced-motion CSS kills animation and transition |

**Other scope checks:**
- **Security:** no `dangerouslySetInnerHTML`, `innerHTML`, `eval`, `document.cookie` or `console` in `src`. Storage stays with its owners (`session.ts` for sessionStorage, `prefs.ts` for localStorage). Markup in feature, tag and run names renders as text and never runs (Q).
- **Long names:** a 100-character project name, 200-character path segments, a 255-character run name and long tags don't scroll the page sideways at 1440 or 390 (Q).
- **Contrast:** `theme.contrast.test.js` passes, and axe passes in both themes.

## Bugs
| ID | Sev | Status | Owner |
|---|---|---|---|
| FE-QA-1 | S4 | Open | frontend-swe |
| FE-QA-2 | S3 | Open | frontend-swe |
| FE-QA-3 | S3 | Open (fails AC-5.1, P0) | frontend-swe, or pm (deviation) |
| FE-QA-4 | S4 | Open | pm |

**FE-QA-1 (S4): intermittent axe failure in the design spec, AC-9.3.**
- **Area:** `e2e/design.spec.ts:233`, "AC-9.3 axe, light theme › login, register and not-found".
- **Steps:** run the full `npx playwright test --project=chromium`.
- **Expected:** 0 violations.
- **Actual:**
  - once in 217 runs: `color-contrast: #react-aria…-_r_r_ > .truncate.flex-1, …` on the theme-menu item labels;
  - 0 failures in 20 repeats and in the final full run.
- **Cause:** axe runs while the menu's 180 ms `data-entering:animate-pop-in` is still playing. `design.spec.ts` doesn't wait for `[data-entering]` to clear, as `reportHarness.ts` already does. This is a test race, not a product contrast defect.
- **Fix:** add `await expect(page.locator('[data-entering]')).toHaveCount(0)` before `analyze()`.

**FE-QA-2 (S3): a malformed invitation link crashes the page.**
- **Area:** `AcceptInvitePage.tsx` (the fragment effect) and `app/router.tsx`. Linked to AC-2.1/D14 and AC-9.2.
- **Steps:** open `/invite#%E0%A4%A` (also `/invite#%` and `/invite#%ZZ`).
- **Expected:** the "This invitation link isn't valid." state, with an h1, and the fragment removed from the address bar.
- **Actual:**
  - TanStack Router's unstyled default screen: "Something went wrong!", and "Show Error" reveals "URI malformed";
  - no h1, no design-system state;
  - the fragment stays in the URL (`http://127.0.0.1:4173/invite#%E0%A4%A`).
- **Causes:**
  1. `decodeURIComponent(fromHash)` throws `URIError` and nothing catches it.
  2. The router has no `errorComponent` or `defaultErrorComponent`, so any unexpected render error anywhere shows the framework's bare screen.
- **Evidence:** a Playwright probe recorded the page text and URL above.
- **Regression test:** the three rows are documented in `e2e/qa/security.qa.spec.ts`, to be added as assertions once fixed.

**FE-QA-3 (S3): tree rows don't show the feature name, so AC-5.1 (P0) fails.**
- **Area:** `src/features/tree/FeatureTree.tsx`, `toUiNodes`.
- **Steps:** open `/projects/{checkout}/features` as Gus.
- **Expected:** AC-5.1: "files … each with its name, tags and testCaseCount".
- **Actual:**
  - the row reads `payment.feature @checkout 1 test case`, and "Payment" appears nowhere in the row (`title` is the path);
  - the tags carry `hidden … sm:inline`, so below 640 px they aren't shown either.
- **Test gap:** `e2e/tree.spec.ts:43` and `buildTree.test.ts` never assert the name.
- **Options:** render `feature.name` in the row, or pm amends AC-5.1 as a deviation.

**FE-QA-4 (S4): AC-8.1 button wording.**
- AC-8.1 says "Show as table"; the UI renders "Show <counts or pass-rate chart> as a table".
- The behaviour is right; only the wording differs. pm should accept it or amend the AC.

**Observations (not bugs; pm may decide):**
- A 500 with a non-Problem body (for example Tomcat HTML) shows "Can't reach the server". This follows AC-9.1's "non-Problem body" row literally, but it reads oddly for a reachable server.
- An unknown future error `code` is treated as no code, so a 403 with one shows "The request was refused".
- Only the open run's summary polls. Its results list doesn't refresh while open; AC-8.7 doesn't require it.

## Mutation testing
**23 mutants** on a /tmp copy (`node_modules` symlinked read-only), each scored against the whole vitest suite. The copy is deleted. **23/23 killed.**

| Mutant | Killed by |
|---|---|
| M1: `next` accepts `//` | existing + qa |
| M2: `next` accepts `/\` | existing + qa |
| M3: no control-character check | existing + qa |
| M4: `next` may loop to `/login` | existing + qa |
| M5: a 403 without code becomes "forbidden" | existing + qa |
| M6: 504 isn't "unavailable" | existing + qa |
| M7: any content type is read as a Problem | existing + qa |
| M8: `requestId` not validated | existing + qa |
| M9: a pointer keeps its leading `/` | existing + qa |
| M10: `currentVersion` accepts any number | qa only |
| M11: columns counted in UTF-16 units | existing + qa |
| M12b: no clamp at line end | existing + qa |
| M13: TESTER ranked as MEMBER | qa |
| M14: a manager may manage their own row | existing + qa |
| M15: ADMIN may assign OWNER | existing + qa |
| M16: `exp == now` kept | qa only |
| M17: ending the session keeps the mirror | existing + qa |
| M18: login's own 401 ends the session | existing only |
| M19: token sent to login and register | existing only |
| M20: closed runs keep polling | existing + qa |
| M21: run list polls in the background | qa only |
| M22: a 500 is retried | qa only (new `queryClient.qa.test.ts`; it survived the existing suite) |
| M23b: logout never clears the query cache | existing |

**Equivalent mutants, replaced by the stronger versions above:**
- **M12:** dropping the loop guard alone is still clamped by `Math.min`.
- **M23:** removing the first `clear()` alone leaves the second, post-navigation `clear()`.

## qa files (new)
**Unit tests (200):**
- `frontend/src/auth/qa/next.qa.test.ts`
- `frontend/src/auth/qa/session.qa.test.ts`
- `frontend/src/auth/qa/roles.qa.test.ts`
- `frontend/src/api/qa/problem.qa.test.ts`
- `frontend/src/features/editor/qa/columns.qa.test.ts`
- `frontend/src/features/runs/qa/polling.qa.test.tsx`
- `frontend/src/app/qa/queryClient.qa.test.ts`

**End to end (58):**
- `frontend/e2e/qa/security.qa.spec.ts` (27)
- `frontend/e2e/qa/dialogs.qa.spec.ts` (16)
- `frontend/e2e/qa/problems.qa.spec.ts` (9)
- `frontend/e2e/qa/robustness.qa.spec.ts` (6)

**Real-stack journey:**
- `frontend/e2e/qa/real/success.qa.journey.ts`: a copy of `e2e/real` with screenshots sent to `$QA_SHOTS_DIR`.
- `frontend/e2e/qa/real/playwright.qa-real.config.ts`. It isn't part of `npm run e2e`.

**Side effects of running the suites (generated, gitignored):** rebuilt `frontend/dist`, `frontend/test-results/`, and new `review-screenshots/axe-report-*.json` from the a11y sweep.

## Fix round 1 re-verification (2026-11-21)

Code under test: HEAD `2844565` plus the working tree (F12–F14). Rules unchanged: Node 24 prefix, writes only under `frontend/**/qa/**`, no production edits, no packages, no git.

### Status
| ID | Sev | Status | Evidence |
|---|---|---|---|
| FE-QA-1 | S4 | **Closed** | `e2e/design.spec.ts:104` now waits for `[data-entering]` to clear. The 6 AC-9.3 axe tests in `design.spec.ts` ran 20× each: **120/120** passed. |
| FE-QA-2 | S3 | **Closed** | 8 malformed fragments: `%E0%A4%A`, `%`, `%ZZ`, `%%`, `%C0%AF`, `%ED%A0%80`, `tok%E2%82`, `%FF%FE`. Each shows "This invitation link isn't valid." with an h1, removes the fragment, sends no API request, leaves no `cucumber.pendingInvite`, shows no Accept form, no "URI malformed" and no page error. A malformed link also clears an older pending invitation. My documented rows in `e2e/qa/security.qa.spec.ts` are now real assertions. |
| FE-QA-3 | S3 | **Closed** | At 1440 and 390 px, rows read "Payment (payment.feature)" and "Address (address.feature)". See the F12 rows below. **AC-5.1 now passes.** |
| FE-QA-4 | S4 | Open | Unchanged; pm decides. |
| **FE-QA-5** | **S3** | **Open (new)** | Error page: focus is lost to `<body>`; details below. Owner: frontend-swe. |

### Adversarial rows
**F12, tree rows (`e2e/qa/fixround1.qa.spec.ts`, both widths, axe clean, no horizontal scroll):**
- Many tags show the first tag plus "+4", visible at 390 px too.
- An empty name and a whitespace-only name both fall back to the file name.
- Unicode names work: Arabic (RTL), 💳, "Zoë", and a tag `@ünï`.
- A 255-character name with a 180-character file name is truncated, and the full path is in `title`.
- Type-ahead follows the name: "quo" focuses "Quokka refunds" (file `zz.feature`), "addr" focuses Address, and Enter opens it.

**F13, error page:** I provoked real render errors on three routes by feeding malformed API data through the FakeBackend:
- the features tree with `tags: null`;
- the runs list with an invalid `createdAt`;
- the upload tokens list with an invalid `createdAt`.

In both themes, each one shows:
- the h1 "Something went wrong", the safe sentence, Retry and Home;
- exactly one h1 and no "Show Error" button;
- no `RangeError`, `TypeError`, "Invalid time value" or stack text anywhere in the DOM;
- a clean axe run.

**FE-QA-5 (S3, open): focus is lost to `<body>` on the error page (D17, AC-9.4).**
- **Steps:**
  1. As Gus, open `/projects/{checkout}/runs`.
  2. Make `GET …/features` return items with `tags: null`.
  3. Click Sidebar › Features.
- **Expected:** focus on the error page's h1, as after any in-app navigation. The control case of a normal Features → Runs navigation does focus the Runs h1.
- **Actual:** `document.activeElement` is `<body>` at 100 ms, 1 s and 3 s, even though the h1 has `tabindex=-1`. The same happens on the runs and tokens routes.
- **Likely cause (reading `Shell.tsx`):** the shell focuses the route's own h1 as it renders. The error boundary then replaces that subtree, the focused element is removed, and nothing focuses again.
- **Test:** the repro is documented in `fixround1.qa.spec.ts`, with the `toBeFocused()` assertion to restore once fixed.
- **Note:** on a full page load, focus stays on `<body>` for normal pages too. That's by design, not part of this bug.

### Mutants: 7 of 7 killed
Run on a /tmp copy (since deleted), against the full vitest suite plus `e2e/qa/fixround1.qa.spec.ts`, `e2e/qa/security.qa.spec.ts` and `e2e/tree.spec.ts`.

| Mutant | Change | Killed by |
|---|---|---|
| F12a | name ignored | vitest 1, e2e 5 |
| F12b | blank name not trimmed | vitest 1 |
| F12c | "+N" tags dropped | e2e 2 (qa only) |
| F12d | row text value without the file name | e2e 6 (qa only) |
| F13a | fragment decode not guarded | vitest 3, e2e 9 |
| F13b | fragment kept on a bad link | vitest 3, e2e 8 |
| F13c | no `defaultErrorComponent` | vitest 1, e2e 6 |

### Full suite, run once
| Check | Result |
|---|---|
| `npm run lint` | exit 0 |
| `npm run typecheck` | exit 0 |
| `npm test` | gen:api:check matches; vitest **645/0/0** |
| `npx playwright test --project=chromium` | **275/0/0**: 199 existing + 76 qa (58 earlier, 9 new FE-QA-2 rows in `security.qa.spec.ts`, 9 in `fixround1.qa.spec.ts`) |

Nothing is left listening on 4173, and no qa containers remain.

### Updated verdict: **Signed off with known issues**
- All 52 ACs pass, including every P0 (AC-5.1 now passes).
- No S1 or S2 bug is open.
- **Known issues:**
  - FE-QA-5 (S3): focus on the error page, frontend-swe;
  - FE-QA-4 (S4): AC-8.1 wording, pm;
  - the 3 observations from the main report.

## FE-QA-5 re-verification (2026-11-21)

Code under test: HEAD `2844565` plus the working tree. The fix adds `shouldRecoverFocus()` to `src/app/Shell.tsx`, and `src/app/ErrorPage.tsx` focuses its h1 on mount when that's true. Rules unchanged: Node 24 prefix, writes only under `frontend/**/qa/**`, no production edits, no packages, no git.

### FE-QA-5 (S3): **Closed**
The `toBeFocused()` assertion is back in `e2e/qa/fixround1.qa.spec.ts`. The repro passes on all three routes, in both themes, after an in-app navigation:
- features tree with `tags: null`;
- runs list with an invalid `createdAt`;
- upload tokens with an invalid `createdAt`.

Each run shows the error page's h1 focused, safe text only, no stack or error text, and a clean axe run.

### Adversarial rows (`fixround1.qa.spec.ts`, "FE-QA-5 focus edge cases"): 4 of 4 pass
| Row | Result |
|---|---|
| The user moves focus away before the error | With a 1.5 s delayed failing response, focus moved to the top bar's Theme button stays there; the error page doesn't steal it. |
| A full page load straight to a failing route | Focus stays on `<body>`, as designed for any full load. |
| A second navigation after the error page | Sidebar › Settings focuses the new page's h1. |
| Retry that fails again | A fresh error page, with focus on its h1, never `<body>`. |

### New: FE-QA-6 (S4), open
**Retry on the error page doesn't recover once the data is good again.**
- **Owner:** frontend-swe.
- **Steps:**
  1. As Gus, go from Features to Runs while `GET …/runs` returns an invalid `createdAt`, so the error page shows.
  2. Make the server answer correctly.
  3. Press Retry.
- **Expected:** the Runs page.
- **Actual:** the error page again. A probe counted 1 request before Retry and still 1 after it: Retry re-renders from the cached bad data and sends no request. It recovers only when the query goes stale (30 s), the window refocuses, or the user navigates.
- **Test:** the repro is documented in `fixround1.qa.spec.ts`; I'll assert it once fixed.

### Mutants (/tmp copy, since deleted): 3 of 3 killed
| Mutant | Change | Killed by |
|---|---|---|
| R1 | `shouldRecoverFocus` ignores full page loads | `ErrorPage.focus.test.tsx` and my full-load row |
| R2 | `shouldRecoverFocus` steals focus the user moved | `ErrorPage.focus.test.tsx` and my tab-away row |
| R3 | `ErrorPage` never focuses | `ErrorPage.focus.test.tsx` and 8 qa e2e rows |

### Full suite, run once
| Check | Result |
|---|---|
| `npm run lint` | exit 0 |
| `npm run typecheck` | exit 0 |
| `npm test` | gen:api:check matches; vitest **648/0/0** |
| `npx playwright test --project=chromium` | **280/0/0**, 80 of them qa |

No listener left on 4173, no qa containers.

### Final verdict: **Signed off with known issues**
- All 52 ACs pass, including every P0 (25/25).
- No S1, S2 or S3 bug is open: FE-QA-1, FE-QA-2, FE-QA-3 and FE-QA-5 are closed.
- **Known issues:**
  - FE-QA-4 (S4): AC-8.1 wording, pm;
  - FE-QA-6 (S4): Retry on the error page doesn't refetch, frontend-swe;
  - the 3 observations from the main report.
