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
