# scenario-service phase 1: Gherkin features, versions, scenarios, test cases and tags
Status: Ready (approved by the user on 2026-10-14: Q1 to Q10 as recommended, SE1–SE8, GE1–GE3, X1). **Gate:** T1 (contract, docs only) may start now. T2 onward starts only after auth-service phase 4 (`docs/specs/auth-service-persistence.md`) has passed its last review point (E), so every item of its "Database pattern for every service" is proven before it's copied here.
Last updated: 2026-10-14 (aligned with `auth-service-persistence`'s database pattern; Testcontainers decision; D19 after the T1 contract review)

## Problem and goal

The platform manages BDD content, but nothing can store it. At HEAD `6fd4434`:

- `scenario-service` is an empty Spring Boot app: one main class, one `contextLoads` test that fails because there is no datasource, and a `build.gradle` that declares Elasticsearch, JPA, a REST client, security, validation and the PostgreSQL driver, none of them used.
- The data model for Gherkin content was designed and approved on 2026-10-12 (ERD-D1 to ERD-D11: `features`, `feature_versions`, `rules`, `scenarios`, `steps`, `examples`, `example_rows`, `tags`, `tag_assignments`), but nothing is built.
- auth-service now offers the project access check C5 (`GET /internal/projects/{projectId}/access`, contract 1.2.1, accepted 2026-10-14), so a content service can ask "which role does this user have in project P?". No consumer exists yet. Follow-up FU-16 (verify the consumer rules in the first consumer) is open.
- The gateway has a provisional placeholder route `/api/scenarios/**` for this service and reserves `/api/projects/**` for project-scoped content services. The final paths aren't decided.
- report-service will store results against feature, scenario and example-row ids (ERD-D2, ERD-D5). It needs those ids to exist and to stay stable across edits.

**Goal of phase 1:** a project's members can write Gherkin features as text, and the platform validates, parses, versions and structures them. Concretely:
- Create, list, view, edit and archive features in a project. The Gherkin text is the source of truth (ERD-D4).
- Every save is validated by the official Gherkin parser. Errors come back with line and column, and nothing invalid is stored.
- Every saved change is an immutable version. Two people editing at once can't overwrite each other silently.
- Scenarios, Scenario Outlines with their Examples, steps with data tables and doc strings, and tags are available as structured data. Scenario and example-row ids stay stable across edits, so results can point at them later.
- Test cases (a Scenario, or one Examples row of a Scenario Outline) are derived exactly as Cucumber would run them.
- Every request is authorised through auth-service's C5 access check, and the service fails closed.
- scenario-service is built on PostgreSQL and Flyway from the first commit, with the same layering, error format and logging as auth-service.

**Success:** through the gateway on `http://localhost:8080`, a project `MEMBER` creates `features/checkout/payment.feature` from Gherkin text, sees its scenarios and its 4 derived test cases, edits it into version 2 without losing scenario ids, and gets a precise parse error for broken text. A project `READER` can read but not write. Stopping auth-service makes every scenario-service call fail with 503, never with data. Every P0 acceptance criterion passes on PostgreSQL.

## Users and roles affected

| Persona | Need |
|---|---|
| Project `MEMBER` and `ADMIN` (business analyst, developer, test lead) | Write and change features as Gherkin text, get immediate parse feedback, never lose a colleague's edit, archive features that are obsolete |
| Project `TESTER` and `READER` | Read features, scenarios, test cases, tags and history |
| Organization member without access to the project; user of another organization | See nothing of the project's content |
| report-service developer (later) | Stable feature, scenario and example-row ids, and a defined test-case shape |
| Frontend developer (not in the repo) | One written contract, one error format, and parse errors with line and column for an editor |
| Operator | Correlated, safe logs (request ID end to end), and a service that fails closed when auth-service is down |
| The user, as code owner | No change to existing files without approval; thin controllers; no unused dependencies |

**Roles.** No new roles. Project roles `ADMIN` > `MEMBER` > `TESTER` > `READER` (ranks 4 to 1) come from auth-service through C5, as **effective** roles: organization `OWNER` and `ADMIN` are implicit project `ADMIN` (auth-service-projects D3). scenario-service never reads organization roles. What each role may do here is decided in D3.

## Glossary

The auth-service glossaries apply where they are referenced: Problem, "a Problem S / C" (status S and `code` C), `{unknown id}`, Role rank, Effective project role, Access check (C5). New or changed terms:

| Term | Meaning in this spec |
|---|---|
| Feature | One Gherkin file in a project, identified by an id and a path. Its content is the Gherkin text of its current version. |
| Path | The feature's file path inside the project, for example `features/checkout/payment.feature`. It replaces folders (ERD-D10). Rules in D10. |
| Version | An immutable snapshot of a feature's full Gherkin text, numbered 1, 2, 3, … per feature. Every saved change of the text creates one (ERD-D3). |
| Current version | The feature's newest version. The structured data (rules, scenarios, steps, examples, tags) always reflects it. |
| Base version | The version number a client edited. A save names it, and the save is refused if the feature has moved on (D9). |
| Valid Gherkin | Text that the official Gherkin parser accepts without error and that contains a `Feature` (D8). |
| Parse error | One error from the parser or the "no Feature" rule, reported with `line` (1-based), `column` (1-based) and a `message` (D8). |
| Scenario (stored) | A Background, Scenario (or Example) or Scenario Outline (or Scenario Template) element of the current version, with its id. Backgrounds are stored as scenarios of kind `BACKGROUND` (ERD). |
| Test case | Derived, never stored (ERD-D5): a Scenario, or one row of an Examples table of a Scenario Outline. Its id is the scenario's id or the example row's id. Its content follows Cucumber's pickle rules (D13). A Background is never a test case on its own. |
| Tag | A Gherkin tag such as `@smoke`, written in the text on a Feature, Rule, Scenario, Scenario Outline or Examples. Stored with the leading `@`, compared case-sensitively (ERD-D7). |
| Effective tags | The tags a test case carries: those of its Feature, Rule (if any), Scenario or Scenario Outline, and Examples block (if any), as Cucumber computes them. |
| Active feature, archived feature | A feature with no `archivedAt`, or with one. Archived features are read-only and hidden from default lists (D11). |
| Archived project | A project for which C5 answers `archived: true`. Its content is read-only (auth-service-projects D5). |
| Access check stub | In automated tests, an in-process HTTP server that stands in for auth-service's C5 endpoint and answers according to the Scenario fixture. |
| Scenario fixture | The test data for this spec, defined below. |
| `{token of X}` | A JWT signed with HS512 and the test secret, `exp` 1 hour after issue, `sub` = X's user id, and `email` = X's email. |
| `{checkout-features}` | `/api/projects/{checkout}/features`. So `{checkout-features}/{payment}/versions` is `/api/projects/{checkout}/features/{payment}/versions`. |
| `{payment}` | The id of the Payment feature after "Gus has created Payment". |
| "Gus has created Payment" | Gus sent `POST {checkout-features}` with path `features/checkout/payment.feature`, the Payment source and message `First draft`, and got 201. The feature is at version 1. |
| "nothing is stored" | The number of features, versions and tags in every fixture project is the same as before the request. |
| Direct call | A request to scenario-service itself (port 8082, or MockMvc in tests), not through the gateway. Every scenario in this spec is a direct call unless it says "through the gateway". |

### Scenario fixture

Users (as in auth-service-projects' Projects fixture): Ana, Cai, Gus, Hal, Dan, Ivy, Eve, each with a fixed user id and the email `<name>@example.com`.

Projects (fixed ids): Checkout `{checkout}`, Billing `{billing}`, Legacy `{legacy}`. Legacy is archived. Any other project id is unknown.

The access check stub answers `GET /internal/projects/{id}/access` as follows. "none" means 403 `FORBIDDEN` (the same answer auth-service gives for no access and for an unknown project).

| User | Checkout | Billing | Legacy (archived) | Any other id |
|---|---|---|---|---|
| Ana | `ADMIN` | `ADMIN` | `ADMIN` | none |
| Cai | `ADMIN` | none | none | none |
| Gus | `MEMBER` | none | `MEMBER` | none |
| Hal | `TESTER` | none | none | none |
| Dan | `READER` | none | `READER` | none |
| Ivy | none | none | none | none |
| Eve | none | none | none | none |

A 200 answer is exactly `{"projectId":"<id>","orgId":"<acme id>","role":"<role>","archived":<true for Legacy, else false>}`.

### Gherkin sources used in the ACs

Indentation is two spaces per level. Line numbers matter for the error ACs.

**Payment source** (in Checkout):

```text
@checkout
Feature: Payment
  Customers pay for their basket.

  Background:
    Given a basket with these items:
      | item  | price |
      | Mug   | 8.00  |
      | Shirt | 20.00 |

  @smoke
  Scenario: Pay by card
    When the customer pays by card
    Then the receipt is:
      """text
      Total: 28.00
      """

  Rule: Vouchers

    Scenario Outline: Pay with a voucher
      When the customer pays with voucher "<code>"
      Then the result is "<result>"

      @regression
      Examples: Valid vouchers
        | code   | result    |
        | SAVE10 | confirmed |
        | SAVE20 | confirmed |

      Examples: Expired vouchers
        | code | result   |
        | OLD5 | rejected |
```

**Payment v2 source:** the Payment source with these changes only:
- In "Pay by card", the line `When the customer pays by card` becomes `When the customer pays by credit card`.
- A new scenario is added after "Pay by card" and before the Rule:
  ```text
    Scenario: Pay by bank transfer
      When the customer pays by bank transfer
      Then the order waits for payment
  ```

**Address source** (in Checkout, path `features/Address.feature`):

```text
@address @smoke
Feature: Address
  Scenario: Add an address
    When the customer adds an address
    Then the address is saved
```

**Invoice source** (in Billing, path `features/invoice.feature`):

```text
@billing
Feature: Invoice
  Scenario: Send an invoice
    Given an order
    Then an invoice is sent
```

**Greeting source** (placeholders everywhere):

```text
Feature: Greeting
  Scenario Outline: Greet <name>
    Given the user "<name>"
    Then the greeting is:
      """
      Hello, <name>!
      """

    Examples:
      | name |
      | Ana  |
```

**Broken sources** (each is invalid; "line:column" is where the error is reported):

| Name | Text | Error at |
|---|---|---|
| step before Feature | `Given a step before the feature` / `Feature: Late` (two lines) | 1:1 |
| inconsistent cells | `Feature: Table` / `  Scenario: Rows` / `    Given a table` / `      \| a \| b \|` / `      \| 1 \|` (five lines) | 5:7 |
| step after Examples | `Feature: Outline` / `  Scenario Outline: O` / `    Given <x>` / `    Examples:` / `      \| x \|` / `      \| 1 \|` / `    Then a late step` (seven lines) | 7:5 |
| unknown language | `# language: xx` / `Feature: Unknown` (two lines) | 1:1 |
| no Feature | `# only a comment` (one line) | 1:1 |

## Scope

### In scope
- **Foundation:** PostgreSQL and Flyway from the first commit (D14). JWT validation in the service (D6). C5 access checks on every request, failing closed (D4, D5). auth-service's error format (D16), layering (PL) and logging pattern (D17), copied.
- **Features:** create from Gherkin text, list, view (text and structure), save a new version, change the path, archive, restore (US-1, US-2, US-3, US-6).
- **Versions:** an immutable history; list it and read any version's text (US-3).
- **Structure:** Rules, Backgrounds, Scenarios, Scenario Outlines, Examples, steps with data tables and doc strings, tags. Parsed on every save from the text, with stable scenario and example-row ids (D9).
- **Test cases:** derived per feature, following Cucumber's pickle rules (US-4, D13).
- **Tags:** the project's tag catalogue, and filtering the feature list by one tag (US-5, D12).
- **Gateway route** for the final paths (GE1 to GE3, D2), with the user's approval.
- **Contract** `docs/api/scenario-service.openapi.yaml` 1.0.0, a backend-swe task (T1, T14).
- **ERD:** `docs/erd/cucumber.dbml`, `scenario_service` group, changes marked `proposed (scenario-service)` (done with this spec).
- **Build clean-up:** remove the unused Elasticsearch dependency (SE1).

### Out of scope (later phases or specs)
- **Search** (full text over features and steps), **Elasticsearch**, and Cucumber **tag expressions** (`@smoke and not @wip`). The list filter takes one tag.
- **Living documentation**, published snapshots (`doc_releases`), and the latest result per scenario. They need report-service.
- **Folders** as entities, folder rename or move as one operation, and a tree endpoint. The UI derives folders from paths (ERD-D10).
- **Import and export** (zip, Git sync, `.feature` file upload), and the link from CI results to features.
- **Diffs** between versions (the client can diff two version texts), "restore an old version" as one operation (the client saves the old text as a new version), and version messages edited after the fact.
- **Hard deletion** of features or versions. Results in report-service will point at feature ids (ERD-D10).
- **Free-standing manual test cases** not written in Gherkin (ERD-D5).
- **A step definition library**, step autocomplete, and a "validate without saving" endpoint (the save itself reports errors).
- **Real-time collaboration** and locks held while someone is editing. Optimistic concurrency only (D9).
- **Pagination** (A5) and per-plan limits.
- **Audit log**, events and messaging. No event is emitted when a feature changes. report-service reads by id when it needs to.
- **Actuator and health endpoints** (they come with the deployment or Helm spec; D18).
- **Service-to-service calls without a user** (for example report-service reading scenarios for a CI upload). Decided with the result-ingestion spec.
- Any change to auth-service or report-service. Removing the `/api/scenarios/**` placeholder route from the gateway (it is used by 18 gateway test files; D2).

## Code facts verified (HEAD `6fd4434`, read on 2026-10-14)

pm read the files below. pm has no shell in this round, so **no build or test was run**. Test results come from the existing report under `scenario-service/build/`.

### scenario-service

| Item | Result |
|---|---|
| `src/main/java/org/scenarioservice/ScenarioServiceApplication.java` | `@SpringBootApplication` and `main` only. Package `org.scenarioservice`. |
| `src/test/java/org/scenarioservice/ScenarioServiceApplicationTests.java` | `@SpringBootTest`, one empty `contextLoads()`. |
| Last test result | `build/test-results/test/TEST-org.scenarioservice.ScenarioServiceApplicationTests.xml` (2026-10-11T12:22:37Z): 1 test, **1 failure**, `Failed to load ApplicationContext` caused by `Failed to determine a suitable driver class` (no datasource URL). So `./gradlew build` is red today. |
| `src/main/resources/application.properties` | One line: `spring.application.name=scenario-service`. No port; Boot's default 8080 would clash with the gateway. |
| `build.gradle` | Plugins Spring Boot `4.1.0` and dependency-management `1.1.7`; `group = 'org'`; Java toolchain 17. Dependencies, lines 22–40: `spring-boot-starter-data-elasticsearch` (22), `-data-jpa` (23), `-restclient` (24), `-security` (25), `-validation` (26), `-webmvc` (27); Lombok (28, 31, 38, 40); devtools (29); `org.postgresql:postgresql` runtime (30); test starters `-data-elasticsearch-test` (32), `-data-jpa-test` (33), `-restclient-test` (34), `-security-test` (35), `-validation-test` (36), `-webmvc-test` (37); JUnit platform launcher (39). No Flyway, AspectJ, JWT or Gherkin library. |
| Other files | `settings.gradle` (`rootProject.name = 'scenario-service'`), Gradle wrapper 9.5.1, `gradlew`, `gradlew.bat`, `.gitattributes`, `.gitignore`. No README, no `db/migration`, no other class. |

### Dependencies (verified in local caches; Maven Central itself could not be queried in this round)

| Artefact | Evidence |
|---|---|
| Boot 4.1.0 BOM | `~/.gradle/caches/.../spring-boot-dependencies-4.1.0.pom`: `flyway.version` 12.4.0 (line 58); `spring-boot-starter-flyway` (2359); `flyway-database-postgresql` (563); `spring-boot-starter-aspectj` (2114); `spring-boot-starter-actuator` (2084); `postgresql.version` 42.7.11 (165); `testcontainers.version` 2.0.5 (210); `jackson-bom.version` 3.1.4 (83). |
| `io.cucumber:gherkin` | `~/.m2/repository/io/cucumber/gherkin/28.0.0/` has the jar and pom, downloaded from Maven Central (`_remote.repositories`, 2026-04-28). The pom depends on `io.cucumber:messages` with the **version range** `[19.1.4,25.0.0)`, and its other dependencies are test-scoped. No other gherkin version is cached, and no gherkin `maven-metadata` is cached, so **the newest release on Central is not verified**. |
| `io.cucumber:messages` | `~/.m2/.../messages/24.1.0/` has the jar; its non-test dependencies: none. `cucumber-bom-7.18.1.pom` pairs gherkin 28.0.0 with messages 24.1.0 (lines 18, 21). The cached Central metadata for `messages` (dated 2026-04-13) lists releases up to 32.3.1, so newer gherkin majors very likely exist. |
| jjwt | auth-service (`build.gradle` 27–29) and api-gateway (39–41) both use `io.jsonwebtoken:jjwt-api` / `-impl` / `-jackson` **0.12.5**. |
| Gradle cache | No `io.cucumber` artefact in `~/.gradle/caches/modules-2`. |

### auth-service patterns to copy (G9: copied, never shared)

| Item | Where |
|---|---|
| JWT validation | `service/impl/JwtTokenProvider.java`: one `JwtParser` with `verifyWith(key)`; rejects any algorithm other than HS512 (line 74), a missing `exp` (78) or `sub` (81); parses `sub` with `UUID.fromString` (84), which is lenient (BUG-1 / FU-10). The secret is standard Base64 of at least 64 bytes, and startup fails otherwise (88–103). The gateway's `security/JwtTokenValidator.java` is the same, without a user lookup. |
| Error handling | `exception/ApiException` (an `ErrorCode` and a detail), `ErrorCode`, `Problems` (Problem completion, `requestId` echo with `^[A-Za-z0-9._-]{1,128}$`), `ApiExceptionHandler extends ResponseEntityExceptionHandler`, `security/ProblemAuthenticationEntryPoint`, `ProblemAccessDeniedHandler`. Error table in `docs/api/auth-service.openapi.yaml` lines 173–229. |
| Logging (auth-service-projects D17, E55) | `aop/Logging.java` (`@Order(HIGHEST_PRECEDENCE)`, pointcut on `@Service` classes in `service..`, one outcome line, business errors at INFO, never arguments or messages); `configuration/RequestIdMdcFilter` (order `SecurityFilterProperties.DEFAULT_FILTER_ORDER - 10`); `UserIdMdcFilter` (`+ 10`); `LoggingProperties` (`app.logging.enabled`, `slow-call-threshold` 500ms); `application.yaml` `logging.pattern.correlation: "[requestId=%X{requestId:--} userId=%X{userId:--}] "`. |
| C5 | `docs/api/auth-service.openapi.yaml` 1.2.1, lines 1580–1644 and schema `ProjectAccess` (2035–2055): 200 with exactly `projectId`, `orgId`, `role`, `archived`; 401, 403, 400; consumer rules (rank comparison, no writes when archived, fail closed, no cache unless ≤ 30 s accepted). Base URL `AUTH_SERVICE_URL`, `http://localhost:8081` locally. A suspended or deleted user's token gets 401, because auth-service's filter loads the user. |

### Gateway

| Item | Result |
|---|---|
| `api-gateway/src/main/resources/application.yaml` | Route `scenarios`: `Path=/api/scenarios/**` → `${gateway.downstream.scenario-service-url}` (default `http://localhost:8082`, env `SCENARIO_SERVICE_URL`), comment "Provisional placeholder" (lines 38–44). CORS allowed headers: `Authorization,Content-Type,Accept,X-Request-Id` (line 104), so a browser can't send `If-Match` cross-origin today. |
| Tests using the placeholder | `/api/scenarios/**` and `/api/projects/**` occur 87 times in 20 gateway test files. `/api/scenarios/42` is the standard stub path for downstream-failure tests in backend-swe and qa files (for example `QaDownstreamResponseFailureTest`, 27 occurrences; `QaContractConformanceTest:39`). |
| Unrouted example | `/api/projects/1` (with and without a query string) in 7 files: `RoutingTest:143`, `GatewayErrorResponseTest`, `JwtAuthenticationTest:225`, `QaUnknownPathsTest:26`, `QaAccessLogTest:46`, `QaCorsAdversarialTest:132`, `QaLeakageTest:52,144`. No test uses a deeper `/api/projects/...` path. |
| Specs | Gateway spec D2: "each prefix maps to exactly one service, and prefixes must not overlap"; placeholders may be renamed without a breaking change (A8); `/api/projects/**` reserved for content services, `/internal/**` never routed (2026-10-13 note). Port 8082 reserved for scenario-service (D3). |

## Decisions (recommended defaults)

Each decision has a recommended default and a reason. Items that change existing files are listed under "Changes to existing code", for the user's approval. Blocking questions Q1 to Q10 summarise what needs an answer.

### D1. Phase scope and slug
**Default:** slug `scenario-service`, phase 1 = the In scope list. 8 stories, 83 acceptance criteria (21 P0, 42 P1, 20 P2; 12 of them added by D19 and its follow-ups after T1), three batches with review points.
**Why:** it's the smallest set that makes the service useful: a team can keep its features here, see their scenarios and test cases, and report-service can later refer to stable ids. Search, living documentation and import are valuable, but each needs this core first.

### D2. External paths and the gateway route
**Default:**
- Every operation is project-scoped, under `/api/projects/{projectId}/...`, as auth-service-projects D2 reserved:

  | Operation | Method and path |
  |---|---|
  | List features | `GET /api/projects/{projectId}/features` (query: `archived`, `tag`) |
  | Create a feature | `POST /api/projects/{projectId}/features` |
  | View a feature | `GET /api/projects/{projectId}/features/{featureId}` |
  | Save a new version (and change the path) | `PUT /api/projects/{projectId}/features/{featureId}` |
  | Archive, restore | `POST /api/projects/{projectId}/features/{featureId}/archive`, `.../restore` |
  | Version history | `GET .../features/{featureId}/versions` |
  | One version's text | `GET .../features/{featureId}/versions/{version}` |
  | Test cases | `GET .../features/{featureId}/test-cases` |
  | Tag catalogue | `GET /api/projects/{projectId}/tags` |

- **Gateway (GE1, needs approval):** add three path patterns to the existing `scenarios` route, `/api/projects/*/features`, `/api/projects/*/features/**` and `/api/projects/*/tags`. The route keeps its id, target and the `/api/scenarios/**` pattern. No other route changes.
- **Keep `/api/scenarios/**` for now.** scenario-service doesn't serve it, so a request there reaches scenario-service and gets its 404 `NOT_FOUND`. Removing it would break 18 gateway test files, qa's included, for no user benefit. A later gateway clean-up can retire it together with those tests.
- `/api/projects/1`, `/api/projects/{id}` and any other `/api/projects/**` path that isn't listed stay unrouted (the gateway's 404 with a token). The 7 files that use `/api/projects/1` stay valid. report-service later adds its own non-overlapping patterns, for example `/api/projects/*/runs/**`.
- Docs: gateway contract 0.2.3 (GE2), a gateway routing test (GE3), and the gateway spec's D2 table (pm task X1).

**Why:** project-scoped paths make the access check natural (the project id is in every path) and match the reservation. Adding patterns to the existing route keeps "one prefix, one service" and needs no change to any existing test.

**Alternative (not recommended):** replace `/api/scenarios/**` with the new patterns. That's cleaner, but about 80 occurrences in 18 gateway test files (11 of them qa's) would change.

