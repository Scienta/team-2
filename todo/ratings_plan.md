# Plan: Ratings, check the base app against the spec and close the gaps

- [ ] Phase 0: Check the harness feedback for the `ratings` checks and revise the decisions below
- [ ] Phase 1: Unit tests for the rating display format and rounding
- [ ] Phase 2: Integration tests for ratings (`RatingsTest`)
- [ ] Phase 3: Fix the agreed edge cases (error precedence) and anything Phase 2 exposed
- [ ] Phase 4: Hurl examples for the edge cases
- [ ] Phase 5: Record the decisions in `docs/known-issues.md`

## Context (read this after a restart)

- Spec: `workflow/specs/ratings.md` (a copy of the harness base spec; the Talk schema is in
  `workflow/specs/talks.md`). Ratings is a **base feature**: it's already implemented and claimed
  from tick 0. This plan is about verification and closing gaps, not building something new.
- There are **no tests for ratings today**, only the happy-path Hurl in `examples/ratings.hurl`.
- Code, all in `src/main/java/no/scienta/cfp/`:
  - `TalkController`: `PUT /talks/{id}/ratings/{reviewerId}` (body bound as `Map<String, Object>`),
    `GET /talks?sort=&speakerId=`, `GET /talks/{id}`
  - `ConferenceService.rate` (validation, then `talkDao.exists`, then save), `listTalks` (ranking),
    `addRatings` / `calculateAverage` (count and the half-up average to 1 decimal)
  - `RatingDao.save`: one `INSERT … ON CONFLICT (talk_id, reviewer_id) DO UPDATE`, so double
    clicks and concurrent raters can't create duplicates
  - `Utils.now` / `Utils.parse` (UTC, `yyyy-MM-dd HH:mm:ss`), `Utils.usShort` (JDK
    `DateFormat.SHORT`, `Locale.US`, UTC)
  - `TalkDao.findAll` / `findBySpeaker` order by `id` (this is "oldest first")
- Schema: `src/main/resources/schema.sql`, table `rating` with PK `(talk_id, reviewer_id)`.
- Test style: copy `src/test/java/no/scienta/cfp/TalksTest.java` (`@SpringBootTest(RANDOM_PORT)`,
  `TestDatabase.fresh(registry)`, `java.net.http.HttpClient`, regex on the JSON body). Code
  conventions are in `skills/PROJECT.md`.
- Commands: `./mvnw package` (needs Docker), `./mvnw test -Dtest=RatingsTest`,
  `examples/run.sh http://localhost:8080` (service from `docker compose up -d` + `java -jar target/cfp.jar`).

## What already matches the spec (verify it with tests; don't change it)

| Spec rule | Implementation |
|---|---|
| Score is an integer from 1 to 5 | `score instanceof Integer` and range check, so `5.0`, `"5"`, `0`, `6` and a missing score all give 400 |
| Reviewer is a positive whole number | `reviewerId < 1` gives 400; a non-numeric path gives Spring's 400 |
| One rating per reviewer per talk, and re-rating replaces it | upsert on the PK |
| No lost ratings, no double counting | single-statement upsert, with the count and sum read from the table |
| Average to 1 decimal, half up (4,4,4,5 gives 4.3) | `BigDecimal` division with `HALF_UP` on total/count |
| Unrated talk: no average | `averageRating` is `null`, `ratingCount` is 0 |
| Ranking: rounded average desc, then count desc, then oldest first, unrated last | `listTalks` comparator on the rounded `averageRating` |
| `ratedAtDisplay` as `M/d/yy, h:mm AM`, UTC | `Utils.usShort` (CLDR on Java 11 gives the comma and an ordinary space; see D2) |

## Decisions the spec leaves open (human: please confirm or change)

| # | Case | Today | Proposal |
|---|---|---|---|
| E1 | PUT on a missing talk with an invalid body | 400 (body checked first, R8) | **404**: check that the talk exists before validating the body |
| E2 | PUT on a missing talk with a valid body | 404 | keep |
| E3 | `reviewerId` ≤ 0, or non-numeric | 400 | keep, and check it before the talk lookup (the path is malformed whatever the talk) |
| E4 | Missing body, non-JSON body, or a JSON array | 400 (Spring) | keep |
| E5 | `GET /talks?speakerId=<unknown>` | `[]` (R7) | keep `[]`: it's a filter, and the spec says nothing about a 404 |
| E6 | `GET /talks?sort=<other>` | 400 | keep |
| E7 | Response code on re-rating (replace) | 200 | keep 200, as in the spec (no 201 on the first rating either) |

The resulting order in `rate`: reviewerId check (400), then talk exists (404), then body validation (400).

## Phase 0: Harness feedback

Needs `.fagdag/harness-token` (a person pastes it from the harness `/feedback` page; it was missing
when this plan was written). Fetch `$H/api/teams/$TEAM/feedback` (see "Harness API" in `AGENTS.md`)
and look at the `ratings/*` checks. For each FAULTY check, fetch its timeline/transcript and note
here the request and our answer. If a check contradicts a proposal in the table above, change the
proposal and tell the human. If the token is still missing, tick this off as "skipped, no token" and
carry on.

