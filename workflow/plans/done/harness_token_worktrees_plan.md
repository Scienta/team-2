# Plan: harness.sh reads the token from the main checkout (issue #41)

## Context (restart from here)

- `workflow/scripts/harness.sh` reads `.fagdag/harness-token` from the checkout it runs in. The token
  is gitignored, so a git worktree (`git worktree add`, Claude Code's agent worktrees) has none.
  During PR #20, a background agent copied the token into its worktree to get past this, which goes
  around the `Read(.fagdag/harness-token)` deny rule in `.claude/settings.json`.
- Julian chose option 2 of four: fix the script so no copy is ever needed, and add a rule against
  copying the token. The deny rules (option 3) and an environment variable (option 4) were not chosen.
- Branch `41-harness-token-worktrees`, worktree `../team-2-token`.

## Decisions

- **D1. Lookup order:** the current checkout's `.fagdag/harness-token` first, so a person who pasted
  it into a worktree keeps working. Then the main checkout's, found as the parent of
  `git rev-parse --path-format=absolute --git-common-dir` (git ≥ 2.31; we have 2.43).
- **D2. Missing token:** exit 1 with a message naming both paths and where to get the token (the
  harness `/feedback` page). The token itself is never printed.
- **D3. No new test harness.** The script's only logic is a path lookup. It's checked by hand from a
  worktree with no token (below), as `test-check-review.sh` would need a fake harness to do more.

## Phase 1: Script and rule

- [x] `harness.sh`: a `token_file` lookup per D1/D2, used by `fetch`.
- [x] `.claude/rules/project.md`: never copy, move, `cat` or print `.fagdag/harness-token`; in a
  worktree, `harness.sh` finds the main checkout's.
- [x] `REVIEW.md` §4 or §6 if it fits, then `workflow/scripts/check-review.sh`.

Check:
- from `../team-2-token`, which has no token: `workflow/scripts/harness.sh feedback` answers 200;
- `transcript-to-hurl.sh`, which calls `harness.sh`, works there too;
- with a missing token everywhere (a temporary clone), the D2 message shows;
- `shellcheck` is clean if it's installed.

Commit: `fix: harness.sh finds the token from a worktree`.

## Review response

| Finding | Response |
|---|---|
| No second-agent review | A 15-line script change and two doc entries; reviewed by a person in the PR instead |
