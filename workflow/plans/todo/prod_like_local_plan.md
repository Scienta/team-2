# Plan: Prod-like local environment and harness transcript replay

- [x] Phase 1: Request logging filter (`RequestLogFilter`) with a test
- [x] Phase 2: Compose `app` service and a persistent Postgres volume
- [x] Phase 3: `workflow/scripts/local-rollout.sh`: build, start, wait for the SHA on `/health`, run the Hurl examples
- [ ] Phase 4: `workflow/scripts/transcript-to-hurl.sh` with its own test script
- [ ] Phase 5: Docs, `REVIEW.md` §1/§5, plan to done

## Context (read this after a restart)

- Issue: #14. Not a harness feature, so nothing to claim.
- Branch: `14-prod-like-local`. A draft PR was explicitly requested via `gh pr create --draft`.
  Merging to `main` deploys.
- Commands: `./mvnw package`, `./mvnw test -Dtest=RequestLogFilterTest`, `docker compose up -d`,
  `workflow/scripts/local-rollout.sh`, `workflow/scripts/transcript-to-hurl.sh <feature>/<check>`.
- Why: the harness only ever hits `TEAM_URL`. Locally we can match the runtime (same image, `PG*` env,
  pool of 5, a database that keeps its data across restarts). We can't run the harness's checks, but we
  can replay the requests from their transcripts.
- Code in `src/main/java/no/scienta/cfp/`: the new `RequestLogFilter` is a Spring `@Component`
  `OncePerRequestFilter`, so Spring Boot registers it for every request.
- Test style: `HealthTest`/`TalksTest` (`@SpringBootTest(RANDOM_PORT)`, `TestDatabase.fresh`),
  plus `OutputCaptureExtension` to assert on the log line.
- Harness JSON (checked at tick 121): `GET /api/teams/<team>/feedback` → `.features[] | {slug, checks[]}`.
  Each check has `{name, status, transcript: {tick, omitted, requests: [{method, path, requestBody,
  status, responseBody}]}}`, and `status`/`responseBody` may be `null`. The timeline endpoint
  (`?check=<feature>/<check>`) only has `{tick, status, notes, failure: {method, path}}`, with no
  transcript. So the replay script reads the transcript from **feedback**.
- Transcript paths hold the IDs prod handed out (e.g. `/talks/123/...`). Replayed locally they point
  at other rows or none. The generated Hurl therefore asserts nothing (`HTTP *`), and prod's answer
  goes in as a comment. A person edits IDs/captures before saving it to `examples/`.

## Decisions for the human to check

- **D1.** The log line format: `METHOD /path?query -> STATUS (N ms)` at INFO, logger
  `no.scienta.cfp.RequestLogFilter`, no bodies, no headers (so no secrets in logs). `/health` is logged too
  (the rollout polls it; that's useful to see). Not visible to the harness.
- **D2.** The compose `app` service sits behind a profile (`app`), so `docker compose up -d` still starts
  only Postgres for the `java -jar` flow, and `docker compose --profile app up -d --build` starts
  both. The named volume `pgdata` makes data survive `down`/`up`; `docker compose down -v` resets it.
- **D3.** `local-rollout.sh` doesn't stop anything when it finishes, so the service stays up to poke at.

## Phase 1: Request logging

1. `src/main/java/no/scienta/cfp/RequestLogFilter.java`: `OncePerRequestFilter`, `@Component`, measures
   time with `System.nanoTime()`, logs in a `finally` so a thrown exception still gets a line (status
   from the response; Spring's error dispatch then logs nothing extra since it's once per request).
2. `src/test/java/no/scienta/cfp/RequestLogFilterTest.java`: `@SpringBootTest(RANDOM_PORT)`,
   `@ExtendWith(OutputCaptureExtension.class)`, GET `/health` and GET `/no-such-path`, then assert that
   the output has `GET /health -> 200` and `GET /no-such-path -> 404`.

Check: `./mvnw package` green, `RequestLogFilterTest` passes, no warnings, no coverage drop. Commit:
`feat: Log one line per request`.

## Phase 2: Compose app service

1. `docker-compose.yml`: add a `pgdata` named volume on `postgres` (`/var/lib/postgresql/data`), a
   healthcheck (`pg_isready -U cfp`), and an `app` service under profile `app`: `build: {context: .,
   args: {GIT_SHA: "${GIT_SHA:-unknown}"}}`, `platform: linux/amd64`, env `PGHOST=postgres`,
   `PGPORT=5432`, `PGDATABASE/PGUSER/PGPASSWORD=cfp`, `PGSSLMODE=disable`, port `8080:8080`,
   `depends_on: postgres: condition: service_healthy`.

Check: `docker compose config` is valid; `docker compose up -d` starts only `postgres`;
`GIT_SHA=test docker compose --profile app up -d --build` → `curl localhost:8080/health` gives
`{"status":"ok","version":"test"}`. Commit: `chore: Run the deployed image locally with compose`.

## Phase 3: Local rollout script

1. `workflow/scripts/local-rollout.sh [--no-examples]`: `GIT_SHA=$(git rev-parse HEAD)` (with a `-dirty`
   suffix if the tree is dirty, so nobody mistakes it for a committed build), `docker compose --profile
   app up -d --build`, poll `http://localhost:8080/health` every 2 s for up to 60 s (`ROLLOUT_GATE_SECONDS`)
   until it reports that version, then `examples/run.sh http://localhost:8080`. On timeout print
   `docker compose logs --tail 100 app` and exit 1.

Check: run it end to end and get `/health` with the SHA and the examples passing. Commit:
`chore: Add a local prod-like rollout script`.

## Phase 4: Transcript to Hurl

1. `workflow/scripts/transcript-to-hurl.sh <feature>/<check> [feedback.json]`: without a file it calls
   `workflow/scripts/harness.sh feedback` (never touches the token itself), selects the check, and prints
   Hurl to stdout: a header comment (feature, check, tick, status, omitted count), then per request
   `METHOD {{base_url}}PATH`, a JSON body if there is one, a `# prod answered: <status> <body, cut to 200
   chars>` comment, and `HTTP *`. It exits 1 if the check or the transcript isn't there.
2. `workflow/scripts/test-transcript-to-hurl.sh`, in the style of `test-check-review.sh`: from a fixture
   JSON with a two-request transcript (one with a body and status, one with nulls), the output has the
   expected request lines, and `hurlfmt --check` accepts it (skipped with a note if `hurlfmt` isn't
   installed). A missing check exits 1.

Check: `workflow/scripts/test-transcript-to-hurl.sh` prints all ok; running the script on a real check
(`speakers/check-1`) and then `hurl --test --variable base_url=http://localhost:8080` against the local
service runs. Commit: `chore: Turn harness transcripts into Hurl files`.

## Phase 5: Docs and REVIEW.md

1. `.claude/rules/project.md`: a "Prod-like local run" section (the three commands, what's the same
   as prod and what isn't: managed Postgres quirks, TLS proxy, the 503 at 80 concurrent requests, the hidden
   checks).
2. `workflow/README.md`: artifact rows for the two scripts.
3. `REVIEW.md` §1 (step 5 now runs `local-rollout.sh` before the PR) and §5 (the scripts). Run
   `workflow/scripts/check-review.sh`.
4. `git mv` plan and review to `workflow/plans/done/`.

Check: `workflow/scripts/check-review.sh` lists only the human TODO headings it listed before;
`./mvnw package` green. Commit: `docs: Document the prod-like local run`.

## Review response

| Finding | Response |
|---|---|
