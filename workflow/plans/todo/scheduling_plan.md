# Plan: Programme scheduling

- [x] Phase 0: Commit the plan and its review; get Eirik's OK on the S4 wording in the spec
- [x] Phase 1: The venue and the rules as pure code (`Venue`, `SchedulingRules`), with unit tests
- [x] Phase 2: `PUT` and `GET /programme/{room}/{slot}` (D1 to D10, D13 to D18, D20), with `ProgrammeTest`. The `DuplicateKeyException` → 409 path moved to Phase 4, with the concurrency tests that reach it
- [x] Phase 3: `DELETE /programme/{room}/{slot}` (D11, D12, D18)
- [x] Phase 4: Concurrency (D19): tests that assert the loser's reason, so removing either lock fails one; green 3 runs in a row; then offer the push. Without the slot lock: `concurrentPutsIntoOnePlace` gets a 500 (primary key) and `concurrentSharedSpeakerInOneSlot` two 200s. Without the talk lock: `concurrentPutsOfOneTalkIntoTwoPlaces` gets a 500 (`UNIQUE (talk_id)`). With both: 36/36 green 3 runs in a row. The `DuplicateKeyException` → 409 catch (step 0) is not added: with the locks it can't happen, and without them the tests already fail on the 500; see the note in step 0
- [x] Phase 5: Hurl examples: a deploy-safe `examples/scheduling.hurl` and a local-only happy path. All 9 example files and `examples/local/` green twice in a row against a local service on Java 25; `programme_entry` empty afterwards
- [ ] Phase 6: Update `docs/`, finish (update from `main`, coverage floor, `REVIEW.md`, plan to done)

