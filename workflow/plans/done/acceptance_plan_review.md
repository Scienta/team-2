# Review: acceptance plan

Reviewer: a general-purpose agent following `.claude/agents/plan-reviewer.md` (the agent definition
wasn't loaded in the session yet), 2026-11-06. Findings verified against the code by the planning
agent before revising.

**Verdict: executable after two fixes.** B1 (the withdrawal speaker comparison doesn't compile, or
gives 403 to everyone) and S1 (the Phase 3 waitlist test is too loose and lets a real bug through).
Locking is sound under READ COMMITTED; nothing breaks the base checks.

## Blocking

- **B1.** Plan 99-101: `body.get("speakerId")` is an `Integer` from Jackson; `TalkDto.speakerId` is a
  boxed `Long` (`TalkDto.java:16`). `Long != Integer` doesn't compile; as `Object`, `!=` compares
  references, so every withdrawal gets 403. Fix: `((Number) speakerId).longValue() != talk.speakerId.longValue()`.

## Significant

- **S1.** `concurrentWaitlistThenAccept` (plan 128-130): with the row lock serialising requests and 2
  reviewers, the first ACCEPTED always succeeds, so the final status is always ACCEPTED, ACCEPTED
  200s == 1, WAITLISTED 200s <= 1. "ACCEPTED or WAITLISTED" would pass a stale-read bug.
- **S2.** The other two concurrency tests are correct for every interleaving, but depend on D5; say so
  next to them. Gap: ACCEPTED racing a withdrawal (both can succeed) isn't covered.
- **S3.** D7: `POST /talks` answers 400 for an unknown `speakerId` in the body
  (`ConferenceService.java:126-127`), and talks checks pass with it; that's a closer precedent than the
  co-speakers 404. Add it to D7's reasoning and the watch list.
- **S4.** No request logging exists; AGENTS.md calls it the main way to see what checks send.

## Minor

- **M1.** Locking correct; optional backup: conditional `UPDATE … WHERE status = ?`.
- **M2.** Withdrawal written as a deny-list; use one allow-list for both endpoints.
- **M3.** Hurl: `{{newUuid}}` emails; find the talk by id, not position.
- **M4.** "One reviewer rating twice" needs two explicit `rate(t, 1, …)`; `rateAll` uses distinct reviewers.
- **M5.** `speakerId` 0 or negative: decide (ratings gives 400 for `reviewerId < 1`). Non-numeric path id → Spring 400, add to D11.
- **M6.** Phase 5 must replace "Always `SUBMITTED` today" in `docs/api.md:74`.
- **M7.** Offer the push and PR after Phase 3; docs and Hurl don't block the deploy.

## Keep

Lock then separate read; transition table in the plan; open risks named; fixture rules; no absolute
list positions; "fix the service, not the test"; 3 green runs; restart-proof context.
