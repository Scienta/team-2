# workflow/

How team-2 gets from a feature request to a claim. Every agent starts with
**"Read AGENTS.md and follow instructions"**: `AGENTS.md` has the organisers' facts and sends it to
[`.claude/rules/`](../.claude/rules/) (Claude Code loads that folder by itself).

```
feature request on the harness
  → /spec-from-request          our spec + decisions table           workflow/specs/<slug>.md
  → a person approves the decisions (no PO: options + recommendation, .claude/rules/no-product-owner.md)
  → /make-plan                  phased, restart-proof plan           plans/todo/<slug>_plan.md
  → plan-reviewer (or another tool) reviews it, the plan answers     plans/todo/<slug>_plan_review.md
  → /execute-plan               phase by phase, tests, one commit each
  → /list-risks                 what the harness could see differently  PR description
  → PR in the GitHub UI → merge to main deploys → /health shows the SHA → check each risk
  → /pre-claim                  checklist with evidence              workflow/checklists/pre-claim.md
  → a person claims on the harness (permanent)
  → claim-log row in REVIEW.md §3; harness surprises in §4
```

**Unattended variant: the spec pipeline** (set up 2026-11-06, issue #35; first supervised run pending).
`/loop 10m /spec-watch` in a local session: [`scripts/new-specs.sh`](scripts/new-specs.sh) finds
released, unclaimed specs without a `spec:<slug>` issue, `/spec-watch` opens a `pipeline` issue
assigned to Daniel, and [`/implement-issue`](../.claude/skills/implement-issue/SKILL.md) runs the
flow above with the [`issue-implementer`](../.claude/agents/issue-implementer.md) agent, taking the
recommended option at each decision. It ends in a **draft** PR with the decisions on top: a person
approves them, marks it ready and merges. State lives in issue labels (`pipeline:running`,
`pipeline:pr-open`, `blocked`), read by [`scripts/pipeline-queue.sh`](scripts/pipeline-queue.sh).

## Artifacts

| Path | What it's for |
|---|---|
| [`specs/`](specs/) | Copies of the harness specs and our specs for drip features |
| [`templates/spec.md`](templates/spec.md) | Spec with a decisions table and an "ask the PO" column |
| [`templates/plan.md`](templates/plan.md) | Plan: checklist, restart context, decisions, phases with checks, review response |
| [`checklists/pre-claim.md`](checklists/pre-claim.md) | Evidence a person needs before claiming |
| [`scripts/harness.sh`](scripts/harness.sh) | Harness API client (spec, feedback, timeline), handles `429`, never prints the token |
| [`scripts/check-review.sh`](scripts/check-review.sh) | Every `REVIEW.md` heading has our text (run before 17:00); tested by `test-check-review.sh` |
| [`scripts/check-paths.sh`](scripts/check-paths.sh) | No references to paths that moved |
| [`scripts/local-rollout.sh`](scripts/local-rollout.sh) | Prod-like run: the deployed image with this SHA, persistent Postgres, `/health` gate, Hurl examples |
| [`../examples/*-rules.hurl`](../examples/) | Spec-based checks per feature, harness-shaped data, re-runnable on old data; CI job `hurl` runs them against the built image (`.claude/rules/project.md`, "Spec-based Hurl Checks") |
| [`scripts/transcript-to-hurl.sh`](scripts/transcript-to-hurl.sh) | A harness check's transcript as Hurl, to replay locally; tested by `test-transcript-to-hurl.sh` |
| [`scripts/new-specs.sh`](scripts/new-specs.sh) | Released, unclaimed harness specs without a `spec:<slug>` issue; tested by `test-new-specs.sh` |
| [`scripts/pipeline-queue.sh`](scripts/pipeline-queue.sh) | The spec pipeline's issues by state (running, blocked, pr-open, queued); tested by `test-pipeline-queue.sh` |
| [`coverage/`](coverage/) | JaCoCo line coverage ratchet: the build fails if coverage drops; `line-coverage.sh` prints it |
| [`./adrs/`](./adrs/) | Architecture decision records |
| [`../.claude/rules/`](../.claude/rules/) | Team rules: `team-workflow.md`, `project.md` |
| [`../.claude/skills/`](../.claude/skills/) | `make-plan`, `make-master-plan`, `execute-plan`, `spec-from-request`, `list-risks`, `pre-claim`, `spec-watch`, `implement-issue` |
| [`../.claude/agents/`](../.claude/agents/) | `plan-reviewer`, `spec-checker`, `issue-implementer` |
| [`../.claude/settings.json`](../.claude/settings.json) | Shared permissions; agents can't read the harness token |

## A session, step by step

```sh
> Read AGENTS.md and follow instructions.
> /spec-from-request <slug>            # then a person approves the decisions
> /make-plan <slug>                    # then the plan-reviewer agent, then revise
> /execute-plan workflow/plans/todo/<slug>_plan.md    # restart the agent between phases if the context is full
> /pre-claim <slug>                    # a person claims if it says yes
```

Personal preferences and machine settings go in `LOCAL.md` at the repo root (gitignored); agents
read it.
