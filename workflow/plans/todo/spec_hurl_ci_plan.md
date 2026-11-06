# Plan: Spec-based Hurl checks, run against the built image in CI

- [ ] Phase 1: Edge-case Hurl files per released feature in `examples/` (speakers, talks, ratings, co-speakers, acceptance)
- [ ] Phase 2: CI job `hurl` in `.github/workflows/deploy.yml`: build the image, run the suite twice on one database
- [ ] Phase 3: Docs (`workflow/README.md`, `.claude/rules/project.md`, `examples/run.sh` header), `REVIEW.md` §1/§5, plan to done

## Context (read this after a restart)

- Issue: #26. Not a harness feature, nothing to claim. Branch `26-spec-hurl-ci`; a draft PR was
  explicitly requested (`gh pr create --draft`). Merging to `main` deploys.
- Commands: `./mvnw package` (JDK 25: `JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home`),
  `APP_PORT=18081 PG_PORT=55433 COMPOSE_PROJECT_NAME=spechurl workflow/scripts/local-rollout.sh`
  (the image, persistent Postgres, `/health` gate, then `examples/run.sh`), `hurl --test`.
- Why: the harness's checks are opaque. Its feedback (snapshot at tick 167) shows one request per
  check, almost always the setup `POST /speakers`, with no response and no assertions. So we can't
  copy the checks; we write our own from the specs and `docs/api.md` (which records the status codes
  we chose), with the harness's data shapes:
  - names like `Margaret Dijkstra 6c69`, e-mails `x-<12 hex>@example.test`,
  - bios mostly absent, sometimes exactly 500 characters of random letters and spaces (double spaces too),
  - `POST /speakers/` with a trailing slash (speakers check-4).
- Sources of truth for expected answers: `workflow/specs/<slug>.md` (rules, and the decisions table
  for acceptance), `docs/api.md` (status codes and precedence), `docs/known-issues.md` (decisions).
- Existing examples stay as they are; the new files only add what they don't cover:
  `speakers.hurl`, `talks.hurl`, `ratings.hurl`, `ratings-edge.hurl`, `co-speakers.hurl`,
  `acceptance.hurl`, `spring-boot-4.hurl` (versions, trailing slash, formats, error body keys).
- Every file must re-run against a database that keeps its data: unique e-mails via `{{newUuid}}`,
  never a global count or "the first talk in `GET /talks`"; lists are filtered by `speakerId`.
