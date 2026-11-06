# Plan: Reorganise the team workflow into `.claude/` and `workflow/`

Must-have (secures eligibility, about 30 min):
- [x] Phase 0: Branch `workflow-reorg` from `main`, commit this plan and its review, one baseline `./mvnw package` (green; `ratings-verify` not merged yet, 8 commits ahead; build env in `LOCAL.md`)
- [x] Phase 1: Move `skills/PROJECT.md` and `adrs/` (not the plan folders), fix references outside `todo/` and `done/`
- [ ] Phase 2: First full pass of `REVIEW.md` (every heading has text)
- [ ] Phase 3: Split `AGENTS.md`: team half to `.claude/rules/team-workflow.md`, imperative pointer and non-negotiables stay

Optional tail (as budget allows, in this order):
- [ ] Phase 4: Turn the planning skills into Claude Code skills (`.claude/skills/*/SKILL.md`)
- [ ] Phase 5: Gate scripts in `workflow/scripts/` (REVIEW check, stale-path check, harness client), with a test
- [ ] Phase 6: Process artifacts: spec and plan templates, pre-claim checklist, skills `spec-from-request` and `pre-claim`
- [ ] Phase 7: Review agents (`plan-reviewer`, `spec-checker`), `.claude/settings.json`, `workflow/README.md`

Last (after `ratings-verify` is merged):
- [ ] Phase 8: Move `todo/` and `done/` to `workflow/plans/`, update `REVIEW.md` §1 links, final gate, move this plan to done, ask about pushing

Revised after the review in `todo/workflow_reorg_plan_review.md`: B1, B2, S1–S4 and M1–M8 are all
addressed (see "Review response" at the end).

## Context (read this after a restart)

**Where this plan lives.** `todo/workflow_reorg_plan.md`, next to its review
`todo/workflow_reorg_plan_review.md`. Both are committed in Phase 0 and stay in `todo/` until
Phase 8 moves the folder to `workflow/plans/todo/`. After a restart: `git branch --show-current`
must be `workflow-reorg`; if the plan isn't in `todo/`, look in `workflow/plans/todo/`. Once Phase 3
is done, the team rules are in `.claude/rules/`, and `AGENTS.md` points there.

**Why.** At 17:00 a team can win only if (a) something in `.claude/` or `workflow/` was added or
changed and (b) every heading in `REVIEW.md` has the team's own text under it (HTML comments don't
count). The showcase (4 min, screen-shared repo, no slides) asks for: the workflow in one minute,
pointing at files in `workflow/` and `.claude/`; one bug story; one reusable artifact opened on
screen. Today the team's workflow lives in `skills/`, `todo/`, `done/`, `adrs/` and the bottom half
of `AGENTS.md`, none of which counts or can be shown. Edits to `README.md`, `AGENTS.md` and
`CLAUDE.md` don't count. **The most likely way to lose is a nice layout and an unfilled
`REVIEW.md`**, so `REVIEW.md` comes right after the first move (Phase 2).

**Constraints.**
- No Java code changes. `./mvnw package` runs **twice only**: Phase 0 (baseline) and Phase 8
  (final). Each phase's "Check:" line is its gate.
- Use `git mv` / `git rm` so history follows the files. All of these are tracked:
  `adrs/.gitkeep`, `todo/.gitkeep`, `done/.gitkeep`, `skills/*.md`, `todo/ratings_plan*.md`,
  `done/co_speakers_plan.md`.
- Keep `CLAUDE.md` as it is (`@AGENTS.md`). Don't create `.claude/CLAUDE.md`.
- `AGENTS.md` stays at the repo root as the entry point for every agent (team decision). Copilot
  CLI reads `CLAUDE.md` → `@AGENTS.md` and nothing else, so `AGENTS.md` must *instruct* agents to
  read `.claude/rules/`, not just mention it.
- Don't touch `.fagdag/`, the deploy workflow or anything under "Hands off" in `AGENTS.md`.
  Never print or commit `.fagdag/harness-token`.
- **Don't edit anything in `todo/` or `done/` before Phase 8** (except this plan and its review):
  `origin/ratings-verify` (6 commits, unmerged at planning time) changes `todo/ratings_plan.md`
  and moves it to `done/`, so any edit there conflicts.
