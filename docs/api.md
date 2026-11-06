# HTTP API

Documented from the code. The harness's base specs (speakers, talks, ratings) are authoritative;
see [known-issues.md](known-issues.md) for places where the code may not match them.

All bodies are JSON. Errors use Spring Boot's default error body, with the reason in `message`:

```json
{"timestamp": "…", "status": 400, "error": "Bad Request", "message": "title is required", "path": "/talks"}
```

Status codes, keys and our own reasons (like `title is required`) are stable. Messages that Spring or
Jackson generate (bad JSON, a non-numeric id, 405, 415, unknown route, the bean-validation summary)
changed wording with the Spring Boot 4 upgrade and may change again with the framework. A bean-validation 400 also
has an `errors` array between `message` and `path`.

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
| `status` | `SUBMITTED` when new; then `ACCEPTED`, `REJECTED`, `WAITLISTED` or `WITHDRAWN` (see Acceptance) |
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

## Acceptance

A talk's `status` follows a state machine (`workflow/specs/acceptance.md`). Anything not listed,
including repeating the current status, is 409 and changes nothing:

| From | Allowed to |
|---|---|
| `SUBMITTED` | `ACCEPTED` (needs 2+ reviewers), `REJECTED`, `WAITLISTED`, `WITHDRAWN` |
| `WAITLISTED` | `ACCEPTED` (needs 2+ reviewers), `REJECTED`, `WITHDRAWN` |
| `ACCEPTED` | `WITHDRAWN` |
| `REJECTED`, `WITHDRAWN` | nothing |

Talks of every status stay in all lists.

### `PUT /talks/{id}/status` → 200

```json
{"status": "ACCEPTED"}
```

Checks, in this order: talk exists (404); `status` is `ACCEPTED`, `REJECTED` or `WAITLISTED` (400);
the change is allowed (409); `ACCEPTED` needs ratings from at least 2 reviewers (409). Returns the
talk object.

### `POST /talks/{id}/withdraw` → 200

```json
{"speakerId": 3}
```

Checks, in this order: talk exists (404); `speakerId` is a whole number ≥ 1 (400); it's the talk's
main speaker, not a co-speaker or anyone else (403); the talk isn't `REJECTED` or `WITHDRAWN` (409).
Returns the talk object with `status` `WITHDRAWN`.

Both endpoints: no body, invalid JSON or a JSON array is 400, a non-JSON `Content-Type` 415.

## Workshop registration

`{id}` is the workshop's talk id. Each workshop has room for 20 attendees. An attendee doesn't have to
be a registered speaker. There's no way to remove a registration, so a seat stays taken.

### `POST /workshops/{id}/registrations` → 201

```json
{"name": "Ada Lovelace", "email": "ada@example.com"}
```

Returns `id`, `workshopId`, `name` and `email`, with `name` and `email` as typed. Checks, in this
order: the talk exists and is a `WORKSHOP` (404); `name` and `email` are strings with text in them and
no control characters (400); `email` has one `@`, no whitespace inside, and a dot with text on both
sides after the `@`, and at most 254 characters after trimming (400); the talk is `ACCEPTED` (409); the workshop has fewer than 20 registrations
(409); the email isn't registered for this workshop (409), ignoring letter case and surrounding
whitespace.

No body, invalid JSON or a JSON array is 400, a non-JSON `Content-Type` 415.

### `GET /workshops/{id}/registrations` → 200

The registrations in the order they registered, `[]` when none. A workshop that was withdrawn keeps
its list. A talk that doesn't exist or isn't a `WORKSHOP` is 404.

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
