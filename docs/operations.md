# Operations

See `AGENTS.md` and the organiser section of the root `README.md` for the full deploy contract.
This is the short version.

## Build and test

```bash
./mvnw package          # compiles, runs the tests, produces target/cfp.jar
```

Docker must be running: the tests start Postgres with Testcontainers (`TestDatabase`). Test classes:
`SpeakersTest`, `TalksTest`, `ConferenceServiceTest`, `HealthTest`, `UtilsTest`.

## Run locally

```bash
docker compose up -d                 # Postgres on localhost:5432, db/user/password "cfp"
java -jar target/cfp.jar             # http://localhost:8080
examples/run.sh                      # Hurl happy-path examples (needs hurl)
```

`examples/*.hurl` are the base specs' published happy paths only, not what the harness checks. Add
your own `.hurl` files there for drip features and edge cases; `run.sh` picks them all up.

## Deploy

Pushing to `main` runs `.github/workflows/deploy.yml`: it builds the `Dockerfile` with
`--build-arg GIT_SHA=<sha>`, pushes `app:<sha>` and `app:latest`, and runs
`.github/fagdag/rollout.sh`, which waits up to 60 s for `GET /health` to report the new SHA. On
failure `app:latest` is rolled back to the previous image and the job fails.

The service is at `https://fagsem-h2026-team-2.scienta.cloud`. Check a deploy with:

```bash
curl -s https://fagsem-h2026-team-2.scienta.cloud/health
examples/run.sh https://fagsem-h2026-team-2.scienta.cloud
```

Roll back with `git revert` and push, or re-tag a good `app:<sha>` as `app:latest`.

## Logs

```bash
.github/fagdag/logs.sh --tail 200 --since 2026-11-06T13:00:00Z
```

The app doesn't log requests yet, so the harness's traffic barely shows. Adding request logging
(method, path, status) is allowed and recommended.

## Harness API

Needs `.fagdag/harness-token` (gitignored; a team member pastes it from the harness `/feedback`
page). Never commit or print it.

```bash
H=$(cat .fagdag/harness-url) TEAM=$(cat .fagdag/team) TOKEN=$(cat .fagdag/harness-token)
curl -fsS -H "Authorization: Bearer $TOKEN" "$H/specs/<feature>/spec.md"
curl -fsS -H "Authorization: Bearer $TOKEN" "$H/api/teams/$TEAM/feedback"
curl -fsS -H "Authorization: Bearer $TOKEN" "$H/api/teams/$TEAM/feedback/timeline?check=<feature>/<check>"
```

Rate-limited: bursts of 5, then 1 request/s (`429` with `Retry-After`). Only a person can claim a
feature, on the harness, and a claim is permanent.
