# Plan: Acceptance state machine

- [x] Phase 1: Committee decisions, `PUT /talks/{id}/status` (D1 to D5, D11 to D13, D16), with `AcceptanceTest`
- [x] Phase 2: Withdrawal, `POST /talks/{id}/withdraw` (D6 to D10, D14)
- [x] Phase 3: Concurrency tests (D15); then offer the push and PR (review M7). Green 3 runs in a row; with the lock removed, all 4 fail (up to 6 conflicting 200s), so they catch races
- [x] Phase 4: Hurl example `examples/acceptance.hurl`, run against a local service (all 6 example files green)
- [x] Phase 5: Update `docs/`, finish (plan to done, claim-log row in `REVIEW.md`)

## Context (read this after a restart)

- Spec: `workflow/specs/acceptance.md`: the feature request verbatim, the transition table, and the
  decisions D1 to D16, **all approved** by Julian on 2026-11-06 from the spec's wording (the team has
  no product owner). D5 (repeats, WAITLISTED → WAITLISTED), D6 and D7 are marked **Open risk**.
  Claimed? No. A person claims, a claim is permanent; run `/pre-claim acceptance` first.
- Branch: `acceptance`. PR via the GitHub UI, never `gh`. Merging to `main` deploys.
- Commands: `./mvnw package`, `./mvnw test -Dtest=AcceptanceTest`, `examples/run.sh` (needs a local
  service: `docker compose up -d`, `java -jar target/cfp.jar`).
- Code in `src/main/java/no/scienta/cfp/`:
  - `TalkController`: the talk endpoints. `rate` shows the body style: `@RequestBody Map<String, Object>`,
    validated in the service, so bad JSON / arrays / no body give Spring's 400 and `text/plain` 415 (D11).
  - `ConferenceService`: `addCoSpeaker` / `removeCoSpeaker` show the locking pattern:
    `talkDao.lock(id)` (`SELECT id … FOR UPDATE`, false if no talk), **then** a separate read with
    `getTalk(id)` (see the Javadoc on `TalkDao.lock`: a single `SELECT … FOR UPDATE` that waited
    reads a stale snapshot). `getTalk` already fills `ratingCount`, `averageRating`, `coSpeakerIds`.
  - `TalkDao`: add `updateStatus(id, status)`. `talk.status` is already a `TEXT NOT NULL DEFAULT
    'SUBMITTED'` column (`schema.sql`) and already in every Talk response (D13), so **no schema change**.
  - `ratingCount` counts distinct reviewers (PK `(talk_id, reviewer_id)` on `rating`), so it is D16.
- Test style: copy `src/test/java/no/scienta/cfp/RatingsTest.java` (Jackson `ObjectMapper`, `send`
  helper with method/body/content type, `rateAll`). `TestDatabase.fresh` isolates the class, not each
  test: never assert on absolute list positions.
- **Fixtures:** a speaker can submit at most 3 talks (`workflow/specs/talks.md`), so `newTalk()`
  creates a **new speaker per talk** unless a test needs two talks of one speaker. Accepting needs 2
  distinct reviewers: `rateAll(talk, 4, 5)` first.
- Gate (`.claude/skills/execute-plan`): a phase is checked off and committed only when `./mvnw
  package` is green. Known pre-existing warnings: the `@Disabled` `UtilsTest.nowIsCurrentTime` and
  Hikari "Failed to validate connection" from `HealthTest`; nothing new.

## Decisions for the human to check

All approved in `workflow/specs/acceptance.md`. In short:

| From \ To | ACCEPTED | REJECTED | WAITLISTED | WITHDRAWN (main speaker) |
|---|---|---|---|---|
| SUBMITTED | 200 if ≥ 2 reviewers, else 409 | 200 | 200 | 200 |
| WAITLISTED | 200 if ≥ 2 reviewers, else 409 | 200 | 409 (D5) | 200 |
| ACCEPTED | 409 (D5) | 409 | 409 | 200 |
| REJECTED | 409 | 409 (D5) | 409 | 409 (D9) |
| WITHDRAWN | 409 | 409 | 409 | 409 (D5, D9) |

