---
name: spec-watch
description: One tick of the spec pipeline - find released harness specs without an issue, open a pipeline issue for each, and start /implement-issue on the next queued one. Run on a schedule with `/loop 10m /spec-watch`.
---

# One tick of the spec pipeline

Runs in the pipeline session (`../team-2-wt/pipeline` on `main`; see
`workflow/plans/done/spec_pipeline_plan.md`, D3). Never in CI. Keep the tick cheap: shell scripts
first, agents only when there's work.

1. `git pull --ff-only` (the skills and rules you follow come from `main`).
2. **New specs → issues.** `workflow/scripts/new-specs.sh` prints the released, unclaimed features
   without a `spec:<slug>` issue. For each slug:
   - Title: the spec's first heading, from `workflow/scripts/harness.sh spec <slug> | head -1`
     without the `# `.
   - **Safety net:** `gh issue list --state open --search "<title> in:title"`. If an open issue
     matches, don't create one: `gh label create spec:<slug> --force`, add the label to that issue,
     comment "Labelled spec:<slug> by /spec-watch", and leave it to its owner (no `pipeline` label).
   - Else `gh label create spec:<slug> --force`, then
     `gh issue create --title "<title>" --label pipeline --label spec:<slug> --assignee danielbakkelund --body "Harness spec \`<slug>\`, picked up by /spec-watch. The spec pipeline implements it as a draft PR (/implement-issue); decisions are agent defaults until a person approves them on the PR."`
3. **Start the next one.** `workflow/scripts/pipeline-queue.sh`:
   - Any `blocked` line: start nothing; a person has to look (D8).
   - Any `running` line: one is in progress (D6). If this session isn't orchestrating it (no live
     implementer after a restart), resume it with `/implement-issue <n>`, which picks up from the
     issue's comments and the plan file.
   - Else the first `queued` line: `gh issue edit <n> --add-label pipeline:running`, **then** run
     `/implement-issue <n>`.
4. Report in at most three lines: issues created or labelled, what's running, what's blocked. If
   nothing changed, say "no change".
