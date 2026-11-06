# Review: spec_pipeline_plan.md

Reviewer: `plan-reviewer` agent (Claude Code), 2026-11-06, on the plan at commit 59399f7^.

**Verdict:** not safe to run unattended yet: no harness token in worktrees, and the "queued" rule
double-starts issues.

## Blocking
- **B1.** `harness.sh` reads `.fagdag/harness-token` from the worktree root; the token is gitignored, so
  every worktree (implementers, and this plan's own Phase 1 check) fails. Resolve it from the main
  worktree via `git rev-parse --git-common-dir`; don't copy the secret.
- **B2.** D5's "queued = no `<n>-*` branch on origin" holds for the whole run (the push is at the end),
  so each tick starts another implementer; "at most 2" has no state a tick can count. Use state labels
  (`pipeline:running`, `pipeline:pr-open`, `blocked`).
- **B3.** Backfilling `spec:` on #16/#24/#25 makes people's issues look queued; the pipeline would start
  implementing #25 (in progress in the main checkout). Only pick up issues the pipeline created
  (a `pipeline` label).

## Significant
- **S1.** The skills the implementer calls stop for a human (`spec-from-request` step 4, `make-plan`
  step 8 spawns an agent, `execute-plan` Docker/130k/ask-before-push). The brief must override each.
- **S2.** Resuming via SendMessage is unverified: fall back to a fresh implementer given the plan and
  review paths. "Background" vs "run by the main session" is contradictory: decide.
- **S3.** Parallel `local-rollout.sh` runs collide on ports 8080/5432: derive `APP_PORT`/`PG_PORT` from n.
- **S4.** Deny patterns are prefix-bypassable (`git push origin HEAD:main`, `gh api …/merge`); the real
  guard is branch protection on `main`. Deny `git push *main*`, `gh api`, `gh pr ready/merge`, and the
  token via Bash. Make the check concrete.
- **S5.** Say which checkout runs the `/loop` session.
- **S6.** A red `main` sends every implementer off fixing it: circuit breaker.
- **S7.** Title-match safety net for hand-made issues without the label; check releases only after the
  cheap filters (rate limit is shared).
- **S8.** Nothing checks `/implement-issue` itself: one supervised end-to-end run before saying it works.

## Minor
- **M1.** Checks hard-code harness state. **M2.** Parallel implementers conflict on `REVIEW.md`: put the
  rows in the PR description. **M3.** `gh label create` on an existing label: `--force`. **M4.** Skills
  and deny rules in the same PR. **M5.** `execute-plan` step 6 "ask before push" contradicts the exception.

## What's good (keep)
Issue before implementer, local scheduler (no agents in CI), draft PR + "agent default, pending
human", one worktree per issue, testable `new-specs.sh`, main session orchestrates, `blocked` + no PR.