Revised after the review in `scheduling_plan_review.md` (B1, B2, S1 to S4, M1 to M12; see "Review
response" at the end).

## Context (read this after a restart)

- Spec: `workflow/specs/scheduling.md`: the feature request verbatim, decisions D1 to D20 and
  questions Q1 to Q8, **all approved** by Eirik on 2026-11-06 (all recommendations as written; we
  have no product owner, see `.claude/rules/no-product-owner.md`). Not claimed. A person claims, and
  a claim is permanent. Issue: #16.
- Branch: `16-scheduling`, rebased on `main` at `e0a9680` (acceptance, Spring Boot 4 and Java 25 are
  in). PR via the GitHub UI unless the human asks for `gh`. Put `Closes #16` in the PR description.
  Merging to `main` deploys.
- Commands: `./mvnw package` (all tests, JaCoCo, coverage floor), `./mvnw test -Dtest=ProgrammeTest`,
  `workflow/coverage/line-coverage.sh`. Machine settings in `LOCAL.md` if it exists.
- Stack: Java 25, Spring Boot 4.1 (Jackson 3: `tools.jackson.databind`), JdbcTemplate, Postgres.
  Schema in `src/main/resources/schema.sql`: runs on every start, so `IF NOT EXISTS` / `ON CONFLICT`,
  new statements at the bottom only. **The first version of the new tables that reaches `main` is
  final** (the deployed database keeps its data): later changes need `ALTER … IF NOT EXISTS`.
- Coverage floor: `coverage.line.minimum` is `0.790` (340/430 lines). New code must be covered at
  least as well, or `./mvnw package` fails (`workflow/coverage/README.md`).
- Existing code this uses, all in `src/main/java/no/scienta/cfp/`:
  - `TalkDao.lock(id)`: `SELECT … FOR UPDATE` on the talk, false if there's no such talk. Read the
    talk in a separate statement afterwards (see its Javadoc).
  - `TalkDao.findById(id)`: `TalkDto` with `status`, `format`, `speakerId` and `coSpeakerIds` (in the
    order added).
  - `ConferenceService` (406 lines) holds speakers, talks, ratings, co-speakers and acceptance. **Don't
    add to it**: scheduling gets its own classes.
  - The `Map<String, Object>` body pattern and its validation: `ConferenceService.withdraw`
    (`speakerId` must be an `Integer` or `Long` and ≥ 1, else 400). With
    `spring.jackson.use-jackson2-defaults=true` (`application.properties`), JSON whole numbers arrive
    as `Integer`, or `Long` beyond int range, or `BigInteger` beyond that (→ 400); `"5"` is a `String`
    and `1.5` / `5.0` a `Double` (→ 400). Trailing tokens after the JSON are accepted
    (`WireFormatTest`), so don't assert 400 for `{"talkId": 1} x`.
  - Spring answers bad bodies itself, **before the controller** and so before our 404 for an unknown
    room (S4): no body, not JSON, an array → 400; `text/plain` → 415.
  - `TalkController`: thin controller, one line per endpoint.
- Test style: `AcceptanceTest` (HTTP with `java.net.http.HttpClient`, Jackson 3 `JsonNode`, one fresh
  database per test class via `TestDatabase.fresh`, helpers `newSpeaker`, `newTalk`, `rateAll`;
  concurrent requests with `CompletableFuture`). Copy its helpers.
- **Fixture rule (S1): a new speaker per talk**, unless the test is about a shared speaker. A speaker
  can have at most 3 talks (`POST /talks` answers 409 for the 4th).
- Talk formats: `LIGHTNING_10`, `LIGHTNING_20`, `PRESENTATION`, `WORKSHOP`. A talk is `ACCEPTED` after
  2 distinct reviewers rate it and `PUT /talks/{id}/status` `{"status": "ACCEPTED"}`.
- The base app has no request logging: after deploy, the harness transcripts on `/feedback` are the
  only view of what the checks send.

## Decisions for the human to check

The feature's decisions are D1 to D20 in the spec, approved. These are the plan's own, about how
the code is built (not visible to the harness):

- **P1. Own classes.** `Venue` (rooms and slots), `SchedulingRules` (pure), `ProgrammeDao`,
  `ProgrammeService`, `ProgrammeController`, `ProgrammeEntry` (a record). `ConferenceService` is
  already too big and does five things.
- **P2. Tables.** `programme_slot (slot TEXT PRIMARY KEY)`, seeded with `S1` to `S5`, exists only to be
  locked. `programme_entry (room TEXT NOT NULL CHECK (room IN ('R1', 'R2', 'R3')), slot TEXT NOT NULL
  REFERENCES programme_slot (slot), talk_id INTEGER NOT NULL UNIQUE, PRIMARY KEY (room, slot))`. Times
  stay in code (`Venue`).
- **P3. Locks (D19).** A `PUT` locks the talk row (`TalkDao.lock`), then the slot row, always in that
  order, so there's no deadlock (the reviewer checked this against the existing talk and speaker
  locks). The slot lock serialises everything in one slot (taken, speaker clash); the talk lock
  serialises the same talk going into two different slots. `DELETE` locks only the slot. The primary
  key and `UNIQUE (talk_id)` are the backstop: a `DuplicateKeyException` is answered 409 with its own
  reason, `"conflicting change, try again"`, so a test can tell it from the rule reasons (S2).
- **P4. Path parsing.** `room` and `slot` are bound as `String` and parsed by `Venue`, so an unknown
  value is our 404 (D1), not Spring's 400 for a failed enum conversion.
- **P5. Tests.** Plain JUnit 5 (`@Test`, `@ParameterizedTest` with `@CsvSource` for the rule table).
  The project has no TableTest dependency; adding one is a separate chore.
- **P6. Hurl (B2).** `examples/run.sh` runs `examples/*.hurl` against our deployed URL in
  `/pre-claim`, and the deployed programme (15 places) is shared with the harness. So
  `examples/scheduling.hurl` never inserts anything: every request in it fails before the insert. The
  happy path goes in `examples/local/scheduling-happy-path.hurl`, which `run.sh` doesn't glob.

**Watch first after the claim** (harness-visible, least certain), with the fallback to switch to:
D2 (empty place 404; fallback 200 with `talkId: null`), D8 (same place again 200; fallback 409), D12
(`DELETE` 204; fallback 200 with the Entry), D4 (unknown talk 400; fallback 404), D6 (workshop outside
`S4` 409; fallback 400).

## Phase 0: Commit and confirm

1. Commit this plan and `scheduling_plan_review.md`: `docs: Scheduling plan and review` (M8).
2. Ask Eirik to OK this addition to the spec's D1 and D14 (S4, it qualifies an approved decision):
   "Spring answers a bad body (400/415) before the controller, so before D1's 404." Then add it to the
   spec and commit: `docs: Note binding errors come before D1`.

Check: `git status` clean; the spec has the note. 

## Phase 1: Venue and rules, pure

