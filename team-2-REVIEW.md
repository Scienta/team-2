# REVIEW

> **Showcase brief (17:00, 4 minutes, hard stop).** You screen-share this repo from the shared
> presenter laptop, in a random order drawn at 17:00. No slides, and no harness screens.
>
> 1. **Our workflow in one minute:** section 1, pointing at the files in `workflow/` and `.claude/`.
> 2. **One story:** a bug our review caught before the harness did, or one the harness caught that
>    we missed, and why. Use the claim log.
> 3. **One artifact we'd reuse**, opened on screen.
>
> Keep this file as a running log from t = 0, so 17:00–17:05 is for tidying, not writing. To be
> eligible to win, every heading below needs some text. The comments under each heading are prompts
> and don't count; replace them with your own words.

## 1. Workflow

Every feature goes the same way, with a person approving at the two points that matter (decisions
and the claim):

1. **Spec.** Copy the harness spec or feature request into [`workflow/specs/`](workflow/specs/).
   A feature request isn't a spec, so the plan lists every open decision (status codes, edge cases).
2. **Plan.** An agent writes a phased, restart-proof plan with the
   [`/make-plan`](.claude/skills/make-plan/SKILL.md) skill: a checklist on top, a "read this
   after a restart" context block, the decisions for a human (flagging those to ask the product
   owner), and a falsifiable "Check:" per phase. Plans live in
   [`workflow/plans/`](workflow/plans/).
3. **Review the plan.** A second agent, often a different tool, reviews the plan and writes
   `<feature>_plan_review.md` next to it; the first agent revises. Examples:
   `workflow/plans/done/ratings_plan_review.md`, `workflow/plans/done/workflow_reorg_plan_review.md`.
4. **Human approves the decisions** (e.g. E1 in the ratings plan: a missing talk gives 404 before
   score validation). We have no product owner, so every open question comes to a person with
   options and a recommendation ([`.claude/rules/no-product-owner.md`](.claude/rules/no-product-owner.md)).
5. **Execute** with the [`/execute-plan`](.claude/skills/execute-plan/SKILL.md) skill: one phase at a time,
   tests and Hurl examples, one commit per phase, no broken windows. Before the PR,
   [`workflow/scripts/local-rollout.sh`](workflow/scripts/local-rollout.sh) runs the image we deploy
   against a Postgres that keeps its data, gates on `/health` showing the SHA and runs the Hurl
   examples (`.claude/rules/project.md`, "Prod-like Local Run").
