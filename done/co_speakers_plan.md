# Plan: Co-speakers (drip feature)

- [x] Phase 1: Schema, `coSpeakerIds` on every talk (`[]` for now), add and remove endpoints, `CoSpeakersTest`
- [x] Phase 2: Hurl example `examples/co-speakers.hurl`, run against a local service
- [x] Phase 3: Update `docs/` (api, data model, operations)

## Context (read this after a restart)

- Spec: `workflow/specs/co-speakers.md` (copy of the harness spec). Not claimed. A person claims on
  the harness, and a claim is permanent, so bring the evidence from this plan to that person.
- Branch: `co-speakers`. PR via the GitHub UI, never `gh`. Merging to `main` deploys.
- Commands: `./mvnw package`, `./mvnw test -Dtest=CoSpeakersTest`.
- Code is in `src/main/java/no/scienta/cfp/`. Test style: `TalksTest` (one database per test class,
  so never assert on absolute list contents).

## Decisions the spec leaves open (for the team to check)

| # | Situation | Decision | Why |
|---|---|---|---|
| D1 | `PUT` or `DELETE` on a talk that doesn't exist | 404 | Same as `GET /talks/{id}` |
| D2 | `PUT` or `DELETE` with a speaker who isn't registered | 404 | The id is in the path, like `GET /speakers/{id}/talks` |
| D3 | `PUT` with the talk's main speaker | 409 | Well-formed request, conflicts with the talk's state |
| D4 | `PUT` of a third co-speaker | 409 | Same as the 3-talk limit |
| D5 | `PUT` of someone who is already a co-speaker | 200, unchanged, keeps their place | The spec: "changes nothing". Also when the talk is full |
| D6 | `DELETE` of a registered speaker who isn't a co-speaker | 200, unchanged | Mirrors D5 |
| D7 | Removed and added again | Goes last | "in the order they were added" |
| D8 | Non-numeric id in the path | 400 | Spring's default, as on the other endpoints |
| D9 | Check order on `PUT` | talk, speaker, main speaker, already added, limit | Follows from D1 to D5 |
| D10 | `/admin/talks.csv` | Unchanged | Fixed columns for a spreadsheet, not in the spec |

`GET /speakers/{id}/talks`, `GET /talks?speakerId=` and the 3-talk limit keep looking only at
`talk.speaker_id`, so they need no change. The tests pin this.

## Design

- **Table** `talk_co_speaker (id BIGSERIAL PRIMARY KEY, talk_id INTEGER NOT NULL, speaker_id INTEGER
  NOT NULL, UNIQUE (talk_id, speaker_id))`, added at the bottom of `schema.sql` with `IF NOT EXISTS`.
  `id` gives the order. No foreign keys, like the other tables (known issue D3), and nothing deletes
  talks or speakers.
- **Read**: a correlated `ARRAY(SELECT … ORDER BY id)` in `TalkDao.COLUMNS`, so every path that reads
  a talk gets the field, with no extra query per talk (known issue P1 stays as it is).
- **Write**: `@Transactional` service methods that first lock the talk row with
  `SELECT id FROM talk WHERE id = ? FOR UPDATE`, so concurrent `PUT`s can't get past the limit.
  Advisory locks aren't an option on this database.
- **Trap**: lock and read in **two statements**. Under READ COMMITTED a single `SELECT <array
  subquery> … FOR UPDATE` that waited for the lock still uses the snapshot from before the wait, so
  it can see a stale list and break the limit. `concurrentAddsNeverExceedTwo` guards this.
- `ConferenceService`'s constructor stays unchanged, since `ConferenceServiceTest` builds it by hand.

## Phase 1

Red first: `CoSpeakersTest` covers the spec's rules and D1 to D7, plus concurrency on the limit.
Then schema, `TalkDto.coSpeakerIds`, `TalkDao`, `ConferenceService`, `TalkController`.

## Phase 2

`examples/co-speakers.hurl`, picked up by `examples/run.sh`. Hurl isn't installed on every machine:
the Docker image `ghcr.io/orange-opensource/hurl` works too.

## Phase 3

- `docs/api.md`: the field and both endpoints, with the decisions above
- `docs/data-model.md`: the new table
- `docs/operations.md`: add `CoSpeakersTest` to the list of test classes

Result: all of `examples/*.hurl` pass against the `Dockerfile` image (Java 11). On a local JDK 20+,
`ratings.hurl` fails on `ratedAtDisplay` (known issue D2), so check against the image, not a local jar.
