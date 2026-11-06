# Data model

Defined in `src/main/resources/schema.sql`, which runs on **every** start-up
(`spring.sql.init.mode=always`). Every statement must therefore be idempotent (`IF NOT EXISTS`), and
the file's own rule is: don't edit old statements, append new ones at the bottom. The database keeps
its data across deploys, so a changed `CREATE TABLE` won't alter an existing table: use
`ALTER TABLE … ADD COLUMN IF NOT EXISTS` instead.

## `speaker`

| Column | Type | Notes |
|---|---|---|
| `id` | `SERIAL` PK | |
| `name` | `TEXT NOT NULL` | |
| `email` | `TEXT NOT NULL` | Unique on `lower(email)` (`ux_speaker_email`) |
| `bio` | `TEXT` | |
| `created` | `TEXT` | `dd.MM.yyyy` in the JVM's default time zone. Not exposed. |

## `talk`

| Column | Type | Notes |
|---|---|---|
| `id` | `SERIAL` PK | |
| `title` | `TEXT NOT NULL` | |
| `abstract` | `TEXT` | Never written or exposed |
| `format` | `TEXT NOT NULL` | `LIGHTNING_10`, `LIGHTNING_20`, `PRESENTATION`, `WORKSHOP` |
| `duration` | `INTEGER` | Minutes, derived from `format`; exposed as `durationMinutes` |
| `level` | `TEXT` | `BEGINNER`, `INTERMEDIATE`, `ADVANCED` |
| `status` | `TEXT NOT NULL DEFAULT 'SUBMITTED'` | `SUBMITTED`, `ACCEPTED`, `REJECTED`, `WAITLISTED` or `WITHDRAWN`; changed only through the acceptance endpoints |
| `speaker_id` | `INTEGER NOT NULL` | Indexed (`ix_talk_speaker`). **No foreign key** to `speaker`. |
| `submitted_at` | `BIGINT` | Epoch millis, UTC. Exposed as `submittedAt` (ISO instant) and `submittedOn` (UTC date). |

## `rating`

| Column | Type | Notes |
|---|---|---|
| `talk_id` | `INTEGER NOT NULL` | PK part; no foreign key to `talk` |
| `reviewer_id` | `INTEGER NOT NULL` | PK part; reviewers aren't stored anywhere else |
| `score` | `INTEGER NOT NULL` | 1–5, checked in the service, not the database |
| `rated_at` | `TEXT` | `yyyy-MM-dd HH:mm:ss`, UTC |

Primary key `(talk_id, reviewer_id)`: one rating per reviewer per talk, enforced by the upsert in
`RatingDao.save`.

## `talk_co_speaker`

| Column | Type | Notes |
|---|---|---|
| `id` | `BIGSERIAL` | Primary key; gives the order the co-speakers were added in |
| `talk_id` | `INTEGER NOT NULL` | No foreign key to `talk` |
| `speaker_id` | `INTEGER NOT NULL` | No foreign key to `speaker` |

Unique on `(talk_id, speaker_id)`. The limit of 2 per talk is enforced in `ConferenceService`, under
a row lock on the talk.

## `workshop_registration`

| Column | Type | Notes |
|---|---|---|
| `id` | `BIGSERIAL` | Primary key; gives the order they registered in |
| `talk_id` | `INTEGER NOT NULL` | The workshop. No foreign key to `talk` |
| `name` | `TEXT NOT NULL` | As typed |
| `email` | `TEXT NOT NULL` | As typed |
| `email_key` | `TEXT NOT NULL` | Trimmed and lower-cased: what makes two emails the same person. At most 254 characters, enforced in `WorkshopService`, since a much longer value can't fit in the unique index |

Unique on `(talk_id, email_key)`. The limit of 20 per workshop is enforced in `WorkshopService`, under
a row lock on the talk.

## `programme_slot`

One row per slot, `S1` to `S5`, seeded by `schema.sql`. `slot TEXT` is the primary key. It holds no
data: it exists to be locked (`SELECT … FOR UPDATE`), so the three rooms of a slot are changed one
request at a time. The times are in code (`Venue`).

## `programme_entry`

| Column | Type | Notes |
|---|---|---|
| `room` | `TEXT NOT NULL` | `R1`, `R2` or `R3` (`CHECK`) |
| `slot` | `TEXT NOT NULL` | References `programme_slot` |
| `talk_id` | `INTEGER NOT NULL` | Unique: a talk is on the programme only once. No foreign key to `talk` |

Primary key `(room, slot)`: one talk per place. The other rules (accepted, the workshop slot, no
speaker in two rooms) are enforced in `ProgrammeService`, under the locks.

## `vote` (legacy)

`talk_id`, `voter`, `stars`. The 2019 voting system, replaced by `rating` in 2020. Unused by the
code; kept for old statistics.