- Historical plans (`done/` → `workflow/plans/done/`) are left as written, old paths included.
  The stale-path check excludes them.
- Time: Phases 0–3 about 30 min; the tail is optional and can stop at any phase boundary.

**Claude Code file formats.**
- `.claude/rules/*.md`: plain markdown, loaded at start-up next to the root `CLAUDE.md`.
- `.claude/skills/<name>/SKILL.md`: frontmatter `name: <name>` and `description: <when to use>`,
  then the instructions. Invoked as `/<name>`.
- `.claude/agents/<name>.md`: frontmatter `name`, `description`, `tools` (comma-separated, e.g.
  `Read, Grep, Glob, Bash`), optionally `model`, then the system prompt.
- `.claude/settings.json`: `{"permissions": {"allow": [...], "deny": [...]}}`, committed.

**Old → new paths.**

| Old | New | Phase |
|---|---|---|
| `skills/PROJECT.md` | `.claude/rules/project.md` | 1 |
| `adrs/.gitkeep` | `workflow/adrs/.gitkeep` (plus a one-line `workflow/adrs/README.md`) | 1 |
| `AGENTS.md` from `# Team workflow (skills)` (line 77) to the end | `.claude/rules/team-workflow.md` | 3 |
| `skills/making_a_plan.md` | `.claude/skills/make-plan/SKILL.md` | 4 |
| `skills/making_a_master_plan.md` | `.claude/skills/make-master-plan/SKILL.md` | 4 |
| `skills/phase_execution.md` | `.claude/skills/execute-plan/SKILL.md` | 4 |
| `README.md` section `# Agentic workflow` to the end | `workflow/README.md` | 7 |
| `todo/` | `workflow/plans/todo/` | 8 |
| `done/` | `workflow/plans/done/` | 8 |

**References to old paths at planning time** (`grep -rn -E "skills/|todo/|done/|making_a_plan|phase_execution|PROJECT\.md|adrs"` excluding `src/`, `target/`, `.git/`):
- `README.md` lines 152–231 (the "Agentic workflow" section; replaced by a link in Phase 7)
- `AGENTS.md` lines 92–95, 110, 172–173, 194, 209 (all in the team half that moves in Phase 3;
  fixed as part of the move)
- `skills/phase_execution.md:41`, `skills/making_a_plan.md:6`, `skills/making_a_master_plan.md:6`
  (fixed in Phase 4)
- `todo/ratings_plan*.md`, `done/*`: historical, not edited (see Constraints)

## Decisions for the human to check

- **D1.** `adrs/` moves to `workflow/adrs/`.
- **D2.** The team half of `AGENTS.md` moves to `.claude/rules/team-workflow.md`. `AGENTS.md` keeps,
  where that half was, an imperative pointer plus a short "Non-negotiables" list so a cold agent
  can't miss the essentials (text in Phase 3). `team-workflow.md` opens with the same instruction.
- **D3.** The opening ritual "Read AGENTS.md and follow instructions" **stays** as the entry point
  for every agent, and is documented in `workflow/README.md`. Claude Code additionally loads
  `.claude/rules/` automatically and gets the skills as `/make-plan`, `/execute-plan` etc.
- **D4.** Skill names: `make-plan`, `make-master-plan`, `execute-plan`, `spec-from-request`,
  `pre-claim`. Agent names: `plan-reviewer`, `spec-checker`.
- **D5.** A plan's review sits next to it as `<feature>_plan_review.md`.
- **D6.** `REVIEW.md`: the agent writes only what the repo proves. Claimed/skipped and the tick come
  from a person: the agent writes `TODO(human)` in those cells. No `TODO` may remain at 17:00.
- **D7.** `tmp/` stays at the root (gitignored scratch).
- **D8.** A `REVIEW.md` heading is filled when there's at least one non-blank line under it that
  is outside any HTML comment (comments can span lines and contain tables) and doesn't contain
  `TODO`.
- **D9.** `check-review.sh` is **not a build gate**: it's red while `TODO(human)` cells remain,
  which is expected. It's for a person to run at about 16:45.

## Phase 0: Branch and baseline

1. `git switch main && git pull && git switch -c workflow-reorg` (the untracked plan and review in
   `todo/` come along).