- CI today (`deploy.yml`): `tests` (Maven) on PRs and main; `deploy` on main only, **not** gated by
  `tests`. That was a deliberate choice in `workflow/plans/todo/ci-clean-build_plan.md` ("no main
  deployment barrier for competition speed").

## Decisions for the human to check

- **D1. Gate the deploy or not.** The request was "a regression blocks the merge or deploy". The
  new `hurl` job runs on every PR (a red check blocks the merge by convention) and on main. It does
  **not** gate `deploy`, to keep the ci-clean-build decision above. Gating is a one-line change
  (`needs: [hurl]` on `deploy`), which would delay each deploy by the job's run time (~3–4 min).
  Recommendation: keep it ungated and treat a red PR check as the gate.
- **D2. Rules the service breaks are left out of the gated suite**, so main stays deployable, and
  are reported instead (found by probing a local stack, `tmp/probe.sh`, not committed):
  - Speakers: "a real email": `"email": "not-an-email"` is accepted with 201 (known issue R5).
  - Speakers: "a real name": `"name": 5` is accepted with 201 and stored as `"5"`.
  Fixing them is a separate task with its own decisions (400? which e-mail check?).
- **D3. Not covered by Hurl:** concurrency (the 3-talk limit and ratings under load, decisions under
  races) needs parallel requests on shared data, which Hurl files can't do; `RatingsTest` and the
  acceptance tests cover it in Java. "Stays registered after a restart" is only exercised loosely:
  CI restarts the app between the two runs, and the second run must pass.
- **D4. Hurl version** pinned to 8.1.0 (what we run locally), installed from the release `.deb`.

## Phase 1: Edge-case Hurl files

One file per feature, named `<slug>-rules.hurl` (`ratings-edge.hurl` exists, so ratings gets
`ratings-rules.hurl`). Each request has a comment naming the spec rule or decision it checks.

1. `speakers-rules.hurl`: harness-shaped register (500-char bio with spaces, `x-<hex>@example.test`),
   read back exactly; 501 chars 400; missing/blank/null name 400; missing/blank email 400; e-mail
   case-insensitive duplicate 409; unknown id 404 (with and without slash); non-numeric id 400;
   no body, not JSON, array 400; `text/plain` 415; unknown speaker's talk list 404.
2. `talks-rules.hurl`: all four durations, a client `durationMinutes` ignored; missing/blank title,
   lower-case or unknown format/level, missing level, missing or unknown `speakerId` 400; 4th talk 409
   and the list still has 3, oldest first; unknown talk 404; non-numeric id 400; `?speakerId=<unknown>`
   is `[]`.
3. `ratings-rules.hurl`: `ratedAtDisplay` format (no leading zeros, ordinary space, AM/PM); scores
   0, 6, 4.0, "4", null, missing 400; reviewer -1 and `abc` 400; `text/plain` 415; ranking within one
   speaker (`?speakerId=&sort=rating`): same average, more reviewers first; unrated last;
   the rating fields show in the speaker's list too.
4. `co-speakers-rules.hurl`: a speaker at the 3-talk limit can still co-speak; co-speaking on 3 talks
   doesn't block own submissions; `coSpeakerIds` shows in `GET /talks?speakerId=` and
   `/speakers/{id}/talks`; DELETE on an unknown talk or speaker 404.
5. `acceptance-rules.hurl`: D5 repeats 409 (ACCEPTED, REJECTED, WAITLISTED), REJECTED → ACCEPTED 409,
   WAITLISTED → REJECTED 200; D4 one reviewer rating twice is still one (409); D7 unknown speakerId 403;
   a co-speaker can't withdraw (403); D8 `0`, missing, `null`, `"x"` 400; withdrawing an accepted talk
   200; D11 no body / not JSON / array 400, `text/plain` 415, non-numeric id 400; D13 status in the
   speaker's list and in `sort=rating`; D14 rating a withdrawn talk works and it still counts toward
   the limit; after each refused request the talk is unchanged.

Check: `local-rollout.sh` on a stack started from empty passes every file, then passes again on the
same database. Commit: `test: Spec-based Hurl rules per feature`.

## Phase 2: CI job

1. Job `hurl` in `deploy.yml`, on the same triggers as `tests`: checkout, install Hurl 8.1.0 `.deb`,
   `workflow/scripts/local-rollout.sh` (builds the root `Dockerfile` for amd64 with the SHA, Postgres
   with its volume, `/health` gate, all `examples/*.hurl`), `docker compose --profile app restart app`,
   `local-rollout.sh` again, and the app's logs on failure.
2. `deploy` unchanged (D1).
3. Validate: `yq` parses the file; `actionlint` if available.

Check: the job is green on the PR (`gh run watch`). Commit: `ci: Run the Hurl suite against the built image`.

## Phase 3: Docs and finish

1. `.claude/rules/project.md` and `workflow/README.md`: how to add a check (which file, unique data,
   comment with the rule, run locally, CI runs it).
2. `REVIEW.md` §1 (the CI step) and §5 (the suite and job). Run `workflow/scripts/check-review.sh`.
3. Move this plan to `workflow/plans/done/`.

Check: `./mvnw package` green; `check-review.sh` clean. Commit: `docs: Spec-based Hurl checks in CI`.

## Review response

The `plan-reviewer` agent couldn't run: this plan was written by a background agent that can't spawn
sub-agents. **A person must review this plan and D1–D4** before merging.

| Finding | Response |
|---|---|