1. `Venue.java`: `enum Room { R1, R2, R3 }` and `enum Slot { S1("09:00", "10:00"), … S5("15:45",
   "16:45") }` with `start()` and `end()`. Static `Optional<Room> room(String)` and
   `Optional<Slot> slot(String)`: exact, case-sensitive match, empty otherwise (D1).
2. `SchedulingRules.java`, pure, no Spring. It decides the whole D13 outcome after the path, body and
   unknown-talk checks (B1):
   ```java
   record Candidate(long id, String status, String format, Set<Long> people) { static Candidate of(TalkDto t) }
   sealed interface Outcome { record Ok() …; record AlreadyHere() …; record Violation(String reason) … }
   record Place(Room room, Slot slot) {}
   static Outcome decide(Candidate talk, Place target, Optional<Place> talkIsAt,
                         boolean targetTaken, List<Candidate> othersInSlot)
   ```
   `people` is the main speaker plus the co-speakers. `talkIsAt` is where the talk is on the programme
   now. `othersInSlot` are the talks in the target slot in the other rooms. Order, exactly D13:
   not `ACCEPTED` → Violation (D5) → `WORKSHOP` and slot ≠ `S4` → Violation (D6) → `talkIsAt` equals
   the target → AlreadyHere (D8) → `talkIsAt` present → Violation "elsewhere" (D9) → `targetTaken` →
   Violation "taken" (D7) → shared person with any of `othersInSlot` → Violation "clash" (D10) → Ok.
   The reasons are constants, so the tests can assert them (S2).
3. `VenueTest`, `SchedulingRulesTest`: every slot's times as `HH:mm`; `r1`, `R4`, `S0`, `""` are empty;
   each rule on its own; a workshop in `S4` is fine and a presentation anywhere (`S4` included) is
   fine; clash main↔main, main↔co, co↔co; **a non-accepted talk in its own place is a Violation, not
   AlreadyHere** (B1); the D13 order when two rules break at once.

Check: `./mvnw test -Dtest='VenueTest,SchedulingRulesTest'` green; `./mvnw package` green.
Commit: `feat: Add venue and scheduling rules`.

## Phase 2: PUT and GET

1. `schema.sql`, at the bottom, under `-- 2026-11 programme scheduling`: the two tables of P2, then
   `INSERT INTO programme_slot (slot) VALUES ('S1'), ('S2'), ('S3'), ('S4'), ('S5') ON CONFLICT DO
   NOTHING;` on one line. Leave the old commented-out `room`/`slot` columns alone.
2. `ProgrammeDao`: `lockSlot(Slot)` (throws `IllegalStateException` if the seed row is missing, M1),
   `find(Room, Slot)` → `Optional<Long>` talk id, `findByTalk(long)` → `Optional<Place>`,
   `talksInSlot(Slot, Room except)` → talk ids, `insert(Room, Slot, long)`.
3. `ProgrammeService.schedule(String room, String slot, Map<String, Object> body)`, `@Transactional`:
   1. parse room and slot, else 404 (D1)
   2. `talkId` an `Integer`/`Long` ≥ 1, else 400 (D3)
   3. `TalkDao.lock(talkId)`, false → 400 "talk not found" (D4)
   4. `ProgrammeDao.lockSlot(slot)` (P3)
   5. after both locks (M3): the talk (`TalkDao.findById`), `talkIsAt` (`findByTalk`), `targetTaken`
      (`find(room, slot).isPresent()`), the other talks in the slot (`talksInSlot`, each via
      `TalkDao.findById`, unlocked reads)
   6. `SchedulingRules.decide`: Ok → insert and return the Entry; AlreadyHere → return the Entry (200,
      D8); Violation → 409 with the reason. (The `DuplicateKeyException` → 409 with P3's reason comes in
      Phase 4, where a test reaches it.)
   `get(String room, String slot)`: 404 for an unknown place (D1) or an empty one (D2).