6. **PR in the GitHub UI**, with a risk list from [`/list-risks`](.claude/skills/list-risks/SKILL.md):
   what the harness could see differently from our guesses, and what to watch after the deploy
   (first used on PR #12, acceptance). Merge to `main` deploys; check `/health` shows the new SHA,
   then check each listed risk on the harness.
7. **Evidence, then a person claims**, then a row in the claim log below.

The whole flow on one screen: [`workflow/README.md`](workflow/README.md). Team rules for agents:
[`.claude/rules/`](.claude/rules/) (`AGENTS.md` sends every agent there). Skills:
[`.claude/skills/`](.claude/skills/). Review agents: [`.claude/agents/`](.claude/agents/). ADRs:
[`workflow/adrs/`](workflow/adrs/).

This file is written as we go: [`.claude/rules/review-log.md`](.claude/rules/review-log.md) maps
each event (review caught a bug, claim, harness surprise, new artifact) to its section, and
`/execute-plan` ends by updating it and running `workflow/scripts/check-review.sh`.

The [CI workflow](.github/workflows/deploy.yml) runs `./mvnw -B clean verify` on Java 25
for PRs and pushes to main. For competition speed, main deployment runs independently
in parallel with the build/test job; a failing main test does not prevent deployment.
PRs never deploy; manual deployment is main-only.

A second CI job, `hurl`, runs the image we deploy (`workflow/scripts/local-rollout.sh`) with every
`examples/*.hurl`, including our spec-based checks `examples/<slug>-rules.hurl`, on each PR and on
main: twice on the same database, with a restart in between, since the harness checks run against
old data too. Like the Maven job it doesn't hold back a deploy; a red PR check is the gate (issue #26).

## 2. How we reviewed agent output

- **Plans** were reviewed by a second agent before any code was written, against the spec, the code
  and our rules. This plan's review came from Copilot CLI, the code from Claude Code: a different
  tool catches different things.
- **Decisions** the spec leaves open were approved by a person (recorded in the plan).
- **Tests:** `./mvnw package` with Testcontainers Postgres after every phase; one integration test
  class per feature (`CoSpeakersTest`, `RatingsTest`, …).
- **Coverage ratchet:** JaCoCo measures which production code the tests visit, and `./mvnw package`
  fails if line coverage drops below the floor in `pom.xml` (started at 72.2 %, 278/385 lines). A
  change that raised coverage raised the floor, until we froze it at 0.870 (#43): with several PRs
  open at once, every merge raised it and the other PRs conflicted on that line. Report in
  `target/site/jacoco/index.html`, summary from `workflow/coverage/line-coverage.sh`
  ([`workflow/coverage/`](workflow/coverage/README.md)).
- **Hurl examples** in `examples/` against a local service, then against our URL.
- **Harness feedback** (`/api/teams/team-2/feedback`) after deploy.

**Bug our review caught:** the ratings plan's ranking test created five talks for one speaker, but
the talks spec allows three. The plan review caught it before execution; the test would have failed
on a 409 before testing any ranking. The review of the reorg plan also caught that a per-line
HTML-comment check would have reported an empty claim-log section as filled.

**A bug the ratings verification caught before a Java upgrade did** (`workflow/plans/done/ratings_plan.md`,
Phase 1): a unit test pinning `ratedAtDisplay` failed on a laptop with JDK 21. From JDK 20 the
locale's US short format puts a narrow no-break space (U+202F) before AM/PM; the ratings spec wants
"exactly one ordinary space". Production ran JDK 11, so the harness never saw it, but the Spring
Boot 4 upgrade to Java 25 later would have broken a claimed feature. Fixed with an explicit
`M/d/yy, h:mm a` pattern before the upgrade; the base checks stayed OK through it.

**Bugs the acceptance plan review caught** (`workflow/plans/done/acceptance_plan_review.md`):
B1, the withdrawal check compared the body's `speakerId` (an `Integer` from Jackson) with
`TalkDto.speakerId` (a boxed `Long`) using `!=`, which compares references: every withdrawal would
have got 403, including the talk's own speaker. S1, a concurrency test accepted "ACCEPTED or
WAITLISTED" as the final state, which would have passed a stale-read bug. Both fixed in the plan
before any code was written. We also checked that the concurrency tests have teeth: with the row
lock removed, all four fail (up to 6 conflicting decisions answered 200).

After the acceptance deploy, the `spec-checker` agent found 6 decisions only partly covered (e.g.
accepting from WAITLISTED with one reviewer, 400 before 409 on a withdrawn talk). The tests were
added on branch `11-acceptance-gaps`; all passed against the deployed code. That branch was never
merged: we held it back to save a deploy.

**What the workshop registration plan review caught**
(`workflow/plans/done/workshop_registration_plan_review.md`): every planned test would have passed
even if the code read a workshop's status from before it got the row lock, letting someone register
after a withdrawal. It also found that `Utils.isValidEmail` accepts `@example.com`, and that a NUL
character would give a 500. We added a test that holds the lock while the withdrawal commits, then
checked the tests have teeth by breaking the code on purpose: with the stale read,
`aRegistrationWaitingForTheLockSeesTheWithdrawal` fails; with no lock, `aRushNeverOverfills` fails too.

**A bug `/pre-claim` caught after the deploy:** the `spec-checker` agent read
`longNameAndEmailAreAccepted` and saw that its email, `"e".repeat(10_000)`, compresses well, so it
fits in the unique index on `email_key`. A random email of the same length gave a 500
(`index row requires 10040 bytes, maximum size is 8191`), in a feature we had just deployed with all
tests green. Fix: at most 254 characters, 400 above that (D13). Without the check, this would have
been a claimed feature answering 500.

**What the scheduling plan review caught** (`workflow/plans/done/scheduling_plan_review.md`): B1, the
plan answered 200 for "already in this place" before checking the talk was still accepted, against
the approved error order (D13): a talk scheduled, then withdrawn, then put back in its own place would
have got 200 instead of 409. B2, the Hurl example would have written to the deployed programme, which
has only 15 places and is shared with the harness: a failed run would have left talks behind and made
the harness's own checks fail. Fixed before any code: the rules return the whole D13 outcome
(`SchedulingRulesTest.aNotAcceptedTalkInItsOwnPlaceIsAViolation`), and `examples/scheduling.hurl`
never writes, with the happy path in `examples/local/`. S2: the talk lock would have gone untested,
since the unique key also stops that race; the concurrency tests now assert the loser's reason, and
removing either lock fails one of them.

**Bug a person's PR review caught:** the coverage ratchet (PR #8) checked each branch against its
own floor. The reviewer pointed out that two parallel PRs can each pass and still break `main`
together, unnoticed, since the CI `tests` job has `continue-on-error`. Fix: update the branch with
`main` and set the floor from that run before merging (`workflow/coverage/README.md`).

The `plan-reviewer` agent's review of the Spring Boot 4 plan
(`workflow/plans/done/spring-boot-4_plan_review.md`) caught three blocking problems before any code
was written:
- The plan only tested the trailing slash on `/talks/{id}/` and treated keeping it as a question
  for the PO. `speakers.md:20` requires it, `POST /speakers/` included. Spring 6 drops it, so the
  upgrade would have broken a claimed feature.
- The plan promised identical error bodies, which can't hold: Spring 6 and Jackson 3 reword their
  own messages, and we send them to users with `include-message=always`.
- The before/after snapshot would have collided on the unique e-mail index, so its diff could
  never come out empty.

During the upgrade itself, `WireFormatTest` (written first, on Boot 2.7) caught that Spring 6
reorders the keys in each entry of a 400's `errors` array (`objectName`, `field` and
`rejectedValue` moved to the front). Without the net this would have reached the deployed service
unnoticed. `FieldErrorMixin` restores the old order.

On Boot 4.1.2 the net caught three more:
- **`submittedOn` broke.** Jackson 3 writes `java.sql.Date` as a timestamp
  (`"2026-11-05T23:00:00.000+00:00"`) instead of `yyyy-MM-dd`, and that's the wrong day outside
  UTC. The talks spec fixes this format and talks is claimed, so it would have cost harness checks.
  `WireFormatTest` caught it.
- **Four properties were silently ignored.** Boot 4 renamed `server.error.include-message`,
  `include-binding-errors`, `server.servlet.encoding.force` and `spring.jackson.parser.*`. The plan
  review had predicted two. Found from Boot's metadata, guarded by `WireFormatTest`.
- **Error responses lost `;charset=UTF-8`.** Caught by the `snapshot-api.sh` diff, not by the
  unit tests, which only pinned a 200. Fixed in `WebConfig` and pinned by a new
  `WireFormatTest.errorJsonIsUtf8`.

Merging `main` (coverage ratchet, PR #8) into the Boot 4 branch turned up two more bugs:
- **The ratchet would have measured nothing.** The Mockito fix had set surefire's `<argLine>`,
  which replaces the JaCoCo agent's. Found by reading the merged pom; fixed with `@{argLine}`.
- **`line-coverage.sh` overstated coverage.** It summed `jacoco.csv` rows, and those count a line
  twice when it opens an anonymous class (`WebConfig`). The script said 0.773 while the gate
  measured 0.772, so the floor the script recommended failed the build. The script now reads the
  report-wide total from `jacoco.xml`, the same number the gate uses.

## 3. Claim log

| Feature | Claimed or skipped | Tick | Evidence before claiming |
|---|---|---|---|
| Speakers | claimed (base feature) | 0 | Shipped with the base app; harness checks 1–4 OK |
| Talks | claimed (base feature) | 0 | Shipped with the base app; harness checks 1–6 OK |
| Ratings | claimed (base feature) | 0 | Shipped; then verified by `workflow/plans/done/ratings_plan.md` (`RatingsTest`, Hurl edge cases, E1 fix on branch `ratings-verify`); harness checks 1–5 OK |
| Co-speakers | claimed | 68 | `CoSpeakersTest` (11 tests incl. concurrency), `examples/co-speakers.hurl`, `workflow/plans/done/co_speakers_plan.md`. At tick 81: checks 1 and 2 FAULTY, see §4 |
| Spring Boot 4 | claimed | 163 | `WireFormatTest` (11 tests, written on 2.7 before the upgrade, assertions unchanged since `b2e8528`), `snapshot-api.sh` diff identical to the 2.7 baseline on Boot 3.5, 4.1.2 jar and the `linux/amd64` image, `examples/spring-boot-4.hurl`, all 60 tests green on JDK 25; `workflow/plans/done/spring-boot-4_plan.md`. After deploy (`e0a9680`, live ~13:58): base checks OK for ~10 ticks before the claim, `/pre-claim` all items pass, logs clean, 7/7 Hurl files green against our URL. After the claim: spring-boot-4 check-1 and check-2 OK at tick 166, all other claimed checks OK, no bleed |
| Acceptance | claimed | 148 | `AcceptanceTest` (19 tests: every transition, error codes, 4 concurrency tests failing without the lock), `examples/acceptance.hurl` green locally, `workflow/specs/acceptance.md` D1–D16; `/pre-claim` all 7 items pass (deploy `50ea65c` live, 6/6 Hurl files green against our URL, 18/18 checks of claimed features OK at tick 147). After the claim: acceptance check-1 to check-5 all OK at tick 150, no bleed. No check failed on D5, D6 or D7 (the harness doesn't say which rules its checks exercise) |
| Workshop registration | claimed | 205 | `WorkshopRegistrationTest` (24 tests: a rush of 60 gives exactly 20 registrations, a registration waiting for the lock sees a withdrawal; the lock tests go red with the lock removed), `examples/workshop-registration.hurl` against a local service, `workflow/plans/done/workshop_registration_plan.md`. Deployed as `42ac64e` (`/health`), `examples/run.sh` against our URL 8 of 8 files with Hurl 8.1.0. The harness shows no checks for it until claimed. After the claim: check-1, check-3 and check-4 OK; check-2 FAULTY on ticks 206–218 (decision D2, see §4). Fixes #38 (deployed 15:03, the harness accepted the new `POST` answer at tick 217) and #42 (deployed 15:12); the harness stopped ticking at 218 (15:06) before it could check #42 |
| Scheduling | not claimed (merged, #32) | – | `ProgrammeTest` (36 tests: D1–D20, 4 concurrency tests × 10 rounds; without the slot lock or the talk lock at least one fails), `SchedulingRulesTest` and `VenueTest` (48 tests), new code 100 % line and branch covered, `examples/scheduling.hurl` (never writes) and `examples/local/scheduling-happy-path.hurl` green twice against a local service, `workflow/plans/done/scheduling_plan.md`. Not yet deployed or run against our URL. Merged at 15:06 as `9fc6908`, right when the harness stopped ticking (tick 218), so it was never claimed |

| Ratings change request | not claimed | – | Released at tick 167; issue #39 open at the end |
| Submission cap change request | not claimed | – | Issue #40 open at the end |
| Programme export | not claimed | – | Plan in `workflow/plans/todo/programme_export_plan.md`, issue #25 open at the end |

**Final harness state (tick 218, when the harness stopped ticking at 15:06):** total 1279.95,
earned 1377.98, bled −98.03. Seven features claimed; every check OK except workshop
registration check-2. Ratings check-2 and check-4 and co-speakers check-3 had shown no result for
the last ~50 ticks (not FAULTY, no bleed); we didn't find out why.

## 4. What the harness caught that we didn't

**Co-speakers, claimed at tick 68, two of three checks FAULTY at tick 81.** The feature request
doesn't give status codes, so we decided them ourselves and pinned them in tests:
- removing someone who isn't a co-speaker → 200, unchanged (`removingSomeoneWhoIsNotACoSpeakerChangesNothing`);
  the harness fails on exactly that request (`DELETE` the same co-speaker twice).
- the main speaker as co-speaker → 409; the harness fails on that request (`PUT`).

Why we missed it: our tests checked *our* decisions, and nobody asked the product owner before
claiming. The decisions were in the plan but weren't flagged as "ask the PO". Lesson for the
workflow: open status-code decisions on a drip feature go to the product owner before the claim.
We changed the workflow accordingly: the spec template has an "Ask the PO?" column, `/make-plan`
flags such decisions, and `/pre-claim` won't say yes without the PO's answers.

Fix: PR #7 (`c4a6dd9`) answers 400 for the main speaker and 404 for removing someone who isn't a
co-speaker, deployed as `4ee0b38`. The harness timeline shows check-1 and check-2 FAULTY on ticks
69–90 and OK from tick 91. Total bleed was −29.33 at tick 90.

**A deploy can cost points, even with no change in behaviour.** At tick 121, 15 of the 18 claimed
checks went FAULTY for that one tick, all on `POST /speakers`, and bleed went from −30.93 (tick 119)
to −37.83 (tick 122). The container logs line up with the rollout of `3cc2288` (PR #8, build and
docs only): the new revision had started by 13:27:07Z and the old one shut down at 13:27:42Z. It
happened again at tick 126: 7 checks, bleed −37.83 to −41.93, as the rollout of `2ce9c6b` (PR #9)
shut down the old revision at 13:33:19Z. Tick 102 (two checks, also `POST /speakers`, no response
recorded) came soon after the deploy of `33ad4c3`. We didn't see this because we only looked at
current status, not the timeline.

**Workshop registration, claimed at tick 205, check-2 FAULTY from tick 206.** The transcript's
failing request: `POST /workshops/{id}/registrations` for an accepted `LIGHTNING_10` talk, which we
answered 404 "workshop not found" (decision D2: "it isn't a workshop, so that resource doesn't
exist"). The harness wants something else. Our tests pinned our own guess, and the risk list didn't
flag D2. Fix in #37: 409 after body validation, like a workshop that isn't accepted (D3, D4). After that deploy (tick 217) check-2 got past the `POST` and failed on the next request, `GET` on the same talk (404, flagged as a risk in PR #38); second fix: 200 `[]`.
The check failed on 13 ticks (206–218), about 1 point each. We never saw it go OK: the
harness stopped ticking at tick 218, before the second fix was deployed. Lesson: our own guess
(404, "that resource doesn't exist") was reasonable, but the harness treats a talk that isn't a
workshop like one that isn't accepted. `/list-risks` flagged the `GET` in PR #38 but not the `POST`
in the first place, because D2 was "approved" and not marked as an open risk.

**The rollout bleed's cause, found late (fix #31).** We saw deploys cost points at tick 121 but
only investigated at tick ~200. The cause: the new container answered every request, but slowly. The
harness's first burst after a rollout reached a cold JVM, `POST /speakers` took up to 5.3 s (50–70 ms
warm), and the harness gave up (`status: null` in the transcript). It was not the shutdown and not
the platform. Fix: a `StartupWarmup` that sends ~180 read-only or rejected requests to itself after
start, and `-XX:TieredStopAtLevel=1` so the JIT doesn't take CPU from requests on our small CPU
share. Locally at 0.5 CPU, the slowest request in the first burst went from 5.3 s to 0.8 s. On the
harness, `talks/check-5` was FAULTY at ticks 121, 144, 152, 154 (deploys); after #31 went live, the
deploys of #31 itself (tick 204) and #38 (tick 216) cost nothing. One more FAULTY tick (210)
lines up with the platform restarting the container on its own at 14:56:48.

## 5. Reusable artifacts

- **CI clean build** [`.github/workflows/deploy.yml`](.github/workflows/deploy.yml):
  the same clean Maven verification on PRs and main, with independent main deployment.

- **Plan skill** [`.claude/skills/make-plan/`](.claude/skills/make-plan/SKILL.md): restart-proof
  plans with a decisions list for the human.
- **Execution skill** [`.claude/skills/execute-plan/`](.claude/skills/execute-plan/SKILL.md): phase
  by phase, test-gated, one commit per phase.
- **Cross-tool plan review** (`workflow/plans/*/*_plan_review.md`): Blocking / Significant / Minor / What's good,
  with file:line.
- **Spec copies** in `workflow/specs/`, so agents work offline from the harness rate limit.
- **Spec template with an "ask the PO" column** [`workflow/templates/spec.md`](workflow/templates/spec.md)
  and the skill [`/spec-from-request`](.claude/skills/spec-from-request/SKILL.md): born from the
  co-speakers miss in §4.
- **Pre-claim gate** [`workflow/checklists/pre-claim.md`](workflow/checklists/pre-claim.md), run by
  [`/pre-claim`](.claude/skills/pre-claim/SKILL.md) with the
  [`spec-checker`](.claude/agents/spec-checker.md) agent.
- **Plan reviewer agent** [`.claude/agents/plan-reviewer.md`](.claude/agents/plan-reviewer.md): the
  review rubric, with our two real misses as examples.
- **Coverage floor** [`workflow/coverage/`](workflow/coverage/README.md): JaCoCo `check` with a
  floor property in `pom.xml`, plus a script that prints the current ratio. We ran it as a ratchet
  (only goes up) and froze it at 0.870 once parallel PRs kept conflicting on it. No extra tools, works on any Maven project.
- **Risk list skill** [`.claude/skills/list-risks/`](.claude/skills/list-risks/SKILL.md): every
  behaviour change ships with a list of what could break on the harness, ordered by likelihood, each
  with the fallback, and is checked off after the deploy. Works for any change where an external
  checker or client sees behaviour the spec didn't pin down.
- **JVM warm-up after start** [`StartupWarmup`](src/main/java/no/scienta/cfp/StartupWarmup.java)
  and `-XX:TieredStopAtLevel=1` in the [`Dockerfile`](Dockerfile): for any JVM service on a small
  CPU share that a checker or load balancer hits right after a rollout. Measure the first burst
  after start before reaching for it.
- **Scripts** in [`workflow/scripts/`](workflow/scripts/): a rate-limit-aware harness client that
  never prints the token, and a `REVIEW.md` check with its own test.
- **API snapshot** [`workflow/scripts/snapshot-api.sh`](workflow/scripts/snapshot-api.sh)
  `<base-url> <out-file>`. It sends ~55 fixed requests and normalises the answers (ids, e-mails,
  timestamp digits, framework messages), so two runs on the same build diff empty. Diff a snapshot
  from before any upgrade or refactor against one from after it, and you see every user-visible
  change. It was written for the Boot 4 upgrade; its first baseline is
  `workflow/plans/done/spring-boot-4.baseline.txt`.
- **Wire-format net** [`WireFormatTest`](src/test/java/no/scienta/cfp/WireFormatTest.java): asserts
  on raw response strings, so it survives a JSON library swap. It was checked by breaking each
  guarded behaviour with a property (8 of 8 caught).
- **Prod-like local run** [`workflow/scripts/local-rollout.sh`](workflow/scripts/local-rollout.sh)
  with the compose `app` profile: the same rollout gate as `.github/fagdag/rollout.sh`, on a laptop,
  against a database that keeps its data like prod. Works for any service with a Dockerfile and a
  `/health` that reports its version.
- **Transcript replay** [`workflow/scripts/transcript-to-hurl.sh`](workflow/scripts/transcript-to-hurl.sh):
  a harness check's requests as a Hurl file to replay locally, with production's answers as comments.
  With request logging (`RequestLogFilter`), the harness's traffic shows in the container logs.
- **Spec-based Hurl checks** [`examples/*-rules.hurl`](examples/) with the CI job `hurl` in
  [`deploy.yml`](.github/workflows/deploy.yml): 179 requests over five features, each commented with
  the spec rule or decision it checks, in the harness's data shapes, and re-runnable on a database
  that keeps its data. The job pattern (build the deployed image, compose it with Postgres, gate on
  `/health`, run Hurl twice around a restart) works for any HTTP service with a Dockerfile.

## 6. One thing we'd do differently

**Treat the first unexplained bleed as a bug, right away.** At tick 121 we saw 15 checks go FAULTY
during a docs-only deploy and wrote it down (§4), but we only answered with "merge less". The cause
(a cold JVM after start) took one agent about 20 minutes to find from the harness timeline and the
container logs, and the fix cost nothing on the next deploys. Before that, rollouts cost 4–13 points each,
over a third of our −98 bleed. The same habit would have helped with status codes:
the transcript of a FAULTY check says exactly which request failed (co-speakers, workshop D2), so
read it at the first FAULTY tick instead of guessing.

Other candidates we discussed: batch docs-only PRs with the next feature merge (each deploy cost
points until #31); freeze the coverage floor from the start, since a ratchet with many parallel PRs
made every merge conflict on one line of `pom.xml` (#43).