### D3. Who can do what
**Default:**

| Operation | Minimum effective project role | On an archived project |
|---|---|---|
| List and view features; versions; test cases; tags | `READER` | Allowed |
| Create; save a new version or change the path; archive; restore | `MEMBER` | 409 `PROJECT_ARCHIVED` |

- `TESTER` has the same rights as `READER` here. Recording manual results is report-service's job (auth-service-projects D3).
- `ADMIN` has no extra rights in scenario-service in phase 1.
- No access (C5 403) and an unknown project get the same 403 `FORBIDDEN`, so project ids can't be probed.

**Why:** it's exactly the role meaning auth-service-projects D3 published ("`MEMBER` also edits features"). Archive is reversible and is part of editing, so it needs no admin. **Alternative:** archive and restore for `ADMIN` only (one row changes in AC-6.6).

### D4. Using the access check (C5)
**Default:**
- **One C5 call per request** to a project-scoped operation, made with the caller's `Authorization` header forwarded unchanged, directly to auth-service's base URL (`AUTH_SERVICE_URL`, default `http://localhost:8081`), never through the gateway. The request's `X-Request-Id` (the MDC value; see D17) is forwarded too, so auth-service's logs correlate.
- **No caching** of answers, not even for a few seconds. A removal or demotion is effective on the next request (AC-7.5). This closes auth-service follow-up FU-16 for this consumer.
- **Timeouts:** connect 1 s, read 2 s (`app.auth-service.connect-timeout`, `app.auth-service.read-timeout`). **No retries.** The gateway's 10 s response timeout leaves room.
- **Mapping of C5's answer:**

  | C5 answer | scenario-service answer |
  |---|---|
  | 200 with a known role and a boolean `archived`, `projectId` equal to the requested id | Proceed; compare the role by rank (`ADMIN` 4, `MEMBER` 3, `TESTER` 2, `READER` 1) |
  | 403 | 403 `FORBIDDEN` |
  | 401 (token rejected by auth-service, for example a deleted or suspended user) | 401 `UNAUTHENTICATED` with `WWW-Authenticate: Bearer` |
  | Connection failure, timeout, any 5xx, any other status, an unreadable body, an unknown role, a mismatched `projectId` | **503 `ACCESS_CHECK_UNAVAILABLE`** (fail closed). The request changes nothing. One WARN line with the failure type, no stack trace, no URL or token. |

- **Before calling C5** scenario-service has already rejected a malformed project id (400) and an invalid token (401), so C5 is never called with a non-canonical id. auth-service's lenient ids (FU-10) therefore can't create two spellings of one project here. FU-10 must still land before scenario-service goes to production (the gate recorded in auth-service-projects).

**Why:** these are C5's published consumer rules. 503 rather than 403 for an unavailable check is honest: the user isn't denied, the platform is degraded, and clients may retry. Short timeouts keep a slow auth-service from tying up request threads.

### D5. Authorization pattern
**Default:** a dedicated **access checker** component in `security`, called by each service use case as its first step.
- It reads the caller's token from the security context, calls C5 (D4), and either returns the answer (role, archived) or throws a typed `ApiException`: `FORBIDDEN` (no access or role too low), `UNAUTHENTICATED`, `ACCESS_CHECK_UNAVAILABLE`, or `PROJECT_ARCHIVED` for a write on an archived project.
- Two entry points: "require read" (`READER` or higher) and "require write" (`MEMBER` or higher, project not archived).
- **The access check runs before any database transaction opens**, so no database connection is held during the remote call (P13).
- Controllers carry no `@PreAuthorize` for project access. Every endpoint is still authenticated by the security chain (deny by default).

**Why not `@PreAuthorize` with a guard, as auth-service does?** auth-service's guards answer from its own database and return true or false (403). Here the answer is remote, it can be "unavailable" (503), and `archived` must become a 409 business error, not a 403. A guard would need a second call, or a hidden per-request store, to give the service the `archived` flag. One explicit step in the service is simpler, and it's testable without HTTP. **Alternative:** a `@PreAuthorize("@projectAccess.canRead(#projectId)")` guard that memoises the C5 answer in a request attribute for the service. It works, but it hides state between two layers.

### D6. Authentication in scenario-service
**Default:** copy auth-service's validation rules into scenario-service's own classes (G9: no shared library).
- jjwt **0.12.5** (the version auth-service and the gateway use).
- The secret comes from `JWT_SECRET` (`app.jwtSecret`, the auth-service setting name). It must be standard Base64 of at least 64 bytes. There is **no default**: startup fails without it. The `dev` profile carries the same DEV ONLY value as auth-service's and the gateway's `application-dev.yaml`.
- A token is valid when it's signed with that secret, its algorithm is exactly HS512, `exp` is present and in the future, and `sub` is a **canonical** UUID (8-4-4-4-12 hexadecimal digits). This is stricter than auth-service's `UUID.fromString`, on purpose (FU-10).
- scenario-service has no user table, so it doesn't look the user up. Account existence and suspension are enforced by C5 (401, D4).
- The principal holds the user id and the raw token, which only the access checker reads. The `X-User-Id` header and the `email` and `orgs` claims are never used.
- The 401 is a Problem `UNAUTHENTICATED` with `WWW-Authenticate: Bearer`, from an authentication entry point, as in auth-service.

**Why:** the gateway spec (D4) requires services to keep validating tokens, and a service reachable on its own port must not trust headers. Copying a design qa has already verified twice is lower risk than a new one.