Errors, in this order (D10): unknown talk 404 (D1) → bad body 400 (D2, D8; Spring binding 400/415
comes even earlier, D11) → withdrawal by anyone but the main speaker, including an unknown speaker,
403 (D6, D7) → state 409 (D3, D4, D5, D9). Everything rejected leaves the talk unchanged. Success is
200 with the full Talk (D12). Lists keep every talk whatever its status (D13); ratings, co-speakers and
the 3-talk limit don't change (D14).

Harness-visible and least certain, watch first after the claim: **D5** (409 for a repeat, especially
WAITLISTED → WAITLISTED), **D6** (403), **D7** (403; but `POST /talks` answers **400** for an unknown
`speakerId` in the body and passes the talks checks, review S3, so 400 is the first fallback).

## Phase 1: Committee decisions

1. `TalkDao.updateStatus(long id, String status)`: `UPDATE talk SET status = ? WHERE id = ?`.
2. `TalkController`: `@PutMapping("/talks/{id}/status")`, `decide(@PathVariable Long id,
   @RequestBody Map<String, Object> body)` → `service.decide(id, body)`.
3. `ConferenceService.decide`, `@Transactional`:
   1. `if (!talkDao.lock(id))` → 404 `talk not found` (D1).
   2. `status = body.get("status")`; unless it's a `String` equal to `ACCEPTED`, `REJECTED` or
      `WAITLISTED` → 400 (D2).
   3. `talk = getTalk(id)` (after the lock). `if (!allowed(talk.status, status))` → 409 (D3, D5).
   4. `ACCEPTED` with `talk.ratingCount < 2` → 409 (D4, D16).
   5. `talkDao.updateStatus`, return `getTalk(id)`.
   The rule is one **allow-list** (review M2), `private static boolean allowed(String from, String to)`,
   used by both endpoints: `SUBMITTED` → `ACCEPTED`/`REJECTED`/`WAITLISTED`/`WITHDRAWN`;
   `WAITLISTED` → `ACCEPTED`/`REJECTED`/`WITHDRAWN`; `ACCEPTED` → `WITHDRAWN`; nothing else.
