# Review: scheduling plan

Reviewer: `plan-reviewer` agent (Claude Code), 2026-11-06, on the first draft of
`scheduling_plan.md`. Verdict: executable once B1 and B2 are fixed; the lock design is sound.

## Blocking

- **B1. D8 before D5/D6 contradicts D13.** The plan answered 200 for "already in this place" before
  the not-accepted and workshop checks; D13 puts those first. Case: schedule, withdraw, re-`PUT` into
  the same place: D13 gives 409, the plan 200. Fix: `SchedulingRules` returns the whole outcome (ok,
  already here, violation), with "already here" after D5 and D6; add the withdraw-then-re-PUT test.
- **B2. Hurl against the shared deployed database.** `/pre-claim` runs `examples/run.sh` against our
  URL; only 15 places, Hurl stops a file at the first failed assertion, and leftover entries can make
  harness checks fail. Fix: a deploy-safe file that never inserts (every request fails before the
  insert), and a local-only happy-path file that `run.sh` doesn't run against our URL.

## Significant

- **S1. No fixture rule; the 3-talk limit breaks tests.** State "a new speaker per talk unless the
  test is about a shared speaker".
- **S2. "Fails without the locks" is false for the talk lock.** The unique key turns that race into a
  409 too. Fix: the duplicate-key path gets its own reason, and the concurrency tests assert the
  loser's reason (D19: "the 409 it would get if it came second").
- **S3. The concurrency loop needs cleanup per round**, and `AcceptanceTest` has no rounds.
- **S4. D1 "before anything else" doesn't hold for `PUT`:** Spring answers bad bodies (400/415) before
  the controller. Record it in the spec (needs the approver's OK); test unknown rooms with valid bodies.

## Minor

- M1 `lockSlot` throws if the seed row is missing.
- M2 The first schema version on `main` is final; optional `CHECK` and `REFERENCES`.
- M3 Name how "here / elsewhere / taken" are computed, after both locks.
- M4 Binding: ints as `Integer`/`Long`, `"5"` String, `1.5` Double → 400; add `2147483648` → 400
  unknown talk; trailing tokens are accepted, don't assert 400.
- M5 Keep `TRUNCATE` in `@BeforeEach`.
- M6 Reuse `WireFormatTest`'s Talk key check for D20.
- M7 Phase 6: `docs/operations.md` test list, `check-paths.sh`.
- M8 Commit the plan and review before Phase 1.
- M9 A "watch first after the claim" list with fallbacks.
- M10 Local Hurl: give the cleanup command.
- M11 Ask about pushing with evidence; note there is no request logging.
- M12 Optional: the same talk into the same place twice at once → both 200.

## What's good

Lock order talk → slot with no cycle against existing locks; separate read after locking; own
classes; `String` path parsing for 404; schema at the bottom with `ON CONFLICT`; coverage numbers;
restart context.
