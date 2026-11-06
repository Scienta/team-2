# Programme scheduling

Version: 1
Kind: drip feature (released at tick 100)
V: 3
Source: `workflow/scripts/harness.sh spec scheduling`, copied verbatim below the line, then our
decisions at the end.

## Context

As the programme committee, I want to put accepted talks into rooms and time slots, so that the conference gets a programme where every talk fits its slot and nobody has to be in two rooms at once.

## What we want

- The venue is fixed: three rooms, `R1`, `R2` and `R3`, and the same five slots in every room. Nobody can add or change rooms or slots.

  | Slot | Start | End |
  |---|---|---|
  | `S1` | 09:00 | 10:00 |
  | `S2` | 10:15 | 11:15 |
  | `S3` | 11:30 | 12:30 |
  | `S4` | 13:30 | 15:30 |
  | `S5` | 15:45 | 16:45 |

  Times are written as on the signs, `HH:mm` on a 24-hour clock (`09:00`, not `9:00`).
- The committee puts a talk in a room and slot, looks up what's in it, and takes the talk out again. A talk that's been taken out can go somewhere else.
- Only accepted talks ([Acceptance state machine](../acceptance/spec.md)) go on the programme.
- A talk must fit its slot: a workshop needs the long slot, everything else fits anywhere.
- One talk per room and slot, and a talk is on the programme only once. To move a talk, take it out first.
- Nobody speaks in two rooms at the same time: the main speaker and the co-speakers ([Co-speakers](../co-speakers/spec.md)) of talks in the same slot must all be different people.
- We check the rules when a talk is put on the programme. If a talk changes afterwards, the programme stays as it is.
- Several committee members work on the programme at once, and it must never end up breaking the rules.

## API

```yaml
paths:
  /programme/{room}/{slot}:
    parameters:
      - { name: room, in: path, required: true, schema: { $ref: '#/components/schemas/Room' } }
      - { name: slot, in: path, required: true, schema: { $ref: '#/components/schemas/Slot' } }
    put:
      requestBody:
        content:
          application/json:
            schema: { $ref: '#/components/schemas/Scheduling' }
      responses:
        '200': { content: { application/json: { schema: { $ref: '#/components/schemas/Entry' } } } }
    get:
      responses:
        '200': { content: { application/json: { schema: { $ref: '#/components/schemas/Entry' } } } }
    delete:
      description: takes the talk out of this room and slot
components:
  schemas:
    Room: { type: string, enum: [ R1, R2, R3 ] }
    Slot: { type: string, enum: [ S1, S2, S3, S4, S5 ] }
    Scheduling:
      type: object
      properties:
        talkId: { type: integer }
    Entry:
      type: object
      properties:
        room: { $ref: '#/components/schemas/Room' }
        slot: { $ref: '#/components/schemas/Slot' }
        start: { type: string, example: '13:30' }
        end: { type: string, example: '15:30' }
        talkId: { type: integer }
```

## Decisions

The feature request isn't a spec: it doesn't give status codes, edge cases or error handling. Every
answer here is ours until a person has asked the product owner. **Ask the PO** about every row whose
behaviour a harness check can see (status codes, idempotency, ordering) before the claim.

