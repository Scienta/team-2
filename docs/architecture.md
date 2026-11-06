# Architecture

## Stack

- Java 25, Spring Boot 4.1.2 / Spring Framework 7.0 (`spring-boot-starter-webmvc`, `-jdbc`, `-validation`,
  `-actuator`); Jackson 3 with `spring.jackson.use-jackson2-defaults=true`. Upgraded from Java 11 /
  Boot 2.7 in ADR [0002](../workflow/adrs/0002-spring-boot-4-java-25.md)
- PostgreSQL through plain `JdbcTemplate`; no ORM (the JPA starter is commented out in `pom.xml`)
- Tests: JUnit 5 with Testcontainers 2 Postgres (`testcontainers-postgresql`, version from the Boot BOM);
  Mockito loaded as a `-javaagent` by surefire. Maven wrapper 3.9.16
- Packaged as `target/cfp.jar`, shipped in a two-stage `Dockerfile` (`maven:3.9-eclipse-temurin-25` build,
  `eclipse-temurin:25-jre` runtime)

## Layers

Everything lives in one package, `no.scienta.cfp` (`src/main/java/no/scienta/cfp/`).

```
HTTP ──> Controllers ──> ConferenceService ──> DAOs ──> Postgres
                              │
                              └──> NotificationService (logs only)
```

| Layer | Classes | Role |
|---|---|---|
| Controllers | `SpeakerController`, `TalkController`, `WorkshopController`, `ProgrammeController`, `ReportController`, `HealthController` | Map routes to service calls. Little logic of their own. |
| Service | `ConferenceService`, `WorkshopService`, `ProgrammeService` | All business rules: validation, the submission limit, durations, rating averages, sorting, statistics; workshop registration and programme scheduling in their own services. The scheduling rules themselves are a pure function, `SchedulingRules`, with the venue in `Venue`. Throws `ResponseStatusException` for 4xx. |
| DAOs | `SpeakerDao`, `TalkDao`, `RatingDao`, `RegistrationDao`, `ProgrammeDao` | SQL via `JdbcTemplate`. Speakers come back as `Map<String, Object>`, talks as `TalkDto`. |
| DTOs / forms | `SpeakerForm`, `TalkForm`, `TalkDto`, `RegistrationDto`, `ProgrammeEntry` | Request and response bodies. `SpeakerForm` has bean-validation annotations; `TalkForm` is validated by hand in the service. |
| Support | `Utils`, `NotificationService`, `VersionInfoContributor` | Date formatting, CSV escaping, rounding; e-mail stub (logs only, `cfp.mail.enabled=false`); versions on `/actuator/info`. |
| Web config | `WebConfig`, `FieldErrorMixin` | `UrlHandlerFilter` strips a trailing slash before routing (the speakers spec requires `/speakers/` to work; Spring 6 stopped matching it). A second filter forces `charset=UTF-8` on error bodies too (Tomcat 11 drops it on the `/error` forward). The mixin keeps Boot 2.7's key order in the entries of a 400's `errors` array. |

`HealthController` deliberately bypasses the service: it runs `SELECT 1` with a 5 s query timeout on
a single-thread executor, so concurrent `/health` calls share one probe and a hung database can't
exhaust threads or the 5-connection pool.

## Request flows

**Submit a talk** (`POST /talks`):

1. `ConferenceService.submitTalk` validates title, format, level and `speakerId`, and checks that the
   speaker exists (400 otherwise).
