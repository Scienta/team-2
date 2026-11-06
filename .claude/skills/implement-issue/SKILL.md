---
name: implement-issue
description: Take a spec-pipeline issue from harness spec to a draft PR - implementer agent writes spec and plan, plan-reviewer reviews, implementer executes and opens the draft PR. Run by the main session, usually from /spec-watch.
---

# From a pipeline issue to a draft PR

You are the orchestrator, in the main session: subagents can't start subagents, so the review step
is yours to start. The issue `<n>` has labels `pipeline`, `spec:<slug>` and `pipeline:running`.
Run the agents in the background and carry on when each one notifies you, so `/loop` ticks keep
firing meanwhile.

**Resuming:** read the issue's comments (`gh issue view <n> --comments`) and, if it exists, the plan
in the feature worktree: start at the first step that has no comment yet.

1. **Spec and plan.** Start an `issue-implementer` agent in the background: "Issue <n>, slug <slug>:
   part A (spec and plan)." It replies with the worktree path, the plan path and the decisions it
   took. Comment on the issue: "Plan written: <path>, decisions: <list>".
2. **Review.** Start a `plan-reviewer` agent on `<worktree>/workflow/plans/todo/<slug>_plan.md` with
   the worktree as its root. Comment "Plan reviewed".
3. **Execute.** Send the review to the same implementer (SendMessage): "Part B: here's the review;
   write it to `<slug>_plan_review.md`, answer it, then execute." If it can't be resumed, start a
   fresh `issue-implementer` with "Issue <n>, slug <slug>: part B; plan <path>; review: <text>".
   It replies with the draft PR URL and the evidence.
4. **Finish.** Check `gh pr view <url> --json isDraft,body`: `isDraft` must be `true` and the body
   must start with the decisions. Then
   `gh issue edit <n> --remove-label pipeline:running --add-label pipeline:pr-open` and comment the
   PR URL.

**On any failure** (an agent reports it's stuck, a red build it can't fix, no PR):
`gh issue edit <n> --add-label blocked`, comment what failed and where (worktree, last commit), and
stop. Don't open a PR. `/spec-watch` starts nothing new until a person removes `blocked`.

Never merge, never `gh pr ready`, never push to `main`, never claim a feature.
