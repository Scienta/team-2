# HTTP API

Documented from the code. The harness's base specs (speakers, talks, ratings) are authoritative;
see [known-issues.md](known-issues.md) for places where the code may not match them.

All bodies are JSON. Errors use Spring Boot's default error body, with the reason in `message`:

```json
{"timestamp": "…", "status": 400, "error": "Bad Request", "message": "title is required", "path": "/talks"}
```

## Speakers

### `POST /speakers` → 201

```json
{"name": "Ada Lovelace", "email": "ada@example.com", "bio": "Wrote the first program."}
```

| Rule | Error |
|---|---|
| `name`, `email` not blank | 400 (bean validation) |
| `bio` optional, at most 500 characters | 400 |
| `email` unique, case-insensitive | 409 `email already registered` |

Response: `{"id", "name", "email", "bio"}` (`bio` is `null` when omitted). Note that the e-mail
format is **not** checked here.

### `GET /speakers/{id}` → 200

`{"id", "name", "email", "bio"}`. 404 `speaker not found`. A trailing slash (`/speakers/1/`) works too.

### `PUT /speakers/{id}` → 200

Body `{"name", "email", "bio"}`, all three replaced. Undocumented ("admin UI"). Rules: 404 if the
speaker doesn't exist; `name` required (400); e-mail must contain `@` followed by a `.` (400); `bio`
at most 1000 characters (400); new e-mail taken by another speaker (409).

### `GET /speakers/{id}/talks` → 200

The speaker's talks ordered by `id`, each as a [talk](#talk-object). 404 if the speaker doesn't exist.

### `GET /speakers/search?q=…` → 200

Undocumented admin lookup: speakers whose name contains `q` (case-insensitive), ordered by name.
**Vulnerable to SQL injection**, see [known-issues.md](known-issues.md).

## Talks

### Talk object

```json
{
  "id": 7,
  "title": "Characterise first",
  "format": "WORKSHOP",
  "level": "BEGINNER",
  "speakerId": 3,
  "durationMinutes": 120,
  "status": "SUBMITTED",
  "submittedAt": "2026-11-06T12:34:56.789+00:00",
  "submittedOn": "2026-11-06",
  "averageRating": null,
  "ratingCount": 0,
  "coSpeakerIds": []
}
```

| Field | Meaning |
|---|---|
| `format` | `LIGHTNING_10` (10 min), `LIGHTNING_20` (20), `PRESENTATION` (45), `WORKSHOP` (120) |
| `level` | `BEGINNER`, `INTERMEDIATE`, `ADVANCED` |
| `durationMinutes` | Derived from `format`; clients can't set it |
| `status` | Always `SUBMITTED` today |
| `submittedAt` / `submittedOn` | Submission instant (UTC) and its UTC date |
| `averageRating` | Mean score to 1 decimal, rounded half up (4.25 → 4.3); `null` with no ratings |
| `ratingCount` | Number of reviewers who rated it |
| `coSpeakerIds` | Co-speakers' speaker ids in the order they were added; `[]` when none |

### `POST /talks` → 201

```json
{"title": "Characterise first", "format": "WORKSHOP", "level": "BEGINNER", "speakerId": 3}
```

Checks, in this order, each 400 on failure: `title` not blank; `format` valid; `level` valid;
`speakerId` present; speaker exists. Then 409 `a speaker can submit at most 3 talks` when the speaker
already has 3. Returns the talk object.

### `GET /talks/{id}` → 200

The talk object. 404 `talk not found`.

### `GET /talks?sort=rating&speakerId=…` → 200

All talks ordered by `id`, optionally filtered by `speakerId` (an unknown speaker gives `[]`, not
404). With `sort=rating`: rated talks first by `averageRating` descending, then `ratingCount`
descending, then `id` ascending; unrated talks last, by `id`. Any other `sort` value is 400.

## Co-speakers

### `PUT /talks/{id}/co-speakers/{speakerId}` → 200

No body. Returns the talk object. Checks, in this order: talk exists (404); speaker exists (404); not
the talk's main speaker (400); already a co-speaker gives 200 with nothing changed, also on a full
talk; fewer than 2 co-speakers (409). Co-speaking doesn't count towards the limit of 3 talks, and
talk lists by speaker show only the talks where they are the main speaker.

### `DELETE /talks/{id}/co-speakers/{speakerId}` → 200

Returns the talk object. Talk exists (404); speaker exists (404); is a co-speaker of this talk (404).
Someone removed and added again goes last.

## Ratings

### `PUT /talks/{id}/ratings/{reviewerId}` → 200

```json
{"score": 4}
```

Before the service runs, Spring rejects a missing, non-JSON or non-object body (400), a
non-numeric `reviewerId` (400) and a non-JSON `Content-Type` (415). Then, in this order:
`reviewerId` ≥ 1 (400); talk exists (404); `score` is a JSON integer (400, so `4.0` and `"4"` are
rejected); `score` in 1–5 (400). A reviewer rating the same talk again replaces their earlier score.

```json
{"talkId": 7, "reviewerId": 1, "score": 4, "ratedAtDisplay": "11/6/26, 1:05 PM"}
```

`ratedAtDisplay` is the rating time in UTC, as `M/d/yy, h:mm a` with an ordinary space before
AM/PM.

## Admin and operations

| Endpoint | Response |
|---|---|
| `GET /health` | 200 `{"status": "ok", "version": "<GIT_SHA>"}` if `SELECT 1` answers within 5 s, else 503 `{"status": "db-unavailable", …}` |
| `GET /admin/stats` | Counts of speakers, speakers with talks, talks, talks by format / level / status, `totalHours` (whole hours, truncated), `averageRating` (mean of talk averages, rounded **down** to 1 decimal), `generated` timestamp |
| `GET /admin/talks.csv` | Semicolon-separated export for the committee spreadsheet: Norwegian dates, decimal comma |
| `GET /actuator/health`, `GET /actuator/info` | Spring Actuator; `info` shows Java and Spring versions |

The `/admin` endpoints have no authentication.
