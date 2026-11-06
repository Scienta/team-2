# Plan: Ratings change request (trimmed average, 2 decimals half-even)

- [ ] Phase 1: Pure average function `Utils.ratingAverage` with unit tests
- [ ] Phase 2: Wire it into `RatingDao` and `ConferenceService`; update the old 1-decimal tests; new HTTP tests
- [ ] Phase 3: Hurl: fix the old 1-decimal examples, add `examples/ratings-cr.hurl`, prod-like local run
- [ ] Phase 4: Update `docs/` and the Ratings spec pointer, finish (plan to done; REVIEW.md rows in the PR description)

## Context (read this after a restart)

- Spec: `workflow/specs/ratings-cr.md` (decisions D1 to D9 at the end, agent defaults pending a
  person on issue #39). It changes the base spec `workflow/specs/ratings.md` (claimed from tick 0).
  Claimed? No. A person claims, a claim is permanent.
- Branch: `39-ratings-cr`, worktree `/Users/t995318/mycode/scienta2/team-2-wt/39-ratings-cr`. Draft PR
  only (`gh pr create --draft`), never merge. Merging to `main` deploys.
- Commands (JDK 25 and Colima settings from the main checkout's `LOCAL.md`):
  `JAVA_HOME=/opt/homebrew/opt/openjdk@25 DOCKER_HOST=unix:///Users/t995318/.colima/default/docker.sock TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock TESTCONTAINERS_RYUK_DISABLED=true ./mvnw package`,
  single class with `./mvnw test -Dtest=<Test>`. Baseline at `f3f2784`: green, 114 tests.
- The rule in one line: `n = ratingCount`; `n < 5`: `sum / n`; `n >= 5`: `(sum - max - min) / (n - 2)`;
  both rounded with `BigDecimal.divide(..., 2, RoundingMode.HALF_EVEN)` on the exact integers (D1, D2, D6).
- Code in `src/main/java/no/scienta/cfp/`:
  - `RatingDao.stats(talkId)` and `statsFor(ids)` return `{cnt, total}` maps (one query each; the
    list must stay one query, `ConferenceServiceTest.listingAllTalksFetchesRatingsInOneQuery`).
  - `ConferenceService.applyRatings` sets `ratingCount` and `averageRating` via `calculateAverage`
    (now `HALF_UP`, scale 1). `listTalks` sorts on `averageRating` desc, `ratingCount` desc, `id`
    asc, unrated last: unchanged code, it just sees the new average (D5).
  - `ConferenceService` stats (`/admin/stats`, `Utils.roundRating`, floor 1 decimal) and
    `ReportController` (`/admin/talks.csv`) stay as they are (D7, D8). Acceptance's
    `ratingCount < 2` stays (D9).
  - `TalkDto.averageRating` stays a `Double` (D4): `WireFormatTest` (`"averageRating":4.0`) must
    pass **unchanged**.
- Test style: `RatingsTest` (`@SpringBootTest(RANDOM_PORT)`, `TestDatabase.fresh`, helpers
  `newSpeaker`, `newTalk`, `rateAll`, `rate`, `getJson`, `idsInOrder`). One database per test class,
  so never assert absolute list positions without filtering to the test's own talks
  (`idsInOrder(..., ours)` or `speakerId=`). Unit tests for `Utils` in `UtilsTest`.
- Existing assertions the change breaks (all were 1 decimal half-up):
  - `RatingsTest.assertAggregates`: scores 4, 4, 5 now `4.33` (was 4.3).
  - `RatingsTest.averageRoundsHalfUp`: 4, 4, 4, 5 now `4.25`; rename to `averageHasTwoDecimals`.
  - `RatingsTest.ranking`: a = 4,4,4,5 (4.25), b = 5,4,4 (4.33), c = 4,5,4,4 (4.25), e = 5, d unrated:
    order becomes `e, b, a, c, d` (was `e, a, c, b, d`); speaker s1 `b, a, c`.
  - `TalkListRatingsTest.listsShowEachTalksOwnRatingsAndRankThem`: `"4.3"` becomes `"4.25"`.
  - `examples/ratings-rules.hurl` ~line 278: three_reviewers (4,4,5 = 4.33) now ranks **before**
    four_reviewers (4,4,4,5 = 4.25); and every other `averageRating == 4.3` there.
  - `examples/ratings-edge.hurl` ~line 45: `4.3` becomes `4.25`.
  - `grep -n "averageRating" examples/*.hurl src/test/java -r` after the change: every hit re-checked.

## Decisions for the human to check

All in `workflow/specs/ratings-cr.md`; each took the recommended option as an agent default.

- **D1.** Rounding: exact `BigDecimal` HALF_EVEN, scale 2. **Ask the PO** (visible).
- **D2.** Ties at the top/bottom: leave out exactly one max and one min. **Ask the PO** (visible).
- **D3.** Threshold counts current ratings (one per reviewer). **Ask the PO** (visible).
- **D4.** JSON stays a `Double` number (`4.0`, `4.25`), no `4.00` padding. **Ask the PO** (visible; fallback: scaled `BigDecimal`).
- **D5.** Ranking compares the rounded average, then count, then oldest. **Ask the PO** (visible).
- **D6.** 2-decimal rounding also below 5 ratings. **Ask the PO** (visible).
- **D7.** `/admin/stats` unchanged rule (floor 1 decimal of the new averages).
- **D8.** `/admin/talks.csv` prints the new average (`4,25`).
- **D9.** Acceptance's 2-rating minimum unchanged.

## Phase 1: Pure average function

1. Add to `Utils`: `static Double ratingAverage(long total, int count, int max, int min)`: `null`
   when `count == 0`; for `count >= 5` use `total - max - min` over `count - 2`; divide with
   `BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_EVEN).doubleValue()`.
   The threshold `5` as a named constant (`TRIM_FROM = 5`).
2. `UtilsTest` cases (scores listed, compute total/max/min in a small test helper):
   - 0 ratings: `null`. 1 rating `5`: `5.0`.
   - below 5, untrimmed: `4,4,4,5` gives `4.25`; `4,4,5` gives `4.33`; `1,2,2,2` (4 ratings, the max 2
     and min 1 still count) gives `1.75`.
   - exactly 5, trimmed: `1,5,5,5,5` gives `5.0` (one 5 and the 1 out); `1,1,1,1,5` gives `1.0`;
     `3,3,3,3,3` gives `3.0`; `1,2,3,4,5` gives `3.0`; `1,4,4,4,4` gives `4.0` vs untrimmed 3.4.
   - half-even ties (10 ratings, 8 counted): `1,3,3,3,3,3,3,3,4,5` (counted sum 25, `3.125`) gives
     `3.12`; `1,3,3,3,3,3,3,4,5,5` (counted sum 27, `3.375`) gives `3.38`.
   - non-tie rounding at 6 ratings: `1,2,2,3,3,5` (counted 2,2,3,3 = 2.5) gives `2.5`;
     `1,2,2,2,3,5` (counted 9/4 = 2.25) gives `2.25`; at 7 ratings `1,4,4,4,4,5,5` (21/5 = 4.2) gives `4.2`.

Check: `./mvnw test -Dtest=UtilsTest` green, then `./mvnw package` green (the function is unused
yet, coverage of `Utils` goes up). Commit: `feat: Trimmed, half-even rating average`.

## Phase 2: Wire it in

1. `RatingDao.stats` and `statsFor`: add `MAX(score) AS hi, MIN(score) AS lo` (still one query each;
   `COALESCE(..., 0)` in `stats` for a talk without ratings). Update their javadoc (`{cnt, total, hi, lo}`).
2. `ConferenceService.applyRatings`: read `hi`/`lo` and call `Utils.ratingAverage`; delete
   `calculateAverage` and its commented-out line. Leave `listTalks`, stats and the CSV alone.
   Keep `RoundingMode`/`BigDecimal` imports only if still used (no unused-import warnings).
3. Fix the broken assertions listed in Context (RatingsTest x3, TalkListRatingsTest).
4. New HTTP tests in a new class `RatingsTrimTest` (copy `RatingsTest`'s setup and helpers; its own
   database), each on its own talks:
   - `fiveRatingsLeaveOutOneHighestAndOneLowest`: `1,5,5,5,5` shows `5.0`, `ratingCount` 5, on
     `GET /talks/{id}`, `GET /talks`, `GET /talks?speakerId=`, `GET /speakers/{id}/talks`.
   - `fourRatingsCountEveryScore`: `1,4,4,4` shows `3.25`, count 4; a fifth rating `4` then shows
     `4.0`, count 5.
   - `reRatingDoesNotReachTheThreshold`: 4 reviewers, one re-rates twice: count stays 4, untrimmed (D3).
   - `halfEvenAtTheThirdDecimal`: the two 10-rating sets from Phase 1 show `3.12` and `3.38`.
   - `rankingUsesTheTrimmedAverage`: talk X `1,5,5,5,5` (trimmed 5.0, untrimmed 4.2) and talk Y
     `4,4,4,4,5` (trimmed 4.0, untrimmed 4.2) and talk Z `5,5,5,5` (5.0, 4 ratings): `sort=rating`
     on their speaker gives `X, Z, Y` (X and Z tie on 5.0, X has more ratings) (D5).
   - `sameAverageRanksMoreRatingsFirst`: talk P `4,4,5` (3 ratings, 4.33) and talk Q `1,4,4,5,5`
     (5 ratings, trimmed to `4,4,5`, 4.33; untrimmed it would be 3.8 and rank last): `sort=rating` on
     their speaker gives `Q, P` (D5 tie-break on `ratingCount`).
5. Run `WireFormatTest` unchanged.

Check: `./mvnw package` green, no new warnings, coverage not below the floor;
`git diff origin/main -- src/test/java/no/scienta/cfp/WireFormatTest.java` empty.
Commit: `feat: Trimmed 2-decimal average on talks`.

## Phase 3: Hurl and prod-like run

1. Fix `examples/ratings-rules.hurl` (the 4.25/4.33 ranking flip and every 1-decimal value),
   `examples/ratings-edge.hurl` (`4.25`), and the comment in `examples/ratings.hurl` ("rounded to 1
   decimal"). Re-run the grep from Context.
2. New `examples/ratings-cr.hurl`: one speaker, talks for the Phase 2 cases (5-rating trim, 4-rating
   no trim, half-even `3.12`/`3.38`, ranking `X, Z, Y`), each asserting `averageRating` and
   `ratingCount`. Unique names/emails per run (as the other examples do).
3. Prod-like local run: `APP_PORT=18039 PG_PORT=15039 COMPOSE_PROJECT_NAME=cfp-39
   workflow/scripts/local-rollout.sh`, then `docker compose -p cfp-39 --profile app down -v`.

Check: `local-rollout.sh` reports `/health` with the commit SHA and every `examples/*.hurl` green
(including `acceptance-rules.hurl` and `co-speakers-rules.hurl`, which use `sort=rating`).
Commit: `test: Hurl examples for the ratings change request`.

## Phase 4: Docs and finish

1. `docs/api.md`: the `averageRating` row (trimmed at 5+, 2 decimals half-even) and the
   `sort=rating` paragraph; `docs/architecture.md` line on `RatingDao.stats` (`{cnt, total, hi, lo}`).
2. `workflow/specs/ratings.md`: one line under the title pointing to `ratings-cr.md` for the changed
   average (don't edit the verbatim text).
3. `git mv` this plan (and its review) to `workflow/plans/done/`.
4. `REVIEW.md` is not edited on this branch: the §1/§2/§3-draft/§5 rows go in the PR description
   under "For REVIEW.md".

Check: `./mvnw package` green. Commit: `docs: Ratings change request`.

## Review response

| Finding | Response |
|---|---|