Conventions we follow, from what the harness accepted on earlier features (see
`workflow/specs/co-speakers.md` and the acceptance spec in PR #12): 404 for an unknown id in the
path, 400 for input that isn't valid, 409 for a valid request the current state doesn't allow.
`POST /talks` answers 400 for an unknown `speakerId` in the body, and passes the talks checks.

Depends on acceptance (issue #11, PR #12) for `ACCEPTED`. Programme-export builds on this feature.

| ID | Question | Our answer | Ask the PO? | Approved by (person, PO answer) |
|---|---|---|---|---|
| D1 | Unknown room or slot in the path (`R4`, `S6`, lower case `r1`), on any method | 404, before anything else | yes | Eirik, 2026-11-06: recommendation (Q7 a) |
| D2 | `GET` a room and slot with nothing in it | 404 | **yes, likely checked** | Eirik, 2026-11-06: recommendation (Q1 a) |
| D3 | `PUT` body: `talkId` missing, `null`, not an integer (incl. `"5"`, `1.5`), or below 1 | 400 | yes | Eirik, 2026-11-06 |
| D4 | `PUT` with a `talkId` that isn't a talk | 400, as an unknown `speakerId` in `POST /talks` (an id in the body, not the path). Fallback: 404 | yes. **Open risk** | Eirik, 2026-11-06: recommendation (Q6 a) |
| D5 | `PUT` a talk that isn't `ACCEPTED` (`SUBMITTED`, `WAITLISTED`, `REJECTED`, `WITHDRAWN`) | 409, nothing changes | yes | Eirik, 2026-11-06: recommendation (Q4 a) |
| D6 | `PUT` a `WORKSHOP` in a slot other than `S4` | 409, nothing changes. Any other format fits any slot, `S4` included | yes. **Open risk:** could be 400 | Eirik, 2026-11-06: recommendation (Q5 a) |
| D7 | `PUT` into a room and slot that already holds another talk | 409, nothing changes | yes | Eirik, 2026-11-06: recommendation (Q4 a) |
| D8 | `PUT` the same talk into the room and slot it's already in | 200 with the Entry, nothing changes (PUT is idempotent). Alternative: 409 | **yes, likely checked** | Eirik, 2026-11-06: recommendation (Q2 a) |
| D9 | `PUT` a talk that's already somewhere else on the programme | 409, nothing changes ("to move a talk, take it out first") | yes | Eirik, 2026-11-06: recommendation (Q4 a) |
| D10 | `PUT` a talk whose main speaker or a co-speaker is also the main speaker or a co-speaker of a talk in the same slot, in another room | 409, nothing changes. People are compared by speaker id | yes | Eirik, 2026-11-06: recommendation (Q4 a) |
| D11 | `DELETE` a room and slot with nothing in it | 404, as removing someone who isn't a co-speaker | yes | Eirik, 2026-11-06: recommendation (as Q1 a) |
| D12 | Response to a successful `DELETE` | 204, no body (the API gives no response content). Alternative: 200 with the removed Entry | **yes, likely checked** | Eirik, 2026-11-06: recommendation (Q3 a) |
| D13 | Precedence when several things are wrong | 404 room/slot → 400 body → 400 unknown talk → 409 not accepted → 409 workshop not in `S4` → 200 if already in this place (D8) → 409 elsewhere on the programme → 409 room and slot taken → 409 speaker clash | yes | Eirik, 2026-11-06: recommendation (Q8 a) |
| D14 | Bad bodies: no body, not JSON, a JSON array; `Content-Type: text/plain` | 400; 415 (Spring binding, as ratings and acceptance) | no | Eirik, 2026-11-06 |
| D15 | `PUT` success | 200 with the Entry (`room`, `slot`, `start`, `end`, `talkId`), as the API says. Not 201 | no | Eirik, 2026-11-06 |
| D16 | Times in the Entry | From the fixed slot table, `HH:mm` (`"09:00"`) | no (the rule is clear) | Eirik, 2026-11-06 |
| D17 | A talk changes after it's on the programme (withdrawn, co-speakers changed, format can't change) | The programme stays as it is: `GET` still shows it, nothing is removed or re-checked ("if a talk changes afterwards, the programme stays as it is") | no (the rule is clear) | Eirik, 2026-11-06 |
| D18 | A talk that's been taken out | Can be put anywhere again, checked like a new `PUT` | no | Eirik, 2026-11-06 |
| D19 | Concurrency: two `PUT`s into the same room and slot; the same talk into two places at once; two talks with a shared speaker into the same slot in different rooms | One transaction per `PUT`: lock the slot (a row per slot, `SELECT … FOR UPDATE`, so all three rooms of a slot are serialised), then check and write. A primary key on (room, slot) and a unique `talk_id` back it up in the database. The loser gets the 409 it would get if it came second | no (the rule is clear) | Eirik, 2026-11-06 |
| D20 | Does the Talk show where it's scheduled? | No: the Talk schema is unchanged. The programme is only read through `/programme/{room}/{slot}` (and later programme-export) | no | Eirik, 2026-11-06 |

## Questions for the team

We have no product owner: a person on the team picks an option per question and records it in the
"Approved by" column above. **Approved 2026-11-06 by Eirik: all recommendations as written.** Most likely to be checked by the harness first.

**Q1 (D2). `GET /programme/R1/S1` when nothing is scheduled there?**
- a) 404 (nothing there; as `DELETE` of someone who isn't a co-speaker, which the harness accepted)
- b) 200 with `{"room": "R1", "slot": "S1", "start": "09:00", "end": "10:00", "talkId": null}`
- Recommendation: **a)**. The API's Entry always has a talk, and a) matches our 404 convention.
  Fallback b) if a check fails on it.

