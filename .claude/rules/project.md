
# Project-Specific Development Guidelines

This file contains **overrides and additions** to the team workflow described in `.claude/rules/team-workflow.md`.

## Stack

- Java 11, Spring Boot 2.7, Maven wrapper (`./mvnw`)
- Postgres, schema in `src/main/resources/schema.sql`
- Code in `src/main/java/no/scienta/cfp/`, tests in `src/test/java/no/scienta/cfp/`
- Background docs on the base app in `docs/` (API, architecture, data model, known issues, operations)

## Code Conventions

Match the existing code:

- 4-space indentation, no tabs
- Constructor injection with `private final` fields, no field `@Autowired`
- Imports grouped as `java.*` first, then third-party, separated by a blank line; no wildcard imports
- Javadoc only on classes and methods whose purpose isn't obvious from the name

## Testing Conventions

- JUnit 5 (`org.junit.jupiter`)
- HTTP-level tests use `@SpringBootTest(webEnvironment = RANDOM_PORT)` with a fresh database from
  `TestDatabase.fresh(registry)` in a `@DynamicPropertySource` method; see `TalksTest`
- Docker must be running: the tests start Postgres with Testcontainers
- Behaviour visible over HTTP also gets a Hurl example in `examples/`
- Line coverage may not drop: see `workflow/coverage/README.md`

## Prod-like Local Run

Before a PR that changes HTTP behaviour, run the image we deploy, not just `java -jar`:

- `workflow/scripts/local-rollout.sh` builds the root `Dockerfile` for `linux/amd64` with the commit's
  SHA, starts it with `docker compose --profile app` against a Postgres whose data survives restarts
  (volume `pgdata`, as in prod), waits for `/health` to report the SHA and runs `examples/run.sh`.
  `docker compose --profile app down -v` empties the database. `APP_PORT`/`PG_PORT` move the ports.
- `workflow/scripts/transcript-to-hurl.sh <feature>/<check>` turns a harness check's latest transcript
  into Hurl that replays its requests (prod IDs, no headers, no asserts: fix those before saving it to
  `examples/`).
- Every request is logged as `METHOD /path -> STATUS (N ms)` (`RequestLogFilter`), so the harness's
  traffic shows in `.github/fagdag/logs.sh`.

Not reproduced locally: the managed Postgres's quirks (advisory locks, a pooler leaking session `SET`),
the TLS proxy, the platform's own `503` beyond 80 concurrent requests, scale-to-zero cold starts, and the
harness's hidden checks.
