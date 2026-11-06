---
name: make-plan
description: Write a phased, restart-proof plan for a feature or chore into workflow/plans/todo/. Use before implementing any harness feature.
---


# How to make a plan

1. Get an overview of the tasks to be done. For a feature, fetch its spec or feature
   request from the harness (see "Harness API" in `AGENTS.md`).
2. Check with the ADRs in `workflow/adrs` to make sure you have an overview
   over important architectural decisions made so far.
3. Break down the work into clearly separated phases with
   clear deliverables that can be tested (if applicable)
4. Find the optimal order of implementation of the tasks,
   minimising the number of reads and writes (keeping the
   token consumption down)
5. Write the plan into `workflow/plans/todo/<slug>_plan.md`, starting from `workflow/templates/plan.md`.
6. At the top of the file, insert a todo-list with the phases
   or steps that you have defined, and format the list in terms
   of check-box items.

A feature request isn't a spec: if the plan has to decide status codes, edge cases or
error handling, list those decisions in the plan so the human can check them. Mark every
decision on behaviour the harness checks (status codes, idempotency, error cases) as
**"ask the product owner"**: a person asks before the claim. Our co-speakers claim failed two
checks on exactly such decisions (`REVIEW.md` §4).

## After making the plan

7. As a separate activity, go over the plan and make sure it can
   withstand a restart with a cleared context (in addition to AGENTS.md).
8. Have a second agent review the plan (the `plan-reviewer` agent, or another tool such as
   Copilot CLI). It writes its findings to `<feature>_plan_review.md` next to the plan; revise
   the plan and add a "Review response" table mapping each finding to its fix.
