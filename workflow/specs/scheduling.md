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
| D1 | Unknown room or slot in the path (`R4`, `S6`, lower case `r1`), on any method | 404, before anything else | yes | |
| D2 | `GET` a room and slot with nothing in it | 404 | **yes, likely checked** | |
| D3 | `PUT` body: `talkId` missing, `null`, not an integer (incl. `"5"`, `1.5`), or below 1 | 400 | yes | |
| D4 | `PUT` with a `talkId` that isn't a talk | 400, as an unknown `speakerId` in `POST /talks` (an id in the body, not the path). Fallback: 404 | yes. **Open risk** | |
| D5 | `PUT` a talk that isn't `ACCEPTED` (`SUBMITTED`, `WAITLISTED`, `REJECTED`, `WITHDRAWN`) | 409, nothing changes | yes | |
| D6 | `PUT` a `WORKSHOP` in a slot other than `S4` | 409, nothing changes. Any other format fits any slot, `S4` included | yes. **Open risk:** could be 400 | |
| D7 | `PUT` into a room and slot that already holds another talk | 409, nothing changes | yes | |
| D8 | `PUT` the same talk into the room and slot it's already in | 200 with the Entry, nothing changes (PUT is idempotent). Alternative: 409 | **yes, likely checked** | |
| D9 | `PUT` a talk that's already somewhere else on the programme | 409, nothing changes ("to move a talk, take it out first") | yes | |
| D10 | `PUT` a talk whose main speaker or a co-speaker is also the main speaker or a co-speaker of a talk in the same slot, in another room | 409, nothing changes. People are compared by speaker id | yes | |
| D11 | `DELETE` a room and slot with nothing in it | 404, as removing someone who isn't a co-speaker | yes | |
| D12 | Response to a successful `DELETE` | 204, no body (the API gives no response content). Alternative: 200 with the removed Entry | **yes, likely checked** | |
| D13 | Precedence when several things are wrong | 404 room/slot → 400 body → 400 unknown talk → 409 not accepted → 409 workshop not in `S4` → 200 if already in this place (D8) → 409 elsewhere on the programme → 409 room and slot taken → 409 speaker clash | yes | |
| D14 | Bad bodies: no body, not JSON, a JSON array; `Content-Type: text/plain` | 400; 415 (Spring binding, as ratings and acceptance) | no | |
| D15 | `PUT` success | 200 with the Entry (`room`, `slot`, `start`, `end`, `talkId`), as the API says. Not 201 | no | |
| D16 | Times in the Entry | From the fixed slot table, `HH:mm` (`"09:00"`) | no (the rule is clear) | |
| D17 | A talk changes after it's on the programme (withdrawn, co-speakers changed, format can't change) | The programme stays as it is: `GET` still shows it, nothing is removed or re-checked ("if a talk changes afterwards, the programme stays as it is") | no (the rule is clear) | |
| D18 | A talk that's been taken out | Can be put anywhere again, checked like a new `PUT` | no | |
| D19 | Concurrency: two `PUT`s into the same room and slot; the same talk into two places at once; two talks with a shared speaker into the same slot in different rooms | One transaction per `PUT`: lock the slot (a row per slot, `SELECT … FOR UPDATE`, so all three rooms of a slot are serialised), then check and write. A primary key on (room, slot) and a unique `talk_id` back it up in the database. The loser gets the 409 it would get if it came second | no (the rule is clear) | |
| D20 | Does the Talk show where it's scheduled? | No: the Talk schema is unchanged. The programme is only read through `/programme/{room}/{slot}` (and later programme-export) | no | |
