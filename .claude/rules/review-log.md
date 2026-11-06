# REVIEW.md is part of the work

**Why.** At 17:00 a team can only win if every `##` heading in `REVIEW.md` has our own text
(HTML comments don't count) and something in `.claude/` or `workflow/` changed. In the 4-minute
showcase we screen-share `REVIEW.md` and show: our workflow (§1), one bug story (review caught it,
or the harness did and we didn't), and one reusable artifact. 17:00–17:05 is for tidying, so
`REVIEW.md` is written as we go, not at the end.

**When something happens, write it down in the same commit:**

| Event | Section |
|---|---|
| The workflow changed (new step, skill, agent, gate) | §1 Workflow, with links |
| A review (plan reviewer, second tool, test, Hurl, person) caught a bug before the harness did | §2, as a short story: what, who caught it, what would have happened |
| A feature is claimed or deliberately skipped | §3 claim log row: tick and the evidence held before claiming |
| A harness check went FAULTY on something we thought was done | §4: what, why our review missed it, what we changed |
| A new skill, agent, template, checklist or script | §5, with its path and one line on why it's reusable |
| Something we'd do differently | add a candidate to §6 (a person picks the one) |

**Rules.**
- Only facts with evidence: commit SHAs, ticks and statuses from `workflow/scripts/harness.sh
  feedback`, file paths. A claim's status and tick come from the harness, never a guess.
- Where only a person can know, write `TODO(human): <what's needed>`. `check-review.sh` counts a
  heading with only `TODO` lines as empty, so these can't be forgotten.
- After editing, run `workflow/scripts/check-review.sh` and report the headings it still lists.
