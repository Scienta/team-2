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
   score validation).
5. **Execute** with the [`/execute-plan`](.claude/skills/execute-plan/SKILL.md) skill: one phase at a time,
   tests and Hurl examples, one commit per phase, no broken windows.
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

## 2. How we reviewed agent output

- **Plans** were reviewed by a second agent before any code was written, against the spec, the code
  and our rules. This plan's review came from Copilot CLI, the code from Claude Code: a different
  tool catches different things.
- **Decisions** the spec leaves open were approved by a person (recorded in the plan).
- **Tests:** `./mvnw package` with Testcontainers Postgres after every phase; one integration test
  class per feature (`CoSpeakersTest`, `RatingsTest`, …).
- **Coverage ratchet:** JaCoCo measures which production code the tests visit, and `./mvnw package`
  fails if line coverage drops below the floor in `pom.xml` (started at 72.2 %, 278/385 lines). A
  change that raises coverage raises the floor; lowering it needs a person's consent. Report in
  `target/site/jacoco/index.html`, summary from `workflow/coverage/line-coverage.sh`
  ([`workflow/coverage/`](workflow/coverage/README.md)).
- **Hurl examples** in `examples/` against a local service, then against our URL.
- **Harness feedback** (`/api/teams/team-2/feedback`) after deploy.

**Bug our review caught:** the ratings plan's ranking test created five talks for one speaker, but
the talks spec allows three. The plan review caught it before execution; the test would have failed
on a 409 before testing any ranking. The review of the reorg plan also caught that a per-line
HTML-comment check would have reported an empty claim-log section as filled.

**Bugs the acceptance plan review caught** (`workflow/plans/done/acceptance_plan_review.md`):
B1, the withdrawal check compared the body's `speakerId` (an `Integer` from Jackson) with
`TalkDto.speakerId` (a boxed `Long`) using `!=`, which compares references: every withdrawal would
have got 403, including the talk's own speaker. S1, a concurrency test accepted "ACCEPTED or
WAITLISTED" as the final state, which would have passed a stale-read bug. Both fixed in the plan
before any code was written. We also checked that the concurrency tests have teeth: with the row
lock removed, all four fail (up to 6 conflicting decisions answered 200).

**Bug a person's PR review caught:** the coverage ratchet (PR #8) checked each branch against its
own floor. The reviewer pointed out that two parallel PRs can each pass and still break `main`
together, unnoticed, since the CI `tests` job has `continue-on-error`. Fix: update the branch with
`main` and set the floor from that run before merging (`workflow/coverage/README.md`).

## 3. Claim log

| Feature | Claimed or skipped | Tick | Evidence before claiming |
|---|---|---|---|
| Speakers | claimed (base feature) | 0 | Shipped with the base app; harness checks 1–4 OK |
| Talks | claimed (base feature) | 0 | Shipped with the base app; harness checks 1–6 OK |
| Ratings | claimed (base feature) | 0 | Shipped; then verified by `workflow/plans/done/ratings_plan.md` (`RatingsTest`, Hurl edge cases, E1 fix on branch `ratings-verify`); harness checks 1–5 OK |
| Co-speakers | claimed | 68 | `CoSpeakersTest` (11 tests incl. concurrency), `examples/co-speakers.hurl`, `workflow/plans/done/co_speakers_plan.md`. At tick 81: checks 1 and 2 FAULTY, see §4 |
| Acceptance | claimed | 148 | `AcceptanceTest` (19 tests: every transition, error codes, 4 concurrency tests failing without the lock), `examples/acceptance.hurl` green locally, `workflow/specs/acceptance.md` D1–D16; `/pre-claim` all 7 items pass (deploy `50ea65c` live, 6/6 Hurl files green against our URL, 18/18 checks of claimed features OK at tick 147). After the claim: acceptance check-1 to check-5 all OK at tick 150, no bleed. No check failed on D5, D6 or D7 (the harness doesn't say which rules its checks exercise) |

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

## 5. Reusable artifacts

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
- **Coverage ratchet** [`workflow/coverage/`](workflow/coverage/README.md): JaCoCo `check` with a
  floor property in `pom.xml` that only goes up, plus a script that prints the value to raise it
  to. No extra tools, works on any Maven project.
- **Risk list skill** [`.claude/skills/list-risks/`](.claude/skills/list-risks/SKILL.md): every
  behaviour change ships with a list of what could break on the harness, ordered by likelihood, each
  with the fallback, and is checked off after the deploy. Works for any change where an external
  checker or client sees behaviour the spec didn't pin down.
- **Scripts** in [`workflow/scripts/`](workflow/scripts/): a rate-limit-aware harness client that
  never prints the token, and a `REVIEW.md` check with its own test.

## 6. One thing we'd do differently

Candidate: don't merge docs-only PRs on their own, since every push to `main` redeploys and a
rollout can bleed points (§4, tick 121). Batch them with the next feature merge.

TODO(human): pick the one thing we'd do differently (this candidate or another).
