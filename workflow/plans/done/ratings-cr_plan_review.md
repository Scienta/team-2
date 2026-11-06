# Review: ratings-cr plan

Reviewer: `plan-reviewer` agent, 2026-11-06.

Verdict: sound, arithmetic checked (25/8→3.12, 27/8→3.38 HALF_EVEN; trimmed sets; X/Z/Y and Q/P rankings; e,b,a,c,d). One blocker.

- **B1.** `examples/ratings-cr.hurl` as planned gives one speaker 7 talks; `ConferenceService.java:147` returns 409 at the 4th. Fix: one speaker per case, ≤3 talks each (A: trim + no-trim; B: 3.12 + 3.38; C: X,Y,Z), assert ranking with `?speakerId=` and `sort=rating`, reviewer ids 1..10 (rate only rejects <1, `ConferenceService.java:382`). `RatingsTrimTest`: `newSpeaker()` in every test, say so in the plan.
- **S1.** Harness-visible decisions need fallbacks (no-product-owner rule). D5 a) fallback b) unrounded sort. D6 a) fallback b) 1 decimal half-up below 5; D6 changes values the base spec claimed at tick 0 (`ratings.md:18` "4.3"), riskiest default: put it right after D1. D2 a) fallback b). D1/D3: write "none needed" into the plan's Decisions list.
- **S2.** `gh pr create` conflicts with main's rules: resolved by the orchestrator (it creates the PR on the user's explicit request).
- **S3.** `RatingsTrimTest` asserts with `asDouble()`, which can't tell 3.12 from 3.120 or 4.0 from 4.00. Pin the wire text at least once for 3.12, 3.38 and 4.0 (from 1,4,4,4,4), with a regex as in `TalkListRatingsTest.java:66-69`.
- **M1.** `stats()` COALESCE unnecessary; read hi/lo as `Number.intValue()` like cnt (`ConferenceService.java:231`), since MAX(int) is Integer.
- **M2.** `ratings-rules.hurl:273-280` was the "same rounded average ranks more ratings first" tie; with 2 decimals 4.25/4.33 don't tie. Rewrite as P/Q (4,4,5 vs 1,4,4,5,5) and fix titles :211, :222 and `ratings-edge.hurl:25`.
- **M3.** Add a non-tie round-up case, e.g. 4,5,5 = 4.67, to `UtilsTest`.
- **M4.** Add "`snapshot-api.sh` shows no diff" to a Check (its rated talk 4,3 → 3.5 unchanged).
- **M6.** Phase 4 Check: `grep -n "1 decimal" docs/api.md examples/*.hurl` gives no hits.

Keep: BigDecimal HALF_EVEN in Java, not Postgres ROUND (which gives 3.13); every averageRating goes through applyRatings; MAX/MIN in statsFor keeps one query (N+1 guard `ConferenceServiceTest:82-96`); WireFormatTest unchanged with git diff check.