2. `git add todo/workflow_reorg_plan.md todo/workflow_reorg_plan_review.md`, tick this box, commit
   `docs: Plan the workflow reorganisation, with review`. Committing first matters: Phase 8's
   `git mv todo …` relies on the plan being tracked.
3. `docker info`, then `./mvnw package` once to prove the base is green. If it's red on `main`,
   stop and tell the human (it isn't this plan's breakage).
4. `git log --oneline origin/main..origin/ratings-verify`: note in the plan's checklist line whether
   it's merged yet. **Doesn't block** Phases 1–7.

Check: on `workflow-reorg`, plan and review committed, baseline green.

## Phase 1: Move project rules and ADRs

1. `mkdir -p .claude/rules workflow/adrs`.
2. `git mv skills/PROJECT.md .claude/rules/project.md`. Its first line says it holds "overrides
   and additions to the team workflow described in `AGENTS.md`". Interim wording until Phase 3:
   "…described in `AGENTS.md` (moving to `.claude/rules/team-workflow.md`)"; Phase 3 makes it
   "…in `.claude/rules/team-workflow.md`".
3. `git mv adrs/.gitkeep workflow/adrs/.gitkeep`; add `workflow/adrs/README.md`: "Architecture
   decision records, one file per decision: `NNNN-title.md`." Remove the empty `adrs/` dir.
4. Fix the `skills/PROJECT.md` references in `AGENTS.md` (lines 92, 110, 194, 209) to
   `.claude/rules/project.md`. Don't touch `todo/` or `done/`.

Check: `git status` shows renames (`R`), not delete + add; `ls adrs skills/PROJECT.md` fails;
`grep -rn "skills/PROJECT" --exclude-dir={target,.git,src,todo,done} .` is empty. Commit:
`chore: Move project rules to .claude/rules/ and ADRs to workflow/`.

## Phase 2: `REVIEW.md`, first full pass

Replace each heading's comment prompt with text (delete the comment; keep the headings). Link only
to files that exist now; Phase 8 updates the links once everything has moved.

- §1 Workflow: the steps we actually run: spec fetched from the harness into `workflow/specs/`,
  plan with `making_a_plan` (becoming `/make-plan`), a separate review of the plan (another agent,
  e.g. `todo/ratings_plan_review.md`, `todo/workflow_reorg_plan_review.md`), human approves the
  decisions, `phase_execution` (becoming `/execute-plan`), PR in the GitHub UI, merge deploys,
  evidence, a person claims. Links: `workflow/specs/`, `.claude/rules/project.md`, `workflow/adrs/`.
- §2 How we reviewed agent output: plan reviews by a second agent (incl. a different tool:
  Copilot CLI reviewed this plan), human approval of decisions (E1 in the ratings plan),
  `./mvnw package` with Testcontainers per phase, Hurl examples, harness feedback.
  **Bug story:** the review of the ratings plan caught that its ranking test fixture created five
  talks for one speaker while the spec allows three, so the test would have failed on a 409 before
  testing anything. The review of this plan caught that a per-line comment check would have
  reported an empty §3 as filled.
- §3 Claim log: the table with rows for speakers, talks, ratings, co-speakers. Fill the evidence
  column from the repo (tests, Hurl files, plans); `TODO(human)` for "claimed or skipped" and
  "tick" (D6).
