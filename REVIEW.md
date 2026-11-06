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
   owner), and a falsifiable "Check:" per phase. Plans live in `todo/` (moving to
   `workflow/plans/`).
3. **Review the plan.** A second agent, often a different tool, reviews the plan and writes
   `<feature>_plan_review.md` next to it; the first agent revises. Examples:
   `todo/ratings_plan_review.md`, `todo/workflow_reorg_plan_review.md`.
4. **Human approves the decisions** (e.g. E1 in the ratings plan: a missing talk gives 404 before
   score validation).
5. **Execute** with the [`/execute-plan`](.claude/skills/execute-plan/SKILL.md) skill: one phase at a time,
   tests and Hurl examples, one commit per phase, no broken windows.
6. **PR in the GitHub UI**, merge to `main` deploys, check `/health` shows the new SHA.
7. **Evidence, then a person claims**, then a row in the claim log below.

Team rules for agents: [`.claude/rules/project.md`](.claude/rules/project.md) (moving the rest of
`AGENTS.md`'s team half to `.claude/rules/`). ADRs: [`workflow/adrs/`](workflow/adrs/).

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

## 3. Claim log

| Feature | Claimed or skipped | Tick | Evidence before claiming |
|---|---|---|---|
| Speakers | claimed (base feature) | 0 | Shipped with the base app; harness checks 1–4 OK |
| Talks | claimed (base feature) | 0 | Shipped with the base app; harness checks 1–6 OK |
| Ratings | claimed (base feature) | 0 | Shipped; then verified by `todo/ratings_plan.md` (`RatingsTest`, Hurl edge cases, E1 fix on branch `ratings-verify`); harness checks 1–5 OK |
| Co-speakers | claimed | 68 | `CoSpeakersTest` (11 tests incl. concurrency), `examples/co-speakers.hurl`, `done/co_speakers_plan.md`. At tick 81: checks 1 and 2 FAULTY, see §4 |

## 4. What the harness caught that we didn't

**Co-speakers, claimed at tick 68, two of three checks FAULTY at tick 81.** The feature request
doesn't give status codes, so we decided them ourselves and pinned them in tests:
- removing someone who isn't a co-speaker → 200, unchanged (`removingSomeoneWhoIsNotACoSpeakerChangesNothing`);
  the harness fails on exactly that request (`DELETE` the same co-speaker twice).
- the main speaker as co-speaker → 409; the harness fails on that request (`PUT`).

Why we missed it: our tests checked *our* decisions, and nobody asked the product owner before
claiming. The decisions were in the plan but weren't flagged as "ask the PO". Lesson for the
workflow: open status-code decisions on a drip feature go to the product owner before the claim.

## 5. Reusable artifacts

- **Plan skill** [`.claude/skills/make-plan/`](.claude/skills/make-plan/SKILL.md): restart-proof
  plans with a decisions list for the human.
- **Execution skill** [`.claude/skills/execute-plan/`](.claude/skills/execute-plan/SKILL.md): phase
  by phase, test-gated, one commit per phase.
- **Cross-tool plan review** (`todo/*_plan_review.md`): Blocking / Significant / Minor / What's good,
  with file:line.
- **Spec copies** in `workflow/specs/`, so agents work offline from the harness rate limit.

## 6. One thing we'd do differently

TODO(human)
