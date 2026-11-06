# Review: `todo/workflow_reorg_plan.md`

Reviewed against the repo as it stands on `main` (`git status` clean apart from the untracked plan),
`AGENTS.md`, `.claude/README.md`, `workflow/README.md` and `REVIEW.md`.

**Verdict: sound plan, wrong priorities and too long for its own time box.** The structure,
restart notes and per-phase checks are good. Two issues are blocking (B1, B2), four are significant
(S1–S4), the rest are nits. Fix B1–B2 and S1–S2 before executing.

## Blocking

**B1. D2's pointer must be an instruction, not a signpost.**
**Team decision: `AGENTS.md` stays at the repo root and points into `.claude/`.** That settles the
*where*; the risk is the *wording*. Phase 2 moves the team half into `.claude/rules/team-workflow.md`,
which only Claude Code loads automatically. This review was produced by Copilot CLI, which reads
`CLAUDE.md` → `@AGENTS.md` and nothing else; a descriptive two-line pointer ("Team workflow:
`.claude/rules/`…") leaves it free to carry on without the plan/execute workflow, the
PR-not-`gh` rule or the no-broken-windows gate.
*Fix:* make the pointer imperative and complete, and put it near the top of the team section it
replaces, not at the bottom:

> **Every agent must now read `.claude/rules/*.md` before doing anything else.** Claude Code loads
> them automatically; other agents read them explicitly. Skills are in `.claude/skills/`, plans in
> `workflow/plans/`, the process in `workflow/README.md`.

Add the same instruction to the top of `.claude/rules/team-workflow.md` so it is self-describing,
and check it by asking a non-Claude agent "what's our git workflow?" after Phase 2 — if it can't
answer, the pointer is too soft. Note the eligibility rule only needs something in `.claude/` or
`workflow/` to change; it never required emptying `AGENTS.md`, so err on the side of leaving enough
in `AGENTS.md` that a cold agent can't miss the rules.

**B2. Phase 0 is blocked right now, and it blocks all eight other phases.**
`git log origin/main..origin/ratings-verify` prints 6 commits (`f4fb6d6`…`e52df3d`), so the
precondition fails today and the plan says "stop and ask the human". That makes a 45-minute plan
wait on a human merge, and `e52df3d chore: Move completed ratings plan to done/` is exactly the
commit that will conflict.
*Fix:* decouple. Only Phase 1's `git mv todo … / git mv done …` depends on `ratings-verify`;
Phases 2–7 do not. Split Phase 1 into 1a (`skills/`, `adrs/`, `.claude/rules/`) and 1b (the plan
folders), and run 1b last, after the merge. Then nothing is blocked.

## Significant

**S1. Priority is inverted against the 17:00 rule.** Eligibility needs (a) *any* add/change in
`.claude/` or `workflow/` and (b) text under every heading of `REVIEW.md`. (a) is already satisfied
by the end of Phase 1; (b) is Phase 7, the second-to-last thing in a plan that admits it may run
out of budget and stop "after Phase 4". As written, the most likely failure mode is an elegant
folder layout and an unfilled `REVIEW.md` — i.e. not eligible.
*Fix:* move Phase 7's `REVIEW.md` pass to immediately after Phase 1 (it needs no scripts; the
`check-review.sh` gate can verify it later), and treat Phases 3–6 as the optional tail. The
showcase also wants a *bug story* and *one artifact*, both of which live in `REVIEW.md`.

**S2. `check-paths.sh` will fail on itself.** The script contains the literal patterns
(`skills/`, `todo/`, `done/`, `adrs/`, `making_a_plan`, `phase_execution`, `PROJECT.md`), and
Phase 4 runs `git grep` over tracked files excluding only `src/`, `workflow_reorg_plan.md` and
`workflow/plans/done/`. The script is tracked, so it matches itself and exits 1 forever.
*Fix:* exclude `workflow/scripts/check-paths.sh` via pathspec (`':!workflow/scripts/check-paths.sh'`),
or build the patterns from fragments. Also exclude `target/` and `REVIEW.md`'s historical prose if
it quotes old paths. Sanity-check the exclusion set by running it before writing the rest of Phase 4.

**S3. `check-review.sh` needs multi-line HTML-comment state, and the plan's own fixture list
doesn't cover it.** Today's `REVIEW.md` §3 holds a comment that spans ~8 lines *and contains a
markdown table* — a per-line "is this line a comment" test marks the table rows as real content and
reports §3 as filled, which is exactly the heading most likely to be left empty. D8's wording
("isn't inside `<!-- … -->`") is right; the Phase 4 fixtures are not.
*Fix:* make the script track an in-comment flag across lines, and add a fourth fixture: a heading
whose only content is a multi-line comment containing a table → exit 1.

