# workflow/

How team-2 gets from a feature request to a claim. Every agent starts with
**"Read AGENTS.md and follow instructions"**: `AGENTS.md` has the organisers' facts and sends it to
[`.claude/rules/`](../.claude/rules/) (Claude Code loads that folder by itself).

```
feature request on the harness
  → /spec-from-request          our spec + decisions table           workflow/specs/<slug>.md
  → a person approves, asks the product owner about harness-visible decisions
  → /make-plan                  phased, restart-proof plan           todo/<slug>_plan.md
  → plan-reviewer (or another tool) reviews it, the plan answers     todo/<slug>_plan_review.md
  → /execute-plan               phase by phase, tests, one commit each
  → PR in the GitHub UI → merge to main deploys → /health shows the SHA
  → /pre-claim                  checklist with evidence              workflow/checklists/pre-claim.md
  → a person claims on the harness (permanent)
  → claim-log row in REVIEW.md §3; harness surprises in §4
```

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
| [`./adrs/`](./adrs/) | Architecture decision records |
| [`../.claude/rules/`](../.claude/rules/) | Team rules: `team-workflow.md`, `project.md` |
| [`../.claude/skills/`](../.claude/skills/) | `make-plan`, `make-master-plan`, `execute-plan`, `spec-from-request`, `pre-claim` |
| [`../.claude/agents/`](../.claude/agents/) | `plan-reviewer`, `spec-checker` |
| [`../.claude/settings.json`](../.claude/settings.json) | Shared permissions; agents can't read the harness token |

## A session, step by step

```sh
> Read AGENTS.md and follow instructions.
> /spec-from-request <slug>            # then a person approves the decisions
> /make-plan <slug>                    # then the plan-reviewer agent, then revise
> /execute-plan todo/<slug>_plan.md    # restart the agent between phases if the context is full
> /pre-claim <slug>                    # a person claims if it says yes
```

Personal preferences and machine settings go in `LOCAL.md` at the repo root (gitignored); agents
read it.