4. `ProgrammeEntry` record: `room`, `slot`, `start`, `end`, `talkId`, in that order (as the API).
5. `ProgrammeController`: `PUT` and `GET /programme/{room}/{slot}`, `@RequestBody Map<String, Object>`.
6. `ProgrammeTest`, a fresh database for the class. `@BeforeEach`: `TRUNCATE programme_entry` through a
   `JdbcTemplate` autowired into the test (M5). Helper `acceptedTalk(format)`: a new speaker (S1), a
   talk, two ratings, accept. Tests:
   - schedule and get back the Entry with times (D15, D16); empty place `GET` 404 (D2)
   - unknown room or slot on `PUT` (with a valid body, S4) and `GET`, `r1` included (D1)
   - bad `talkId`: missing, `null`, `"5"`, `1.5`, `0`, `-1` → 400 (D3); `2147483648` and an unused id
     → 400 unknown talk (D4, M4)
   - each non-accepted status (`SUBMITTED`, `WAITLISTED`, `REJECTED`, `WITHDRAWN`) → 409 (D5)
   - workshop outside `S4` 409, in `S4` 200 (D6); taken 409 (D7); same place again 200, unchanged (D8);
     elsewhere 409 (D9)
   - clash with the main speaker and with a co-speaker (one speaker on two talks: it has room for 3),
     and no clash in another slot (D10)
   - bad bodies: none, not JSON, array → 400; `text/plain` → 415 (D14)
   - a talk withdrawn after scheduling stays, `GET` still shows it (D17); **re-`PUT` of that withdrawn
     talk into its own place → 409** (B1)
   - D20: `GET /talks/{id}` of a scheduled talk has exactly the existing Talk keys: reuse
     `WireFormatTest`'s `TALK_KEYS` (make it package-visible, M6)

Check: `./mvnw test -Dtest=ProgrammeTest` green; `./mvnw package` green, coverage at or above the
floor. Commit: `feat: Schedule talks into rooms and slots`.

## Phase 3: DELETE

1. `ProgrammeDao.delete(Room, Slot)` returns the row count. `ProgrammeService.unschedule`: parse or 404
   (D1) → lock the slot → delete, 0 rows → 404 (D11).
2. `ProgrammeController`: `DELETE /programme/{room}/{slot}`, `@ResponseStatus(NO_CONTENT)` (D12).
3. `ProgrammeTest`: `DELETE` answers 204 with an empty body, then `GET` is 404; `DELETE` an empty place
   404; unknown room or slot 404; a taken-out talk can go somewhere else (D18).

Check: `./mvnw test -Dtest=ProgrammeTest` green; `./mvnw package` green. Commit:
`feat: Take talks off the programme`.

## Phase 4: Concurrency (D19)

0. In `ProgrammeService.schedule`, catch `DuplicateKeyException` on insert → 409 with P3's reason
   (moved here from Phase 2). **Not done:** the locks make it unreachable, so it would be an untested line; the
   constraints still guard the data, and a 500 makes a broken lock visible. Approved by Eirik,
   2026-11-06: leave it out.
1. In `ProgrammeTest`, start the requests together with `CompletableFuture`, in 10 rounds. **Each
   round** (S3): `TRUNCATE programme_entry` and new talks with new speakers. Each test asserts the
   loser's reason, D19's "the 409 it would get if it came second" (S2):
   - `concurrentPutsIntoOnePlace`: 5 accepted talks into `R1/S1` → one 200, four 409 with reason
     "taken" (not the duplicate-key reason), and `GET` shows the winner.
   - `concurrentPutsOfOneTalkIntoTwoPlaces`: the same talk into `R1/S1` and `R2/S2` → one 200, one 409
     with reason "elsewhere", and the talk is in exactly one place.
   - `concurrentSharedSpeakerInOneSlot`: two talks by one speaker into `R1/S3` and `R2/S3` → one 200,
     one 409 "clash".
   - `concurrentSamePutTwice` (M12): the same talk into `R1/S2` twice at once → both 200.
   Five requests at once use the whole pool of 5; that's fine, each request holds one connection.
2. Prove the tests catch races: comment out the slot lock, run, see which fail (expected:
   `concurrentPutsIntoOnePlace` gets the duplicate-key reason, `concurrentSharedSpeakerInOneSlot`
   gets two 200s). Restore it. Comment out the talk lock (expected: `concurrentPutsOfOneTalkIntoTwoPlaces`
   gets the duplicate-key reason). Restore it. Write what you saw in this phase's checklist line.
3. Run `./mvnw test -Dtest=ProgrammeTest` 3 times in a row, all green.

Check: as above; `./mvnw package` green. Commit: `test: Scheduling under concurrent requests`. Then
ask the human whether to push the branch, with the evidence: the test names, the 3 green runs, what
failed without each lock, and the commit SHA (M11).