## Phase 1: Unit tests (`UtilsTest`, plus a new `ConferenceService` average test if needed)

- `Utils.usShort` with fixed UTC instants:
  - `2026-11-04T13:05:00Z` gives `11/4/26, 1:05 PM`
  - `2026-11-04T00:00:00Z` gives `11/4/26, 12:00 AM`
  - `2026-11-04T12:00:00Z` gives `11/4/26, 12:00 PM`
  - `2026-01-09T09:07:00Z` gives `1/9/26, 9:07 AM`
  - assert that the separator before AM/PM is U+0020 (guards D2)
- The rounding goes through HTTP in Phase 2, since `calculateAverage` is private. Don't widen its visibility.
- Leave the existing `roundsRatingToOneDecimal` test alone: it tests `Utils.roundRating`, which
  only the stats use (R6, out of scope).

## Phase 2: Integration tests, `src/test/java/no/scienta/cfp/RatingsTest.java`

Helpers as in `TalksTest`: `newSpeaker()`, `newTalk(speakerId)`, `rate(talk, reviewer, json)`
returning `HttpResponse<String>`. Write one test per bullet:

1. Happy path: 200, and the body has `talkId`, `reviewerId`, `score` and `ratedAtDisplay`
   matching `^\d{1,2}/\d{1,2}/\d{2}, \d{1,2}:\d{2} [AP]M$`.
2. Re-rating replaces: rate 2, then 5, by the same reviewer gives `ratingCount` 1 and `averageRating` 5.0.
3. Rounding: reviewers 1 to 4 give 4, 4, 4, 5, so `GET /talks/{id}` gives `averageRating` 4.3 and `ratingCount` 4.
4. Unrated talk: `"averageRating":null` and `"ratingCount":0`, both on `GET /talks/{id}` and in
   `POST /talks`'s response ("wherever a talk is shown"). Check `GET /speakers/{id}/talks` too.
5. Invalid scores give 400: `0`, `6`, `4.5`, `5.0`, `"5"`, `null`, `{}`, `true`.
6. Invalid reviewer gives 400: `0`, `-1`, `abc`.
7. Missing talk gives 404 with a valid body, **and with an invalid body (E1)**. This one fails until Phase 3.
8. Ranking, one speaker, talks A to E created in order:
   - A: 4, 4, 4, 5 (4.3, n=4)
   - B: 5, 4, 4 (4.3, n=3)
   - C: 4, 5, 4, 4 (4.3, n=4, created after A)
   - D: unrated
   - E: 5 (5.0, n=1)

   `GET /talks?sort=rating&speakerId=…` gives the ids in the order E, A, C, B, D. The plain list
   `GET /talks?speakerId=…` gives A to E in creation order.
9. Filter: `GET /talks?speakerId=<other speaker>` doesn't include this speaker's talks, and an
   unknown speakerId gives `[]` (E5). `sort=foo` gives 400 (E6).
10. Concurrency: 40 parallel PUTs on one talk (20 distinct reviewers, each sending the same score
    twice at once; reviewers 1–10 score 3, 11–20 score 4) all give 200, then `ratingCount` is 20
    and `averageRating` is 3.5.
    Also 10 parallel PUTs from one reviewer with different scores give `ratingCount` 1. Keep the
    parallelism moderate: the pool is 5 connections.

Run `./mvnw test -Dtest=RatingsTest`. Everything except 7 (invalid body) is expected to pass. If
anything else fails, write it down here and fix it in Phase 3.

## Phase 3: Fixes

- `ConferenceService.rate`: reorder as described under the decisions (reviewerId, then
  `talkDao.exists`, then score). Keep `@Transactional`. This is the only planned production change.
- Fix any other failure from Phase 2 in the smallest way possible.
- `./mvnw package`: all green, no warnings.

## Phase 4: Hurl examples

Add `examples/ratings-edge.hurl` (`run.sh` picks up all `examples/*.hurl`), with its own speaker
and talk setup as in `examples/ratings.hurl`:

- the 4, 4, 4, 5 average gives 4.3
- invalid score (`6`, `"5"`, `4.5`) gives 400; reviewer `0` gives 400
- missing talk (e.g. id `2147483647`) gives 404, both with a valid and an invalid body
- `sort=foo` gives 400

Run it against a local service: `docker compose up -d`, `./mvnw package`,
`java -jar target/cfp.jar &`, `examples/run.sh`, then stop the service.

## Phase 5: Docs

In `docs/known-issues.md`, record the decisions next to R7 (kept), R8 (fixed: 404 before 400) and D2
(guarded by a unit test). Add an ADR in `adrs/` only if the human changed a proposal into something
architectural. Nothing here is expected to need one.

## Out of scope (separate plans)

- P1 (N+1 rating queries in lists). It may matter under load against the 5-connection pool, but it's
  a performance change. Revisit if the harness shows timeouts or 503s on `GET /talks`.
- R6 (the stats rounding), O1 (request logging).
