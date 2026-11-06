# Plan: spec pipeline (watch specs → issue → implement → draft PR)

- [x] Phase 1: `workflow/scripts/new-specs.sh` + `test-new-specs.sh`
- [ ] Phase 2: Skills `/spec-watch` and `/implement-issue`, agent `issue-implementer`
- [ ] Phase 3: Rules and permissions: the draft-PR exception, guard rails in `.claude/settings.json`
- [ ] Phase 4: Docs (`workflow/README.md`, `REVIEW.md` §1/§5), backfill `spec:<slug>` labels, finish

## Context (read this after a restart)

- Issue #35. Branch `35-spec-pipeline`, worktree `../team-2-wt/35-spec-pipeline` (off `origin/main`;
  the main checkout is on someone's `25-programme-export` with uncommitted work: don't touch it).
- Request (Daniel, 2026-11-06): periodically list the harness specs, match them against team-2's
  GitHub issues; a new spec without an issue gets an issue assigned to `danielbakkelund`, then an
  agent plans and implements it, ending in a PR made with `gh`.
- No Java changes. Tests are shell scripts like `workflow/scripts/test-check-review.sh`.
- Harness access only via `workflow/scripts/harness.sh` (agents can't read the token;
  `.claude/settings.json` denies it). Rate limit: bursts of 5, then 1/s.
- `harness.sh feedback` lists every feature: `.features[] | {slug, claimed, checks}`. Unreleased
  specs answer `404` on `harness.sh spec <slug>`.
- ADRs: `workflow/adrs/0001-workflow-lives-in-claude-and-workflow.md` (workflow lives in `.claude/`
  and `workflow/`).

## Decisions (approved by Daniel, 2026-11-06, via question prompt)

- **D1. Open decisions in an unattended run:** the agent picks the recommended option, records
  "agent default, pending human" in the spec's "Approved by" column, and opens a **draft** PR that lists
  them at the top. A person approves before marking it ready. (Options were: draft PR / pause on the
  issue for approval / fully autonomous.)
- **D2. PRs via `gh`:** only `gh pr create --draft`, only from `/implement-issue`. Never merge, never
  `gh pr ready`, never push to `main`. Enforced by deny rules in `.claude/settings.json`.
- **D3. Scheduler:** `/loop 10m /spec-watch` in a local Claude Code session. Not CI: `AGENTS.md`
  forbids agents in CI.
- **D4. Matching a spec to an issue:** label `spec:<slug>` on the issue, open or closed. A spec
  counts as "new" when it's released (`harness.sh spec` gives 2xx), unclaimed, and no issue carries its
  label. Claimed features are done and never get an issue. Existing issues for drip features
  (#16 scheduling, #24 workshop-registration, #25 programme-export) get their label in Phase 4, so the
  watcher doesn't duplicate them.
- **D5. Concurrency:** the watcher creates the issue *before* it starts the implementer, so the next
  tick sees the label and skips it. At most 2 implementers at once (rate limit, Docker, tokens);
  further issues wait for a later tick (an open `spec:` issue assigned to danielbakkelund with no
  branch `<n>-*` on origin is "queued").
- **D6. Worktrees:** each implementer works in `../team-2-wt/<n>-<slug>` on branch `<n>-<slug>` off
  `origin/main`, so parallel runs and a person's checkout don't collide. `LOCAL.md` is gitignored:
  the implementer reads it from the main checkout.

## Phase 1: `new-specs.sh`

1. `workflow/scripts/new-specs.sh`: prints one `<slug>` per line for a released, unclaimed feature
   without a `spec:<slug>` issue. Inputs: `harness.sh feedback`, `gh issue list --state all --json
   labels --limit 500`; `NEW_SPECS_FEEDBACK` / `NEW_SPECS_ISSUES` (files) override both for tests,
   and `NEW_SPECS_RELEASED` (a command) overrides the release check (`harness.sh spec <slug>`).
2. `workflow/scripts/test-new-specs.sh` with fixtures: claimed → skipped; labelled issue (open or
   closed) → skipped; unreleased → skipped; new → printed; null `checks` handled.

Check: `workflow/scripts/test-new-specs.sh` ends with `all new-specs tests passed`; against the real
harness `workflow/scripts/new-specs.sh` prints the unlabelled features (before Phase 4's backfill:
`workshop-registration scheduling ratings-cr submission-cap-cr programme-export`).
Commit: `feat: new-specs.sh lists released specs without an issue`.

## Phase 2: skills and agent

1. `.claude/skills/spec-watch/SKILL.md`: run `new-specs.sh`; per slug create the label, then
   `gh issue create --title "<spec H1>" --label spec:<slug> --assignee danielbakkelund`; start
   `/implement-issue <n>` for queued issues (D5), in the background; report what it did.
2. `.claude/skills/implement-issue/SKILL.md`: the orchestration, run by the main session:
   a) `issue-implementer` agent: worktree, `/spec-from-request` with D1, `/make-plan`, comment on the
   issue; b) `plan-reviewer` agent on the plan (read-only, given the worktree path); c) the same
   implementer via SendMessage: review response, `/execute-plan`, `local-rollout.sh`, `/list-risks`,
   push the branch, `gh pr create --draft` with `Closes #<n>`, decisions, risks; comment the PR URL on
   the issue. On failure: comment on the issue with the label `blocked`, stop, don't open a PR.
3. `.claude/agents/issue-implementer.md`: the implementer's brief: rules to read, worktree and
   `LOCAL.md`, D1/D2, never claim, never merge, stop and report on a red build it can't fix.

Check: `/spec-watch` dry run (`new-specs.sh` only) lists the same slugs as Phase 1; the files pass
`workflow/scripts/check-paths.sh`. Commit: `feat: spec-watch and implement-issue pipeline`.

## Phase 3: rules and permissions

1. `.claude/rules/team-workflow.md` and the team part of `AGENTS.md` (below the `---`): the one
   exception to "PRs in the GitHub UI" (D2).
2. `.claude/settings.json`: allow `gh issue`, `gh label`, `gh pr create --draft`, `git worktree`;
   deny `gh pr merge`, `gh pr ready`, `git push origin main`, `git push --force`.

Check: `jq . .claude/settings.json` parses; `grep -n "draft" .claude/rules/team-workflow.md AGENTS.md`
shows the exception in both. Commit: `chore: allow draft PRs from the spec pipeline only`.

## Phase 4: docs, labels, finish

1. `workflow/README.md`: the pipeline in the flow and the artifact table. `REVIEW.md` §1 (workflow
   step) and §5 (artifacts).
2. Backfill labels: `spec:scheduling` on #16, `spec:workshop-registration` on #24,
   `spec:programme-export` on #25.
3. `./mvnw package` green (nothing in Java changed, but no broken windows); `check-review.sh`;
   plan to `done/`.

Check: `workflow/scripts/new-specs.sh` prints exactly `ratings-cr` and `submission-cap-cr`.
Commit: `docs: spec pipeline in workflow README and REVIEW`.

## Review response

| Finding | Response |
|---|---|
