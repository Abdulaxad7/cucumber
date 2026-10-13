# auth-service

Accounts, login, organizations, invitations and members (spec: `docs/specs/auth-service.md`, phase 1).
The API contract is `docs/api/auth-service.openapi.yaml`; the runtime `/v3/api-docs` is not the contract.

## Run locally

Requirements: JDK 17 (the Gradle toolchain), nothing else. The database is in-memory H2, recreated at every start.

```sh
cd auth-service
./gradlew bootRun --args='--spring.profiles.active=dev'
```

- Port: **8081** (`server.port`). Normal clients call it through the API gateway on port 8080; direct calls on 8081
  are for local development only.
- The `dev` profile supplies a development-only JWT secret and logs Hibernate SQL. Never use the `dev` profile
  outside a developer machine.
- Without the `dev` profile you must set `JWT_SECRET` (setting `app.jwtSecret`): the standard Base64 encoding of
  at least 64 random bytes, the same value the gateway uses. The service refuses to start without a usable secret.
  For example, generate one with `head -c 64 /dev/urandom | base64` and keep it out of version control.

```sh
JWT_SECRET='<standard Base64 of at least 64 random bytes>' ./gradlew bootRun
```

## Settings

| Setting | Default | Meaning |
|---|---|---|
| `app.jwtSecret` (`JWT_SECRET`) | none (the `dev` profile sets one) | Shared HS512 secret, standard Base64 of at least 64 bytes |
| `app.jwtExpirationInMs` | `86400000` (24 h) | Lifetime of issued tokens |
| `app.orgs.free-seat-limit` | `5` | Seat limit of every organization created in phase 1 (plan `FREE`); must be positive |

## Public operations

Exactly three operations work without a token: `POST /api/auth/register`, `POST /api/auth/login` and
`POST /api/auth/invitations/{token}/accept`. Swagger UI (`/swagger-ui/**`) and `/v3/api-docs` are public on port
8081 only. Every other path needs `Authorization: Bearer <accessToken>` and otherwise gets a 401 Problem. The H2
console is off.

## Projects

Projects belong to one organization and live under `/api/orgs/{orgId}/projects` (spec:
`docs/specs/auth-service-projects.md`, phase 2). The gateway's existing `orgs` route covers these paths.

- **Project roles**, with an explicit rank: `ADMIN` 4 > `MEMBER` 3 > `TESTER` 2 > `READER` 1. Each role includes
  the ones below it. Code compares roles only with `ProjectRole.isAtLeast`, never by declaration order.
- **Effective role**, computed from the database on every request (no cache), in `ProjectRoleService` only:
  - not a member of the project's organization: no access;
  - organization `OWNER` or `ADMIN`: project `ADMIN` on every project of the organization (implicit admin, no
    membership row);
  - otherwise the explicit project role, or no access.

  An organization `READER` can only hold project `READER` (the cap). `myRole` in project responses is the
  effective role. A removal or demotion applies to the next request.
- **Who can do what:** organization `OWNER` and `ADMIN` create projects; any organization member lists the
  projects they have access to; anyone with access views a project and its members; an effective project `ADMIN`
  renames, archives, restores and manages members; any explicit member can leave.
- **Members** are current organization members, added by user id; there are no project invitations. A project
  membership uses no organization seat. Leaving or being removed from the organization deletes the user's project
  memberships there; becoming organization `READER` lowers their project roles to `READER`.
- **Archive** sets `archivedAt`. An archived project stays readable but is read-only: renaming and every membership
  change (leaving included) get 409 `PROJECT_ARCHIVED` until it is restored. Projects can't be deleted.
- **Names** are 1–100 characters as sent, stored trimmed, and unique per organization ignoring case, archived
  projects included (409 `PROJECT_NAME_TAKEN`).
- No access, an unknown project and a project of another organization all get the same 403 `FORBIDDEN`.

## Access check for other services

`GET /internal/projects/{projectId}/access` tells another service (scenario-service, report-service) the calling
user's effective role in a project (contract C5).

- **How to call it:** directly on auth-service's base URL (`AUTH_SERVICE_URL`, `http://localhost:8081` locally),
  never through the gateway: the gateway never routes `/internal/**`. Forward the end user's
  `Authorization: Bearer <jwt>` header unchanged; no service credential is needed.
- **Answer:** 200 with exactly `projectId`, `orgId`, `role` (`ADMIN`, `MEMBER`, `TESTER` or `READER`) and `archived`.
  401 `UNAUTHENTICATED` for a missing or invalid token; 403 `FORBIDDEN` for no access or an unknown project; 400
  `MALFORMED_REQUEST` for an id that isn't a UUID.
- **Consumer rules:**
  - compare `role` by rank (`ADMIN` > `MEMBER` > `TESTER` > `READER`), never by string order;
  - refuse content writes when `archived` is true;
  - treat a timeout or a 5xx as "deny" (fail closed);
  - don't cache the answer, unless your service's spec accepts a staleness window of at most 30 s, documented as a
    known delay for removals.
- **Stability:** fields are only ever added; a breaking change gets a new path.
- **Not yet available:** calls without an end user (CI uploads, background jobs) need a service credential, which
  comes with the result-ingestion spec.

## Tests

```sh
cd auth-service
./gradlew build      # compile, all tests, package
./gradlew test --tests 'com.example.authservice.controller.MembersTest'
```

Tests use a TEST-ONLY secret defined in test code and the in-memory database; they need no environment variables.

## Manual end-to-end check through the gateway

Start both services with the `dev` profile (they share the dev secret):

```sh
(cd auth-service && ./gradlew bootRun --args='--spring.profiles.active=dev')
(cd api-gateway && ./gradlew bootRun --args='--spring.profiles.active=dev')
```

Then, against `http://localhost:8080` (expected status in brackets):

1. Register Ana: `POST /api/auth/register` `{"email":"ana@example.com","password":"Secret123!","fullName":"Ana Lima"}` [201]
2. Log in as Ana: `POST /api/auth/login` `{"email":"ana@example.com","password":"Secret123!"}` [200]; keep `accessToken`.
3. Create "Acme QA": `POST /api/orgs` `{"name":"Acme QA"}` with Ana's token [201]; keep `id`.
4. Invite Fay: `POST /api/orgs/{id}/invitations` `{"email":"fay@example.com","role":"MEMBER"}` with Ana's token
   [201]; keep `token` (shown only here).
5. Accept anonymously: `POST /api/auth/invitations/{token}/accept` `{"password":"Secret123!","fullName":"Fay Lin"}` [201]; keep `user.id`.
6. Log in as Fay [200].
7. Ana changes Fay to `READER`: `PATCH /api/orgs/{id}/members/{fayId}` `{"role":"READER"}` [200].
8. Ana removes Fay: `DELETE /api/orgs/{id}/members/{fayId}` [204].
9. An error passes through unchanged, for example `POST /api/orgs` `{"name":""}` with Ana's token: a 400
   `application/problem+json` body with `code` `VALIDATION_FAILED` and the gateway's `requestId`.

Stop both applications afterwards (Ctrl+C), and run `./gradlew --stop` in each service if you want the Gradle
daemons gone.
