
# How to make a plan

1. Get an overview of the tasks to be done
2. Check with the ADRs in `adrs` to make sure you have an overview
   over important architectural decisions  made so far.
2. Break down the work into clearly separated phases with 
   clear deliverables that can be tested (if applickable)
3. Find the optimal order of implementation of the tasks, 
   minimising the number of reads and writes (keeping the
   token consumption down)
3. Write the plan into a markdown file in the `todo` folder.
4. At the top of the file, insert a todo-list with the phases
   or steps that you have defined, and format the list in terms
   of check-box items.

## After making the plan

5. As a separate activity, go over the plan and make sure it can 
   withstand a restart with a cleared context (in addition to AGENTS.md).
