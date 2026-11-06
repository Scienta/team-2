# Plan: Ratings, check the base app against the spec and close the gaps

- [x] Phase 0: Check the harness feedback for the `ratings` checks and revise the decisions below (tick 44: ratings check-1 to check-5 all OK, no FAULTY checks; no policy changed)
- [x] Phase 1: Unit tests for the rating display format (the D2 guard failed on local JDK 21: U+202F before AM/PM. Fixed `Utils.usShort` with the explicit pattern `M/d/yy, h:mm a`; prod JDK 11 was unaffected, but a JDK upgrade would have broken it)
- [x] Phase 2: Integration tests for current ratings behaviour (`RatingsTest`, all green; 14 tests, no new bugs found)
- [x] Phase 3: E1 error precedence, red then green (E1 approved: 404; red confirmed on 2 tests, then green)
- [x] Phase 4: Hurl examples for the edge cases (`examples/ratings-edge.hurl`, all 4 example files green against a local service)
- [ ] Phase 5: Update `docs/`: record the known-issue decisions, and keep api, architecture and operations in line with the code

Revised after the review in `ratings_plan_review.md` (findings 1 to 5 and the note on concurrency are addressed).

## Context (read this after a restart)

- Spec: `workflow/specs/ratings.md` (a copy of the harness base spec; the Talk schema and the
  **3-talks-per-speaker limit** are in `workflow/specs/talks.md`). Ratings is a **base feature**:
  it's already implemented and claimed from tick 0. This plan is about verification and closing
  gaps, not building something new.
- There are **no tests for ratings today**, only the happy-path Hurl in `examples/ratings.hurl`.
- Code, all in `src/main/java/no/scienta/cfp/`:
  - `TalkController`: `PUT /talks/{id}/ratings/{reviewerId}` (body bound as
    `@RequestBody Map<String, Object>`, so malformed JSON or a JSON array fails in Spring's binding,
    **before** the service runs), `GET /talks?sort=&speakerId=`, `GET /talks/{id}`
  - `SpeakerController`: `GET /speakers/{id}/talks`
  - `ConferenceService.rate` (checks the reviewerId, then the score, then `talkDao.exists`, then
    saves), `listTalks` (ranking), `getTalksForSpeaker`, `addRatings` / `calculateAverage` (count
    and the half-up average to 1 decimal), `submitTalk` (409 above 3 talks per speaker)
  - `RatingDao.save`: one `INSERT … ON CONFLICT (talk_id, reviewer_id) DO UPDATE SET score, rated_at`
  - `Utils.now` / `Utils.parse` (UTC, `yyyy-MM-dd HH:mm:ss`), `Utils.usShort` (JDK
    `DateFormat.SHORT`, `Locale.US`, UTC)
  - `TalkDao.findAll` / `findBySpeaker` order by `id` (this is "oldest first")
- Schema: `src/main/resources/schema.sql`, table `rating (talk_id, reviewer_id, score, rated_at TEXT)`
  with PK `(talk_id, reviewer_id)`.
- Test style: copy `src/test/java/no/scienta/cfp/TalksTest.java` (`@SpringBootTest(RANDOM_PORT)`,
  `TestDatabase.fresh(registry)`, `java.net.http.HttpClient`). `TestDatabase.fresh` isolates **test
  classes, not methods**: every test in `RatingsTest` shares one database, so never assert on
  absolute list contents or positions. Code conventions are in `skills/PROJECT.md`.
- Commands: `./mvnw package` (needs Docker), `./mvnw test -Dtest=RatingsTest`,
  `examples/run.sh http://localhost:8080` (service from `docker compose up -d` + `java -jar target/cfp.jar`).
- Gate (`skills/phase_execution.md`): a phase is checked off and committed only when **all** tests
  pass without warnings. No phase may end with a deliberately failing test.

## Docs and known issues (`docs/`)

