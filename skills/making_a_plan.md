
# How to make a plan

1. Get an overview of the tasks to be done. For a feature, fetch its spec or feature
   request from the harness (see "Harness API" in `AGENTS.md`).
2. Check with the ADRs in `adrs` to make sure you have an overview
   over important architectural decisions made so far.
3. Go through the known issues in `docs/known_issues.md` to make sure there are no
   blockers that have to be fixed first.
4. Break down the work into clearly separated phases with
   clear deliverables that can be tested (if applicable)
5. Find the optimal order of implementation of the tasks,
   minimising the number of reads and writes (keeping the
   token consumption down)
6. Write the plan into a markdown file in the `todo` folder.
7. At the top of the file, insert a todo-list with the phases
   or steps that you have defined, and format the list in terms
   of check-box items.

A feature request isn't a spec: if the plan has to decide status codes, edge cases or
error handling, list those decisions in the plan so the human can check them.

## After making the plan

7. As a separate activity, go over the plan and make sure it can
   withstand a restart with a cleared context (in addition to AGENTS.md).
