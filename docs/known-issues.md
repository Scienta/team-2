# Known issues

Found by reading the code on 2026-11-06. **These haven't been checked against the base specs yet.**
Some may be intended behaviour. Check the spec before changing anything, and record the decision
here. Ratings entries were checked on 2026-11-06 (`workflow/plans/done/ratings_plan.md`); see "Decisions" below.

## Security

| # | Issue | Where |
|---|---|---|
| S1 | **SQL injection**: `q` is concatenated into the `ILIKE` query, and anyone can reach it through `GET /speakers/search?q=` | `SpeakerDao.search`, `SpeakerDao.java:619` |
| S2 | `/admin/stats`, `/admin/talks.csv` and `PUT /speakers/{id}` have no authentication | `ReportController`, `SpeakerController` |
| S3 | Speakers' e-mail addresses are logged at INFO on registration | `ConferenceService.registerSpeaker` |

## Inconsistent rules

| # | Issue | Where |
|---|---|---|
| R1 | Talk limit: the code enforces **3**, but `Utils.MAX_TALKS = 5` (unused) and the old README both say 5 | `ConferenceService.submitTalk`, `checkSubmissionLimit` |
| R2 | Formats: the code has `LIGHTNING_10/20`, `PRESENTATION`, `WORKSHOP`; the old README says `LIGHTNING` and `KEYNOTE` | `ConferenceService.DURATIONS` |
| R3 | Durations are defined twice, in the `DURATIONS` map and in a `switch` with a 45-minute default | `ConferenceService.submitTalk` |
| R4 | Bio limit: 500 on create, 1000 on update | `SpeakerForm`, `ConferenceService.updateSpeaker` |
| R5 | E-mail format is checked on update but not on create | `SpeakerForm`, `Utils.isValidEmail` |
| R6 | Rounding: a talk's average rounds to 2 decimals half-even (`Utils.ratingAverage`, ratings change request), the stats average rounds down to 1 decimal (`Utils.roundRating`) | `ConferenceService` |
| R7 | Unknown speaker: `GET /talks?speakerId=` gives `[]`, `GET /speakers/{id}/talks` gives 404 | `ConferenceService.listTalks` |
| R8 | Ratings validate the body before checking the talk exists, so a bad score on a missing talk is 400, not 404 | `ConferenceService.rate` |
| R9 | `title` is trimmed for the blank check, but `name` on update isn't (`"  "` is accepted) | `ConferenceService` |

## Data and time

| # | Issue | Where |
|---|---|---|
| D1 | `speaker.created` uses the JVM's default time zone, unlike everything else (UTC) | `Utils.today` |
| D2 | `ratedAtDisplay` uses the JDK's US short format; on JDK 20+ the space before AM/PM becomes U+202F, which breaks the published Hurl regex. Prod runs JDK 25 since the Spring Boot 4 upgrade; guarded by `UtilsTest`. | `Utils.usShort` |
| D3 | No foreign keys from `talk.speaker_id` or `rating.talk_id`; integrity relies on the service | `schema.sql` |
| D4 | The CSV export fails with 404 if a talk's speaker is missing | `ReportController.talksCsv` |

## Performance

| # | Issue | Where |
|---|---|---|
| P1 | N+1 queries: one speaker query per talk in stats and CSV. Fixed for ratings: lists now use one query (`RatingDao.statsFor`) | `ConferenceService.statistics`, `ReportController` |

## Observability

| # | Issue |
|---|---|
| O1 | No request logging, so the harness's traffic is invisible in the container logs |

## Decisions

| # | Checked against | Decision |
|---|---|---|
| R6 | Ratings spec | Kept, out of scope. The spec's rounding rule is about a talk's average; `/admin/stats` is a mean of talk means and isn't in any base spec. The CSV uses the talk's own (half-up) value. |
| R7 | Ratings spec (silent) | Kept: `GET /talks?speakerId=<unknown>` gives `[]`, since it's a filter. Pinned by `RatingsTest.filterAndSort`. |
| R8 | Ratings spec (silent) | **Fixed** as a chosen policy (E1, approved by Daniel Bakkelund, 2026-11-06): a missing talk gives 404 before the score is validated. `reviewerId` and body-binding errors still give 400 first. Pinned by `RatingsTest.errorPrecedence`. |
| D2 | Ratings spec ("exactly one ordinary space") | **Fixed.** The bug was real on JDK 21 locally (U+202F before AM/PM); prod on JDK 11 was unaffected. `Utils.usShort` now uses the explicit pattern `M/d/yy, h:mm a`. Guarded by `UtilsTest.usShortUsesAnOrdinarySpaceBeforeAmPm`. |
| U1 | No base spec (`/admin/talks.csv` only) | Kept (spring-boot-4 plan, D8). `Utils.norwegianDate` gives the same output on JDK 25 as on JDK 11 (`4. nov. 2026`); pinned by `UtilsTest.norwegianDateIsMediumNbNoInOsloTime` (ADR 0002). |
| U2 | Speakers spec (`/speakers/` and `/speakers/{id}/` work like without the slash) | Spring 6 stopped matching a trailing slash. `WebConfig` strips it with `UrlHandlerFilter` (`wrapRequest`, not a redirect, so `POST` works) for every path, as Boot 2.7 did (spring-boot-4 plan, D5). Pinned by `WireFormatTest.trailingSlashesWorkLikeWithout`. |
| D3 | Ratings spec | No action for ratings: no endpoint deletes talks, so `rate`'s exists-check then upsert can't leave orphans. Revisit if a drip feature deletes talks. |
| P1 | Ratings spec | Out of scope for the ratings plan; the list part was fixed separately (PR #5, `RatingDao.statsFor`). |

Phase 2 of the ratings plan (`RatingsTest`, 14 tests including concurrency) found no other bugs.