`docs/` describes the code **as it is**; where it disagrees with the spec, the spec wins
(`docs/README.md`). `docs/known-issues.md` says its entries "haven't been checked against the base
specs yet" and that each decision must be recorded there. The ratings-related entries:

| Issue | Checked against the spec | Outcome in this plan |
|---|---|---|
| R6: the stats average rounds down, a talk's average rounds half up | The spec's rounding rule is about a talk's average. `/admin/stats` `averageRating` is a mean of talk means, not a talk, and isn't in any base spec. `/admin/talks.csv` uses the talk's own (half-up) value. | Not a ratings defect: record "kept, out of scope" |
| R7: unknown `speakerId` gives `[]` on `/talks`, 404 on `/speakers/{id}/talks` | The spec doesn't say | E5: keep `[]`, record it |
| R8: score validated before talk existence | The spec doesn't say | E1 approved: 404 (Phase 3) |
| D2: the `ratedAtDisplay` space becomes U+202F on JDK 20+ | The spec requires "exactly one ordinary space" | Guarded by a Phase 1 unit test |
| D3: no FK from `rating.talk_id` | No endpoint deletes talks, so `rate`'s exists-check then upsert can't leave orphans | Record "no action"; revisit if a drip feature deletes or withdraws talks |
| P1: one rating query per talk in every list | "Reviewing is busy" is about correctness, not latency | Out of scope; separate plan if the harness shows slow or 503 lists |