2. The duration is derived from the format (the client can't choose it).
3. `TalkDao.insertIfBelow` locks the speaker row (`SELECT … FOR UPDATE`) and inserts the talk only
   if the speaker has fewer than 3 talks, in one conditional `INSERT … SELECT … WHERE`. No row back
   means 409.
4. A "thank you" notification is logged, and the talk is re-read with its rating stats.

**Rate a talk** (`PUT /talks/{id}/ratings/{reviewerId}`): validates `reviewerId`, checks the talk
exists (404), validates `score`, then upserts with `INSERT … ON CONFLICT (talk_id, reviewer_id) DO UPDATE`, so a
reviewer has at most one rating per talk even under concurrent requests.

**Decide on or withdraw a talk** (`PUT /talks/{id}/status`, `POST /talks/{id}/withdraw`): in one
transaction, `TalkDao.lock` takes the talk's row lock (`SELECT … FOR UPDATE`, 404 if none), then
the talk is re-read in a separate statement, checked against the state machine
(`ConferenceService.allowed`) and written with `TalkDao.updateStatus`. Concurrent decisions on one
talk wait for each other, so a final status is never overwritten (`AcceptanceTest.concurrent*`).

**Register for a workshop** (`POST /workshops/{id}/registrations`): in one transaction,
`WorkshopService` checks the talk is a workshop and validates the body before taking any lock. It then
takes the same row lock with `TalkDao.lock`, re-reads the status in a separate statement, counts the
registrations, and inserts with `ON CONFLICT (talk_id, email_key) DO NOTHING`. Registrations and
withdrawals on one workshop wait for each other, so a rush never gets past 20 and nobody gets in after
a withdrawal (`WorkshopRegistrationTest.aRush*`, `aRegistrationWaitingForTheLockSeesTheWithdrawal`).

**Put a talk on the programme** (`PUT /programme/{room}/{slot}`): in one transaction,
`ProgrammeService` parses the room and slot and validates `talkId`, then locks the talk row
(`TalkDao.lock`) and then the slot row (`ProgrammeDao.lockSlot`), always in that order. Under the locks
it reads the talk, where the talk is now, whether the place is taken and the talks in the slot's other
rooms, and `SchedulingRules.decide` returns Ok (insert), AlreadyHere (200, no change) or a Violation
(409). The slot lock makes the three rooms of a slot wait for each other (taken, speaker clash); the
talk lock makes the same talk going into two slots wait. `DELETE` locks only the slot. The primary key
and `UNIQUE (talk_id)` are a backstop: a broken lock shows as a 500 (`ProgrammeTest.concurrent*`).

**Read a talk**: `TalkDao` loads the row, then `ConferenceService.addRatings` adds `ratingCount` and
`averageRating` from `RatingDao.stats`, or `RatingDao.statsFor` (one query for the whole list) for lists.
Both return count, sum, highest and lowest score; `Utils.ratingAverage` trims (5+ ratings) and rounds
(2 decimals, half-even).

## Concurrency and platform constraints

- Connection pool capped at 5 (`spring.datasource.hikari.maximum-pool-size`).
- Advisory locks aren't available on the hosted database; the code uses row locks and conditional
  writes instead, as above.
- One instance, may scale to zero; beyond 80 concurrent requests the platform answers 503.

## Configuration

`src/main/resources/application.properties`:

| Property | Value | Note |
|---|---|---|
| `server.port` | 8080 | Deploy contract |
| `spring.datasource.url` | built from `PGHOST`, `PGPORT`, `PGDATABASE`, `PGSSLMODE` | Defaults to local Compose Postgres, `sslmode=disable` |
| `spring.datasource.username` / `password` | `PGUSER` / `PGPASSWORD` | Default `cfp` / `cfp` |
| `spring.sql.init.mode` | `always` | `schema.sql` runs on every start-up |
| `spring.web.error.include-message` | `always` | The `ResponseStatusException` reason appears in error bodies (was `server.error.include-message`) |
| `spring.web.error.include-binding-errors` | `always` | Bean-validation 400s carry an `errors` array (was `server.error.include-binding-errors`) |
| `spring.servlet.encoding.force` | `true` | JSON answers are `application/json;charset=UTF-8` (was `server.servlet.encoding.force`) |
| `spring.jackson.json.read.allow-java-comments` | `true` | Request JSON may contain comments (was `spring.jackson.parser.allow-comments`) |
| `spring.jackson.use-jackson2-defaults` | `true` | Jackson 3 keeps Jackson 2's defaults: key order, dates, lenient parsing |
| `GIT_SHA` (env) | baked in by the `Dockerfile` | Reported as `version` by `/health` |

Boot 4 renamed four of these. It ignores the old names silently, so a missed rename only shows up
as changed behaviour; `WireFormatTest` covers each one.
