---
name: pre-claim
description: Check a feature against workflow/checklists/pre-claim.md before a person claims it on the harness. Use when someone asks whether a feature is ready to claim.
---

# Pre-claim check

Walk `workflow/checklists/pre-claim.md` for `<slug>`. For each item, run what can be run (build,
health, `examples/run.sh` against our URL, `workflow/scripts/harness.sh feedback`, the
`spec-checker` agent) and read what can't (decisions table, `REVIEW.md` §3).

Report one line per item: `pass` / `fail` / `unknown`, with the evidence (command and the relevant
output, or file:line). End with **claim: yes** only if every item passes, else **claim: no** and
what's missing.

You can't claim. A person claims on the harness, and a claim is permanent: never say a feature is
done or claimable without the evidence above.