**S4. `./mvnw package` as the per-phase gate costs more than it buys.** The plan changes no Java
(stated in Constraints) and still runs a Testcontainers build nine times. At a few minutes each
that is most of the 45-minute box.
*Fix:* run `./mvnw package` once in Phase 0 (to prove `main` is green) and once in Phase 8. Use the
per-phase "Check:" lines as the real gate, as the plan already suggests.

## Minor

- **M1. `adrs/` is tracked, not untracked.** Phase 1 says "`adrs/` is empty and untracked by git".
  `git ls-files adrs` returns `adrs/.gitkeep`. The step must be `git mv adrs/.gitkeep
  workflow/adrs/.gitkeep` (or `git rm adrs/.gitkeep` plus the new `README.md`), otherwise `rm -r adrs`
  leaves a deletion the plan never stages. Same care for `todo/.gitkeep` and `done/.gitkeep`, which
  are also tracked.
- **M2. Ordering nit in Phase 1 step 3.** `skills/PROJECT.md` opens with "overrides and additions to
  the team workflow described in `AGENTS.md`". That sentence can only be made correct in Phase 2,
  once `team-workflow.md` exists. Either do the rename in Phase 1 and the pointer in Phase 2, or
  state the interim wording.
- **M3. `git mv todo workflow/plans/todo` carries an untracked file.** The plan itself is untracked
  until Phase 0 step 3 commits it, and `git mv` on a directory renames it on disk. That works, but
  it only works because Phase 0 commits the plan first — make that dependency explicit, since a
  restart mid-Phase-0 would leave the file in the old place.
- **M4. `harness.sh` and `curl -fsS`.** `-f` makes curl exit 22 and swallow the body on `429`, so
  there is no `Retry-After` to read. Use `-sS -w '%{http_code}'` (or `--retry`/`--retry-after`) and
  check the code yourself. Keep the "never `set -x`" rule — good catch in the plan.
- **M5. The `check-review.sh` gate is never green.** Phase 7 deliberately leaves `TODO(human)`
  cells, and Phase 8 (correctly) doesn't run the script. Say so out loud in Phase 8 step 4: the
  script's purpose is the 16:45 check by a person, not a build gate.
- **M6. Time box.** Five skills, two agents, four scripts, two templates, a checklist, two READMEs,
  an ADR and a full `REVIEW.md` in 45 minutes is optimistic by a factor of two or three. With S1
  applied, the stop-early point ("after Phase 4") becomes safe; without it, it isn't.
- **M7. D3 removes the opening ritual** ("Read AGENTS.md and follow instructions") for Claude Code
  only. `README.md`'s workflow section documents that ritual for everyone, and B1 means other agents
  still need it. Keep `AGENTS.md` as the entry point for them — it stays at the root by team
  decision — and document the ritual in `workflow/README.md`.
- **M8. Phase 6 `allow` list.** `Bash(workflow/scripts/*)` is broader than it looks — it permits any
  future script in that folder. Fine for today; worth a comment in `settings.json`'s commit message.

## What's good (keep)

- The "Context (read this after a restart)" block, the old → new table and the explicit
  `git branch --show-current` check — this plan genuinely survives a cleared context.
- Every phase ends in a falsifiable "Check:" line and one conventional commit.
- D6 (agent never invents claim status; a person claims, claims are permanent) matches `AGENTS.md`
  exactly, and D8 gives the REVIEW gate a testable definition.
- Phase 4 ships a test for its own script (`test-check-review.sh`). That is the right instinct and
  is the one phase whose deliverable is self-verifying.
- The `plan-reviewer` brief naming the ratings fixture bug (5 talks per speaker against a limit of
  3) is a concrete, reusable rubric and is also the showcase's bug story.

## Suggested order after the fixes

1. Phase 0 minus the `ratings-verify` block (branch + one `./mvnw package`).
2. Phase 1a: `skills/` → `.claude/rules/`, `adrs/` → `workflow/adrs/` (M1).
3. Phase 7's `REVIEW.md` pass (S1) — eligibility secured here.
4. Phase 2 with B1's fix.
5. Phases 3–6 as budget allows.
6. Phase 1b (`todo/`, `done/`) after `ratings-verify` is merged, then Phase 8.
