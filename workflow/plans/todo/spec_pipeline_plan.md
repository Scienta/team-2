# Plan: spec pipeline (watch specs → issue → implement → draft PR)

- [x] Phase 1: `workflow/scripts/new-specs.sh` + `test-new-specs.sh` (and `harness.sh` in worktrees)
- [x] Phase 2: Rules and permissions: the draft-PR exception, deny rules, `spec:` labels in `issues.md`
- [ ] Phase 3: `pipeline-queue.sh`, skills `/spec-watch` and `/implement-issue`, agent `issue-implementer`
- [ ] Phase 4: Docs (`workflow/README.md`, `REVIEW.md` §1/§5), backfill `spec:` labels, finish
- [ ] Phase 5 (after the PR, with a person watching): one supervised end-to-end run, then `/loop`

## Context (read this after a restart)

- Issue #35. Branch `35-spec-pipeline`, worktree `../team-2-wt/35-spec-pipeline` (off `origin/main`;
  the main checkout is on someone's `25-programme-export` with uncommitted work: don't touch it).
- Request (Daniel, 2026-11-06): periodically list the harness specs, match them against team-2's
  GitHub issues; a new spec without an issue gets an issue assigned to `danielbakkelund`, then an
  agent plans and implements it, ending in a PR made with `gh`.
- No Java changes. Tests are shell scripts like `workflow/scripts/test-check-review.sh`.
- Harness access only via `workflow/scripts/harness.sh` (agents can't read the token). It reads the
  main checkout's token from a worktree (B1). Rate limit: bursts of 5, then 1/s, shared with people.
- `harness.sh feedback` lists every feature: `.features[] | {slug, claimed, checks}`. Unreleased
  specs answer `404` on `harness.sh spec <slug>`.
- ADRs: `workflow/adrs/0001-workflow-lives-in-claude-and-workflow.md`.

## Decisions

Approved by Daniel, 2026-11-06 (question prompt): D1, D2, D3. D4–D9 are the agent's, from the plan
review; a person checks them on the PR.

- **D1. Open decisions in an unattended run:** the agent picks the recommended option, records
  "agent default, pending human" in the spec's "Approved by" column, and opens a **draft** PR that lists
  them at the top. A person approves before marking it ready.
- **D2. PRs via `gh`:** only `gh pr create --draft`, only from `/implement-issue`. Never merge, never
  `gh pr ready`, never push to `main`. Deny rules in `.claude/settings.json`; the real guard is branch
  protection on `main` (a person turns it on: `TODO(human)` in Phase 5).
- **D3. Scheduler:** `/loop 10m /spec-watch` in a local Claude Code session, not CI. The session runs
  in a dedicated worktree on an up-to-date `main` (`../team-2-wt/pipeline`, `git pull` each tick); until
  this PR is merged, in `../team-2-wt/35-spec-pipeline` (S5).
- **D4. Matching:** label `spec:<slug>` on the issue, open or closed. New = released, unclaimed, no
  labelled issue. Safety net: if an open issue's title contains the spec's H1 title, `/spec-watch`
  labels that issue `spec:<slug>` instead of creating one (S7).
- **D5. Ownership and state (B2, B3):** the pipeline only works on issues labelled `pipeline` (it adds
  it at creation). State labels: `pipeline:running` (put on *before* the implementer starts),
  `pipeline:pr-open`, `blocked`. Queued = open, `pipeline` + `spec:*`, no state label.
- **D6. One at a time (S2):** at most one `pipeline:running` issue. The implementer runs as a
  background agent; the main session orchestrates on its notifications, so `/loop` ticks keep firing
  and only create issues meanwhile.
- **D7. Worktrees and ports (S3):** each implementer works in `../team-2-wt/<n>-<slug>` on branch
  `<n>-<slug>` off `origin/main`; `LOCAL.md` from the main checkout; `APP_PORT=18000+n`,
  `PG_PORT=15000+n`, `COMPOSE_PROJECT_NAME=cfp-<n>` for `local-rollout.sh`.
- **D8. Circuit breaker (S6):** the implementer runs `./mvnw package` on the fresh worktree first. Red
  → label `blocked`, comment "main red at <sha>", stop. `/spec-watch` starts nothing while an issue is
  `blocked`; a person removes the label.
- **D9. REVIEW.md (M2):** implementers don't edit `REVIEW.md`; its rows go in the PR description.

## Phase 2: rules and permissions

1. `.claude/rules/team-workflow.md` ("Git Workflow") and the team part of `AGENTS.md` (below `---`):
   the one exception (D2). `execute-plan` step 6: "unless `/implement-issue` runs you" (M5).
2. `.claude/rules/issues.md`: an issue for a harness feature carries `spec:<slug>` (D4).
3. `.claude/settings.json`: allow `gh issue`, `gh label`, `gh pr create --draft`, `git worktree`; deny
   `gh pr merge`, `gh pr ready`, `gh api`, `git push` to anything with `main`, `git push --force`, and
   `cat`/`Read` of the token (S4).

Check: `jq . .claude/settings.json` parses; in a fresh session `gh pr merge 1` and
`git push origin HEAD:main` are refused by permissions (done in Phase 5, the session must reload).
Commit: `chore: allow draft PRs from the spec pipeline only`.

## Phase 3: queue script, skills, agent

1. `workflow/scripts/pipeline-queue.sh`: prints `running <n> <slug>`, `blocked <n> <slug>`,
   `queued <n> <slug>` from `gh issue list --label pipeline --state open --json number,labels`
   (override `PIPELINE_ISSUES` for tests); `test-pipeline-queue.sh`.
2. `.claude/skills/spec-watch/SKILL.md`: one tick: `git pull`; `new-specs.sh`; per slug D4 safety net
   or `gh issue create --label pipeline --label spec:<slug> --assignee danielbakkelund`; then
   `pipeline-queue.sh`: nothing running or blocked → label the first queued `pipeline:running` and start
   `/implement-issue <n>`. Report in three lines.
3. `.claude/skills/implement-issue/SKILL.md` (main session): a) background `issue-implementer`: spec
   and plan; b) `plan-reviewer` on the plan; c) the implementer again (SendMessage, else a fresh one
   given the plan and review paths): review response, execute, rollout, risks, push, draft PR; then
   label `pipeline:pr-open`. Any failure → `blocked` + comment, no PR.