### D7. Gherkin parser
**Default:** the official Cucumber parser, **`io.cucumber:gherkin` 28.0.0**, with **`io.cucumber:messages` 24.1.0 declared explicitly** (SE5).
- **Verified (2026-10-14):** both jars are on Maven Central (local Maven repository, downloaded 2026-04-28). cucumber-bom 7.18.1 uses exactly this pair, and neither has non-test dependencies, so there's no Jackson or other transitive conflict with Boot 4.1.
- **Why declare `messages`:** gherkin 28.0.0 depends on `messages` through the version range `[19.1.4,25.0.0)`. A range makes the build resolve differently over time. Pinning 24.1.0 makes it reproducible.
- **Not verified:** the newest gherkin release. Central's `messages` metadata cached on 2026-04-13 already lists 32.3.1, so gherkin has very likely moved past 28. pm couldn't query Central in this round. See Q5: either approve 28.0.0 now (recommended: it's the only version verified, and it supports everything this spec uses: Rules, all dialects and pickles), or have backend-swe verify the newest release in T2 and come back for approval before using it.
- The parser is used through one adapter in the `gherkin` package. No other class imports `io.cucumber.*` (P14). A later version bump then touches one package.

**Why:** the official parser is what Cucumber itself uses, so "valid here" means "runs in Cucumber". It covers every dialect (`# language:`), Rules and the pickle compiler that defines test cases (D13). A hand-written parser would drift from Cucumber.

### D8. Valid Gherkin, what is stored and what is derived
**Default:**
- **Valid** = the parser reports no error **and** the document has a Feature. Anything the parser accepts is accepted, including an empty Feature name, a Feature without scenarios, and any dialect. The "no Feature" rule is scenario-service's own, because a feature row needs one.
- **Parse errors** → 422 `GHERKIN_INVALID`. The Problem has a member `parseErrors`: an array of `{line, column, message}`, one entry per error the parser reports, sorted by line, then column. `line` and `column` are 1-based. A column the parser reports as 0 (for example at end of file) is reported as 1. The `message` is the parser's text. **Nothing is stored.** The member is named `parseErrors`, not `errors`, because `errors` already means field errors in `VALIDATION_FAILED` Problems.
- **Request limits** (400 `VALIDATION_FAILED`, an `errors` entry for the field). Every length counts **code points**, as the database pattern's item 18 requires (auth-service-persistence D11 rule T3):
  - `source`: required, not empty, at most 1,000,000 code points;
  - `message`: optional, at most 500 code points; blank counts as no message; no control character (pattern item 18, rule T1);
  - `path`: D10;
  - in every string field: no U+0000 and no lone surrogate. PostgreSQL rejects both, so they'd otherwise become a 500 (the auth-service FU-3 lesson).
  - A JSON number or boolean where a string is expected is 400 `MALFORMED_REQUEST` (pattern item 18, persistence D12).
- **Named deviation from pattern item 18 (rule T1) for `source`:** a Gherkin file legitimately contains tabs, line feeds and carriage returns, and may contain other control characters in doc strings. So `source` rejects only U+0000 and lone surrogates (what PostgreSQL can't store), not every control character. `path` and `message` follow T1 fully.
- **Stored:** the text of every version, **exactly as sent** (line endings, trailing spaces and all), and its SHA-256 (of the UTF-8 bytes). Feature metadata derived from the current version: name, description, language. The parsed structure of the current version only: rules, scenarios (including backgrounds), steps with their single argument, examples, example rows, tags and tag assignments (ERD-D3, ERD-D6, ERD-D7).
- **Derived on read, never stored:** test cases (D13), effective tags, outline placeholder substitution.
- Keywords are stored as written, without trailing whitespace (`Given`, `Soit`, `*`), plus their normalised type (`GIVEN`, `WHEN`, `THEN`, `AND`, `BUT`, `ASTERISK`) taken from the dialect. Descriptions are stored as the parser reports them. ACs compare them after trimming.

**Why:** ERD-D4 makes the text the source of truth. So every Cucumber-valid file must be storable, and the stored text must round-trip byte for byte. Line and column are what an editor needs to put a marker on the right spot.

### D9. Versions, ids and concurrent edits
**Default:**
- **Create** makes version 1. **Save** (`PUT`) takes `source`, `path`, `baseVersion` (required, ≥ 1) and an optional `message`.
- **Optimistic concurrency:** if `baseVersion` isn't the current version, the answer is 409 `VERSION_CONFLICT`, with a Problem member `currentVersion`. Nothing changes. The client reloads, merges and saves again. Two saves with the same base version at the same moment give exactly one new version and one 409, never a 500 and never two versions with one number.
- **No new version for an unchanged text:** if `source` is identical to the current version's text (same SHA-256), no version is created and `currentVersion` and `updatedAt` stay the same. The answer is 200. `baseVersion` is still checked first.
- **A path change** (D10) in the same save updates the feature's path. On its own it creates no version, because versions are about the text.
- **Base version as a body field, not `If-Match`:** the gateway's CORS policy doesn't allow `If-Match` from browsers today (allowed headers line 104), and a body field needs no gateway change.
- **Stable ids across versions** (ERD-D3, made precise):
  - **Feature id:** never changes.
  - **Scenario id** (Scenario, Scenario Outline, Background): kept when the new version has an element of the **same kind** (Background, Scenario or Outline) with the **same name**, in the **same container** (the Feature itself, or a Rule with the same name). When several elements share a name in one container, the n-th in the old version matches the n-th in the new one. Otherwise the element gets a new id, and old ids that match nothing disappear from the structure. Scenario and Example keywords are one kind; Scenario Outline and Scenario Template are another.
  - **Example row id:** kept when its outline keeps its id, the Examples block has the same header, and a row with **identical cells** exists in the old version (each old row matches at most one new row, in order). A changed row is a different test case, so it gets a new id.
  - **Rules, Examples blocks, steps:** ids may change on every save, and clients must not keep them. report-service relies on snapshots for steps (ERD `step_results.step_text`).
- **History** lists every version, newest first, with number, author, time and message. Reading one version returns its text. Versions are never changed or deleted.

**Why:** results will point at scenario and example-row ids, so ids should survive the usual edits (typos in steps, new scenarios, reordering) and change exactly when the test itself changes. The n-th-occurrence rule makes duplicate names deterministic. A body field is the least invasive way to get conflict detection through the existing gateway.

### D10. Feature paths
**Default:**
- 1 to 1024 characters. Segments are separated by `/`, and the last one ends in `.feature`.
- Not allowed: a leading `/`, an empty segment (`a//b.feature`), a `.` or `..` segment, a backslash, a control character (rule T1's set: U+0000–U+001F and U+007F–U+009F, so C1 controls too; D19 #1), and leading or trailing whitespace in the whole path. Spaces inside segments are allowed. The suffix is exactly lower-case `.feature`, after at least one other character in the last segment (D19 #10).
- Stored as sent. **Unique per project among active features, compared case-insensitively** (`Locale.ROOT` lower case), so an export never collides on case-insensitive file systems. An archived feature's path is free.
- A path can be changed by a save (D9).
- Invalid → 400 `VALIDATION_FAILED` (`errors` entry `path`). Taken → 409 `FEATURE_PATH_TAKEN`.

**Why:** a relative POSIX path is what Cucumber projects use, and import and export (later) can map it directly to files. Case-insensitive uniqueness avoids `Payment.feature` and `payment.feature` colliding on macOS and Windows.

### D11. Archived features and archived projects
**Default:**
- **Archive** sets `archivedAt`, and **restore** clears it. Both are idempotent: a second archive is 200 with the unchanged state.
- An archived feature is **readable** (view, versions, test cases) but **read-only**: a save is 409 `FEATURE_ARCHIVED`. It's left out of the default list, the tag catalogue and the default (active) tag filter. `?archived=true` lists only archived features, and `?archived=true&tag=@x` lists the archived features that use `@x` (D12; D19 #4).
- Restoring fails with 409 `FEATURE_PATH_TAKEN` if an active feature now has its path (compared case-insensitively). The feature stays archived.
- Ids (feature, scenario, example row) are kept through archive and restore.
- **Archived project** (C5 `archived: true`): every write is 409 `PROJECT_ARCHIVED`, and every read works (auth-service-projects D5).
- No hard delete.

**Why:** archive is ERD-D10's soft delete. Results in report-service will keep pointing at the feature id, and restore is the undo.

### D12. Tags
**Default:**
- Tags are derived from the text on every save (ERD-D7). The catalogue (`GET /api/projects/{projectId}/tags`) lists every tag used **anywhere in an active feature's current version** (on the Feature, a Rule, a Scenario or Outline, or Examples), each with `featureCount`, the number of active features that use it. It's sorted by name in ordinal (case-sensitive) order.
- A tag no longer used anywhere disappears from the catalogue at once. Whether a stored catalogue row is deleted or kept is backend-swe's choice. The API behaviour is binding.
- **Feature list filter:** `?tag=@smoke` returns the active (or, with `archived=true`, archived) features that use the tag anywhere. The match is exact and case-sensitive. The parameter must start with `@` and contain no whitespace, else 400 `VALIDATION_FAILED`. One tag only. Tag expressions are out of scope.
- `@Smoke` and `@smoke` are different tags.

**Why:** a catalogue with counts supports autocomplete and the "which features are tagged" question. One-tag filtering covers the common case without building a tag-expression engine, which belongs with test-run planning in report-service.

### D13. Test cases
**Default:** `GET .../features/{featureId}/test-cases` returns the current version's test cases in text order. Each has:
- `id`: the scenario id for a Scenario; the example row id for an Outline row;
- `type`: `SCENARIO` or `EXAMPLE_ROW`;
- `scenarioId`, and `exampleRowId` (null for a Scenario);
- `name`: for an Outline row, the Outline name with placeholders replaced by the row's values;
- `effective tags`: Feature, Rule, Scenario or Outline, Examples, in that order, without duplicates;
- `steps`: the Feature Background's steps, then the Rule Background's (if any), then the scenario's. Each has its keyword as written, its text with placeholders replaced, and its data table or doc string with placeholders replaced;
- `line`: the scenario's line, or the example row's line.

The content must equal what Cucumber's pickle compiler produces for the same text. backend-swe should use the parser's pickle compiler and map its results to stored ids, not reimplement the rules (P14). An Outline without Examples rows has no test case, and a Background is never one.
**Why:** "a test case is what Cucumber would execute" is the only definition that will match automated results later (report-service ingestion keys on the same pickles).

### D14. Persistence: PostgreSQL and Flyway from the start
**Default:** scenario-service adopts **"Database pattern for every service"** of `docs/specs/auth-service-persistence.md` (items 1–19, approved by the user on 2026-10-14) **exactly**. auth-service phase 4 is the reference implementation, and backend-swe copies its configuration and test support into scenario-service's own classes (G9: no shared library). For this service, `<svc>` is `scenario_service` and `<SVC>` is `SCENARIO`. In short:
- **Server and database (items 1–3):** PostgreSQL 17 everywhere. Database `scenario_service`, owned by role `scenario_service`, schema `public`. UTF-8 with locale provider `builtin`, locale `C.UTF-8` (ERD-D13), so feature lists (D10) and tags (D12) sort by code point on every OS.
- **Settings (items 4, 5):** `SCENARIO_DB_URL`, `SCENARIO_DB_USERNAME`, `SCENARIO_DB_PASSWORD`, with no default outside `dev`. A missing value stops start-up with an error naming it, and URLs never contain credentials. The `dev` profile uses `jdbc:postgresql://localhost:5432/scenario_service`, user `scenario_service`, password `scenario_service_dev_only` (`# DEV ONLY`), on the shared local `postgres:17` dev container from persistence D5 (`cucumber-postgres`). The README documents the one-time `CREATE ROLE` / `CREATE DATABASE` (item 3's SQL, run with `docker exec`). **Nobody creates databases or roles for tests**, and backend-swe doesn't create the dev database on the user's machine; the user runs the README commands before the manual check (T14). Port 5432 is held by Postgres.app 16 today (not usable: no builtin `C.UTF-8` before 17; persistence D3).
- **Migrations (item 6):** Flyway only, `src/main/resources/db/migration/V<n>__<snake_case_description>.sql`, append-only once reviewed. No `R__`, no Java migrations, no `IF [NOT] EXISTS`. Boot's Flyway defaults. `V1__create_scenario_service_schema.sql` creates the whole `scenario_service` group of the ERD (there's no as-built state to reproduce, unlike auth-service's V1).
- **Hibernate (item 7):** `ddl-auto: validate` in every profile and test; `open-in-view: false`; entities mirror NOT NULL and lengths with `@Column`.
- **Names (item 8):** `pk_`, `fk_<table>_<column>`, `uk_<table>_<columns>`, `ix_<table>_<columns>`, `ck_<table>_<column>_<rule>`. For example: `uk_features_project_id_path_lower` (the partial index below), `uk_feature_versions_feature_id_version`, `uk_tags_project_id_name`, `ck_scenarios_kind_values`.
- **Types (item 9):**
  - ids `uuid` generated by the application;
  - `timestamptz` ↔ `Instant`;
  - enums (`scenarios.kind`, `steps.keyword`, `steps.argument_type`, `tag_assignments.target_type`) as **`varchar(32)` plus CHECK `ck_<table>_<column>_values`**, never PostgreSQL `ENUM` types (ERD-D12);
  - JSON columns (`steps.data_table`, `examples.header`, `example_rows.cells`) as `jsonb`. backend-swe verifies Hibernate 7's `jsonb` mapping with Boot 4.1's Jackson 3 (P12; stop condition in T7).
- **Constraints (item 10):** NOT NULL as in the ERD, and every foreign-key column leads an index.
  - **Case-insensitive path uniqueness** uses the `lower()` variant (the stored path keeps its case): partial unique index `uk_features_project_id_path_lower` on `(project_id, lower(path)) WHERE archived_at IS NULL`.
  - Also unique: `(feature_id, version)` and `(project_id, name)` for tags.
  - **Named deviation from item 10 (delete rules):** within the parsed-structure tables, foreign keys are `ON DELETE CASCADE` as the 2026-10-12 ERD designed (rules, scenarios, steps, examples, example rows, tag assignments). Each save replaces the current version's structure, and the cascade keeps that one statement per parent. `feature_versions.feature_id` and `tag_assignments.tag_id` are `ON DELETE RESTRICT` instead of cascade: versions are never deleted (D9), and features are never hard-deleted (D11), so no cascade should ever reach them.
- **Time (item 11):** one `Clock` bean, UTC with a microsecond tick (`Clock.tick(Clock.systemUTC(), Duration.ofNanos(1000))`), and `Instant.now(clock)` everywhere. `created_at` and `updated_at` are set by the application, with no database default and no JPA auditing. The API shows UTC timestamps with `Z`.
- **Locks and errors (item 12):** `lock_timeout` = 5 s on every connection, which also bounds the wait for the project advisory lock below; `logServerErrorDetail=false`. A lock timeout (55P03) or deadlock (40P01) is 409 `CONFLICT`. No code catches a database exception and continues in the same transaction.
- **Logging (item 13):** in dev, SQL only through `org.hibernate.SQL` at DEBUG; `org.hibernate.orm.jdbc.bind` is never enabled; no password is logged (D17).
- **Concurrent writes in one project** (create, save, archive, restore) are serialised by a transaction-scoped PostgreSQL advisory lock (`pg_advisory_xact_lock`) keyed by the project id, taken through a repository method (P13). There's no project row here to lock. The unique indexes stay as the safety net, and a violation that slips through is 409 `CONFLICT`, never 500 (P17).

**Why:** ERD-D11 decided PostgreSQL and Flyway everywhere, and the user approved one pattern for every service on 2026-10-14. Starting on it avoids an H2 phase and a later migration. Only the two named deviations differ, both because Gherkin content isn't like auth-service's data.

### D15. Testing approach
**Default:**
- **Full-context tests against the real security chain on PostgreSQL 17 through Testcontainers** (database pattern items 14–17, decided by the user on 2026-10-14):
  - **Item 14:** one **singleton** `postgres:17` container per Gradle test JVM, with item 3's initdb arguments, wired by Boot's `@ServiceConnection` through scenario-service's own shared test meta-annotation in `support/`. The real Flyway migrations run. There's no H2 on the classpath. **Docker must be running for `./gradlew build`.** A missing Docker daemon fails the build, and tests are never skipped.
  - **Item 15:** every Spring context shares the one test database. Fixtures reset data in `@BeforeEach`, children first. No test creates or drops schema objects there. Start-up tests (missing `JWT_SECRET`, missing `SCENARIO_DB_URL`) and drift tests use **scratch databases** that they create and drop. Tests also use fresh random project ids, so a forgotten reset can't make one test see another's features.
  - **Item 16:** connection budget. Start from the container's `max_connections=300` and a test pool of `maximum-pool-size=12`, `minimum-idle=1`, or whatever auth-service's T3 settled on. Each pool must stay above the highest concurrency a test uses (5 simultaneous requests in AC-1.10 and AC-3.11). Report distinct contexts and peak connections. Never use `@DirtiesContext` as a workaround.
  - **Item 17:** one schema test pinned to the ERD (columns, CHECK values, unique constraints and indexes including the partial path index, foreign keys and their delete rules, `timestamptz`), plus direct-SQL tests for every constraint.
  - API tests are not `@Transactional`.
- **auth-service is never started in automated tests.** The access check stub (JDK `com.sun.net.httpserver.HttpServer` on a random port, so no new dependency) answers per the Scenario fixture. It records the requests it receives (for AC-7.2 and "no call made"), and it can be told to fail, delay, or answer oddly (AC-7.3).
- Tokens are made in test code with the test-only secret (as in auth-service). `src/test/resources` isn't used. Test settings (secret, stub URL) live in the test `support` package. The database connection comes only from `@ServiceConnection`; no test holds a database URL or password.
- `@Tag("AC-x.y")` on every AC test. Concurrency tests (AC-1.10, AC-3.11) repeat at least 5 times and are green 3 runs in a row.
- **Test first (binding, P15):** in every task, write the AC tests first, run them, and report how they failed (404, compile error, wrong status) before writing the code. For the security-critical behaviours (JWT rules, fail closed, no caching, no PII in logs), a test that passes before the code exists proves nothing: strengthen it or explain why.
- AC-7.11 is a manual check through the gateway with the real auth-service.

**Why:** the database is part of the behaviour here (partial unique index, advisory lock, `jsonb`, `timestamptz`, `C.UTF-8` order). Testing on H2 would prove the wrong thing. Testcontainers gives the same PostgreSQL major and collation on every machine, and it's the approved pattern.

### D16. Error codes and order
Same Problem format as auth-service (contract C3): `type` `about:blank`, `title`, `status`, `detail`, `instance`, `code`, `requestId` when the request has a valid `X-Request-Id`, and no stack traces, SQL, class names, hosts or ports. Codes copied from auth-service keep their meaning: `VALIDATION_FAILED`, `MALFORMED_REQUEST`, `UNAUTHENTICATED`, `FORBIDDEN`, `NOT_FOUND`, `METHOD_NOT_ALLOWED`, `NOT_ACCEPTABLE`, `UNSUPPORTED_MEDIA_TYPE`, `CONFLICT`, `INTERNAL_ERROR`. New:

| Situation | Status | `code` | Extra member |
|---|---|---|---|
| The text isn't valid Gherkin (D8) | 422 | `GHERKIN_INVALID` | `parseErrors` |
| Another active feature in the project has that path (case-insensitive) | 409 | `FEATURE_PATH_TAKEN` | — |
| `baseVersion` isn't the current version | 409 | `VERSION_CONFLICT` | `currentVersion` |
| Saving an archived feature | 409 | `FEATURE_ARCHIVED` | — |
| A write on an archived project | 409 | `PROJECT_ARCHIVED` | — |
| No such feature in this project (also a feature of another project) | 404 | `FEATURE_NOT_FOUND` | — |
| No such version of the feature | 404 | `VERSION_NOT_FOUND` | — |
| The access check couldn't be completed (D4) | 503 | `ACCESS_CHECK_UNAVAILABLE` | — |

**Order** (the first failure wins):
1. 401 `UNAUTHENTICATED` (security filter).
2. 400 `MALFORMED_REQUEST` (malformed ids, unreadable JSON, wrong types including a number or boolean for a string; pattern item 18) or `VALIDATION_FAILED` (body and query rules). As in auth-service (A3), this runs before authorization.
3. The access check: 503, 401 (from C5), 403.
4. Writes: 409 `PROJECT_ARCHIVED`.
5. 404 `FEATURE_NOT_FOUND`, then `VERSION_NOT_FOUND`.
6. Save: 409 `FEATURE_ARCHIVED`, then 409 `VERSION_CONFLICT`.
7. Create and save: 422 `GHERKIN_INVALID`.
8. Create, save with a new path, restore: 409 `FEATURE_PATH_TAKEN`.

**Ids in paths** must be canonical UUIDs (8-4-4-4-12 hex digits, either case; normalised to lower case). Anything else is 400 `MALFORMED_REQUEST`, including the forms Java's `UUID.fromString` accepts, such as `1-1-1-1-1` (FU-10 lesson). `{version}` must be a positive integer; otherwise 400.

### D17. Logging (copy of auth-service-projects D17)
**Default:** scenario-service adopts D17 unchanged, as that spec's item 9 asked. Its own copies are:
- `aop/Logging`, advising public methods of `@Service` classes in `org.scenarioservice.service..`;
- the request-ID MDC filter (before the security chain) and the user-ID MDC filter (after it), with MDC keys `requestId` and `userId`, the same `X-Request-Id` validation, and cleanup in `finally`;
- `app.logging.enabled` (true) and `app.logging.slow-call-threshold` (500ms), a typed properties record;
- the same `logging.pattern.correlation`.

The rules carry over: entry and exit at DEBUG; one outcome line, with business errors at INFO for the outermost call; slow calls at WARN; never arguments, return values or exception messages.

Additions for this service:
- **Content is never logged.** User content is treated like personal data: Gherkin text, step text, feature, scenario and tag names, paths, version messages and **parser error messages** (they quote the offending line). Logs carry ids only: project, feature, version number, user.
- **The access checker and the C5 client are not advised**, because they handle tokens. They aren't `@Service` classes in `service..`, and that must stay so. A C5 failure is logged once, at WARN, by the checker or the advice: the failure type only, with no token, no `Authorization` value and no auth-service URL.
- **The JWT filter never logs the token or a claim value** (the `email` claim is personal data).
- The MDC request ID is the one forwarded to C5 (D4).

**Why:** one logging design across services makes a request traceable from the gateway through scenario-service to auth-service with one ID.

### D18. Build dependencies
**Default:** these build changes (SE1 to SE5, SE8; all approved on 2026-10-14). Versions are managed by the Boot 4.1.0 plugin unless stated.
- **Remove** `spring-boot-starter-data-elasticsearch` and `spring-boot-starter-data-elasticsearch-test` (SE1). Search is a later phase. Unused starters add auto-configuration and attack surface. The search spec adds it back with a purpose.
- **Add** `spring-boot-starter-flyway` and `org.flywaydb:flyway-database-postgresql` (SE2). In Boot 4, Flyway's auto-configuration lives in the starter's module, and Flyway's PostgreSQL support is a separate artefact. Both are in the 4.1.0 BOM (Flyway 12.4.0). As in persistence E61: **if the starter already brings the PostgreSQL module, the second line isn't added** (report `./gradlew dependencies`). `spring-boot-starter-flyway-test` is added only if `@DataJpaTest` classes don't run Flyway without it (persistence E61b), with the verification reported.
- **Add** the test dependencies `org.springframework.boot:spring-boot-testcontainers` and `org.testcontainers:testcontainers-postgresql` (SE8, the pattern's item 14; Testcontainers 2.0.5 from the Boot BOM). `testcontainers-junit-jupiter` is added only if verification shows a singleton needs it, as in persistence E62.
- **Add** `spring-boot-starter-aspectj` (SE3), as auth-service's E57, because the logging aspect uses AspectJ annotations directly.
- **Add** `io.jsonwebtoken:jjwt-api` (implementation) and `jjwt-impl` and `jjwt-jackson` (runtime), version 0.12.5 (SE4).
- **Add** `io.cucumber:gherkin:28.0.0` and `io.cucumber:messages:24.1.0` (SE5; Q5).
- **Keep** data-jpa, restclient (the C5 client), security, validation, webmvc, the PostgreSQL driver, Lombok, devtools, and the remaining test starters.
- **Not added:** actuator (no consumer until the deployment spec), WireMock (the JDK HTTP server is enough), springdoc (the checked-in contract is the contract; runtime docs can come later), H2.

**Why:** every dependency has a user in this spec, and nothing unused is kept. That's auth-service-projects D16's rule, applied from day one.

### D19. Clarifications from the T1 contract review (pm, 2026-10-14)
backend-swe wrote contract 1.0.0 in T1 and reported 13 gaps. Each is decided below with a default and a reason. These rules are **binding** like D8 to D16, and where a D-section is shorter, D19 adds to it. New ACs: AC-1.13, AC-1.14, AC-3.13, AC-3.14, AC-4.6, AC-5.7, AC-5.8, AC-6.9, AC-6.10, AC-7.12, AC-7.13. Contract changes: T1b.

| # | Gap | Decision (default) | Reason |
|---|---|---|---|
| 1 | Control characters in `path` | **Rule T1's set:** U+0000–U+001F and U+007F–U+009F (C0, DEL and C1). The contract is right, and D10 is corrected. | One definition of "control character" across services (persistence D11), and C1 controls are just as invisible in a file name. |
| 2 | `source` length unit | **Code points** (D8). The ERD note is corrected. | One unit for every length (pattern item 18). |
| 3 | `updatedAt` / `updatedBy` | **Changed by every effective change:** a new version, a path change (even without a new version), and a real archive or restore. **Unchanged by a no-op:** an identical save (AC-3.7), archiving an archived feature, restoring an active one (AC-6.8). `createdAt` and `createdBy` never change. | "Last updated" should mean "anything a user sees changed", so a list sorted by it shows moves and archives. A no-op that changed it would hide the real last change. |
| 4 | Tag filter on archived features | **D12 wins:** `?archived=true&tag=@x` returns the archived features whose current version uses `@x`. The default (active) filter and the tag catalogue leave archived features out. D11 is corrected. | The `archived` switch picks the set and `tag` narrows it; both views stay searchable. |
| 5 | The `tag` parameter | A tag is `@` followed by **at least one character**. It has **no whitespace** and **no control character**, at most **1,000 code points**, and the parameter is given **at most once**. Otherwise 400 `VALIDATION_FAILED` with an `errors` entry for `tag`. That covers a bare `@`, a repeated `tag` (Spring would join the values with a comma, so the service must check the raw count), and too long. "Whitespace" = ECMAScript `\s`, the contract pattern's definition: Unicode White_Space plus U+FEFF. In Java that's `Character.isWhitespace(c)`, or `Character.isSpaceChar(c)`, or `c` is U+FEFF. **ERD:** `tags.name` becomes `text`, so any tag the parser accepts can be stored (D8). | A Gherkin tag can never contain whitespace, so such a filter could only be a mistake. One clear 400 beats an empty list for a malformed request. 1,000 code points stays far below URL limits. |
| 6 | Lexical edge cases | **One spelling per value; anything else is 400 `MALFORMED_REQUEST`.** **`baseVersion`:** a JSON integer from 1 to 2147483647; a JSON string (`"1"`), a fraction (`1.5`, `1.0`) or a value above int32 is `MALFORMED_REQUEST`; 0 and negative numbers stay `VALIDATION_FAILED` (AC-3.10). **`{version}`:** `^[1-9][0-9]{0,9}$` and at most 2147483647; leading zeros (`01`), a sign (`+1`), 0, or above int32 are `MALFORMED_REQUEST`. **`archived`:** exactly `true` or `false` (lower case); `yes`, `on`, `1`, `True` and an empty value are `MALFORMED_REQUEST`. | The same strictness as canonical UUIDs (D16) and strict JSON types (pattern item 18). One resource or value has one spelling, so caches, logs and tests don't see aliases. |
| 7 | Empty vs null in the structure | **Names and descriptions are always strings**, `""` when absent, never `null`, for Feature, Rule, Background, Scenario, Outline and Examples. They're stored the same way (`NOT NULL`, `''`; ERD). An **Examples block without a table** has `header` `[]` and `rows` `[]`, and yields no test case. `dataTable`, `docString` and `docString.mediaType` stay nullable (an absent argument isn't text). | One representation, so clients never handle both; name matching for stable ids (D9) treats "no name" as `""`. |
| 8 | The "no Feature" error | Reported at **line 1, column 1**, message `The text has no Feature`. That applies whatever the text contains: whitespace only, comments only, blank lines. A **whitespace-only `source`** passes the "not empty" check and is 422 `GHERKIN_INVALID` (the contract is right). | The error concerns the whole document, so its start is the only honest position. The 400 check is for a missing or empty field, not for content. |
| 9 | `message` | **Trimmed** of Unicode-blank characters (the persistence D11 rule T2 definition: Unicode whitespace including NBSP, zero-width space, BOM and NEL) before storing. If nothing is left, there's no message (`null`). The **500 code-point limit counts the value as sent, before trimming**. The control-character rule applies to the **trimmed** value, so a trailing newline is fine and an inner one is 400. | It matches project names in auth-service (length before trimming, phase-2 D5 Q3) and rule T1 on the stored value. A message is one line in the history list. |
| 10 | The `.feature` suffix | **Exactly lower-case `.feature`**: `.FEATURE` is 400. The last segment needs at least one character before `.feature`, so `features/.feature` is 400 (the contract changes). | Cucumber's default glob `**/*.feature` is case-sensitive on Linux, so `.FEATURE` files would silently not run. A nameless hidden file is a mistake. |
| 11 | Duplicate tags on one element (`@a @a`) | **Each tag appears once**, at its first position, in every `tags` array (structure, list, test cases), and in storage (one `tag_assignments` row, as its unique index requires). The source text keeps both, unchanged. | A duplicate carries no meaning in Cucumber's tag expressions, and D13 already de-duplicates effective tags. |
| 12 | Bearer scheme letter case | **Case-insensitive** (`bearer`, `BEARER` accepted), like auth-service AC-3.3 and the gateway (RFC 9110 §11.1). Clients should send `Bearer`. | A token accepted at the gateway must not become anonymous one hop later. |
| 13 | Timestamps | **As auth-service-persistence D6:** UTC with `Z`, microsecond precision, written by Jackson with **0 to 6 fractional digits** (trailing zeros may be dropped). Clients must accept 0–6 digits and compare instants, not strings. The contract is right. | The same format as auth-service's planned contract 1.3.0 and its AC-4.x pattern `^…(\.\d{1,6})?Z$`. |

**Follow-ups after T1b (pm, 2026-10-14):**
- **#8 combined with parser errors (decided).** When the parser reports errors, `parseErrors` lists **only the parser's errors**. The "no Feature" entry is reported only when the parser reports none. For example, `Given a step` alone gives the parser's error at 1:1, not `The text has no Feature`. **Why:** the parser's errors point at the line to fix; "no Feature" is a fallback for text the parser accepts. New AC-1.15 (P2). **Contract (T14):** in the `GHERKIN_INVALID` text, "exactly one entry … `The text has no Feature`" applies only "when the parser reports no error".
- **#5 and #9 use different whitespace sets (confirmed, not aligned).** The `tag` filter rejects only what can't occur in a Gherkin tag (ECMAScript `\s` and control characters), so U+200B is allowed and matched exactly, because the parser can keep it inside a tag. `message` trimming removes every Unicode-blank character (persistence rule T2), including U+200B, because there it's invisible padding. **Why:** the filter must be able to match every tag the parser stores, while the message rule only cleans display text.
- **Server-enforced rules the schema can't express** (each needs its own test; listed in T3, T8, T9, T11):
  - `baseVersion: 1.0` (or any fraction, or a string) is `MALFORMED_REQUEST`;
  - `{version}` above 2147483647 is `MALFORMED_REQUEST`;
  - a repeated `tag` is `VALIDATION_FAILED`;
  - `archived` other than exactly `true` or `false` is `MALFORMED_REQUEST`.

**backend-swe's contract-shape choices: all accepted.**
- **Archive and restore return the full `Feature`:** accepted. It's the same as create and save, and the client refreshes one object.
- **`keywordType` on steps** (values `GIVEN`, `WHEN`, `THEN`, `AND`, `BUT`, `ASTERISK`): accepted. It's the D8 "normalised type"; the ACs' "type" means this field.
- **`dataTable` and `docString` as two nullable members**, never both set: accepted. It mirrors ERD-D6's single argument, and both members are always present, so clients don't test for absence.
- **`children` told apart by `kind`** (`BACKGROUND`, `SCENARIO`, `SCENARIO_OUTLINE`, `RULE`): accepted. Text order is preserved, which separate arrays would lose.
- **Extra fields on `FeatureSummary`** (`projectId`, `language`, `createdBy`, `createdAt`, `updatedBy`): accepted, as long as no `source` or structure is added (AC-2.2).
- **`path` required on save:** accepted. `PUT` carries the full new state (D9), so a client can't move a file by accident by leaving `path` out.

## User stories

### US-1: Create a feature from Gherkin text (P0)
As a project member, I want to create a feature by writing Gherkin text, so that the team's behaviour specifications live in one shared, structured place.

```gherkin
Feature: Create a feature from Gherkin text

  Background:
    Given the Scenario fixture

  @US-1 @AC-1.1 @P0
  Scenario: A member creates a feature from valid Gherkin
    When Gus sends "POST {checkout-features}" with "{token of Gus}" and body {"path":"features/checkout/payment.feature","source":"<the Payment source>","message":"First draft"}
    Then the response status is 201
    And the body has a UUID "id", "projectId" "{checkout}", "path" "features/checkout/payment.feature", "name" "Payment", "language" "en", "currentVersion" 1, "createdBy" Gus's user id, "updatedBy" Gus's user id, a "createdAt", an "updatedAt" and "archivedAt" null
    And the body's "description", trimmed, is "Customers pay for their basket."
    And the body's "source" is exactly the Payment source
    And the "Location" header is exactly "/api/projects/{checkout}/features/" followed by that id
    And "GET {checkout-features}/{that id}/versions" by Gus lists exactly one version: 1, by Gus's user id, with message "First draft"

  @US-1 @AC-1.2 @P0
  Scenario: The feature's structure is parsed from the text
    Given Gus has created Payment
    When Dan sends "GET {checkout-features}/{payment}" with "{token of Dan}"
    Then the response status is 200
    And the feature has tags ["@checkout"] and these children, in this order:
      | kind       | keyword    | name     |
      | BACKGROUND | Background |          |
      | SCENARIO   | Scenario   | Pay by card |
      | RULE       | Rule       | Vouchers |
    And the Background has one step: keyword "Given", type "GIVEN", text "a basket with these items:", with the data table [["item","price"],["Mug","8.00"],["Shirt","20.00"]]
    And "Pay by card" has a UUID id, tags ["@smoke"], line 12 and two steps: "When" / "WHEN" / "the customer pays by card" without argument, then "Then" / "THEN" / "the receipt is:" with a doc string "Total: 28.00" whose media type is "text"
    And the Rule "Vouchers" contains one Scenario Outline "Pay with a voucher" (kind SCENARIO_OUTLINE, keyword "Scenario Outline") with steps "When the customer pays with voucher \"<code>\"" and "Then the result is \"<result>\""
    And the Outline has two Examples blocks: "Valid vouchers" with tags ["@regression"], header ["code","result"] and rows ["SAVE10","confirmed"], ["SAVE20","confirmed"]; then "Expired vouchers" with no tags, the same header and the row ["OLD5","rejected"]
    And every example row has a UUID id and its line number (28, 29 and 33)

  @US-1 @AC-1.3 @P0
  Scenario Outline: Only members and admins of the project can create features
    When <actor> sends "POST {checkout-features}" with "{token of <actor>}" and body {"path":"features/address.feature","source":"<the Address source>"}
    Then <outcome>

    Examples:
      | actor | outcome                                                              |
      | Ana   | the response status is 201                                           |
      | Cai   | the response status is 201                                           |
      | Gus   | the response status is 201                                           |
      | Hal   | the response is a Problem 403 / FORBIDDEN, and nothing is stored     |
      | Dan   | the response is a Problem 403 / FORBIDDEN, and nothing is stored     |
      | Ivy   | the response is a Problem 403 / FORBIDDEN, and nothing is stored     |
      | Eve   | the response is a Problem 403 / FORBIDDEN, and nothing is stored     |

  @US-1 @AC-1.4 @P0
  Scenario Outline: Invalid Gherkin is rejected with the error's line and column
    When Gus sends "POST {checkout-features}" with path "features/broken.feature" and the source "<broken>"
    Then the response is a Problem 422 / GHERKIN_INVALID
    And its "parseErrors" has an entry with "line" <line>, "column" <column> and a non-empty "message"
    And nothing is stored

    Examples:
      | broken              | line | column |
      | step before Feature | 1    | 1      |
      | inconsistent cells  | 5    | 7      |
      | step after Examples | 7    | 5      |
      | unknown language    | 1    | 1      |
      | no Feature          | 1    | 1      |

  @US-1 @AC-1.5 @P2
  Scenario: Every parse error is reported, in line order
    When Gus creates a feature whose source has an inconsistent cell count in a table at line 5 and another at line 9
    Then the response is a Problem 422 / GHERKIN_INVALID
    And "parseErrors" has an entry at line 5 and an entry at line 9, and the entries are sorted by line, then column

  @US-1 @AC-1.6 @P0
  Scenario Outline: An invalid path is a validation error
    When Gus sends "POST {checkout-features}" with the Address source and path <path>
    Then the response is a Problem 400 / VALIDATION_FAILED with an "errors" entry for "path"
    And nothing is stored

    Examples:
      | path                                   |
      | (missing)                              |
      | ""                                     |
      | "/features/address.feature"            |
      | "features//address.feature"            |
      | "features/../address.feature"          |
      | "features/./address.feature"           |
      | "features/address.txt"                 |
      | "features\\address.feature"            |
      | " features/address.feature"            |
      | "features/add<U+0007>ress.feature"     |
      | "<1,009 a's>/address.feature" (1,025 characters) |

  @US-1 @AC-1.7 @P1
  Scenario Outline: Invalid source and message values are validation errors
    When Gus sends "POST {checkout-features}" with path "features/address.feature" and <body change>
    Then the response is a Problem 400 / VALIDATION_FAILED with an "errors" entry for "<field>"
    And nothing is stored

    Examples:
      | body change                                              | field   |
      | no "source"                                              | source  |
      | "source" ""                                              | source  |
      | a "source" of 1,000,001 code points                      | source  |
      | the Address source with U+0000 inserted after "Address"  | source  |
      | the Address source with an unpaired U+D800 in a step     | source  |
      | the Address source and a "message" of 501 code points    | message |
      | the Address source and a "message" containing U+0007     | message |

  @US-1 @AC-1.8 @P1
  Scenario Outline: A path can't repeat among the project's active features, whatever the letter case
    Given Gus has created Payment
    When Ana sends "POST {checkout-features}" with the Address source and path "<path>"
    Then the response is a Problem 409 / FEATURE_PATH_TAKEN
    And Checkout still has exactly one feature

    Examples:
      | path                              |
      | features/checkout/payment.feature |
      | Features/Checkout/PAYMENT.feature |

  @US-1 @AC-1.9 @P1
  Scenario: The same path is allowed in another project
    Given Gus has created Payment
    When Ana sends "POST /api/projects/{billing}/features" with path "features/checkout/payment.feature" and the Payment source
    Then the response status is 201 and the body has "projectId" "{billing}"

  @US-1 @AC-1.10 @P2
  Scenario: Simultaneous creations with one path create exactly one feature
    When Ana and Gus together send 5 requests "POST {checkout-features}" with path "features/address.feature" and the Address source at the same moment
    Then exactly one response has status 201
    And each of the other 4 is a Problem 409 / FEATURE_PATH_TAKEN
    And Checkout has exactly one feature, with exactly one version

  @US-1 @AC-1.11 @P1
  Scenario: Any text the Gherkin parser accepts can be stored
    When Gus sends "POST {checkout-features}" with path "features/empty.feature" and source "Feature:\n"
    Then the response status is 201
    And the body has "name" "" and no children
    And "GET {checkout-features}/{that id}/test-cases" returns []

  @US-1 @AC-1.12 @P2
  Scenario: Features in other Gherkin languages are parsed with that language's keywords
    When Gus creates "features/paiement.feature" with the source "# language: fr\nFonctionnalité: Paiement\n  Scénario: Payer\n    Soit un panier\n    Alors la commande est confirmée\n"
    Then the response status is 201 and the body has "language" "fr" and "name" "Paiement"
    And the scenario "Payer" has the steps "Soit" / "GIVEN" / "un panier" and "Alors" / "THEN" / "la commande est confirmée"

  @US-1 @AC-1.13 @P2
  Scenario Outline: A text without a Feature is reported at line 1, column 1
    When Gus sends "POST {checkout-features}" with path "features/none.feature" and source <source>
    Then the response is a Problem 422 / GHERKIN_INVALID
    And "parseErrors" is exactly [{"line":1,"column":1,"message":"The text has no Feature"}]
    And nothing is stored

    Examples:
      | source                       |
      | "   \n"                      |
      | "\n\n# only a comment\n"     |

  @US-1 @AC-1.14 @P2
  Scenario Outline: The path's suffix and control characters follow D19
    When Gus sends "POST {checkout-features}" with the Address source and path <path>
    Then the response is a Problem 400 / VALIDATION_FAILED with an "errors" entry for "path"

    Examples:
      | path                                 |
      | "features/address.FEATURE"           |
      | "features/.feature"                  |
      | "features/add<U+0085>ress.feature"   |

  @US-1 @AC-1.15 @P2
  Scenario: Parser errors replace the "no Feature" entry
    When Gus sends "POST {checkout-features}" with path "features/step.feature" and source "Given a step\n"
    Then the response is a Problem 422 / GHERKIN_INVALID
    And "parseErrors" has an entry at line 1, column 1 whose message isn't "The text has no Feature"
    And no entry has the message "The text has no Feature"
```

**Notes for testers.**
- AC-1.4: the line numbers are binding. The columns are what the official parser reports for these texts as pm expects. If backend-swe or qa observes a different column from the parser itself, use the parser's value and report it to pm. Don't change the parser's output.
- AC-1.2: the line numbers follow the Payment source exactly as printed in the glossary.

### US-2: List and view features (P0)
As anyone with access to a project, I want to see its features and open one with its text and structure, so that I can understand what the product should do.

```gherkin
Feature: List and view features

  Background:
    Given the Scenario fixture
    And Gus has created Payment
    And Gus has created "features/Address.feature" with the Address source
    And Ana has created "features/invoice.feature" with the Invoice source in Billing

  @US-2 @AC-2.1 @P0
  Scenario Outline: Everyone with access to the project can list its features
    When <actor> sends "GET {checkout-features}" with "{token of <actor>}"
    Then <outcome>

    Examples:
      | actor | outcome                                                  |
      | Ana   | the response status is 200 and the body has 2 entries    |
      | Gus   | the response status is 200 and the body has 2 entries    |
      | Hal   | the response status is 200 and the body has 2 entries    |
      | Dan   | the response status is 200 and the body has 2 entries    |
      | Ivy   | the response is a Problem 403 / FORBIDDEN                |
      | Eve   | the response is a Problem 403 / FORBIDDEN                |

  @US-2 @AC-2.2 @P0
  Scenario: The list shows each feature's summary, ordered by path ignoring case
    When Dan sends "GET {checkout-features}"
    Then the body lists exactly "features/Address.feature", then "features/checkout/payment.feature"
    And the Payment entry has "id" "{payment}", "name" "Payment", "currentVersion" 1, "tags" ["@checkout"], "testCaseCount" 4, an "updatedAt" and "archivedAt" null
    And the Address entry has "tags" ["@address","@smoke"] and "testCaseCount" 1
    And no entry has a "source"

  @US-2 @AC-2.3 @P0
  Scenario Outline: Everyone with access to the project can view a feature
    When <actor> sends "GET {checkout-features}/{payment}" with "{token of <actor>}"
    Then <outcome>

    Examples:
      | actor | outcome                                                                   |
      | Cai   | the response status is 200 and the body's "source" is the Payment source  |
      | Hal   | the response status is 200 and the body's "source" is the Payment source  |
      | Dan   | the response status is 200 and the body's "source" is the Payment source  |
      | Ivy   | the response is a Problem 403 / FORBIDDEN                                 |
      | Eve   | the response is a Problem 403 / FORBIDDEN                                 |

  @US-2 @AC-2.4 @P0
  Scenario Outline: An unknown feature, or a feature of another project, is not found
    When Ana sends "GET {checkout-features}/<feature id>" with "{token of Ana}"
    Then the response is a Problem <status> / <code>

    Examples:
      | feature id               | status | code              |
      | {unknown id}             | 404    | FEATURE_NOT_FOUND |
      | the Invoice feature's id | 404    | FEATURE_NOT_FOUND |
      | not-a-uuid               | 400    | MALFORMED_REQUEST |

  @US-2 @AC-2.5 @P1
  Scenario: Projects don't share features
    When Ana sends "GET /api/projects/{billing}/features"
    Then the body lists exactly "features/invoice.feature"

  @US-2 @AC-2.6 @P1
  Scenario: The stored text comes back byte for byte
    When Gus creates "features/crlf.feature" with a source that uses CRLF line endings, a trailing space after "Feature: Spaces " and no final newline
    And Gus sends "GET {checkout-features}/{that id}"
    Then the body's "source" is identical to the sent source, character for character
```

### US-3: Edit a feature and keep its history (P0)
As a project member, I want to save changes to a feature as new versions, without overwriting a colleague's work, so that the specification evolves safely and its history is never lost.

```gherkin
Feature: Edit a feature and keep its history

  Background:
    Given the Scenario fixture
    And Gus has created Payment

  @US-3 @AC-3.1 @P0
  Scenario: Saving changed text creates the next version
    When Gus sends "PUT {checkout-features}/{payment}" with "{token of Gus}" and body {"path":"features/checkout/payment.feature","source":"<the Payment v2 source>","baseVersion":1,"message":"Add bank transfer"}
    Then the response status is 200
    And the body has "currentVersion" 2, "updatedBy" Gus's user id, and "source" equal to the Payment v2 source
    And "GET {checkout-features}/{payment}/versions" lists exactly version 2 with message "Add bank transfer", then version 1 with message "First draft"
    And "GET {checkout-features}/{payment}/versions/1" returns "version" 1 and "source" equal to the Payment source

  @US-3 @AC-3.2 @P0
  Scenario: Scenarios keep their ids when their names are unchanged
    Given the ids of "Pay by card" and "Pay with a voucher" in version 1
    When Gus saves the Payment v2 source with "baseVersion" 1
    Then in the new structure "Pay by card" and "Pay with a voucher" have the same ids as before
    And "Pay by bank transfer" has an id different from every version-1 scenario id
    And the example rows SAVE10, SAVE20 and OLD5 have the same ids as before

  @US-3 @AC-3.3 @P1
  Scenario: A renamed scenario gets a new id
    Given the id of "Pay by card" in version 1
    When Gus saves the Payment source with "Scenario: Pay by card" changed to "Scenario: Pay with a card", with "baseVersion" 1
    Then "Pay with a card" has an id different from the old id of "Pay by card"
    And no element of the feature's structure has the old id

  @US-3 @AC-3.4 @P1
  Scenario: Example rows keep their ids when their cells are unchanged, even when reordered
    Given the ids of the example rows SAVE10, SAVE20 and OLD5 in version 1
    When Gus saves the Payment source with the rows of "Valid vouchers" in the order SAVE20, SAVE10 and the OLD5 row changed to "| OLD9 | rejected |", with "baseVersion" 1
    Then SAVE10 and SAVE20 keep their ids
    And the OLD9 row has an id different from OLD5's old id

  @US-3 @AC-3.5 @P0
  Scenario: A save based on an outdated version is refused
    Given Gus has saved the Payment v2 source with "baseVersion" 1
    When Ana sends "PUT {checkout-features}/{payment}" with the Address source as "source", the same path and "baseVersion" 1
    Then the response is a Problem 409 / VERSION_CONFLICT with "currentVersion" 2
    And the feature's "currentVersion" is still 2 and its "source" is still the Payment v2 source

  @US-3 @AC-3.6 @P0
  Scenario: Invalid Gherkin is not saved
    When Gus sends "PUT {checkout-features}/{payment}" with the "inconsistent cells" source, the same path and "baseVersion" 1
    Then the response is a Problem 422 / GHERKIN_INVALID with a "parseErrors" entry at line 5
    And the feature's "currentVersion" is still 1, its "source" is still the Payment source, and it still has 4 test cases

  @US-3 @AC-3.7 @P1
  Scenario: Saving unchanged text creates no version
    When Gus sends "PUT {checkout-features}/{payment}" with the Payment source, the same path and "baseVersion" 1
    Then the response status is 200
    And the body has "currentVersion" 1 and the same "updatedAt" as before
    And the feature still has exactly one version

  @US-3 @AC-3.8 @P0
  Scenario Outline: Only members and admins of the project can save
    When <actor> sends "PUT {checkout-features}/{payment}" with "{token of <actor>}", the Payment v2 source, the same path and "baseVersion" 1
    Then <outcome>

    Examples:
      | actor | outcome                                                                        |
      | Ana   | the response status is 200 and "currentVersion" is 2                           |
      | Gus   | the response status is 200 and "currentVersion" is 2                           |
      | Hal   | the response is a Problem 403 / FORBIDDEN, and "currentVersion" is still 1     |
      | Dan   | the response is a Problem 403 / FORBIDDEN, and "currentVersion" is still 1     |
      | Ivy   | the response is a Problem 403 / FORBIDDEN, and "currentVersion" is still 1     |

  @US-3 @AC-3.9 @P1
  Scenario Outline: Reading the history
    Given Gus has saved the Payment v2 source with "baseVersion" 1
    When <actor> sends "GET {checkout-features}/{payment}/versions/<version>" with "{token of <actor>}"
    Then <outcome>

    Examples:
      | actor | version | outcome                                                               |
      | Dan   | 1       | the response status is 200 and "source" is the Payment source         |
      | Dan   | 2       | the response status is 200 and "source" is the Payment v2 source      |
      | Dan   | 3       | the response is a Problem 404 / VERSION_NOT_FOUND                     |
      | Dan   | 0       | the response is a Problem 400 / MALFORMED_REQUEST                     |
      | Dan   | abc     | the response is a Problem 400 / MALFORMED_REQUEST                     |
      | Ivy   | 1       | the response is a Problem 403 / FORBIDDEN                             |

  @US-3 @AC-3.10 @P1
  Scenario Outline: A save without a valid base version is a validation error
    When Gus sends "PUT {checkout-features}/{payment}" with the Payment v2 source, the same path and <base version>
    Then the response is a Problem 400 / VALIDATION_FAILED with an "errors" entry for "baseVersion"
    And "currentVersion" is still 1

    Examples:
      | base version          |
      | no "baseVersion"      |
      | "baseVersion" 0       |
      | "baseVersion" -1      |

  @US-3 @AC-3.11 @P2
  Scenario: Simultaneous saves on one version produce exactly one new version
    When Ana and Gus together send 5 requests "PUT {checkout-features}/{payment}" with different valid sources and "baseVersion" 1 at the same moment
    Then exactly one response has status 200 with "currentVersion" 2
    And each of the other 4 is a Problem 409 / VERSION_CONFLICT with "currentVersion" 2
    And the feature has exactly 2 versions

  @US-3 @AC-3.12 @P2
  Scenario Outline: The path can be changed by a save
    Given Gus has created "features/Address.feature" with the Address source
    When Gus sends "PUT {checkout-features}/{payment}" with the Payment source, "baseVersion" 1 and path "<path>"
    Then <outcome>

    Examples:
      | path                                   | outcome                                                                                              |
      | features/checkout/card-payment.feature | the response status is 200, "path" is "features/checkout/card-payment.feature" and "currentVersion" is still 1 |
      | features/address.feature               | the response is a Problem 409 / FEATURE_PATH_TAKEN, and the path is unchanged                         |

  @US-3 @AC-3.13 @P2
  Scenario: A path change alone updates who changed the feature and when, without a version
    When Ana sends "PUT {checkout-features}/{payment}" with the Payment source, "baseVersion" 1 and path "features/checkout/card-payment.feature"
    Then the response status is 200 with "currentVersion" 1
    And "updatedBy" is Ana's user id, "updatedAt" is later than before, and "createdBy" and "createdAt" are unchanged

  @US-3 @AC-3.14 @P2
  Scenario Outline: The version message is trimmed, and its limit counts the value as sent
    When Gus sends "PUT {checkout-features}/{payment}" with the Payment v2 source, the same path, "baseVersion" 1 and "message" <message>
    Then <outcome>

    Examples:
      | message                                      | outcome                                                                                          |
      | "  Fix typo \n"                              | the response status is 200, and version 2 has the message "Fix typo"                             |
      | " <U+00A0><U+200B> "                         | the response status is 200, and version 2 has no message                                         |
      | "Fix\nmore"                                  | the response is a Problem 400 / VALIDATION_FAILED with an "errors" entry for "message"           |
      | 499 "a"s surrounded by one space on each side (501 code points) | the response is a Problem 400 / VALIDATION_FAILED with an "errors" entry for "message" |
```

### US-4: Test cases derived from scenarios (P1)
As a tester or test lead, I want to see the test cases a feature produces, exactly as Cucumber would run them, so that I can plan and later record runs against them.

```gherkin
Feature: Test cases derived from scenarios

  Background:
    Given the Scenario fixture
    And Gus has created Payment

  @US-4 @AC-4.1 @P1
  Scenario: A Scenario is one test case and each Outline row is another
    When Hal sends "GET {checkout-features}/{payment}/test-cases" with "{token of Hal}"
    Then the response status is 200 and the body has exactly 4 test cases, in this order:
      | type        | id                          | name               | tags                         |
      | SCENARIO    | the id of "Pay by card"     | Pay by card        | ["@checkout","@smoke"]      |
      | EXAMPLE_ROW | the id of row SAVE10        | Pay with a voucher | ["@checkout","@regression"] |
      | EXAMPLE_ROW | the id of row SAVE20        | Pay with a voucher | ["@checkout","@regression"] |
      | EXAMPLE_ROW | the id of row OLD5          | Pay with a voucher | ["@checkout"]               |
    And each EXAMPLE_ROW test case has "scenarioId" equal to the id of "Pay with a voucher", and the SCENARIO test case has "exampleRowId" null
    And the first test case's steps are "Given a basket with these items:" with the Background's data table, "When the customer pays by card", and "Then the receipt is:" with the doc string "Total: 28.00"
    And the OLD5 test case's steps are "Given a basket with these items:", "When the customer pays with voucher \"OLD5\"" and "Then the result is \"rejected\""

  @US-4 @AC-4.2 @P2
  Scenario: Placeholders are replaced in the name, step text and doc string
    When Gus creates "features/greeting.feature" with the Greeting source and reads its test cases
    Then there is exactly one test case, named "Greet Ana"
    And its steps are "Given the user \"Ana\"" and "Then the greeting is:" with the doc string "Hello, Ana!"

  @US-4 @AC-4.3 @P1
  Scenario: Backgrounds and Outlines without rows produce no test case
    When Gus creates "features/none.feature" with the source "Feature: None\n  Background:\n    Given a step\n  Scenario Outline: O\n    Given <x>\n    Examples:\n      | x |\n"
    Then "GET {checkout-features}/{that id}/test-cases" returns []

  @US-4 @AC-4.4 @P1
  Scenario: Test cases follow the current version and keep their ids
    Given the ids of the 4 test cases of version 1
    When Gus saves the Payment v2 source with "baseVersion" 1
    Then the feature has 5 test cases, and "Pay by bank transfer" is the second
    And the other 4 have the same ids as before

  @US-4 @AC-4.5 @P1
  Scenario Outline: Everyone with access to the project can read test cases
    When <actor> sends "GET {checkout-features}/{payment}/test-cases" with "{token of <actor>}"
    Then <outcome>

    Examples:
      | actor | outcome                                    |
      | Dan   | the response status is 200                 |
      | Ivy   | the response is a Problem 403 / FORBIDDEN  |

  @US-4 @AC-4.6 @P2
  Scenario: Absent names are empty strings, and an Examples block without a table is empty
    When Gus creates "features/bare.feature" with the source "Feature: Bare\n  Background:\n    Given a step\n  Scenario Outline: O\n    Given <x>\n    Examples:\n"
    Then the Background has "name" "" and "description" "", not null
    And the Examples block has "name" "", "description" "", "header" [] and "rows" []
    And "GET {checkout-features}/{that id}/test-cases" returns []
```

### US-5: Tags (P1)
As a project member, I want to see which tags the project uses and filter features by a tag, so that I can find related behaviour and prepare focused test runs.

```gherkin
Feature: Tags

  Background:
    Given the Scenario fixture
    And Gus has created Payment
    And Gus has created "features/Address.feature" with the Address source
    And Ana has created "features/invoice.feature" with the Invoice source in Billing

  @US-5 @AC-5.1 @P1
  Scenario: The tag catalogue lists every tag in use, with the number of features using it
    When Dan sends "GET /api/projects/{checkout}/tags" with "{token of Dan}"
    Then the response status is 200 and the body is exactly, in this order:
      | name        | featureCount |
      | @address    | 1            |
      | @checkout   | 1            |
      | @regression | 1            |
      | @smoke      | 2            |

  @US-5 @AC-5.2 @P1
  Scenario: The catalogue follows edits
    When Gus saves the Payment source with "@regression" removed and "@wip" added on "Pay by card", with "baseVersion" 1
    Then the tag catalogue of Checkout no longer has "@regression"
    And it has "@wip" with "featureCount" 1

  @US-5 @AC-5.3 @P1
  Scenario: Tags are case-sensitive
    When Gus creates "features/case.feature" with the source "@Smoke\nFeature: Case\n"
    Then the tag catalogue of Checkout has "@Smoke" with "featureCount" 1 and "@smoke" with "featureCount" 2

  @US-5 @AC-5.4 @P1
  Scenario Outline: The feature list can be filtered by one tag
    When Dan sends "GET {checkout-features}?tag=<tag>"
    Then <outcome>

    Examples:
      | tag         | outcome                                                                               |
      | @smoke      | the body lists exactly "features/Address.feature", then "features/checkout/payment.feature" |
      | @regression | the body lists exactly "features/checkout/payment.feature"                           |
      | @billing    | the body is []                                                                        |
      | smoke       | the response is a Problem 400 / VALIDATION_FAILED with an "errors" entry for "tag"   |
      | @a%20b      | the response is a Problem 400 / VALIDATION_FAILED with an "errors" entry for "tag"   |

  @US-5 @AC-5.5 @P1
  Scenario Outline: Everyone with access to the project can read its tags
    When <actor> sends "GET /api/projects/{checkout}/tags" with "{token of <actor>}"
    Then <outcome>

    Examples:
      | actor | outcome                                   |
      | Hal   | the response status is 200                |
      | Ivy   | the response is a Problem 403 / FORBIDDEN |
      | Eve   | the response is a Problem 403 / FORBIDDEN |

  @US-5 @AC-5.6 @P2
  Scenario: Projects don't share tags
    When Ana sends "GET /api/projects/{billing}/tags"
    Then the body is exactly [{"name":"@billing","featureCount":1}]

  @US-5 @AC-5.7 @P2
  Scenario Outline: A malformed tag filter is a validation error
    When Dan sends "GET {checkout-features}?<query>"
    Then the response is a Problem 400 / VALIDATION_FAILED with an "errors" entry for "tag"

    Examples:
      | query                              |
      | tag=@                              |
      | tag=@smoke&tag=@checkout           |
      | tag=@a%C2%A0b                      |
      | tag=@a%C2%85b                      |
      | tag=@ followed by 1,000 "a"s (1,001 code points) |

  @US-5 @AC-5.8 @P2
  Scenario: A tag written twice on one element is listed once
    When Gus creates "features/twice.feature" with the source "@a @b @a\nFeature: Twice\n  Scenario: S\n    Given a step\n"
    Then the feature's "tags" are exactly ["@a","@b"]
    And its test case has the tags exactly ["@a","@b"]
    And the tag catalogue has "@a" with "featureCount" 1
```

### US-6: Archive and restore features (P1)
As a project member, I want to archive features that no longer apply and restore them if needed, so that the project shows current behaviour without losing anything.

```gherkin
Feature: Archive and restore features

  Background:
    Given the Scenario fixture
    And Gus has created Payment
    And Gus has created "features/Address.feature" with the Address source

  @US-6 @AC-6.1 @P1
  Scenario: An archived feature leaves the default list but stays readable
    When Gus sends "POST {checkout-features}/{payment}/archive" with "{token of Gus}"
    Then the response status is 200 and the body has an "archivedAt"
    And "GET {checkout-features}" lists exactly "features/Address.feature"
    And "GET {checkout-features}?archived=true" lists exactly "features/checkout/payment.feature"
    And "GET {checkout-features}/{payment}", ".../versions" and ".../test-cases" by Dan each return 200

  @US-6 @AC-6.2 @P1
  Scenario: An archived feature can't be changed
    Given Gus has archived Payment
    When Gus sends "PUT {checkout-features}/{payment}" with the Payment v2 source, the same path and "baseVersion" 1
    Then the response is a Problem 409 / FEATURE_ARCHIVED
    And the feature's "currentVersion" is still 1

  @US-6 @AC-6.3 @P1
  Scenario: Archived features don't count in tags
    Given Gus has archived Payment
    Then the tag catalogue of Checkout is exactly "@address" with 1 and "@smoke" with 1
    And "GET {checkout-features}?tag=@checkout" returns []

  @US-6 @AC-6.4 @P1
  Scenario: An archived feature's path is free, and restoring it then conflicts
    Given Gus has archived Payment
    When Gus creates "features/checkout/payment.feature" with the Address source
    Then the response status is 201
    When Gus sends "POST {checkout-features}/{payment}/restore"
    Then the response is a Problem 409 / FEATURE_PATH_TAKEN
    And Payment still has an "archivedAt"

  @US-6 @AC-6.5 @P1
  Scenario: Restoring brings the feature back with its ids
    Given the ids of Payment's scenarios and example rows
    And Gus has archived Payment
    When Gus sends "POST {checkout-features}/{payment}/restore"
    Then the response status is 200 and "archivedAt" is null
    And "GET {checkout-features}" lists Payment again
    And Payment's scenarios and example rows have the same ids as before

  @US-6 @AC-6.6 @P1
  Scenario Outline: Only members and admins of the project can archive
    When <actor> sends "POST {checkout-features}/{payment}/archive" with "{token of <actor>}"
    Then <outcome>

    Examples:
      | actor | outcome                                                                   |
      | Ana   | the response status is 200                                                |
      | Gus   | the response status is 200                                                |
      | Hal   | the response is a Problem 403 / FORBIDDEN, and Payment is still active    |
      | Dan   | the response is a Problem 403 / FORBIDDEN, and Payment is still active    |
      | Eve   | the response is a Problem 403 / FORBIDDEN, and Payment is still active    |

  @US-6 @AC-6.7 @P1
  Scenario Outline: Archiving or restoring an unknown feature
    When Gus sends "POST {checkout-features}/{unknown id}/<operation>"
    Then the response is a Problem 404 / FEATURE_NOT_FOUND

    Examples:
      | operation |
      | archive   |
      | restore   |

  @US-6 @AC-6.8 @P2
  Scenario Outline: Archive and restore are idempotent
    Given <state>
    When Gus sends "POST {checkout-features}/{payment}/<operation>"
    Then the response status is 200 and <result>

    Examples:
      | state                     | operation | result                                   |
      | Gus has archived Payment  | archive   | "archivedAt" is unchanged                |
      | Payment is active         | restore   | "archivedAt" is null and "updatedAt" is unchanged |

  @US-6 @AC-6.9 @P2
  Scenario Outline: A real archive or restore records who changed the feature and when
    Given <state>
    When Ana sends "POST {checkout-features}/{payment}/<operation>"
    Then the response status is 200
    And "updatedBy" is Ana's user id and "updatedAt" is later than before
    And a second identical request by Gus leaves "updatedBy" and "updatedAt" unchanged

    Examples:
      | state                    | operation |
      | Payment is active        | archive   |
      | Gus has archived Payment | restore   |

  @US-6 @AC-6.10 @P2
  Scenario: The tag filter works on archived features too
    Given Gus has archived Payment
    When Dan sends "GET {checkout-features}?archived=true&tag=@checkout"
    Then the body lists exactly "features/checkout/payment.feature"
    And "GET {checkout-features}?archived=true&tag=@address" returns []
```

### US-7: A secure, fail-closed and consistent API (P0)
As an operator and as a project's owner, I want every call checked against the caller's current project role, failing closed when that can't be checked, with one error format, so that content is never exposed or changed by mistake.

```gherkin
Feature: Authentication, access checks and errors

  Background:
    Given the Scenario fixture

  @US-7 @AC-7.1 @P0
  Scenario Outline: Requests without a valid token are rejected before any access check
    When a client sends "GET {checkout-features}" with Authorization header <authorization>
    Then the response is a Problem 401 / UNAUTHENTICATED
    And the response has a "WWW-Authenticate" header whose value starts with "Bearer"
    And the access check stub received no request

    Examples:
      | authorization                                                       |
      | (none)                                                              |
      | "Basic Z3VzOnNlY3JldA=="                                            |
      | "Bearer {Gus's token whose exp is 1 hour in the past}"              |
      | "Bearer {Gus's claims signed HS512 with a different 64-byte secret}" |
      | "Bearer {Gus's claims signed HS256 with the shared secret}"         |
      | "Bearer {a token signed with the shared secret without exp}"        |
      | "Bearer {a token signed with the shared secret without sub}"        |
      | "Bearer {a token signed with the shared secret whose sub is 1-1-1-1-1}" |

  @US-7 @AC-7.2 @P0
  Scenario: The caller's token and request ID are forwarded to the access check
    When Gus sends "GET {checkout-features}" with "{token of Gus}" and "X-Request-Id: req-72"
    Then the response status is 200
    And the access check stub received exactly one request: "GET /internal/projects/{checkout}/access" with header "Authorization: Bearer {token of Gus}" unchanged and header "X-Request-Id: req-72"

  @US-7 @AC-7.3 @P0
  Scenario Outline: When the access check can't be completed, the request fails closed
    Given the access check stub <failure>
    When Gus sends "POST {checkout-features}" with path "features/address.feature" and the Address source
    Then the response is a Problem 503 / ACCESS_CHECK_UNAVAILABLE within 3 seconds
    And nothing is stored

    Examples:
      | failure                                                                    |
      | is not running (nothing listens on its port)                               |
      | waits 5 seconds before answering                                           |
      | answers 500                                                                |
      | answers 404                                                                |
      | answers 200 with {"projectId":"{checkout}","orgId":"{acme}","role":"OWNER","archived":false} |
      | answers 200 with a body that isn't JSON                                    |
      | answers 200 for a different "projectId"                                    |

  @US-7 @AC-7.4 @P0
  Scenario: A token that auth-service rejects is rejected here too
    Given the access check stub answers 401 for Gus (for example because Gus's account was suspended)
    When Gus sends "GET {checkout-features}" with "{token of Gus}"
    Then the response is a Problem 401 / UNAUTHENTICATED with a "WWW-Authenticate" header starting with "Bearer"

  @US-7 @AC-7.5 @P0
  Scenario: Role changes apply on the next request (no caching)
    Given Gus has just created "features/address.feature" in Checkout with status 201
    And the access check stub now answers "READER" for Gus in Checkout
    When Gus immediately sends "POST {checkout-features}" with path "features/other.feature" and the Address source
    Then the response is a Problem 403 / FORBIDDEN
    And when the stub then answers 403 for Gus in Checkout, Gus's "GET {checkout-features}" is a Problem 403 / FORBIDDEN

  @US-7 @AC-7.6 @P0
  Scenario Outline: A malformed project id is rejected without an access check
    When Ana sends "GET /api/projects/<id>/features"
    Then <outcome>

    Examples:
      | id                                    | outcome                                                                                       |
      | not-a-uuid                            | the response is a Problem 400 / MALFORMED_REQUEST, and the access check stub received no request |
      | 1-1-1-1-1                             | the response is a Problem 400 / MALFORMED_REQUEST, and the access check stub received no request |
      | {checkout} without its last character | the response is a Problem 400 / MALFORMED_REQUEST, and the access check stub received no request |
      | {unknown id}                          | the response is a Problem 403 / FORBIDDEN                                                      |
      | {checkout} in upper case              | the response status is 200, and the stub received the id in lower case                         |

  @US-7 @AC-7.7 @P0
  Scenario Outline: An archived project is read-only
    Given Gus created "features/address.feature" with the Address source in Legacy while the stub still answered "archived": false
    When <actor> sends <request>
    Then <outcome>
    And the Legacy feature still has "currentVersion" 1 and no "archivedAt"

    Examples:
      | actor | request                                                                            | outcome                                          |
      | Gus   | "GET /api/projects/{legacy}/features"                                              | the response status is 200                       |
      | Gus   | "GET /api/projects/{legacy}/tags"                                                  | the response status is 200                       |
      | Gus   | "POST /api/projects/{legacy}/features" with a new path and the Payment source      | the response is a Problem 409 / PROJECT_ARCHIVED |
      | Gus   | "PUT" on the Legacy feature with the Payment source, its path and "baseVersion" 1   | the response is a Problem 409 / PROJECT_ARCHIVED |
      | Gus   | "POST" on the Legacy feature's ".../archive"                                       | the response is a Problem 409 / PROJECT_ARCHIVED |
      | Dan   | "POST /api/projects/{legacy}/features" with a new path and the Payment source      | the response is a Problem 403 / FORBIDDEN (the role is checked before the archive state) |

  @US-7 @AC-7.8 @P1
  Scenario: Identity headers from the client are ignored
    When Hal sends "POST {checkout-features}" with "{token of Hal}", header "X-User-Id: <Gus's user id>", path "features/address.feature" and the Address source
    Then the response is a Problem 403 / FORBIDDEN

  @US-7 @AC-7.9 @P1
  Scenario Outline: Every error is a Problem with a stable code
    When Gus sends <request> with "X-Request-Id: req-79"
    Then the response is a Problem <status> / <code> with "type" "about:blank", a "title", "status" <status>, a non-empty "detail", "instance" equal to the request path, and "requestId" "req-79"
    And the body contains no stack trace, exception class name, SQL or host name

    Examples:
      | request                                                                 | status | code                   |
      | "GET /api/projects/{checkout}/unknown"                                  | 404    | NOT_FOUND              |
      | "DELETE {checkout-features}/{unknown id}"                               | 405    | METHOD_NOT_ALLOWED     |
      | "POST {checkout-features}" with "Content-Type: text/plain"              | 415    | UNSUPPORTED_MEDIA_TYPE |
      | "POST {checkout-features}" with the body "{not json"                    | 400    | MALFORMED_REQUEST      |
      | "GET {checkout-features}?archived=maybe"                                | 400    | MALFORMED_REQUEST      |
      | "POST {checkout-features}" with path "features/a.feature" and "source": 123 | 400 | MALFORMED_REQUEST      |

  @US-7 @AC-7.10 @P1
  Scenario: An unexpected failure reveals nothing
    Given reading the feature list fails inside the feature service with an unexpected IllegalStateException with message "boom-QA"
    When Gus sends "GET {checkout-features}"
    Then the response is a Problem 500 / INTERNAL_ERROR
    And the body doesn't contain "boom-QA" or "IllegalStateException"

  @US-7 @AC-7.11 @P1
  Scenario: The final paths work through the gateway (manual, end to end)
    Given the gateway, auth-service and scenario-service run with the "dev" profile
    And through the gateway Ana has registered, created organization "Acme" and project "Checkout", and added Gus (an Acme member) to Checkout as MEMBER
    When Gus sends "POST /api/projects/<Checkout's id>/features" through the gateway with the Payment source
    Then the response status is 201
    And Gus's "GET /api/projects/<Checkout's id>/features/<id>/test-cases" through the gateway returns 4 test cases
    And "GET /api/projects/1" through the gateway with Gus's token is still the gateway's 404 Problem without "code"
    And after Ana removes Gus from Checkout, Gus's next "GET /api/projects/<Checkout's id>/features" is a Problem 403 / FORBIDDEN
    And after auth-service is stopped, Gus's next request is a Problem 503 / ACCESS_CHECK_UNAVAILABLE

  @US-7 @AC-7.12 @P2
  Scenario Outline: Values have one spelling; any other is a malformed request
    Given Gus has created Payment
    When Gus sends <request>
    Then the response is a Problem 400 / MALFORMED_REQUEST
    And Payment's "currentVersion" is still 1

    Examples:
      | request                                                                                   |
      | "PUT {checkout-features}/{payment}" with the Payment v2 source, its path and "baseVersion": "1" |
      | "PUT {checkout-features}/{payment}" with the Payment v2 source, its path and "baseVersion": 1.5 |
      | "PUT {checkout-features}/{payment}" with the Payment v2 source, its path and "baseVersion": 2147483648 |
      | "GET {checkout-features}/{payment}/versions/01"                                           |
      | "GET {checkout-features}/{payment}/versions/+1"                                           |
      | "GET {checkout-features}/{payment}/versions/2147483648"                                   |
      | "GET {checkout-features}?archived=yes"                                                    |
      | "GET {checkout-features}?archived=1"                                                      |
      | "GET {checkout-features}?archived=True"                                                   |
      | "GET {checkout-features}?archived="                                                       |

  @US-7 @AC-7.13 @P1
  Scenario Outline: The bearer scheme is matched in any letter case
    When Gus sends "GET {checkout-features}" with the Authorization header "<scheme> {token of Gus}"
    Then the response status is 200

    Examples:
      | scheme |
      | Bearer |
      | bearer |
      | BEARER |
```

**Notes for testers.**
- AC-7.3, row "waits 5 seconds": the read timeout is 2 s, so the answer comes in about 2 s. The row must not take 5 s.
- AC-7.6, upper-case row: the stub records the path it received. scenario-service always sends the canonical lower-case form.
- AC-7.10: stub a **repository** method the real feature service calls (`@MockitoBean`), so the exception passes through the advised service (as in auth-service AC-9.4).

### US-8: Correlated, safe operational logs (P1)
As an operator of scenario-service, I want every log line of a request to carry its request ID and user ID, with timings and business outcomes, so that I can trace one call end to end without user content, personal data or secrets in the logs.

**Test setup for every US-8 scenario:** as auth-service-projects US-9. Log output is captured with `OutputCaptureExtension`. "The aspect logger at DEBUG" means `logging.level.org.scenarioservice.aop=DEBUG` for that test only. "Log of the request" is the captured output between the request's start and its end. "An aspect line" is a line written by the `aop.Logging` logger.

```gherkin
Feature: Operational logging and request correlation

  Background:
    Given the Scenario fixture

  @US-8 @AC-8.1 @P1
  Scenario: A use case logs its entry and exit at DEBUG, with a duration and without arguments
    Given the aspect logger at DEBUG
    When Gus creates Payment with "X-Request-Id: req-81"
    Then the response status is 201
    And the log of the request has exactly one DEBUG aspect line for entering the feature service's create method, and exactly one for leaving it, with a duration in ms
    And no line of the log of the request contains "Customers pay for their basket", "features/checkout/payment.feature" or "First draft"

  @US-8 @AC-8.2 @P1
  Scenario: At the default level, a successful use case writes no aspect line
    Given the default logging levels and Gus has created Payment
    When Dan sends "GET {checkout-features}"
    Then the response status is 200 and the log of the request has no aspect line

  @US-8 @AC-8.3 @P1
  Scenario: A business error is logged once at INFO, with its code and without content
    Given the default logging levels
    When Gus creates "features/broken.feature" with the "step after Examples" source
    Then the response is a Problem 422 / GHERKIN_INVALID
    And the log of the request has exactly one INFO aspect line, and it contains "GHERKIN_INVALID"
    And the log of the request has no stack trace line, and no line contains "Then a late step" or "features/broken.feature"

  @US-8 @AC-8.4 @P1
  Scenario: An unexpected failure is logged exactly once
    Given the aspect logger at DEBUG and the failure of AC-7.10
    When Gus sends "GET {checkout-features}"
    Then the log of the request has exactly one ERROR line, the one from the exception handler
    And the aspect lines of the request name "IllegalStateException" and contain neither "boom-QA" nor a stack trace line

  @US-8 @AC-8.5 @P1
  Scenario Outline: A call slower than the configured threshold is logged at WARN
    Given "app.logging.slow-call-threshold" is <threshold>
    When Gus creates Payment
    Then the response status is 201 and the log of the request has <count> WARN aspect lines

    Examples:
      | threshold | count |
      | 1ms       | 1     |
      | 1h        | 0     |

  @US-8 @AC-8.6 @P1
  Scenario: Every log line of a request carries its request ID and user ID
    Given the aspect logger at DEBUG
    When Gus sends "GET {checkout-features}" with "X-Request-Id: req-86"
    Then every line in the log of the request contains "req-86" and Gus's user id

  @US-8 @AC-8.7 @P1
  Scenario Outline: A missing or invalid request ID is replaced in the logs and never echoed
    Given the aspect logger at DEBUG
    When Gus sends "GET {checkout-features}/{unknown id}" <header>
    Then the response is a Problem 404 / FEATURE_NOT_FOUND without a "requestId" member
    And every line in the log of the request contains the same generated UUID as its request ID
    And the access check stub received that UUID as "X-Request-Id"
    And the sent header value, if any, appears in no line of the log of the request

    Examples:
      | header                             |
      | without "X-Request-Id"             |
      | with "X-Request-Id: bad id QA"     |
      | with an "X-Request-Id" of 129 "a"s |

  @US-8 @AC-8.8 @P1
  Scenario: Request correlation is cleared after each request
    Given Gus sent "GET {checkout-features}" with "X-Request-Id: req-88"
    When the request has completed
    Then the logging MDC of the thread has neither "requestId" nor "userId"
    And a following anonymous "GET {checkout-features}" has no log line containing "req-88" or Gus's user id

  @US-8 @AC-8.9 @P1
  Scenario: No content, personal data or secrets are logged during a full flow
    Given the logger "org.scenarioservice" at DEBUG
    When Gus creates Payment, saves the Payment v2 source, fails to save the "inconsistent cells" source, reads the test cases and the tags, archives and restores Payment
    And Gus sends one request with an expired token
    And the access check stub answers 500 once
    Then the captured log contains none of these values:
      | every JWT used, and every Authorization header value                     |
      | "gus@example.com" in any letter case                                     |
      | "Customers pay for their basket", "Pay by bank transfer", "Total: 28.00" |
      | "features/checkout/payment.feature", "First draft", "@regression"        |
      | the text of any parse error message                                       |
      | the access check stub's URL and port                                     |

  @US-8 @AC-8.10 @P1
  Scenario: An unavailable access check is logged once at WARN, without a stack trace
    Given the default logging levels and the access check stub answers 500
    When Gus sends "GET {checkout-features}"
    Then the response is a Problem 503 / ACCESS_CHECK_UNAVAILABLE
    And the log of the request has exactly one WARN line about the access check, no ERROR line and no stack trace line

  @US-8 @AC-8.11 @P2
  Scenario: The aspect can be switched off
    Given the aspect logger at DEBUG and "app.logging.enabled" is false
    When Dan sends "GET {checkout-features}"
    Then the response status is 200 and the log of the request has no aspect line
```

**AC count:** 83 acceptance criteria across 8 stories: US-1 15, US-2 6, US-3 14, US-4 6, US-5 8, US-6 10, US-7 13, US-8 11. The 12 ACs added on 2026-10-14 come from D19 and its follow-ups.
- P0 (**21**): AC-1.1–1.4, 1.6; AC-2.1–2.4; AC-3.1, 3.2, 3.5, 3.6, 3.8; AC-7.1–7.7.
- P1 (**42**): AC-1.7–1.9, 1.11; AC-2.5, 2.6; AC-3.3, 3.4, 3.7, 3.9, 3.10; AC-4.1, 4.3–4.5; AC-5.1–5.5; AC-6.1–6.7; AC-7.8–7.11, 7.13; AC-8.1–8.10.
- P2 (**20**): AC-1.5, 1.10, 1.12–1.15; AC-3.11–3.14; AC-4.2, 4.6; AC-5.6–5.8; AC-6.8–6.10; AC-7.12; AC-8.11.

## Changes to existing code (approved by the user on 2026-10-14)

Every file in `scenario-service/` at HEAD `6fd4434` is the user's code, and so is everything in `api-gateway/`. Only the items below may change them. Numbering uses the prefix **SE** (scenario-service) and **GE** (gateway) to avoid clashing with auth-service's E1–E80 and EG1.

**User decision, 2026-10-14:** Q1 to Q10 approved as recommended, so SE1–SE7 and GE1–GE3 are approved, and so is X1 (pm edits the gateway spec's D2 route table, docs only). With the Testcontainers decision (`auth-service-persistence` Q1 = A), **SE8 is approved**, and SE2's and SE7's wording follows that pattern. Q8 changed: no database or role is created for tests.

| ID | Change | Files | Why | Risk | Task |
|---|---|---|---|---|---|
| SE1 | `build.gradle`: delete line 22 (`spring-boot-starter-data-elasticsearch`) and line 32 (`spring-boot-starter-data-elasticsearch-test`). | `scenario-service/build.gradle` | D18: unused; search is a later phase. | Low | T2 |
| SE2 | `build.gradle`: add `implementation 'org.springframework.boot:spring-boot-starter-flyway'` and `runtimeOnly 'org.flywaydb:flyway-database-postgresql'` (no versions; Boot BOM, Flyway 12.4.0). The second line only if the starter doesn't already bring the module, and `testImplementation 'org.springframework.boot:spring-boot-starter-flyway-test'` only if `@DataJpaTest` doesn't run Flyway without it (both as persistence E61/E61b; report what was verified). | same | D14, D18. | Low | T2 |
| SE3 | `build.gradle`: add `implementation 'org.springframework.boot:spring-boot-starter-aspectj'` (no version). | same | D17, as auth-service E57. | Low | T2 |
| SE4 | `build.gradle`: add `implementation 'io.jsonwebtoken:jjwt-api:0.12.5'`, `runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.12.5'`, `runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.12.5'` (the lines auth-service has at 27–29). | same | D6. | Low | T2 |
| SE5 | `build.gradle`: add `implementation 'io.cucumber:gherkin:28.0.0'` and `implementation 'io.cucumber:messages:24.1.0'` (or the version the user approves under Q5). | same | D7. | Low–medium (new library, pinned) | T2 |
| SE6 | Replace `src/main/resources/application.properties` with `application.yaml` (with `spring.application.name: scenario-service`, `server.port: 8082`, and the D4, D6, D14 and D17 settings, including the pattern's item 12 `lock_timeout` and `logServerErrorDetail=false`) and a new `application-dev.yaml` (`# DEV ONLY`: the shared dev JWT secret, and pattern item 5's database settings `jdbc:postgresql://localhost:5432/scenario_service` / `scenario_service` / `scenario_service_dev_only`, and `org.hibernate.SQL` at DEBUG). Delete `application.properties`. | `src/main/resources/application.properties` (delete); `application.yaml`, `application-dev.yaml` (create) | One configuration format across the three services; port 8082 (gateway D3). | Low | T2 |
| SE7 | `ScenarioServiceApplicationTests`: keep the class and the empty `contextLoads()`; change only its annotations, so the context starts through the shared test meta-annotation (Testcontainers database via `@ServiceConnection`, test secret, stub URL) from the test `support` package. | `src/test/java/org/scenarioservice/ScenarioServiceApplicationTests.java` | It fails today (no datasource), and the build must be green. | Low | T2 |
| SE8 | **Approved 2026-10-14 (Testcontainers decision).** `build.gradle`: add `testImplementation 'org.springframework.boot:spring-boot-testcontainers'` and `testImplementation 'org.testcontainers:testcontainers-postgresql'` (no versions; Boot BOM, Testcontainers 2.0.5). `testcontainers-junit-jupiter` only if verification shows the singleton needs it (report). | same as SE1 | D15; database pattern item 14; persistence E62. | Low (needs a running Docker daemon for every build) | T2 |
| GE1 | Gateway `application.yaml`: in route `scenarios`, change `Path=/api/scenarios/**` to `Path=/api/scenarios/**,/api/projects/*/features,/api/projects/*/features/**,/api/projects/*/tags`, and update the comment above it ("scenario-service, decided in docs/specs/scenario-service.md D2; /api/scenarios/** is kept for existing tests"). Nothing else changes. | `api-gateway/src/main/resources/application.yaml` (lines 38–42) | D2. | Low–medium (cross-service; every existing gateway test must pass unchanged) | T13 |
| GE2 | Gateway contract: the route table (line 19) and `x-routes` entry `scenarios` (lines 457–466) list the new patterns, `status: active`, `contract: scenario-service.openapi.yaml` with a note naming version 1.0.0; the sentence "`/api/projects/**` is not routed in phase 1" (line 31) becomes "`/api/projects/**` is not routed, except the scenario-service patterns above". `info.version` 0.2.3. | `docs/api/api-gateway.openapi.yaml` | Contract readers see the final route. | Low (docs) | T13 |
| GE3 | Gateway: one new test class (backend-swe's, not qa's) proving that `GET /api/projects/{id}/features`, `.../features/{id}/test-cases` and `/api/projects/{id}/tags` reach the scenario-service stub unchanged, and that `/api/projects/1`, `/api/projects/{id}` and `/api/projects/{id}/runs` are still the gateway's 404 with a token. | create `api-gateway/src/test/java/org/apigateway/ScenarioServiceRoutesTest.java` | Proves GE1. | Low | T13 |

**Will not be touched** (any change needs a new approval):
- `ScenarioServiceApplication.java`, `settings.gradle`, the Gradle wrapper, `gradlew`, `gradlew.bat`, `.gitattributes`, `.gitignore`.
- In `build.gradle`: the plugins, `group`, `version`, `description`, the toolchain, `repositories`, the test task, and every dependency line not named in SE1–SE5 and SE8.
- Everything in `auth-service/`, `report-service/` and `docs/api/auth-service.openapi.yaml`.
- Everything in `api-gateway/` except GE1–GE3, including every existing gateway test (backend-swe's and qa's).

## Engineering guardrails (binding for every task)

auth-service-projects' guardrails apply, adapted below. A task isn't done while any rule is broken. If a rule blocks you, stop and ask in your handoff; don't work around it.

### Scope control
- **G1. Allowed files only.** A task creates, modifies or deletes only the files on its **Allowed files** line. New main code goes only into the packages of the package layout below, under `scenario-service/src/main/java/org/scenarioservice/`. Tests go under `scenario-service/src/test/java/org/scenarioservice/**`, mirroring main, with `support/` for shared helpers. `scenario-service/src/test/resources/` isn't used. Migrations go under `scenario-service/src/main/resources/db/migration/`.
- **G2. Existing files change only through approved SE and GE items**, exactly as described. If an item turns out to need more, stop and ask.
- **G3. Files from earlier tasks of this spec** may change only if the task's Allowed files line names them. Tests may only be strengthened.
- **G4. No drive-by changes.** No refactors, reformatting, renames or "while I'm here" fixes outside the task. Report other defects as candidates.
- **G5. No version or dependency changes** except SE1–SE5 and SE8 (with SE2's and SE8's conditional lines). No other library (no WireMock, springdoc, actuator, H2, Caffeine).
- **G6. Tests stay honest.** No `@Disabled`, no skipping assumptions, no weakened or deleted tests. `cd scenario-service && ./gradlew build` is green at the end of every task from T2 on. Report total, failed and skipped counts.
- **G7. No git operations.** No `git add`, `commit`, `stash`, `restore`, `checkout`, `reset`, `mv` or `rm`; the user commits. Each handoff includes `git status --short`, `git diff --stat`, and every new or deleted file with a one-line summary.
- **G8. Self-review the diff** before handing off: files outside the Allowed files line, unrelated whitespace, debug output, commented-out code, unused imports, secrets other than the test-only secret in test code and the DEV ONLY values in `application-dev.yaml`.
- **G9. Service boundaries.** scenario-service never reads another service's database, code or configuration, and calls auth-service only through C5. No shared library: patterns are copied (D6, D16, D17).
- **G10. Environment.** Tests use only the Testcontainers database and scratch databases inside that container. Don't create, drop or alter databases or roles on any local server (Postgres.app on 5432, or the `cucumber-postgres` dev container). The user runs the README's dev setup. Flyway `clean` is never used.
- **G11. Migrations are append-only** once they've passed a review point (database pattern item 6). A fix is a new `V<n>`, with pm's OK.

### PL. Layering (binding for every task)

| Layer | Owns | Never |
|---|---|---|
| **Controller** | HTTP only: mapping, `@Valid` on request records, reading the principal's user id, **exactly one service call per handler**, turning the result into status, `Location` and body | Repositories, entities, the access checker, the parser, hand-written validation, reflection, business branching |
| **Service** (`service`, `service/impl`) | Use cases: the access step first (D5), then business rules, transactions (P2) opened **after** the access step (P13), repository calls, mapping entities to response records inside the transaction, typed exceptions | HTTP types, `SecurityContextHolder`, tokens, persistence mechanics (`EntityManager`, native SQL, lock modes), `io.cucumber.*` types |
| **Repository** | Data access only: derived and annotated queries, the project advisory lock, counts | Business rules, mapping to records |
| **Gherkin adapter** (`gherkin`) | Parsing text into this service's own immutable model, parse errors with line and column, pickle compilation for test cases. Pure: no Spring beans other than the adapter itself, no JPA, no HTTP | Persistence, authorization, logging of content |
| **Access checker and C5 client** (`security`) | Reading the caller's token, calling C5, mapping its answer (D4), throwing typed `ApiException`s | Business rules beyond role, archived and availability; caching |
| **Advice** (`ApiExceptionHandler`) | The only place that turns exceptions into Problems | Business decisions |
| **Configuration** | Beans, property records and their registration, the security chain, filters | Business logic |

### Package layout (all new, under `org.scenarioservice`)

| Package (main and test) | Contents |
|---|---|
| `controller` | Feature and tag endpoints |
| `service`, `service/impl` | Feature, test-case and tag use cases (interfaces and implementations) |
| `gherkin` | The parser adapter, its model and pickle mapping |
| `repository` | Spring Data repositories, the project advisory lock |
| `model` | JPA entities for the nine tables |
| `enums` | `ProjectRole` (with explicit rank and `isAtLeast`, as auth-service E30), `ScenarioKind`, `StepKeyword`, `StepArgumentType`, `TagTargetType`, `TestCaseType` |
| `dto/request`, `dto/response` | Records, without `@Builder` unless something calls it |
| `exception` | `ErrorCode`, `ApiException`, one typed exception per code, `Problems`, `ApiExceptionHandler` |
| `security` | JWT validator and filter, the principal, the Problem entry point and access-denied handler, the access checker, the C5 client |
| `configuration` | Security chain, properties records and `PropertiesConfiguration`, MDC filters, the `Clock` and `RestClient` beans, the canonical-UUID path conversion |
| `aop` | `Logging` |
| `support` (test only) | Test settings, token factory, access check stub, Scenario fixture, Gherkin sources |

### Patterns and practices
auth-service's P1–P12 apply (layering, transactions, DTOs not entities, validation, errors, typed configuration, constructor injection, Java hygiene, HTTP, tests, "don't guess framework APIs"). Additions:
- **P12 (verify, don't guess).** Verify and report: the gherkin 28 parser and pickle API; Hibernate 7's `jsonb` mapping with Boot 4.1's Jackson 3; Boot 4.1's Flyway auto-configuration with `spring-boot-starter-flyway`; the Spring Security 7 filter order for the MDC filters; that `RestClient` timeouts come from the configured request factory.
- **P13. Remote call before the transaction; one lock per project for writes.** The access check never runs inside a database transaction. Every write (create, save, archive, restore) takes the project advisory lock through a repository method as the first statement of its transaction, then checks paths and versions.
- **P14. One owner per concern.** Only the `gherkin` package imports `io.cucumber.*`. Only the access checker calls C5. Only `ProjectRole.isAtLeast` compares roles. Only the advice builds Problems.
- **P15. Test first** (D15).
- **P16. No content in logs** (D17), and no `toString()` on entities or records that hold source text. Override it, or keep the text out of `toString`.
- **P17. Database constraints are the safety net.** A unique violation that reaches the advice is 409 `CONFLICT`, logged at WARN without the constraint's message (it can contain a path), never a 500.

### Fix rounds after QA
auth-service's F1–F4 apply unchanged. qa's tests under `scenario-service/src/test/java/org/scenarioservice/qa/` are read-only for backend-swe.

## Tasks for backend-swe

Do the tasks in order, batch by batch. **Stop at each review point** and hand off; the user reviews the diff before the next batch. Every SE and GE item is approved (2026-10-14). Run `cd scenario-service && ./gradlew build` at the end of every task (T2 onward), with Docker running. Every task follows PL.

**Gate (user decision, 2026-10-14: auth-service phase 4 goes first).** T1 is docs only and may run now, in parallel with phase 4. **T2 and every later task start only after `auth-service-persistence` has passed its last review point (E).** T2 copies its proven configuration and test support: the Testcontainers singleton, the connection budget, `lock_timeout`, `logServerErrorDetail`, the clock, and the enum CHECKs. If phase 4 changes a pattern item on the way (for example the starting pool sizes), the version proven at review point E applies, and pm records the change here.

### Batch A: foundation (review point A)

**T1. Contract 1.0.0 (docs only).** Items: none (new file).
- Change: write `docs/api/scenario-service.openapi.yaml` (OpenAPI 3.1): every operation in D2 with its parameters, request and response schemas and status codes, the D16 error table and order, `parseErrors` and `currentVersion` members, the D8–D13 rules in descriptions, the bearer scheme with D6's token rules, the `X-Request-Id` header, servers `http://localhost:8080` (gateway) and `http://localhost:8082` (direct). Use the conventions `auth-service-persistence` D6 and D11–D13 define (its planned contract 1.3.0); if that file isn't updated yet, use the spec text:
  - a `Timestamp` schema (UTC with `Z`, microseconds);
  - lengths in code points;
  - `MALFORMED_REQUEST` for a number or boolean where a string is expected;
  - canonical UUIDs (either case).

  `info.version` 1.0.0.
- Done when: the file parses as OpenAPI 3.1 (name the validator, or say none was available), and every AC's operation, status and code appears.
- **Allowed files:** create `docs/api/scenario-service.openapi.yaml`.
- **Must not:** document behaviour not in this spec (raise gaps with pm); touch code.
- **Done (2026-10-14):** contract 1.0.0 is valid OpenAPI 3.1, and backend-swe's coverage script shows all 71 ACs (130 rows). Its 13 gaps are decided in D19.

**T1b. Contract 1.0.1: the D19 decisions (docs only).** Items: none. Runs now, in parallel with auth-service phase 4, so frontend-swe starts from the final rules.
- Change `docs/api/scenario-service.openapi.yaml`:
  1. **`FeaturePath`** (D19 #1, #10): in the pattern, the last segment's `[^/\\\u0000-\u001F\u007F-\u009F]*\.feature$` becomes `[^/\\\u0000-\u001F\u007F-\u009F]+\.feature$` (at least one character before the suffix). The description adds: "`.feature` exactly, in lower case (`.FEATURE` is 400); `features/.feature` is 400; control characters are U+0000–U+001F and U+007F–U+009F".
  2. **`updatedAt` and `updatedBy`** on `Feature` and `FeatureSummary`, and the save, archive and restore descriptions (D19 #3): "Changed by a new version, a path change (even without a new version), and a real archive or restore; unchanged by an identical save, archiving an archived feature, or restoring an active one."
  3. **The `tag` parameter** (D19 #5): pattern `^@[^\s\u0000-\u001F\u007F-\u009F]+$`, `maxLength: 1000` (code points). The description adds: "Given at most once; a repeated `tag`, a bare `@`, whitespace (ECMAScript `\s`: Unicode White_Space and U+FEFF) or a control character is 400 `VALIDATION_FAILED` with an `errors` entry for `tag`." The list operation's text confirms `archived=true&tag=` (D19 #4); it already does, so check only.
  4. **`baseVersion`** (D19 #6): `maximum: 2147483647`. The description adds: "Must be a JSON integer: a string (`"1"`), a fraction (`1.5`, `1.0`) or a value above 2147483647 is 400 `MALFORMED_REQUEST`; 0 and negative numbers stay `VALIDATION_FAILED`." Add the same to the `MALFORMED_REQUEST` row of the error table.
  5. **The `{version}` path parameter** (D19 #6): `type: string`, pattern `^[1-9][0-9]{0,9}$`, and the description "at most 2147483647; leading zeros, a sign, 0 or a larger value is 400 `MALFORMED_REQUEST`".
  6. **The `archived` parameter** (D19 #6): the description adds "exactly `true` or `false`, in lower case; `yes`, `on`, `1`, `True` or an empty value is 400 `MALFORMED_REQUEST`" (use `type: string`, `enum: ['true', 'false']`, `default: 'false'`, if that states it best).
  7. **`Examples.header` and `Examples.rows`** (D19 #7): "`[]` when the block has no table". All `name` and `description` members: "always a string, `""` when absent, never `null`" (check only; already so).
  8. **`GHERKIN_INVALID`** (D19 #8): add "A text without a Feature, including whitespace-only or comment-only text, has exactly one entry: line 1, column 1, message `The text has no Feature`."
  9. **`VersionMessage`** (D19 #9): "Trimmed of Unicode-blank characters (Unicode whitespace including NBSP, zero-width space, BOM and NEL) before storing; nothing left means no message. The 500 code-point limit counts the value as sent, before trimming; the control-character rule applies to the trimmed value, so an inner line break is 400 and a trailing one is removed."
  10. **Every `tags` array** (`Feature`, `FeatureSummary`, `Rule`, `Scenario`, `Examples`, test cases) (D19 #11): "each tag once, at its first position".
  11. **`bearerAuth`** (D19 #12): "The scheme is matched case-insensitively (`bearer`, `BEARER`); clients should send `Bearer`."
  12. **`Timestamp`** (D19 #13): no change (it already allows 0–6 fractional digits); add "compare instants, not strings".
  13. Add the new AC ids (AC-1.13, 1.14, 3.13, 3.14, 4.6, 5.7, 5.8, 6.9, 6.10, 7.12, 7.13) where their operations are described. `info.version` **1.0.1**, with a change note: "1.0.1: D19 clarifications; stricter `path`, `tag`, `{version}`, `archived` and `baseVersion`; no shape changes."
- Done when: the file parses as OpenAPI 3.1, and the coverage script shows all 82 ACs.
- **Allowed files:** modify `docs/api/scenario-service.openapi.yaml`.
- **Must not:** change any shape, path or status beyond the 13 items above; touch code.

**T2. Build, configuration, Flyway V1, Testcontainers, a green build.** Items: SE1–SE8.
- **Precondition:** the gate above (auth-service phase 4 review point E passed), and Docker running.
- Change:
  - `build.gradle` per SE1–SE5 and SE8;
  - `application.yaml` and `application-dev.yaml` per SE6:
    - port 8082; `app.jwtSecret: ${JWT_SECRET}`;
    - datasource from `SCENARIO_DB_*` (no defaults outside `dev`); `lock_timeout` 5 s and `logServerErrorDetail=false` with the mechanism auth-service phase 4 verified;
    - `ddl-auto: validate`; `open-in-view: false`; Boot's Flyway defaults;
    - `app.auth-service.base-url: ${AUTH_SERVICE_URL:http://localhost:8081}`, `connect-timeout: 1s`, `read-timeout: 2s`;
    - the D17 logging keys; `spring.mvc.log-resolved-exception: false` (auth-service E58's lesson);
  - `V1__create_scenario_service_schema.sql`: the nine tables of the ERD's `scenario_service` group with the `proposed (scenario-service)` changes, named per pattern item 8:
    - enums as `varchar(32)` plus `ck_<table>_<column>_values`;
    - `jsonb`, `timestamptz`;
    - foreign keys with D14's delete rules and an index on every foreign-key column;
    - the partial unique index `uk_features_project_id_path_lower` and the other unique indexes;
  - test `support`:
    - the singleton `postgres:17` container (pattern items 3, 14) and the shared test meta-annotation with `@ServiceConnection`;
    - the connection budget (item 16);
    - the test secret and stub URL;
    - `ScenarioServiceApplicationTests` per SE7;
  - `scenario-service/README.md`:
    - local dev setup: the `cucumber-postgres` container from persistence D5, plus this service's `CREATE ROLE scenario_service` and `CREATE DATABASE scenario_service ... LOCALE_PROVIDER builtin BUILTIN_LOCALE 'C.UTF-8' TEMPLATE template0` via `docker exec`;
    - environment variables, the `dev` profile;
    - "Docker must run for `./gradlew build`".
- Tests:
  - `contextLoads` green;
  - a smoke test that the server is major 17 and the database uses `builtin` / `C.UTF-8`;
  - the schema test (pattern item 17) pinned to the ERD;
  - start-up fails without `JWT_SECRET` and without `SCENARIO_DB_URL` outside `dev`, on a scratch database.
- Done when:
  - the build is green;
  - `./gradlew dependencies --configuration runtimeClasspath` shows Flyway (and its PostgreSQL module), AspectJ, jjwt 0.12.5, gherkin and messages at the approved versions, and no Elasticsearch; the test classpath shows the two Testcontainers artefacts and no H2;
  - the handoff includes both outputs, the distinct context count and the peak connections.
- **Stop conditions:**
  - Docker isn't running (report; never skip tests);
  - Flyway doesn't run with the starter and module;
  - any dependency resolves to a version other than the one approved (in particular `messages`);
  - a pattern item can't be applied as auth-service phase 4 proved it.
- **Allowed files:**
  - modify `scenario-service/build.gradle` (SE1–SE5 and SE8 lines only) and `ScenarioServiceApplicationTests.java` (SE7);
  - delete `src/main/resources/application.properties`;
  - create `src/main/resources/application.yaml`, `application-dev.yaml`, `db/migration/V1__create_scenario_service_schema.sql` and `scenario-service/README.md`;
  - create test classes in `support/`, and the smoke, schema and start-up tests.
- **Must not:**
  - touch `ScenarioServiceApplication.java` or `settings.gradle`;
  - add entities (T7) or any other dependency;
  - create databases or roles on any local server (G10);
  - use PostgreSQL `ENUM` types, `json`, or `IF [NOT] EXISTS`.

**T3. Errors, authentication and canonical ids.** Items: none beyond T2's.
- Change: `ErrorCode` (D16 codes), `ApiException`, `Problems`, `ApiExceptionHandler` (copied from auth-service's design: same Problem shape, `requestId` echo rule, field-error ordering, 405/406/415/400 mappings, `INTERNAL_ERROR` logged once at ERROR with a stack trace, unique violations as 409 `CONFLICT` per P17); the JWT validator and filter (D6); the principal; the entry point and access-denied handler; the security chain (stateless, no CSRF, no CORS, every request authenticated, `/error` permitted); the canonical-UUID path conversion (D16).
- ACs: AC-7.1 (without the "stub received no request" line, completed in T5), AC-7.9, AC-7.10 and AC-7.13, against a **test-only** probe controller and service in test sources. They are re-run against the real endpoints in T8.
- **Server-enforced rules (D19 follow-ups), set up here with unit tests:**
  - strict JSON integers: no coercion from strings or fractions, so `1.0` is rejected, and so is overflow above int32;
  - a strict `archived` converter (`true` or `false` only);
  - a strict positive-int32 `{version}` converter, so values above 2147483647 are rejected.

  Each maps to 400 `MALFORMED_REQUEST`.
- Done when: those tests pass, a unit test covers every D6 token rule, and the build is green.
- **Allowed files:** create classes in `exception/`, `security/`, `configuration/`; create tests and test-only probes.
- **Must not:** look up users; read `X-User-Id`, `email` or `orgs`; log tokens or claims; add CORS.

**T4. Logging.** Items: none beyond T2's.
- Change: per D17: `aop/Logging`, the two MDC filters, `LoggingProperties` registered in `PropertiesConfiguration`, the correlation pattern. Copy auth-service's behaviour; don't import its code.
- Test first for AC-8.6 and AC-8.7, with a test-only `@Service` probe in `org.scenarioservice.service`.
- ACs: AC-8.2, AC-8.4, AC-8.5, AC-8.6, AC-8.7, AC-8.8, AC-8.11 (against the probe; re-run against real endpoints in T8).
- Verify and report: the filter order relative to the security chain; that the aspect wraps `@Transactional`.
- **Allowed files:** create `aop/Logging.java`, the MDC filters, `LoggingProperties` and `PropertiesConfiguration` in `configuration/`; modify `application.yaml` (logging keys only, if T2 didn't add them); create tests.
- **Must not:** advise controllers, filters, the access checker or the C5 client; log arguments, return values or exception messages; add Logback XML.

**T5. Access checker and C5 client.** Items: none beyond T2's.
- Change: the C5 client (`RestClient` with D4's timeouts, forwarding `Authorization` and `X-Request-Id`, no retry, no cache) and the access checker (require read, require write; D4 mapping; one WARN line on failure; typed exceptions). `ProjectRole` with ranks. Test `support`: the access check stub, the token factory, the Scenario fixture.
- Test first: every D4 mapping row, and "no caching" (two calls, two stub requests), as component tests against the stub.
- ACs: AC-7.1's last line; AC-7.2 to AC-7.5, AC-7.8 and AC-8.10 as component tests (the end-to-end versions run in T8).
- Done when: those tests pass; a test proves the checker holds no database connection during the C5 call (for example, the call succeeds with the Hikari pool exhausted by other threads); the build is green.
- **Stop condition:** the configured timeouts don't apply to `RestClient` in Boot 4.1 without an extra dependency. Report what you verified.
- **Allowed files:** create classes in `security/`, `enums/ProjectRole.java`, the `RestClient` bean and `AuthServiceProperties` in `configuration/`; modify `PropertiesConfiguration` (one registration); create tests and `support/` helpers.
- **Must not:** cache answers; retry; call C5 through the gateway; log the token, the `Authorization` value or the URL.

**Review point A.** The user reviews the build changes, configuration, migration, security, logging and access checker. frontend-swe can start from contract 1.0.1 (T1, T1b), which is available before this review point.

### Batch B: the Gherkin core (review point B)

**T6. Gherkin adapter.** Items: none.
- Change: the `gherkin` package: parse text to this service's immutable model (feature with metadata, tags, children in order, rules, backgrounds, scenarios and outlines, steps with keyword text and type, data table or doc string with media type, examples with header and rows, lines), D8's validity rule and parse errors (1-based line and column, sorted), and the pickle compilation that D13 needs (mapping pickles to scenario and example-row positions).
- Tests (unit, no Spring context): the Payment, Address, Greeting and French sources; every broken source with its line and column (report any column that differs from AC-1.4); CRLF and trailing spaces; pickle names, steps, arguments and tags for Payment and Greeting.
- **Stop conditions:** the parser reports errors without a location; its pickles can't be mapped back to scenarios and example rows; the API needs anything beyond `gherkin` and `messages`.
- **Allowed files:** create classes in `gherkin/`; create tests.
- **Must not:** let any other package import `io.cucumber.*`; log content.

**T7. Persistence model.** Items: none.
- Change:
  - entities for the nine tables: `Instant`, `jsonb`, `@Enumerated(STRING)`, `@Column(nullable, length)` mirroring V1, and `toString` without source text (P16);
  - repositories, including the project advisory lock as a native query and the queries D11–D13 need;
  - the microsecond UTC `Clock` bean (pattern item 11).

  `ddl-auto: validate` must pass against `V1`.
- Tests: repository tests on the test container:
  - round-trip of every table, with timestamps equal to the clock's microsecond values after reading back;
  - the partial unique index: two active features with paths differing only in case fail, and an archived one doesn't block;
  - `(feature_id, version)` uniqueness;
  - the advisory lock serialises two transactions, and a wait beyond `lock_timeout` becomes 409 `CONFLICT`.
- **Stop conditions:** Hibernate can't map `jsonb` with the classpath's Jackson without a new dependency; `validate` reports a mismatch that needs a migration change. Report; don't edit `V1` after T2's handoff without pm's OK.
- **Allowed files:** create classes in `model/`, `repository/`, the `Clock` bean in `configuration/`; create tests.
- **Must not:** change `V1` (a correction is `V2`, with pm's OK); add `@Transactional` to repositories beyond what Spring Data provides.

**T8. Create, list and view.** Items: none.
- ACs: AC-1.1 to AC-1.15 (AC-1.15: the parser's errors win over "no Feature"); AC-2.1 to AC-2.6; an end-to-end test that each `archived` spelling of AC-7.12 is 400 (server-enforced); AC-7.1 to AC-7.10 and AC-7.13 end to end (AC-7.7's PUT and archive rows are completed in T9 and T12; AC-7.12's `archived` rows here, its `baseVersion` and `{version}` rows in T9); AC-8.1, AC-8.3, and AC-8.2 to AC-8.8, AC-8.10, AC-8.11 re-run against the real endpoints.
- Done when: those tests pass; AC-1.10 is repeated at least 5 times and is green 3 runs in a row; the build is green; the test probes from T3 and T4 may stay as test-only classes.
- **Allowed files:** create the feature controller, service, request and response records, and typed exceptions; modify `exception/ErrorCode.java` (constants only) and the T7 repositories (add methods only); create tests.
- **Must not:** cache access answers; call C5 inside a transaction; add pagination or search.

**T9. Save and history.** Items: none.
- ACs: AC-3.1 to AC-3.14; AC-7.7's PUT row; AC-7.12's `baseVersion` and `{version}` rows. Their server-enforced rules each get an end-to-end test, including `"baseVersion": 1.0` (not only `1.5`) and `/versions/2147483648`.
- Done when: those tests pass; AC-3.11 repeated at least 5 times, green 3 runs in a row; the build is green.
- **Allowed files:** the T8 feature files; the T7 repositories (add methods only); create tests.
- **Must not:** update or delete a `feature_versions` row; let scenario or example-row ids change when D9 says they're kept.

**Review point B.** The user reviews the parser adapter, the model and the create, view, save and history behaviour.

### Batch C: test cases, tags, archive, gateway (review point C)

**T10. Test cases.** ACs: AC-4.1 to AC-4.6. **Allowed files:** create the test-case service, controller handler (in the feature controller or its own), response records; the `gherkin` adapter (pickle mapping only); create tests. **Must not:** store test cases.

**T11. Tags.** ACs: AC-5.1 to AC-5.8, and AC-2.2's `tags` member re-checked. Server-enforced: a repeated `tag` is 400, tested on the raw parameter count (Spring would otherwise join the values). A tag containing U+200B is accepted and matched exactly (D19 follow-ups). **Allowed files:** create the tag controller, service and records; the T8 feature files (the list filter only); the T7 repositories (add methods only); create tests. **Must not:** implement tag expressions.

**T12. Archive and restore.** ACs: AC-6.1 to AC-6.10; AC-7.7's archive row; **AC-8.9** (the full flow, now that every endpoint exists). **Allowed files:** the T8–T11 files; create tests. **Must not:** hard-delete anything.

**T13. Gateway route.** Items: GE1, GE2, GE3.
- Change: per GE1 and GE2. Create GE3's test.
- Done when: `cd api-gateway && ./gradlew build` is green with **every existing gateway test unchanged** (report the counts before and after), and the new test passes.
- **Stop condition:** any existing gateway test fails or needs a change, or the gateway's `Path` predicate doesn't accept `/api/projects/*/features/**`. Stop and report; don't adapt a test.
- **Allowed files:** modify `api-gateway/src/main/resources/application.yaml` (the `scenarios` route's `Path` line and its comment only) and `docs/api/api-gateway.openapi.yaml` (GE2 lines and the version only); create `api-gateway/src/test/java/org/apigateway/ScenarioServiceRoutesTest.java`.
- **Must not:** touch any other gateway file, route, filter, test or setting.

**T14. Contract sync, README, end to end.** Items: none.
- Change: sync `docs/api/scenario-service.openapi.yaml` with the final behaviour (1.0.2: at least the D19 follow-up on `GHERKIN_INVALID`, "exactly one 'no Feature' entry only when the parser reports no error", and AC-1.15); finish the README (API overview, D4 behaviour, troubleshooting).
- **Precondition:** the user has created the dev role and database from the README (the shared `cucumber-postgres` container already serves auth-service).
- Run AC-7.11 manually through the gateway with the real auth-service on the `dev` profile, and record each status. Report p95 for `GET {checkout-features}` and `GET .../{payment}` with 50 features in the project (informational; target 200 ms locally, including the C5 call).
- Done when: `./gradlew clean build` is green in scenario-service and api-gateway; the contract parses; the manual results and measurements are in the handoff.
- **Allowed files:** modify `docs/api/scenario-service.openapi.yaml`, `scenario-service/README.md`.
- **Must not:** change code or configuration to make a manual step pass. Report instead.

**Review point C.** Then qa verifies.

### pm tasks (independent of the batches)
- **X1. Done (2026-10-14).** `docs/specs/api-gateway.md` D2 route table: the `scenarios` row lists the GE1 patterns. Docs only; that spec's Breakdown row was left unchanged, because the approval covered the table only.
- **X2. Done (2026-10-14).** D14, D15, D18, SE2, SE6–SE8, G5, G10, G11, T2, T7 and the ERD notes are aligned with "Database pattern for every service" in `docs/specs/auth-service-persistence.md`. Two named deviations remain: `source` and control characters (D8), and cascades in the parsed-structure tables (D14). If phase 4 changes a pattern item before its review point E, pm updates this spec before T2.
- **X3.** At acceptance: mark auth-service follow-up FU-16 as closed for scenario-service (AC-7.3, AC-7.5) in `docs/specs/auth-service-projects.md`.

## Breakdown

| Area | Work | Depends on |
|---|---|---|
| scenario-service | T1 now (docs only); T2–T5 (Batch A, review point A), T6–T9 (Batch B), T10–T12, T14 (Batch C) | T2 onward: auth-service phase 4 review point E passed; Docker running (Testcontainers) |
| api-gateway | T13: one route's `Path` patterns (GE1), contract 0.2.3 (GE2), one new test (GE3) | GE1–GE3 approved; Batch B done (so the route has a real target) |
| auth-service | None. C5 1.2.1 is consumed as is. FU-10 (canonical UUIDs) must land before scenario-service goes to production. | — |
| report-service | None now. Its first spec uses this spec's stable ids (feature, scenario, example row), the test-case shape (D13) and the snapshot rule for steps (D9). | This spec Accepted |
| docs/api | `scenario-service.openapi.yaml` 1.0.0 (T1, done), 1.0.1 (T1b), synced in T14; `api-gateway.openapi.yaml` 0.2.3 (T13) | Q2 |
| docs/erd | `cucumber.dbml`: `proposed (scenario-service)` marks, done with this spec. backend-swe turns them into as-built in T2 (schema) and T7 (entities). | Q10 |
| qa | Test plan `docs/qa/scenario-service-test-plan.md` once the spec is `Ready`, including the D4 failure matrix, the id-stability rules (D9) and a no-PII sweep; verification after review point C, on PostgreSQL. | Spec `Ready`; T14 |
| Frontend: features (not in repo) | A feature tree built from paths (folders are path segments); a Gherkin editor that shows `parseErrors` at their line and column; save with `baseVersion` and a conflict dialog on `VERSION_CONFLICT` that offers to reload; version history with the text of each version (a client-side diff is optional); archived features in a separate view with restore. | Contract 1.0.1 (T1b) |
| Frontend: scenarios and test cases | The structure view (Background, scenarios, Rules, Outlines with Examples, data tables, doc strings); the test-case list per feature; the tag catalogue with filter chips. Hide write actions unless the user's `myRole` (from auth-service's project view) is `MEMBER` or `ADMIN`, and switch on the Problem `code`. | Contract 1.0.1; auth-service contract 1.2.1 (`myRole`) |

## Contracts needed before parallel work

1. **C1. scenario-service contract** `docs/api/scenario-service.openapi.yaml` 1.0.0 (T1, done), then 1.0.1 with the D19 decisions (T1b, backend-swe). 1.0.1 must exist before frontend-swe starts. The paths, status codes, `code` values and the `parseErrors` and `currentVersion` members in this spec's ACs are binding. If backend-swe needs a different shape, raise it with pm before T1 is final.
2. **C2. JWT contract (consumed, unchanged):** HS512 with the shared `JWT_SECRET`, `exp` required, `sub` a canonical UUID. scenario-service ignores the `email` and `orgs` claims.
3. **C3. Error contract:** auth-service's Problem shape (C3 there), with D16's codes and members. Clients branch on `status`, then `code`, and must handle Problems without `code` from the gateway.
4. **C4. Access check (consumed, auth-service contract 1.2.1, C5):** as D4. scenario-service follows every consumer rule: rank comparison, no writes when archived, fail closed, no caching.
5. **C5. Gateway header contract (consumed, gateway C3):** behind the gateway `X-Request-Id` is always present and valid; `X-User-Id` is ignored; scenario-service emits no CORS headers and no `X-Request-Id` response header.
6. **C6. Stable ids for report-service (published by this spec):** feature ids never change; scenario and example-row ids follow D9; rule, examples and step ids aren't stable. A test case is identified by its scenario id (Scenario) or example-row id (Outline row). report-service's way of reading them (REST or gRPC) is decided in its own spec.

## Cross-service dependencies

| Provider | Provides | Consumer | Blocks parallel work? |
|---|---|---|---|
| auth-service | C5 access check (built, contract 1.2.1) | scenario-service | No: built and accepted. Tests use a stub. The manual check (AC-7.11) needs it running. |
| auth-service | FU-10 canonical UUIDs | scenario-service in production | No for development (D4 sends only canonical ids); yes before production (gate) |
| auth-service | Project ids and users (by id, no foreign keys) | scenario-service | No |
| gateway | A route for the final paths (GE1) | frontend, qa, AC-7.11 | No for backend (direct calls on 8082); yes for frontend end to end |
| scenario-service | Stable ids and the test-case shape (C6) | report-service (later) | No; report-service isn't specced yet |
| auth-service phase 4 (`auth-service-persistence`, Ready) | "Database pattern for every service", proven in its reference implementation | scenario-service T2 onward | **Yes** (user decision 2026-10-14): T2 waits for phase 4 review point E. T1 doesn't. |
| local dev server (`cucumber-postgres`, persistence D5) | The dev database for `bootRun` and the manual check | scenario-service T14 | Only for T14 (the user runs the README setup) |

### Known issues that affect verification
1. **Through the gateway, the gateway answers 401 first.** Verify AC-7.1 with direct calls.
2. **The gateway's own errors have no `code`.** AC-7.11 expects that for `/api/projects/1`.
3. **`/api/scenarios/**` still routes to scenario-service** (D2), which answers its own 404 `NOT_FOUND` there. That's expected until a gateway clean-up retires the placeholder.
4. **Every build needs Docker** (Testcontainers, D15). Without a running daemon the build fails; tests are never skipped. Port 5432 is held by Postgres.app 16, which tests don't use.
5. **`/internal/**` on auth-service is reachable by anyone who can reach port 8081**, with a valid token, revealing only the caller's own access (auth-service Known issue 7).
6. **FU-10:** auth-service still accepts lenient UUIDs. scenario-service never sends one, so it doesn't affect any AC here.

## Non-functional requirements

- **Security and authorization**
  - Deny by default: every endpoint needs a valid token and a C5 answer. No public operation.
  - Effective roles are checked on every request, with no caching (AC-7.5). The service fails closed (AC-7.3).
  - No access and unknown project get the same 403. A feature of another project is a 404 within a project the caller can read, which reveals nothing (AC-2.4).
  - Concurrency: one path per active feature (AC-1.10), one version number per save (AC-3.11), never a 500 from a race.
  - Never log tokens, `Authorization` values, claims or content (D17).
- **Data integrity.** Versions are immutable and never deleted. Stored text round-trips byte for byte (AC-2.6). Database constraints back the rules (P17).
- **Performance (informational, not an AC).** Locally, with the C5 call, list and view should answer within 200 ms at p95 for a project with 50 features; a save of a 1,000-line feature within 1 s. backend-swe reports measurements in T14. No database connection is held during the C5 call (P13).
- **Audit.** No audit entries in phase 1. INFO logs aren't written for successful writes, beyond D17's outcome lines; an audit spec may add them.
- **Observability.** As auth-service-projects D17: `requestId` and `userId` on every line, end-to-end correlation through C5, DEBUG timings, WARN for slow calls and for an unavailable access check, one INFO per business error, one ERROR per unexpected failure.
- **Compatibility.** No existing path or response of any service changes. The gateway gains route patterns only.
- **Accessibility.** Not applicable (no UI in this repo). The frontend spec covers it: an editor's error markers need a text equivalent.

## Blocking questions

None open. **Resolved by the user on 2026-10-14: Q1 to Q10 all as recommended**, with one change. **Q8 is replaced by the Testcontainers decision** (`auth-service-persistence` Q1 = A): tests use a `postgres:17` Testcontainers singleton, and no database or role is created for tests. The dev database follows that spec's D5 (the shared `cucumber-postgres` container), set up by the user from the README. Also decided:
- auth-service phase 4 is built first, and scenario-service T2 onward waits for its review point E (T1 may run now);
- X1 is approved.

The original questions are kept below for the record.

1. **Q1. Scope (D1).** Phase 1 = the 8 stories and 71 ACs above. Recommended: **yes**.
2. **Q2. Paths and gateway (D2, GE1–GE3).** Final paths under `/api/projects/{projectId}/features/...` and `/api/projects/{projectId}/tags`; add those patterns to the existing `scenarios` gateway route and keep `/api/scenarios/**` for now. Recommended: **yes, approve GE1–GE3**.
3. **Q3. Roles (D3).** `READER` and `TESTER` read; `MEMBER` and `ADMIN` also create, save, archive and restore. Recommended: **yes**. Alternative: archive and restore for `ADMIN` only.
4. **Q4. Access check (D4, D5).** One C5 call per request, no caching, timeouts 1 s connect and 2 s read, no retry, fail closed with 503 `ACCESS_CHECK_UNAVAILABLE`, C5's 401 passed on as 401; the check is a first step in each service use case, not a `@PreAuthorize` guard. Recommended: **yes**.
5. **Q5. Parser version (D7, SE5).** `io.cucumber:gherkin` **28.0.0** with `io.cucumber:messages` **24.1.0** pinned. These are verified locally from Maven Central; the newest release couldn't be checked offline and is very likely newer. Recommended: **approve 28.0.0 now**; a bump later is a one-line item. Alternative: backend-swe verifies the newest release in T2 and asks again before using it.
6. **Q6. Build changes (D18, SE1–SE4).** Remove Elasticsearch; add the Flyway starter and its PostgreSQL module, the AspectJ starter, and jjwt 0.12.5. No actuator. Recommended: **approve all**. (Testcontainers, SE8, was added on 2026-10-14 with the Testcontainers decision.)
7. **Q7. Existing files (SE6, SE7).** Replace `application.properties` with `application.yaml` (plus a new `application-dev.yaml`), and change only the annotations of `ScenarioServiceApplicationTests` so `contextLoads` runs. Recommended: **approve**.
8. **Q8. Databases and tests (D14, D15, G10). Superseded on 2026-10-14** by the Testcontainers decision (see above). The original question asked whether backend-swe might create local databases `scenario_service` and `scenario_service_test` on port 5432 for tests without Docker.
9. **Q9. Versioning rules (D9).** `baseVersion` in the save body, 409 `VERSION_CONFLICT` with `currentVersion`; no version for an unchanged text; scenario ids kept by kind, name and container (n-th occurrence for duplicates); example-row ids kept by identical cells; rule, examples and step ids not stable. Recommended: **yes**.
10. **Q10. ERD changes (`proposed (scenario-service)`).** `tag_assignments.feature_id` (new, for counts and filters); path uniqueness only among active features and case-insensitive (replaces the plain unique index); `jsonb` instead of `json`; names as `text` (empty allowed). Recommended: **yes**.

## Assumptions

- A1. auth-service's C5 behaves as contract 1.2.1 and its acceptance report say (accepted 2026-10-14). A suspended or deleted user's token gets 401 from C5.
- A2. A project belongs to one organization for life (auth-service-projects A2), so scenario-service never needs the organization id. It ignores C5's `orgId` beyond reading it.
- A3. Request validation (400) runs before the access check, as in auth-service (A3). An unauthorised caller who sends an invalid body may get 400 instead of 403. Permission scenarios use valid bodies.
- A4. Projects hold at most a few hundred features, and features are at most a few thousand lines. No pagination in phase 1.
- A5. Lists are ordered: features by path (lower case with `Locale.ROOT`, then id); versions newest first; tags by name in code-point order (the database's `C.UTF-8` collation gives the same order); test cases and structure in text order.
- A6. Feature names are taken from the text and may repeat within a project. The path identifies a feature for people, the id for machines.
- A7. Timestamps in responses are ISO-8601 UTC instants with `Z` and microsecond precision (pattern item 11).
- A8. `createdBy` and `updatedBy` are user ids. The frontend resolves names from auth-service (organization member list). scenario-service stores no names or emails.
- A9. Deleting a project or a user in auth-service (neither exists today) would leave content here. A later spec handles it, with events or a clean-up call.
- A10. The longest Gherkin dialect code fits `features.language` (`varchar(10)`), and the longest localised keyword fits `keyword_text` (`varchar(50)`). backend-swe verifies both against the parser's dialect list in T6 and stops if not.
- A11. D14 and D15 follow `auth-service-persistence`'s "Database pattern for every service" as approved on 2026-10-14. Values that phase 4 adjusts while proving the pattern (for example pool sizes or the `lock_timeout` mechanism) apply here in the form proven at its review point E.
- A12. The DEV ONLY JWT secret in `application-dev.yaml` is the same value as in auth-service's and the gateway's dev profiles, so tokens issued locally work across all three.

## Acceptance
(Filled in by pm after qa's sign-off.)
