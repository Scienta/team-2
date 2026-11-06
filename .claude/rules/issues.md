# Issues

We're four people working at once, so GitHub issues are how we see who's doing what. One issue per
task (a feature, a fix, a chore), assigned to the person who's on it.

1. **Before starting**, check what's ongoing so you don't duplicate someone else's work:
   `gh issue list` (open issues and who's assigned). If the task already has an issue assigned to
   someone else, stop and tell your human.
2. **Starting a task**: reuse its open issue if there is one, else create it, and assign it to your
   human: `gh issue create --title "<what>" --body "<why / link to spec or plan>" --assignee @me`.
   Name the branch `<issue-number>-<short-name>` and put `Closes #<n>` in the PR description so
   merging closes the issue.
3. **While working**, comment on the issue when something others should know happens (plan written,
   blocked, scope changed): `gh issue comment <n> --body "..."`.
4. **When done** (merged, with evidence), make sure it's closed: `gh issue close <n> --comment "<evidence>"`.
   If you drop a task, unassign yourself and comment why: `gh issue edit <n> --remove-assignee @me`.

**An issue for a harness feature carries the label `spec:<slug>`** (the slug from the harness
`/roadmap`): `/spec-watch` uses it to see that the feature already has an issue
(`workflow/scripts/new-specs.sh`). Issues labelled `pipeline` belong to the spec pipeline; leave
their `pipeline:*` labels to it.

Keep issues short: a title and a line or two. The plan in `workflow/plans/` holds the detail.