4. `.claude/agents/issue-implementer.md`: the brief, with each stop point of `spec-from-request`,
   `make-plan` and `execute-plan` and what replaces it (S1), D7–D9, never claim.

Check: `test-pipeline-queue.sh` passes; `workflow/scripts/check-paths.sh` passes.
Commit: `feat: spec-watch and implement-issue pipeline`.

## Phase 4: docs, labels, finish

1. `workflow/README.md` (flow, artifacts), `REVIEW.md` §1 and §5: described as *set up, first run
   pending*, not as working (S8).
2. Labels: create `pipeline`, `pipeline:running`, `pipeline:pr-open`, `blocked` (`--force`, M3);
   `spec:scheduling` on #16, `spec:workshop-registration` on #24, `spec:programme-export` on #25, no
   `pipeline` label (B3).
3. `./mvnw package` green; `check-review.sh`; plan to `done/` once Phase 5 has its evidence.

Check: `new-specs.sh` prints the unclaimed, released features minus the labelled ones (recorded in
the commit message, M1). Commit: `docs: spec pipeline in workflow README and REVIEW`.

## Phase 5: supervised first run (a person watching)

1. A person opens the PR for this branch in the GitHub UI and turns on branch protection for `main`.
2. In the pipeline worktree, `/spec-watch` once. Evidence: the issue URL, plan and review files in the
   feature worktree, the draft PR URL with decisions on top, `gh pr view <n> --json isDraft` = true.
3. Then `/loop 10m /spec-watch`.

## Review response

| Finding | Response |
|---|---|
| B1 token in worktrees | Fixed in 59399f7: `harness.sh` falls back to the main checkout's token |
| B2 queued double-starts | D5 state labels, D6 one running at a time |
| B3 backfill hands over people's issues | D5: only `pipeline`-labelled issues; backfill adds `spec:` only |
| S1 stop points in skills | Phase 3.4 brief overrides each one |
| S2 SendMessage, background vs main | D6; Phase 3.3 fallback to a fresh implementer |
| S3 port collisions | D7 ports from the issue number |
| S4 bypassable denies, token via Bash | Phase 2.3 wider denies; branch protection in Phase 5 |
| S5 which checkout runs the loop | D3 |
| S6 red main | D8 |
| S7 hand-made issues, rate limit | D4 title safety net; `new-specs.sh` already checks releases last |
| S8 no end-to-end check | Phase 5; docs say "first run pending" until then |
| M1 hard-coded harness state | Phase 4 check phrased against `harness.sh feedback` |
| M2 REVIEW.md conflicts | D9 |
| M3 existing labels | `--force` |
| M4 same PR | Kept: Phases 2 and 3 in one PR |
| M5 execute-plan ask-before-push | Phase 2.1 |