4. `src/test/java/no/scienta/cfp/AcceptanceTest.java` (copy `RatingsTest`'s helpers):
   - `decisionsFromSubmitted`: REJECTED and WAITLISTED give 200 with that `status` and the full Talk
     (`ratingCount`, `averageRating`, `coSpeakerIds` present, D12); ACCEPTED after `rateAll(t, 4, 5)`
     gives 200.
   - `waitlistedCanStillBeDecided`: WAITLISTED → ACCEPTED (with 2 reviewers) 200; another talk
     WAITLISTED → REJECTED 200.
   - `acceptNeedsTwoDistinctReviewers` (D4, D16): no ratings 409; reviewer 1 rating twice, with two
     explicit `rate(t, 1, …)` calls (`rateAll` uses distinct reviewers, review M4), 409; then
     reviewer 2 rates, 200. After each 409, `GET` shows the old status.
   - `finalDecisionsAreFinal` (D3, D5): from ACCEPTED, each of ACCEPTED, REJECTED, WAITLISTED gives
     409; from REJECTED, the same three give 409; WAITLISTED → WAITLISTED 409. Status unchanged after.
   - `invalidStatusGives400` (D2): `SUBMITTED`, `WITHDRAWN`, `accepted`, `"x"`, `null`, `1`, `{}`.
   - `unknownTalkGives404` (D1): id `2147483647`, also with an invalid body (`{"status":"x"}` → 404).
   - `badBodies` (D11): no body 400, `not json` 400, `[1]` 400, `text/plain` 415; path id `abc` 400.
   - `statusShowsWhereverTheTalkIsShown` (D13): after WAITLISTED, the talk shows
     `"status":"WAITLISTED"` in `GET /talks/{id}`, `GET /talks`, `GET /talks?sort=rating`,
     `GET /talks?speakerId=`, `GET /speakers/{id}/talks`. A REJECTED talk is still in `GET /talks`.

Check: `./mvnw test -Dtest=AcceptanceTest` green, then `./mvnw package` green.
Commit: `feat: Committee decisions on talks (PUT /talks/{id}/status)`.

## Phase 2: Withdrawal

1. `TalkController`: `@PostMapping("/talks/{id}/withdraw")`, `withdraw(@PathVariable Long id,
   @RequestBody Map<String, Object> body)`.
2. `ConferenceService.withdraw`, `@Transactional`:
   1. lock, 404 (D1).
   2. `Object raw = body.get("speakerId")`: must be an `Integer` or `Long` (Jackson gives either) and
      `>= 1`, else 400 (D8; `< 1` as ratings' `reviewerId`, review M5). Then
      `long speakerId = ((Number) raw).longValue()`.
   3. `talk = getTalk(id)`; `speakerId != talk.speakerId.longValue()` → 403 `only the talk's speaker
      can withdraw it` (D6, D7: an unknown speaker is "not the main speaker", no speaker lookup
      needed). **Compare as `long`** (review B1): `TalkDto.speakerId` is a boxed `Long` and Jackson
      gives an `Integer`, so `!=` on the objects would compare references and 403 everyone.
   4. `if (!allowed(talk.status, "WITHDRAWN"))` → 409 (D9, D5), the same allow-list as Phase 1.
   5. `updateStatus(id, "WITHDRAWN")`, return `getTalk(id)`.
3. Tests in `AcceptanceTest`:
   - `speakerWithdraws`: from SUBMITTED, WAITLISTED and ACCEPTED, 200 with `"status":"WITHDRAWN"`.
   - `withdrawnIsOutForGood` (D3, D5): after withdrawal, every decision 409 and withdrawing again 409.
   - `rejectedCantBeWithdrawn` (D9): 409, still REJECTED.
   - `onlyTheMainSpeakerCanWithdraw` (D6, D7): another registered speaker 403; a co-speaker of the
     talk (added with `PUT /talks/{id}/co-speakers/{s}`) 403; speaker `999999` 403. Status unchanged.
   - `invalidWithdrawBody` (D8): `{}`, `{"speakerId":null}`, `{"speakerId":"1"}`, `{"speakerId":1.5}`,
     `{"speakerId":0}`, `{"speakerId":-1}` give 400; no body / `not json` 400; `text/plain` 415.
   - `withdrawPrecedence` (D10): unknown talk with `{}` 404; a stranger on a rejected talk 403.
   - `afterwardsNothingElseChanges` (D14): a withdrawn talk can still be rated (200, `ratingCount` up);
     a speaker with 3 talks, one withdrawn, still gets 409 on a 4th.

Check: `./mvnw package` green. Commit: `feat: Speakers withdraw their talks (POST /talks/{id}/withdraw)`.

## Phase 3: Concurrency (D15)

Tests in `AcceptanceTest`, each on a fresh talk rated by 2 reviewers, at most 20 requests in flight
(pool of 5):
- `concurrentDecisionsLeaveALegalState`: 10 parallel ACCEPTED and 10 parallel REJECTED. Exactly one
  response is 200, the other 19 are 409, and `GET` shows the status that the 200 returned.
- `concurrentWithdrawAndReject`: 10 parallel withdrawals by the main speaker and 10 parallel
  REJECTED. Exactly one 200 (either withdrawal then reject is 409, or reject then withdrawal is 409),
  the rest 409; the final status is the winner's.
- `concurrentWaitlistThenAccept` (review S1): 10 parallel WAITLISTED and 10 parallel ACCEPTED on a
  SUBMITTED talk. The lock serialises them, and the first ACCEPTED always finds SUBMITTED or
  WAITLISTED, so: final status **ACCEPTED**, exactly one ACCEPTED 200, at most one WAITLISTED 200,
  every other response 409.
- `concurrentAcceptAndWithdraw` (review S2): 10 parallel ACCEPTED and 10 parallel withdrawals. Final
  status **WITHDRAWN**; exactly one withdrawal 200; at most one ACCEPTED 200; the rest 409.

The exact counts depend on D5 (a repeat is 409). If D5 is ever made idempotent (200), update these
counts.

If a test fails, the lock isn't held across read-check-write: fix the service, not the test.

Check: `./mvnw package` green, and `AcceptanceTest` green 3 runs in a row
(`./mvnw test -Dtest=AcceptanceTest` ×3). Commit: `test: Concurrent decisions and withdrawals`.
Then **ask the human whether to push** and open the PR in the GitHub UI (review M7): the feature is
complete here, and Phases 4 and 5 can follow on the same branch or a new one.

## Phase 4: Hurl example

`examples/acceptance.hurl` with its own setup (`{{newUuid}}` emails, as `examples/co-speakers.hurl`;
find the talk by id or `?speakerId=`, never by list position, review M3) (2 speakers, 3 talks, 2 reviewers' ratings):
WAITLISTED then ACCEPTED 200; ACCEPTED → REJECTED 409; accept with 1 reviewer 409; `SUBMITTED` 400;
unknown talk 404; withdraw by the other speaker 403, by the main speaker 200 (`status` WITHDRAWN);
withdraw again 409; the talk is still in `GET /talks` with `status` WITHDRAWN.

Check: local service, `examples/run.sh` → all files `Success`, 0 failed. Commit:
`test: Add Hurl example for acceptance`.

## Phase 5: Docs and finish

1. `docs/api.md`: the two endpoints, the error order and codes; replace "Always `SUBMITTED` today"
   in the Talk field table (review M6) with the five values.
   `docs/architecture.md`: the "lock, read, check, write" flow. `docs/operations.md`: add
   `AcceptanceTest`. `docs/data-model.md`: `talk.status` values.
2. `REVIEW.md` §3: a claim-log row "Acceptance | not claimed yet | – | evidence: …" for the person
   to complete after `/pre-claim`.
3. `git mv` this plan (and its review) to `workflow/plans/done/`.

Check: `workflow/scripts/check-paths.sh` passes; `./mvnw package` green. Commit:
`docs: Acceptance in docs, plan done`. Then ask the human to push and open the PR in the GitHub UI.

## Review response

| Finding | Response |
|---|---|
| B1 withdrawal compares `Long` with `Integer` | Fixed: Phase 2 step 3 compares as `long` |
| S1 waitlist/accept race too loose | Fixed: final ACCEPTED, exactly 1 ACCEPTED 200, ≤ 1 WAITLISTED 200 |
| S2 counts depend on D5; accept vs withdraw untested | Note added under Phase 3; `concurrentAcceptAndWithdraw` added |
| S3 400 precedent for an unknown `speakerId` | Added to the watch list as the first fallback; D7 reasoning updated in the spec. D7 stays 403 (approved) |
| S4 no request logging | Agreed, but out of scope for this plan: a separate small PR before the claim (it helps every feature) |
| M1 conditional write as backup | Not done: the lock is correct and tested by Phase 3; one mechanism is simpler |
| M2 deny-list for withdrawal | Fixed: one allow-list `allowed(from, to)` for both endpoints |
| M3 Hurl on a persistent DB | Fixed: `{{newUuid}}`, find by id |
| M4 one reviewer twice | Fixed: explicit `rate(t, 1, …)` twice |
| M5 `speakerId` ≤ 0; non-numeric path id | `speakerId < 1` gives 400 (as ratings' `reviewerId`), added to D8; path `abc` added to `badBodies` |
| M6 "Always SUBMITTED today" | Named in Phase 5 |
| M7 push earlier | Phase 3 ends by asking the human to push |