Other facts from `docs/` that the tests must respect:
- `docs/api.md` documents today's rating check order (reviewer, then score, then talk exists) and the
  error body (Spring's default, reason in `message`). Phase 2 tests assert status codes only, not
  messages.
- `spring.jackson.parser.allow-comments=true` (`docs/architecture.md`): `{"score": 3 /* c */}` is
  valid input. Keep it; don't test it as invalid.
- `docs/operations.md` lists the test classes. Add `RatingsTest` there in Phase 5.

## What already matches the spec (verify it with tests; don't change it)

| Spec rule | Implementation |
|---|---|
| Score is an integer from 1 to 5 | `score instanceof Integer` and range check, so `5.0`, `"5"`, `0`, `6` and a missing score all give 400 |
| Reviewer is a positive whole number | `reviewerId < 1` gives 400; a non-numeric path gives Spring's 400 |
| One rating per reviewer per talk, and re-rating replaces the score and time | upsert on the PK |
| No lost ratings, no double counting | single-statement upsert, with the count and sum read from the table |
| Average to 1 decimal, half up (4,4,4,5 gives 4.3) | `BigDecimal` division with `HALF_UP` on total/count |
| Unrated talk: no average | `averageRating` is `null`, `ratingCount` is 0 |
| Ranking: rounded average desc, then count desc, then oldest first, unrated last | `listTalks` comparator on the rounded `averageRating` |
| `ratedAtDisplay` is the latest rating's time, `M/d/yy, h:mm AM`, UTC | `rate` formats the `now` it just stored with `Utils.usShort` (CLDR on Java 11 gives the comma and an ordinary space; see D2) |

## Error handling: the spec prescribes no error statuses

These are team policy, not spec requirements. "Today" is the code's current behaviour. Phase 2
pins today's behaviour; only E1 proposes a change.

| # | Request | Where it's decided | Today | Policy |
|---|---|---|---|---|
| E1 | Existing JSON object body with an invalid or missing `score` (e.g. `{"score":6}`, `{"score":"5"}`, `{}`), Content-Type `application/json`, **on a talk that doesn't exist** | `ConferenceService.rate` | 400 | **404** (the talk lookup before score validation). **Approval: APPROVED** (Daniel Bakkelund, 2026-11-06, reversing an earlier rejection) |
| E2 | Valid body (`{"score":3}`) on a talk that doesn't exist | service | 404 | keep |
| E3 | `reviewerId` `0`, `-1` (service), or `abc` (Spring path conversion), whatever the talk or body | service / Spring | 400 | keep; it's checked first |
| E4a | No body, Content-Type `application/json` | Spring binding | 400 | keep |
| E4b | Body `not json`, Content-Type `application/json` | Spring binding | 400 | keep |
| E4c | Body `[1]`, Content-Type `application/json` | Spring binding | 400 | keep |
| E4d | Body `{"score":3}`, Content-Type `text/plain` | Spring content negotiation | 415 | keep |
| E5 | `GET /talks?speakerId=<unknown>` | service | `[]` (R7) | keep `[]`: it's a filter |
| E6 | `GET /talks?sort=foo` | service | 400 | keep |
| E7 | Status on first rating and on re-rating | service | 200 | keep 200, as in the spec |

E4a to E4c fail in binding before the service runs, so they stay 400 even on a missing talk,
whatever E1 decides. Only E1 changes with the service reorder.

**E1 approval rule:** a person on the team writes "Approval: APPROVED" (or "REJECTED") in the
table above, or Phase 0 finds a harness check that requires 404. Without that, Phase 3 is skipped
and the current behaviour (400) is kept and pinned by the Phase 2 tests. This is a chosen policy
(known issue R8), not a demonstrated spec violation.

## Phase 0: Harness feedback

Needs `.fagdag/harness-token` (a person pastes it from the harness `/feedback` page; it was missing
when this plan was written). Fetch `$H/api/teams/$TEAM/feedback` (see "Harness API" in `AGENTS.md`)
and look at the `ratings/*` checks. For each FAULTY check, fetch its timeline/transcript and note
here the request and our answer. If a check contradicts a policy in the table above, change it and
tell the human. If the token is still missing, tick this off as "skipped, no token" and carry on.

## Phase 1: Unit tests (`UtilsTest`)

- `Utils.usShort` with fixed UTC instants:
  - `2026-11-04T13:05:00Z` gives `11/4/26, 1:05 PM`
  - `2026-11-04T00:00:00Z` gives `11/4/26, 12:00 AM`
  - `2026-11-04T12:00:00Z` gives `11/4/26, 12:00 PM`
  - `2026-01-09T09:07:00Z` gives `1/9/26, 9:07 AM`
  - assert that the separator before AM/PM is U+0020 (guards D2)
- Leave the existing `roundsRatingToOneDecimal` test alone: it tests `Utils.roundRating`, which
  only the stats use (R6, out of scope). The ratings rounding is tested over HTTP in Phase 2;
  don't widen `calculateAverage`'s visibility.

Gate: `./mvnw package` green, then check off and commit.

## Phase 2: Integration tests for today's behaviour, `src/test/java/no/scienta/cfp/RatingsTest.java`

**Every test in this phase passes against the current code.**

Helpers:
- `newSpeaker()`, `newTalk(speakerId)` (assert 201; never more than 3 talks per speaker),
  `rate(talk, reviewer, json)`, plus a raw `send(method, path, body, contentType)` for E4.
- Parse JSON with Jackson's `ObjectMapper` (on the classpath via spring-boot-starter-web), not regexes.
  `talkIn(JsonNode array, long id)` returns the element whose `id` matches, and fails if it's absent.
- `idsInOrder(JsonNode array, List<Long> ours)` returns the list's ids filtered to `ours`, in
  response order. Use it for every ordering assertion, because other tests share the database.
- `@Autowired JdbcTemplate jdbc` is used only in test 6, to plant an old timestamp.

Tests:

1. **Happy path:** 200, and the body has `talkId`, `reviewerId` and `score` equal to what was sent,
   and `ratedAtDisplay` matching `^\d{1,2}/\d{1,2}/\d{2}, \d{1,2}:\d{2} [AP]M$`.
2. **Re-rating replaces:** rate 2, then 5, by the same reviewer gives `ratingCount` 1 and `averageRating` 5.0.
3. **Aggregates wherever a talk is shown** (review finding 5). Speaker S with talks T1 and T2.
   Rate T1 with 4, 4, 4, then re-rate reviewer 3 to 5, so the scores are 4, 4, 5 (13/3 = 4.33,
   shown as 4.3, n=3). Then, on the T1 object in each of these responses, assert
   `ratingCount == 3` and `averageRating == 4.3`. On T2, assert `ratingCount == 0` and
   `averageRating` is JSON `null` (present and null, not missing):
   - `GET /talks/{T1}` and `GET /talks/{T2}`
   - `GET /talks`
   - `GET /talks?sort=rating`
   - `GET /talks?speakerId=S`
   - `GET /talks?sort=rating&speakerId=S`
   - `GET /speakers/S/talks`

   Also, `POST /talks`'s response for a new talk has `ratingCount` 0 and `averageRating` null.
4. **Rounding half up:** reviewers 1 to 4 give 4, 4, 4, 5, so `GET /talks/{id}` gives `averageRating` 4.3.
5. **Ranking** (review finding 1). There are five talks over two speakers, created in this order:

   | Talk | Speaker | Scores | Shown as | n |
   |---|---|---|---|---|
   | A | S1 | 4, 4, 4, 5 | 4.3 (raw 4.25) | 4 |
   | B | S1 | 5, 4, 4 | 4.3 (raw 4.33) | 3 |
   | C | S1 | 4, 5, 4, 4 | 4.3 (raw 4.25) | 4 |
   | D | S2 | none | null | 0 |
   | E | S2 | 5 | 5.0 | 1 |

   Expected orders:
   - Global: `idsInOrder(GET /talks?sort=rating, [A..E])` is **E, A, C, B, D**. B's raw average
     is higher than A's, but they round to the same value, so A wins on more reviewers. C ties A
     on both, so the older A comes first. D is unrated, so it's last.
   - Filtered: `GET /talks?sort=rating&speakerId=S1` is exactly **A, C, B** (that speaker has no
     other talks, so exact equality is fine), and `speakerId=S2` is exactly **E, D**.
   - Unsorted: `idsInOrder(GET /talks, [A..E])` is **A, B, C, D, E** (oldest first).
6. **`ratedAtDisplay` is the latest rating's time** (review finding 4). Rate talk T as reviewer
   1, then plant an old time with
   `jdbc.update("UPDATE rating SET rated_at = '2020-01-01 00:00:00' WHERE talk_id = ? AND reviewer_id = 1", T)`.
   Record `before = Instant.now()`, re-rate, then record `after = Instant.now()`. Assert:
   - the stored `rated_at` is no longer `2020-01-01 00:00:00`, and parses (as UTC) to between
     `before` and `after`, both truncated to the second
   - the response's `ratedAtDisplay` is not `1/1/20, 12:00 AM`, and equals the formatting of
     either `before` or `after`. Use an independent
     `DateTimeFormatter.ofPattern("M/d/yy, h:mm a", Locale.US).withZone(UTC)`; checking both
     covers a minute boundary between them. Never sleep, and never compare two quick responses
     with each other.
7. **Invalid scores give 400** on an existing talk: `{"score":0}`, `6`, `4.5`, `5.0`, `"5"`,
   `null`, `true`, and `{}`.
8. **Invalid reviewer gives 400 (E3):** reviewers `0`, `-1` and `abc`, each with a valid body on an
   existing talk, plus `0` with `{"score":6}` on a missing talk (id `2147483647`). Reviewer is
   checked first, so all give 400.
9. **Bad bodies (E4a to E4d)** on an existing talk: 400, 400, 400, 415. Also E4b on a missing talk
   gives 400 (binding comes first).
10. **Missing talk:** `{"score":3}` gives 404 (E2). Also add a test named
    `invalidScoreOnMissingTalk` that asserts **today's value, 400** for `{"score":6}`, with a
    comment pointing at E1. Phase 3 flips it to 404 (E1 approved).
11. **Filter and sort:** `GET /talks?speakerId=999999` gives `[]` (E5), and `sort=foo` gives 400 (E6).
12. **Concurrency, many reviewers:** on a fresh talk, send 40 parallel PUTs: reviewers 1 to 20,
    each sending its score twice at once (reviewers 1 to 10 score 3, 11 to 20 score 4). Every
    response is 200. Then `ratingCount == 20` and `averageRating == 3.5`.
13. **Concurrency, one reviewer** (review execution note): on a **fresh** talk, reviewer 1 sends 10
    parallel PUTs with scores 1, 2, 3, 4, 5, 1, 2, 3, 4, 5. Check that **every** response is 200,
    and that each response's `score` is the one that request sent. Then `ratingCount == 1` and
    `averageRating` is one of {1.0, 2.0, 3.0, 4.0, 5.0}, without assuming which racing request wins.

Keep the parallelism at no more than 40 in flight, because the pool is 5 connections. If any test
other than a documented E-policy fails against current code, it's a real bug: fix it in this phase
in the smallest way possible, and note it here and in `docs/known-issues.md`.

Gate: `./mvnw package` green, then check off and commit.

## Phase 3: E1, red then green (only if E1 is APPROVED)

If E1 isn't approved, tick this off as "skipped, E1 not approved; 400 kept" and move on.

1. Red: change `invalidScoreOnMissingTalk` to expect 404, and add the precedence combinations:

   | Reviewer | Talk | Body | Expected |
   |---|---|---|---|
   | `0` | missing | `{"score":6}` | 400 (reviewer first; unchanged) |
   | `1` | missing | `{"score":6}` | 404 |
   | `1` | missing | `{}` | 404 |
   | `1` | existing | `{"score":6}` | 400 |
   | `1` | missing | `not json` | 400 (binding; unchanged) |

   Run `./mvnw test -Dtest=RatingsTest`; only the new 404 expectations fail.
2. Green: in `ConferenceService.rate`, move the `talkDao.exists` check to between the reviewerId
   check and the score validation. Keep `@Transactional`. This is the only production change in the plan.
3. Gate: `./mvnw package` green, then check off and commit (the tests and the fix in one commit).

## Phase 4: Hurl examples

Add `examples/ratings-edge.hurl` (`run.sh` picks up all `examples/*.hurl`), with its own speaker
and talk setup as in `examples/ratings.hurl` (at most 3 talks per speaker):

- the 4, 4, 4, 5 average gives 4.3
- invalid scores (`6`, `"5"`, `4.5`) give 400; reviewer `0` gives 400
- a missing talk (id `2147483647`) with `{"score":3}` gives 404
- a missing talk with `{"score":6}` gives 404 (E1 approved)
- `sort=foo` gives 400

Run it against a local service: `docker compose up -d`, `./mvnw package`,
`java -jar target/cfp.jar &`, `examples/run.sh`, then stop the service.

## Phase 5: Docs

In `docs/known-issues.md`, record every ratings outcome from the "Docs and known issues" table:
- R6: kept, out of scope
- R7: kept (a filter gives `[]`)
- R8: fixed as a chosen policy (E1 approved by Daniel Bakkelund, 2026-11-06): a missing talk gives 404 before score validation; reviewerId and body-binding errors still give 400
- D2: guarded by a unit test
- D3: no action
- P1: deferred
- any real bug found in Phase 2

Keep the other docs in line with the code:
- `docs/api.md`, Ratings section: the check order (only if Phase 3 ran), and the bad-body
  statuses (400, and 415 for a non-JSON Content-Type).
- `docs/architecture.md`, "Rate a talk" flow: the check order (only if Phase 3 ran).
- `docs/operations.md`: add `RatingsTest` to the list of test classes.

Add an ADR in `adrs/` only if an outcome is architectural. Nothing here is expected to need one.

## Out of scope (separate plans)

- P1 (N+1 rating queries in lists). It may matter under load against the 5-connection pool, but it's
  a performance change. Revisit if the harness shows timeouts or 503s on `GET /talks`.
- R6 (the stats rounding), O1 (request logging).
