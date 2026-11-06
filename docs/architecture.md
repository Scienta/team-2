# Architecture

## Stack

- Java 11, Spring Boot 2.7.18 (`spring-boot-starter-web`, `-jdbc`, `-validation`, `-actuator`)
- PostgreSQL through plain `JdbcTemplate`; no ORM (the JPA starter is commented out in `pom.xml`)
- Tests: JUnit 5 with Testcontainers Postgres 1.21.4
- Packaged as `target/cfp.jar`, shipped in a two-stage `Dockerfile` (Maven build, Temurin 11 JRE)

## Layers

Everything lives in one package, `no.scienta.cfp` (`src/main/java/no/scienta/cfp/`).

```
HTTP ──> Controllers ──> ConferenceService ──> DAOs ──> Postgres
                              │
                              └──> NotificationService (logs only)
```

| Layer | Classes | Role |
|---|---|---|
| Controllers | `SpeakerController`, `TalkController`, `ReportController`, `HealthController` | Map routes to service calls. Little logic of their own. |
| Service | `ConferenceService` | All business rules: validation, the submission limit, durations, rating averages, sorting, statistics. Throws `ResponseStatusException` for 4xx. |
| DAOs | `SpeakerDao`, `TalkDao`, `RatingDao` | SQL via `JdbcTemplate`. Speakers come back as `Map<String, Object>`, talks as `TalkDto`. |
| DTOs / forms | `SpeakerForm`, `TalkForm`, `TalkDto` | Request and response bodies. `SpeakerForm` has bean-validation annotations; `TalkForm` is validated by hand in the service. |
| Support | `Utils`, `NotificationService`, `VersionInfoContributor` | Date formatting, CSV escaping, rounding; e-mail stub (logs only, `cfp.mail.enabled=false`); versions on `/actuator/info`. |

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

**Read a talk**: `TalkDao` loads the row, then `ConferenceService.addRatings` adds `ratingCount` and
`averageRating` from `RatingDao.stats`, or `RatingDao.statsFor` (one query for the whole list) for lists.

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
| `server.error.include-message` | `always` | The `ResponseStatusException` reason appears in error bodies |
| `spring.jackson.parser.allow-comments` | `true` | Request JSON may contain comments |
| `GIT_SHA` (env) | baked in by the `Dockerfile` | Reported as `version` by `/health` |
