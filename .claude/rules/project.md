
# Project-Specific Development Guidelines

This file contains **overrides and additions** to the team workflow described in `.claude/rules/team-workflow.md`.

## Stack

- Java 25, Spring Boot 4.1 (Spring Framework 7, Jackson 3, Testcontainers 2), Maven wrapper (`./mvnw`);
  upgraded from Java 11 / Boot 2.7, see `workflow/adrs/0002-spring-boot-4-java-25.md`
- Build with a JDK 25: `LOCAL.md` has the `JAVA_HOME` (and Docker settings) for this machine
- Jackson 3 lives in `tools.jackson.*`; its annotations are still `com.fasterxml.jackson.annotation`
- Boot 4 property names (`spring.web.error.*`, `spring.servlet.encoding.*`, `spring.jackson.json.read.*`):
  Boot ignores unknown property names silently, so a wrong name only shows up in `WireFormatTest`
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

## Spec-based Hurl Checks

The harness's checks are opaque, so `examples/<slug>-rules.hurl` is our own version of them: one file
per feature, every rule in `workflow/specs/<slug>.md` and every decision row, with the status codes from
`docs/api.md`. CI runs every `examples/*.hurl` against the built image (job `hurl` in
`.github/workflows/deploy.yml`) on each PR and on main, twice on the same database with a restart in
between. Adding a check:

- Put it in the feature's `*-rules.hurl`, above it a `#` comment naming the rule or decision (`# D5: ...`).
- Use harness-shaped data: e-mails `x-{{newUuid}}@example.test`, names like `Ada Hamilton 6d06`.
- It must pass on old data: unique e-mails per run, lists filtered by `speakerId`, no global counts.
- Run it with `workflow/scripts/local-rollout.sh` (or `examples/run.sh <url>`), twice.
- A rule the service doesn't meet yet stays out of the suite (the job has to stay green); note it in
  the plan of the fix instead.

Not reproduced locally: the managed Postgres's quirks (advisory locks, a pooler leaking session `SET`),
the TLS proxy, the platform's own `503` beyond 80 concurrent requests, scale-to-zero cold starts, and the
harness's hidden checks.
