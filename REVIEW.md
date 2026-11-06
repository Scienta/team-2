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
6. **PR in the GitHub UI**, merge to `main` deploys, check `/health` shows the new SHA.
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
- **Hurl examples** in `examples/` against a local service, then against our URL.
- **Harness feedback** (`/api/teams/team-2/feedback`) after deploy.

**Bug our review caught:** the ratings plan's ranking test created five talks for one speaker, but
the talks spec allows three. The plan review caught it before execution; the test would have failed
on a 409 before testing any ranking. The review of the reorg plan also caught that a per-line
HTML-comment check would have reported an empty claim-log section as filled.

The `plan-reviewer` agent's review of the Spring Boot 4 plan
(`workflow/plans/todo/spring-boot-4_plan_review.md`) caught three blocking problems before any code
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

## 3. Claim log

| Feature | Claimed or skipped | Tick | Evidence before claiming |
|---|---|---|---|
| Speakers | claimed (base feature) | 0 | Shipped with the base app; harness checks 1–4 OK |
| Talks | claimed (base feature) | 0 | Shipped with the base app; harness checks 1–6 OK |
| Ratings | claimed (base feature) | 0 | Shipped; then verified by `workflow/plans/done/ratings_plan.md` (`RatingsTest`, Hurl edge cases, E1 fix on branch `ratings-verify`); harness checks 1–5 OK |
| Co-speakers | claimed | 68 | `CoSpeakersTest` (11 tests incl. concurrency), `examples/co-speakers.hurl`, `workflow/plans/done/co_speakers_plan.md`. At tick 81: checks 1 and 2 FAULTY, see §4 |

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
co-speaker, deployed as `4ee0b38`. At tick 90 the harness's latest runs of check-1 and check-2
still showed the old answers (200, 409), so they likely ran before the deploy. TODO(human): confirm
the checks go OK, and record the bleed (−29.33 points at tick 90).

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
- **Scripts** in [`workflow/scripts/`](workflow/scripts/): a rate-limit-aware harness client that
  never prints the token, and a `REVIEW.md` check with its own test.
- **API snapshot** [`workflow/scripts/snapshot-api.sh`](workflow/scripts/snapshot-api.sh)
  `<base-url> <out-file>`. It sends ~55 fixed requests and normalises the answers (ids, e-mails,
  timestamp digits, framework messages), so two runs on the same build diff empty. Diff a snapshot
  from before any upgrade or refactor against one from after it, and you see every user-visible
  change. It was written for the Boot 4 upgrade; its first baseline is
  `workflow/plans/todo/spring-boot-4.baseline.txt`.
- **Wire-format net** [`WireFormatTest`](src/test/java/no/scienta/cfp/WireFormatTest.java): asserts
  on raw response strings, so it survives a JSON library swap. It was checked by breaking each
  guarded behaviour with a property (8 of 8 caught).

## 6. One thing we'd do differently

TODO(human)
