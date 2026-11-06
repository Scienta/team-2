---
name: spec-from-request
description: Turn a harness feature request into our spec with a decisions table, for a person to approve (and ask the product owner) before planning. Use when a new drip feature is released.
---

# Spec from a feature request

1. `workflow/scripts/harness.sh spec <slug>` (a `404` means it isn't released yet).
2. Write `workflow/specs/<slug>.md` from `workflow/templates/spec.md`: context, rules and API
   verbatim from the request, nothing reworded.
3. Fill the **Decisions** table: for every endpoint, the unknown ids, invalid input, duplicates,
   removing what isn't there, limits, ordering, concurrency. Propose an answer for each, consistent
   with the base specs in `workflow/specs/` and the existing code. Mark "Ask the PO? yes" for
   anything a harness check can observe.
4. Stop. List the "ask the PO" questions for the person on the team, as short questions they can
   paste. Don't start `/make-plan` until a person has approved the table.
