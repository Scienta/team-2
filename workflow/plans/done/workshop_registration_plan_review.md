# Review: workshop registration plan

Reviewer: `plan-reviewer` agent (`.claude/agents/plan-reviewer.md`), read-only, against the spec, the
code and our rules. It ran nothing. Findings are short here; the responses are in the plan's "Review
response" table.

Verdict: the concurrency design, ordering and schema are sound, but the plan needs revising first.

## Blocking

- **B1.** The plan ends in a guaranteed `claim: no`. `workflow/checklists/pre-claim.md` item 1 requires
  the PO's answer on every "Ask the PO" row, and `AGENTS.md` says a person can ask the organisers' PO
  about business rules, while the plan says no PO is available. Same failure as `REVIEW.md` §4.

## Significant

- **S1.** The re-read of the status after the lock has no test that fails if it's removed. Reusing the
  talk from before the lock would pass every listed test and let a registration in after a withdrawal.
- **S2.** "Phase 3 blocked, do Phase 4 first" moves the plan to `done/` with a box unchecked, and needs
  a second PR and so a second rollout.
- **S3.** Nothing commits the plan or the approvals, and the final `git mv` fails on an untracked file.
- **S4.** The risky decisions are watched "after the claim", but the harness checks unclaimed features
  too, so they can be read before the permanent click.
- **S5.** The rush can't deadlock, but `/health` shares the pool of 5 and answers 503 after 5 s. Six
  round trips per request under the lock is a risk that local tests can't reveal.
- **S6.** Issue and branch setup is prose, not a checkbox, and `main` has moved.
- **S7.** The plan forbids the draft §3 row that pre-claim and `/execute-plan` require.
- **S8.** `Utils.isValidEmail` accepts `@example.com`, `@.`, `a b@c.d` and `a@b@c.d`, so D7's intent
  doesn't hold.

## Minor

- **M1.** The `DuplicateKeyException` backstop is left undecided. `ON CONFLICT DO NOTHING RETURNING id`
  removes the catch and one query.
- **M2.** Whitespace is handled two ways: `strip()` doesn't remove a no-break space.
- **M3.** A NUL character passes every check and gives a 500 from Postgres.
- **M4.** Non-string fields, `{}`, JSON `null`, and an unknown id with bad JSON aren't pinned.
- **M5.** Hurl's `{{newUuid}}` can't produce a duplicate, and the fixture needs three talks.
- **M6.** Test-style pointers are incomplete (`RatingsTest` has `JdbcTemplate` and content types).
- **M7.** Commands are missing for a restarted agent, and phase checks use only `-Dtest`.
- **M8.** A stale reference to `REVIEW.md` §4 being on another branch.
- **M9.** No cutoff for waiting on Acceptance.

## What's good

Decoupling into new classes; the lock and re-read recipe; one `@Transactional` entry point; a `Map`
body for 404 before 400; the schema; rush tests over real HTTP with exact counts and the production
pool size; a check and a commit per phase.