- §4 What the harness caught: one `curl` to the feedback endpoint (AGENTS.md "Harness API"; mind
  the rate limit, don't print the token); summarise any FAULTY checks, else `TODO(human)`.
- §5 Reusable artifacts: the plan/execute skills, the plan-review step, `workflow/specs/`. (Phase 8
  adds the paths that exist by then.)
- §6 One thing we'd do differently: `TODO(human)`.

Check: every `##` heading has a non-comment line under it (by eye, until the Phase 5 script
exists); the only `TODO(human)` are in §3 cells, §4 (if no feedback) and §6. Commit:
`docs: First pass of REVIEW.md`.

## Phase 3: Split `AGENTS.md`

1. Cut from `# Team workflow (skills)` to the end of `AGENTS.md` into
   `.claude/rules/team-workflow.md` (plain edit; `git mv` can't move half a file). Within it:
   update the "Reading List" to `.claude/rules/*.md` and (until Phase 4) `skills/*.md`; point code
   conventions at `.claude/rules/project.md`; add the rule "After a feature is merged or claimed,
   append to `REVIEW.md` §3 (claim log), and §4 if the harness found something we missed."
2. Top of `.claude/rules/team-workflow.md`, before anything else:

   > **Every agent must read all of `.claude/rules/*.md` before doing anything else.** Claude Code
   > loads them automatically; other agents read them explicitly. Skills are in `.claude/skills/`,
   > plans in `todo/` (moving to `workflow/plans/`), the process in `workflow/README.md`.

3. In `AGENTS.md`, where the team half was:

   ```markdown
   # Team workflow

   **Every agent must now read all of `.claude/rules/*.md` before doing anything else.** Claude Code
   loads them automatically; other agents (Copilot CLI, Codex, …) read them explicitly. Skills are
   in `.claude/skills/`, plans in `todo/` (moving to `workflow/plans/`), the process in
   `workflow/README.md`.

   Non-negotiables, in case you read nothing else:
   - Plan first (`.claude/rules/team-workflow.md`, "Planning and Execution"), then execute phase by phase.
   - No broken windows: `./mvnw package` green, no warnings, before you finish.
   - All changes via a feature branch and a PR made by a person in the GitHub UI. Don't use `gh`.
   - Merging to `main` deploys. Never claim a feature or say it's done without checkable evidence.
   ```

4. Fix `.claude/rules/project.md`'s first line (Phase 1 step 2).

Check: `AGENTS.md` has the organisers' facts plus the block above and nothing else of the team's;
`CLAUDE.md` unchanged. **Human check:** ask a non-Claude agent (Copilot CLI) in a fresh session
"read AGENTS.md; what's our git workflow and how do we plan?". If it can't answer from
`.claude/rules/`, strengthen the pointer. Commit:
`chore: Move the team workflow from AGENTS.md to .claude/rules/`.

## Phase 4: Skills (optional tail starts here)

For `making_a_plan.md` → `make-plan`, `making_a_master_plan.md` → `make-master-plan`,
`phase_execution.md` → `execute-plan`:
1. `mkdir .claude/skills/<name>` and `git mv skills/<old>.md .claude/skills/<name>/SKILL.md`.
2. Frontmatter descriptions:
   - `make-plan`: "Write a phased, restart-proof plan for a feature or chore into todo/ (workflow/plans/todo/ after the reorg). Use before implementing any harness feature."
   - `make-master-plan`: "Plan a task too large for one plan: architecture-level steps, ambiguities resolved with the human."
   - `execute-plan`: "Execute the next unchecked phases of a plan, test-gated, one commit per phase."
3. Paths inside: ADRs in `workflow/adrs/`, `make-master-plan` refers to the `make-plan` skill. In
   `make-plan`, add after writing the plan: "Have a second agent review it (the `plan-reviewer`
   agent once it exists, or another tool), write the findings to `<feature>_plan_review.md`, revise."
   In `execute-plan`, add step 0: "`docker info`; if Docker isn't running, stop and tell the human."
4. `skills/` is now empty: remove the dir. In `.claude/rules/team-workflow.md`, replace the
   `skills/*.md` mentions with the skill names and their paths (so non-Claude agents can still read
   them as files).

Check: `git grep -nE "skills/(making|phase|PROJECT)" -- ':!todo' ':!done'` is empty; each
`SKILL.md` starts with frontmatter (`head -4`). Commit:
`chore: Turn the planning skills into Claude Code skills`.

## Phase 5: Gate scripts

All in `workflow/scripts/`, `bash`, `set -euo pipefail`, `chmod +x`. Never `set -x`.

1. `check-review.sh [file]` (default `REVIEW.md`): walks the file line by line keeping an
   **in-comment flag across lines** (`<!--` sets it, `-->` clears it; text on the same line outside
   the markers counts). For each `## ` heading, it's filled per D8. Prints each empty heading and
   exits 1, else prints `REVIEW.md: all headings filled` and exits 0.
2. `test-check-review.sh`: fixtures in a `mktemp -d` dir, asserting exit codes:
   all filled → 0; heading with only a one-line comment → 1; heading with `TODO(human)` → 1;
   **heading whose only content is a multi-line comment containing a markdown table → 1**.
3. `check-paths.sh`: `git grep -nE` for old paths, with pathspecs excluding `src/`, `target/`,
   `todo/`, `done/`, `workflow/plans/`, `workflow/scripts/check-paths.sh` (it contains the patterns)
   and `todo/workflow_reorg_plan*.md` / `workflow/plans/*/workflow_reorg_plan*.md`. Patterns:
   `(^|[^/[:alnum:]_.])(skills|todo|done|adrs)/` (doesn't match `.claude/skills/` or
   `workflow/plans/todo/`), plus `making_a_plan`, `making_a_master_plan`, `phase_execution`,
   `skills/PROJECT\.md`. Before Phase 8, `todo/` and `done/` references are expected: the script
   takes `--before-plans-move` to skip `todo|done`. **Run it once right after writing the pattern**,
   before anything else, to check the exclusions (it must not match itself).
4. `harness.sh spec <feature> | feedback | timeline <feature>/<check>`: wraps the three calls in
   `AGENTS.md` "Harness API"; reads URL, team and token from `.fagdag/`. Uses
   `curl -sS -D <headerfile> -o <bodyfile> -w '%{http_code}'` (**not** `-f`, which hides the
   `429`); on `429` sleeps for `Retry-After` and retries once; on other non-2xx prints code and body
   to stderr and exits 1.

Check: `test-check-review.sh` passes; `check-paths.sh --before-plans-move` exits 0;
`check-review.sh` reports exactly the headings holding `TODO(human)` (expected, D9);
`harness.sh feedback` returns JSON (one call). Commit:
`feat: Add workflow gate scripts (REVIEW headings, stale paths, harness client)`.

## Phase 6: Process artifacts

1. `workflow/templates/spec.md`: the shape of `workflow/specs/co-speakers.md` (context, rules, API)
   plus a **Decisions** table (ID, question, our answer, who approved) for what the feature request
   leaves open (status codes, edge cases, error handling).
2. `workflow/templates/plan.md`: checklist at the top, "Context (read this after a restart)",
   "Decisions for the human to check", one section per phase ending in a "Check:" line.
3. `workflow/checklists/pre-claim.md`: spec decisions approved; `./mvnw package` green on `main`;
   deploy workflow green and `GET $TEAM_URL/health` shows the merged SHA as `version`;
   `examples/run.sh $TEAM_URL` green, including the feature's own Hurl file; `harness.sh feedback`
   shows no FAULTY check for the feature (or each one explained); claim-log row drafted in
   `REVIEW.md` §3.
4. `.claude/skills/spec-from-request/SKILL.md`: `harness.sh spec <f>` → `workflow/specs/<f>.md`
   using the template; propose answers in the Decisions table; stop for the human before
   `/make-plan`.
5. `.claude/skills/pre-claim/SKILL.md`: walk the checklist, run what can be run, report each item
   pass/fail with evidence, end with "claim: yes/no", and remind that a person claims and a claim
   is permanent.

Check: `check-paths.sh --before-plans-move` exits 0; both skills have frontmatter. Commit:
`docs: Add spec and plan templates, the pre-claim checklist and skills`.

## Phase 7: Review agents, settings, `workflow/README.md`

1. `.claude/agents/plan-reviewer.md` (tools `Read, Grep, Glob`): reviews a plan against
   `workflow/specs/`, the code and the team rules. Rubric: fixtures must be creatable through the
   API (example: the ratings plan wanted 5 talks per speaker against a limit of 3); every phase has
   a falsifiable check; it survives a restart; the plan doesn't block on things it could decouple;
   the priority order matches the deadline. Output: Blocking / Significant / Minor / What's good,
   with file:line, like `todo/workflow_reorg_plan_review.md`. Read-only.
2. `.claude/agents/spec-checker.md` (tools `Read, Grep, Glob, Bash`): maps every rule and decision
   in `workflow/specs/<f>.md` to a test or Hurl assertion; lists uncovered rules. Used by
   `pre-claim`.
3. `.claude/settings.json`: allow `Bash(./mvnw:*)`, `Bash(examples/run.sh:*)`,
   `Bash(workflow/scripts/*)`, `Bash(docker info:*)`, `Bash(git status:*)`, `Bash(git diff:*)`,
   `Bash(git log:*)`; deny `Read(.fagdag/harness-token)`. JSON has no comments, so the commit
   message notes that `workflow/scripts/*` allows any future script in that folder.
4. `workflow/README.md`: rewrite as the one-minute workflow. Entry point for every agent:
   "Read AGENTS.md and follow instructions" (D3). Then:
   `feature request → /spec-from-request → human approves decisions → /make-plan → plan review by a
   second agent → /execute-plan → PR in GitHub UI → merge deploys → /pre-claim → a person claims →
   REVIEW.md`, one line per artifact with its path. Bring over what's still true from the
   `README.md` "Agentic workflow" section and replace that section with a one-line link.

Check: frontmatter in both agents; `python3 -m json.tool .claude/settings.json` succeeds. Commit:
`feat: Add review agents, shared settings and the workflow README` (body: the
`workflow/scripts/*` note).

## Phase 8: Plan folders, final gate, finish

Only once `git log --oneline origin/main..origin/ratings-verify` is empty (merged). If it isn't,
stop here and tell the human; everything else is done.

1. `git fetch && git merge origin/main` (brings in `ratings-verify`; resolve conflicts if any).
2. `mkdir -p workflow/plans && git mv todo workflow/plans/todo && git mv done workflow/plans/done`.
   Check `git status`: renames only, `.gitkeep`s included.
3. Update references to `todo/` and `done/` outside the historical plans: `.claude/rules/*.md`,
   `.claude/skills/*/SKILL.md`, `.claude/agents/*.md`, `workflow/README.md`, `REVIEW.md`, `AGENTS.md`
   block. Then `check-paths.sh` (without the flag) must exit 0 (if Phase 5 was skipped, use the grep
   from "Context").
4. `REVIEW.md`: refresh §1 and §5 links to everything that now exists.
5. ADR `workflow/adrs/0001-workflow-lives-in-claude-and-workflow.md`: context (the eligibility
   rule, non-Claude agents), decision (this layout; `AGENTS.md` stays the entry point with an
   imperative pointer), consequences.
6. Final gate: `./mvnw package`, `test-check-review.sh`, `check-paths.sh`. `check-review.sh` is
   expected red while `TODO(human)` remains (D9).
7. `git mv workflow/plans/todo/workflow_reorg_plan*.md workflow/plans/done/`, tick, commit.
8. Tell the human: the `TODO(human)` items left in `REVIEW.md` and that `check-review.sh` is the
   ~16:45 check; teammates must rebase (paths moved); ask whether to push. A person makes the PR.

## Review response

| Finding | Response |
|---|---|
| B1 pointer must instruct | Imperative pointer + non-negotiables in `AGENTS.md`, same instruction atop `team-workflow.md`, human check with Copilot CLI (Phase 3, D2) |
| B2 blocked on `ratings-verify` | Plan-folder moves split out to Phase 8, run last; nothing in `todo/`/`done/` edited before then |
| S1 priority inverted | `REVIEW.md` is Phase 2; Phases 4–7 are an explicit optional tail |
| S2 `check-paths.sh` matches itself | Excluded by pathspec; run once right after writing the pattern (Phase 5) |
| S3 multi-line comments | In-comment flag across lines; fourth fixture with a commented table (Phase 5, D8) |
| S4 `./mvnw package` nine times | Twice: Phase 0 baseline and Phase 8 final |
| M1 `.gitkeep`s are tracked | `git mv` of `.gitkeep`s; listed in Constraints |
| M2 `PROJECT.md` first line | Interim wording in Phase 1, final in Phase 3 |
| M3 untracked plan and `git mv` | Phase 0 commits plan and review first, stated as a dependency |
| M4 `curl -f` hides `429` | `-sS -D -o -w '%{http_code}'`, no `-f` |
| M5 REVIEW gate never green | D9: not a build gate, a 16:45 human check; said in Phase 8 |
| M6 time box | Must-have Phases 0–3 (~30 min), rest optional, stop at any boundary |
| M7 opening ritual | Kept for every agent (D3), documented in `workflow/README.md` |
| M8 broad allow rule | Noted in the Phase 7 commit message |
