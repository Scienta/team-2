# Known issues

Found by reading the code on 2026-11-06. **These haven't been checked against the base specs yet.**
Some may be intended behaviour. Check the spec before changing anything, and record the decision
here.

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
| R6 | Rounding: a talk's average rounds half up (`calculateAverage`), the stats average rounds down (`Utils.roundRating`) | `ConferenceService` |
| R7 | Unknown speaker: `GET /talks?speakerId=` gives `[]`, `GET /speakers/{id}/talks` gives 404 | `ConferenceService.listTalks` |
| R8 | Ratings validate the body before checking the talk exists, so a bad score on a missing talk is 400, not 404 | `ConferenceService.rate` |
| R9 | `title` is trimmed for the blank check, but `name` on update isn't (`"  "` is accepted) | `ConferenceService` |

## Data and time

| # | Issue | Where |
|---|---|---|
| D1 | `speaker.created` uses the JVM's default time zone, unlike everything else (UTC) | `Utils.today` |
| D2 | `ratedAtDisplay` uses the JDK's US short format; on JDK 20+ the space before AM/PM becomes U+202F, which breaks the published Hurl regex. Watch this if Java is upgraded. | `Utils.usShort` |
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
