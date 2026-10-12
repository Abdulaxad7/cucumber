# Platform ERD

`cucumber.dbml` is the entity-relationship diagram of the whole platform, written in [DBML](https://dbml.dbdiagram.io/docs/).

## How to view it
- **dbdiagram.io:** paste the file into https://dbdiagram.io/d. Tables are grouped by owning service.
- **IDE:** use a DBML plugin (VS Code "DBML Language" or "vscode-dbml"; IntelliJ "DBML" / "Database Markup Language") for highlighting and preview.
- **Validate or turn it into SQL:** run `npx -p @dbml/cli dbml2sql /Users/uDevs/IdeaProjects/cucumber/docs/erd/cucumber.dbml --postgres` from an empty directory under `/tmp`.
- **Last validation (2026-10-12, main session):** `npx -p @dbml/cli dbml2sql --postgres` exited 0 and produced 22 tables, 13 enums and 26 foreign keys.

## Legend
| Marker | Meaning |
|---|---|
| Blue header, table note starts with `as-built` | Matches the JPA entities in `auth-service/src/main/java/com/example/authservice/model/` as of the date in the Project note. |
| Orange header, note starts with `proposed — approved design (2026-10-12), not yet implemented` | Domain design by pm, approved by the user (decisions below). Nothing is built yet. |
| `proposed (auth-service phase 1)` | An entity change planned in `docs/specs/auth-service.md` but not in code yet. There are none today, because phase 1 leaves every entity unchanged. |
| `references <service>.<table>.id (by ID, no FK)` | A cross-service link. Services never share foreign keys. |

`TableGroup`s show the owning service: `auth_service`, `scenario_service` and `report_service` (see ERD-D2).

## Maintenance
- **pm** proposes and changes the proposed part, and records the user's decisions here.
- **backend-swe** keeps the as-built part in sync with the code. Any change to an entity, an enum or a migration must update `cucumber.dbml` in the same change, along with the "as-built at" date in the Project note. When a proposed table gets built, change its note from `proposed` to `as-built`.

## Decisions (all approved by the user on 2026-10-12, recommended defaults as written)
| ID | Decision | Approved default |
|---|---|---|
| ERD-D1 | Where projects live | Keep them in auth-service, next to project memberships and roles. Other services store `project_id` by ID and ask auth-service (a contract to agree) whether a user may act on a project. |
| ERD-D2 | Which service owns test runs, results, environments, builds and attachments | report-service. It already has JPA and a gRPC client, which fits ingesting results and reading scenario data from scenario-service. |
| ERD-D3 | Feature versioning | Every save creates an immutable `feature_versions` row with the full Gherkin text. Parsed tables mirror only the current version. A scenario keeps its id when re-parse matches it by name within the same feature and rule; otherwise it gets a new id. Results keep snapshots, so history survives edits. |
| ERD-D4 | Source of truth for Gherkin | The Gherkin text. Structured tables are derived from it on save, and the UI never edits rows directly. |
| ERD-D5 | What a test case is | A derived concept rather than a table: a Scenario, or one example row of a Scenario Outline. There are no free-standing manual test cases in the MVP. |
| ERD-D6 | Step arguments and examples storage | At most one argument per step. Data-table cells and example headers are stored as JSON. Example rows are real rows because results point at them. |
| ERD-D7 | Tags | A project-wide catalogue (`tags`) plus polymorphic `tag_assignments`, both derived on parse. Names are stored with the leading `@` and are case-sensitive. |
| ERD-D8 | Execution statuses | The six Cucumber statuses plus `UNKNOWN` (not executed yet, as in Cucumber messages) for planned manual runs. Stored upper case. |
| ERD-D9 | Attachment storage | In the database (`bytea`) for the MVP, capped at 10 MB per attachment. Object storage comes later. |
| ERD-D10 | MVP exclusions | No folder table (use `features.path`), no report or living-documentation snapshot tables (computed from data), and features are soft-deleted (`archived_at`). |
| ERD-D11 | Databases | One database per service, PostgreSQL in all environments, schemas managed by Flyway (auth-service phase 4 does the same). |