**Q2 (D8). `PUT` the same talk into the room and slot it's already in?**
- a) 200 with the Entry, nothing changes (PUT is idempotent; as adding a co-speaker twice)
- b) 409 ("a talk is on the programme only once")
- Recommendation: **a)**. A retried `PUT` shouldn't fail, and the rule's point (only once) still
  holds. Fallback b).

**Q3 (D12). What does a successful `DELETE /programme/{room}/{slot}` return?**
- a) 204, no body (the API gives no response content for `delete`, unlike `put` and `get`)
- b) 200 with the Entry that was removed (as `DELETE` co-speaker returns the Talk)
- c) 200 with no body
- Recommendation: **a)**. The API lists responses for `put` and `get` but none for `delete`, which
  reads as "no content". Fallback b).

**Q4 (D5, D7, D9, D10). The rule breaks: talk not accepted, room and slot taken, talk already
elsewhere, a speaker in two rooms at once?**
- a) 409 for all four, nothing changes (state doesn't allow it; as the 3-talk limit)
- b) 400 for all four
- c) 422 for all four
- Recommendation: **a)**. The input is valid; it's the current programme or talk that says no. It's
  the convention the harness accepted for the 3-talk limit and co-speakers.

**Q5 (D6). A `WORKSHOP` in a slot other than `S4`?**
- a) 409, like the other rule breaks (Q4)
- b) 400: the request contradicts itself (as "main speaker as co-speaker", which the harness
  wanted as 400)
- Recommendation: **a)**. Whether a talk fits depends on the talk as it is now, not on the request
  alone, and keeping all rule breaks on 409 is simpler. Fallback b): it's the closest precedent for
  400, so it's the first thing to change if a check fails.

**Q6 (D4). `PUT` with a `talkId` that isn't a talk?**
- a) 400 (an unknown id in the body; as unknown `speakerId` in `POST /talks`, which passes)
- b) 404 (the talk isn't found)
- Recommendation: **a)**. The URL (room and slot) exists; only the body is wrong, and a) follows
  the precedent the harness already accepts. Fallback b).

**Q7 (D1). An unknown room or slot in the path (`R4`, `S6`, `r1`)?**
- a) 404 (no such room or slot; as an unknown id in the path)
- b) 400 (not one of the enum values)
- Recommendation: **a)**. Rooms and slots are things in the URL, like talk ids, and our convention
  is 404 for those. Fallback b).

**Q8 (D13). Which error wins when several things are wrong?**
- a) As in D13: path → body → unknown talk → not accepted → workshop slot → already here (200) →
  elsewhere → taken → speaker clash
- b) The same, but "taken" before "elsewhere"
- Recommendation: **a)**. Facts about the talk itself come before facts about the programme, and
  "already here" has to come before "taken", or a retried `PUT` would get 409 for its own place.
  The harness is unlikely to combine errors, so this matters little.