## Phase 5: Hurl examples (P6)

1. `examples/scheduling.hurl`, deploy-safe, **never inserts**: set up a speaker, a `SUBMITTED` talk, and
   an accepted `WORKSHOP` (two ratings, accept; unique emails with `{{newUuid}}`). Then:
   unknown room and slot → 404 (`PUT`, `GET`, `DELETE`); `{"talkId": "5"}` → 400; an unused talk id →
   400; the `SUBMITTED` talk → 409; the accepted workshop into `S1` → 409. Header comment: why it never
   inserts.
2. `examples/local/scheduling-happy-path.hurl`, header "local only: writes to the programme". `PUT`,
   `GET`, taken 409, elsewhere 409, clash 409, `DELETE` 204, `GET` 404; frees every place it uses.
3. `docker compose up -d`, `java -jar target/cfp.jar`, then `examples/run.sh` and
   `hurl --test --variable base_url=http://localhost:8080 examples/local/*.hurl`. If a failed run left
   entries behind: `docker compose exec -T postgres psql -U cfp -c 'TRUNCATE programme_entry'` (M10).

Check: both green against the local service, twice in a row. Commit: `docs: Add scheduling Hurl examples`.

## Phase 6: Docs and finish

1. `docs/api.md` (the three endpoints and their status codes), `docs/data-model.md` (the two tables),
   `docs/architecture.md` (the new classes and the lock order), `docs/operations.md` (add
   `ProgrammeTest` and `examples/local/` to the lists) (M7).
2. Update the branch from `main`, run `./mvnw package`, and raise `coverage.line.minimum` to what
   `workflow/coverage/line-coverage.sh` prints if it went up (`workflow/coverage/README.md`).
3. `REVIEW.md` per `.claude/rules/review-log.md`: a draft §3 claim-log row with the evidence, and a §2
   story: the plan review caught B1 (a re-`PUT` of a withdrawn talk would have got 200 against D13) and
   B2 (the Hurl example would have written to the deployed programme the harness uses). Run
   `workflow/scripts/check-review.sh` and `workflow/scripts/check-paths.sh`.
4. `git mv` this plan and its review to `workflow/plans/done/`. Comment on issue #16.

Check: `./mvnw package` green; `check-review.sh` lists no heading we touched; `check-paths.sh` clean.
Commit: `docs: Scheduling docs and finish`. Ask the human about pushing and the PR (`Closes #16`).

## Review response

| Finding | Response |
|---|---|
| B1 D8 before D5/D6 | Fixed: `SchedulingRules.decide` returns Ok / AlreadyHere / Violation in D13's order (Phase 1); test for re-`PUT` of a withdrawn talk (Phase 2) and a unit test |
| B2 Hurl on the deployed database | Fixed: P6, deploy-safe `examples/scheduling.hurl` that never inserts, and `examples/local/` for the happy path (Phase 5) |
| S1 fixture rule | Added to Context; `acceptedTalk` makes a new speaker |
| S2 talk lock untested | Duplicate-key path has its own reason (P3); concurrency tests assert the loser's reason; Phase 4 step 2 lists the expected failures per lock |
| S3 cleanup per round | Phase 4: truncate and new talks each round; the wrong precedent removed |
| S4 binding before D1 | Context notes it; unknown-room `PUT` tests use valid bodies; Phase 0 asks Eirik to OK the spec note |
| M1 `lockSlot` silent | Throws `IllegalStateException` |
| M2 schema final | Context notes it; `CHECK` and `REFERENCES` added to P2 |
| M3 inputs | Phase 2 step 3.5 names them, after both locks |
| M4 binding | Context lists the types; `2147483648` test added; no 400 asserted for trailing tokens |
| M5 `TRUNCATE` | Kept for `@BeforeEach` and each concurrency round |
| M6 D20 | Reuse `WireFormatTest.TALK_KEYS` |
| M7 operations.md, check-paths | Phase 6 |
| M8 commit plan | Phase 0 |
| M9 watch list | Added under the decisions |
| M10 local cleanup | Phase 5 step 3 |
| M11 push with evidence | Phase 4; no request logging noted in Context |
| M12 same PUT twice | `concurrentSamePutTwice` in Phase 4 |
