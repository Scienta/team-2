---
name: list-risks
description: List what a change could break on the harness, and what to watch after deploy, in the PR description. Use before pushing any change that alters HTTP behaviour or touches a claimed feature.
---

# List the risks of a change

A green build only proves our own decisions. The harness checks the organisers' expectations, and
where the spec is silent we guessed (co-speakers: two FAULTY checks on status codes we chose). A
risk list tells the person merging and claiming exactly what to watch, so a wrong guess costs one
tick, not ten.

## When

Before the push and PR of every change that alters HTTP behaviour, or touches code a claimed
feature uses. Pure docs or workflow changes need none; say "No behaviour change".

## Find the risks

Go through the diff (`git diff origin/main...HEAD -- src/`) and the spec's decisions table, and list:

1. **Guesses the harness can see.** Every status code, idempotency rule or edge case the spec
   doesn't dictate (decisions marked "Open risk" or "Ask the PO"). For each: what we chose, what
   the harness might expect instead, and the fallback if it fails. Use precedents from features the
   harness already passes (e.g. `POST /talks` answers 400 for an unknown `speakerId`).
2. **Changes to claimed features.** Any response that changes for a request a claimed feature's
   checks may send (e.g. ratings E1: 400 → 404). These bleed points from the first tick.
3. **Shared code.** Code paths other features use: `getTalk`, list queries, `TalkDto` fields,
   `schema.sql`, locking. Name the features whose checks must stay OK.
4. **Concurrency.** New read-check-write paths: what is locked, and which test proves it (ideally
   one that fails with the lock removed).
5. **Platform.** Anything near the deploy contract: `/health`, port, `PG*` variables, pool of 5,
   Java or Spring versions, start-up time.
6. **Blind spots.** What we can't see: e.g. no request logging, so a FAULTY check only shows in its
   harness transcript.

Order by likelihood of failing, most likely first. Skip categories with nothing in them; don't pad.

## Write it in the PR description

```markdown
## Risks to check on the harness after deploy

- **D5, repeats → 409.** <what triggers it>. The harness may expect 200. Most likely to fail.
  Fallback: <the one-line change>.
- **Base checks.** Talks, ratings and co-speakers must stay OK (<why they could break>).
```

Keep each line to what triggers it, what we chose, the likely alternative and the fallback.

## After the merge

1. Wait for the deploy and `/health` showing the merged SHA, then at least 2 ticks.
2. `workflow/scripts/harness.sh feedback`: check every listed risk against its check, and every
   other feature for regressions.
3. Comment the outcome on the issue: each risk OK, or FAULTY with the transcript's request.
4. A risk that went FAULTY goes into `REVIEW.md` §4; one that held stays in the PR as evidence.
