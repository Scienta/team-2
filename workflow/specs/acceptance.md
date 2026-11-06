# Acceptance state machine

Version: 1
Kind: drip feature (released at tick 20)
V: 2

## Context

As the programme committee, I want to accept, reject or waitlist submitted talks, and as a speaker I want to withdraw my talk, so that the programme is built only from talks that are reviewed and still on offer.

Every talk starts as `SUBMITTED` ([Talk submissions](../talks/spec.md)), and today nothing can change that.

## What we want

- The committee decides on a talk: accept it, reject it or put it on the waitlist.
- A waitlisted talk can still be accepted or rejected later. Once a talk is accepted or rejected, the committee's decision is final.
- We only accept a talk that at least two different reviewers have rated ([Ratings](../ratings/spec.md)). A reviewer who rated it twice is still one reviewer. Rejecting and waitlisting need no ratings.
- A speaker can withdraw their own talk, unless it was already rejected or withdrawn. Nobody else can withdraw it. A withdrawn talk is out for good.
- A talk's current status shows wherever the talk is shown.
- A decision or withdrawal that isn't allowed changes nothing. The committee is several people clicking at once, so two of them deciding on the same talk must never leave it in a state the rules don't allow.

## API

```yaml
paths:
  /talks/{id}/status:
    put:
      parameters: [ { name: id, in: path, required: true, schema: { type: integer } } ]
      requestBody:
        content:
          application/json:
            schema: { $ref: '#/components/schemas/Decision' }
      responses:
        '200': { content: { application/json: { schema: { $ref: '../talks/spec.md#/components/schemas/Talk' } } } }
  /talks/{id}/withdraw:
    post:
      parameters: [ { name: id, in: path, required: true, schema: { type: integer } } ]
      requestBody:
        content:
          application/json:
            schema: { $ref: '#/components/schemas/Withdrawal' }
      responses:
        '200': { content: { application/json: { schema: { $ref: '../talks/spec.md#/components/schemas/Talk' } } } }
components:
  schemas:
    Status: { type: string, enum: [ SUBMITTED, ACCEPTED, REJECTED, WAITLISTED, WITHDRAWN ] }
    Decision:
      type: object
      properties:
        status: { type: string, enum: [ ACCEPTED, REJECTED, WAITLISTED ] }
    Withdrawal:
      type: object
      properties:
        speakerId: { type: integer, description: the speaker asking to withdraw }
    # Talk.status (Talk submissions spec) now takes any value of Status.
```

## Decisions

The feature request isn't a spec: it doesn't give status codes, edge cases or error handling. Every
answer here is ours until a person has asked the product owner. **Ask the PO** about every row whose
behaviour a harness check can see (status codes, idempotency, ordering) before the claim.

**Approval (2026-11-06):** the team has no product owner to ask, only the harness and the specs. Julian
approved every row below from the spec's own wording (reasoning in the last column). The rows marked
**Open risk** are where the spec doesn't decide; watch those checks first after the claim.

Conventions we follow, from what the harness accepted on earlier features: 404 for an unknown id in
the path (ratings E2, co-speakers), 400 for a request that contradicts itself or isn't valid input
(ratings, co-speakers "main speaker as co-speaker"), 409 for a valid request the current state
doesn't allow (the 3-talk limit; co-speakers "more than two").

The allowed transitions, from the rules:

| From \ To | ACCEPTED | REJECTED | WAITLISTED | WITHDRAWN (speaker) |
|---|---|---|---|---|
| SUBMITTED | yes, if 2+ reviewers | yes | yes | yes |
| WAITLISTED | yes, if 2+ reviewers | yes | see D5 | yes |
| ACCEPTED | see D5 | no (final) | no (final) | yes |
| REJECTED | no (final) | see D5 | no (final) | no |
| WITHDRAWN | no | no | no | see D5 |

| ID | Question | Our answer | Ask the PO? | Approved by (person, PO answer) |
|---|---|---|---|---|
| D1 | Unknown talk id, on `PUT /status` or `POST /withdraw` | 404, before anything else is checked | yes || Julian, 2026-11-06: matches ratings E2 and co-speakers. No PO available |
| D2 | `PUT /status` with a `status` that isn't `ACCEPTED`, `REJECTED` or `WAITLISTED`: `SUBMITTED`, `WITHDRAWN`, lower case, missing, `null`, not a string | 400 | yes || Julian: the Decision enum; as formats and levels in the talks spec (400) |
| D3 | A decision the table above doesn't allow (e.g. `ACCEPTED` → `REJECTED`, anything on a `WITHDRAWN` talk) | 409, talk unchanged | yes || Julian: "a decision that isn't allowed changes nothing"; 409 as the 3-talk limit |
| D4 | Accepting a talk with fewer than 2 distinct reviewers | 409, talk unchanged (a state precondition, like the 3-talk limit) | yes || Julian: same kind as D3 |
| D5 | The same status again: `ACCEPTED` → `ACCEPTED`, `REJECTED` → `REJECTED`, `WAITLISTED` → `WAITLISTED`, withdrawing twice | 409, unchanged: the rules list no such transition, and "a decision that isn't allowed changes nothing". The alternative is 200 unchanged (idempotent, as "adding a co-speaker twice changes nothing") | **yes, most likely to be checked** || Julian: "final", "unless it was already rejected or withdrawn"; unlike co-speakers, no "changes nothing" line for repeats. **Open risk:** WAITLISTED → WAITLISTED isn't covered by the spec |
| D6 | Withdraw by a speaker who isn't the talk's main speaker (another speaker, a co-speaker) | 403, unchanged ("nobody else can withdraw it") | yes || Julian: "nobody else can withdraw it". **Open risk:** a check may expect 400 or 409 |
| D7 | Withdraw with a `speakerId` that isn't a registered speaker | 403, as D6 (they aren't the main speaker); not 404, which would read as "talk not found" | yes || Julian: as D6. **Open risk:** co-speakers answers 404 for an unknown speaker |
| D8 | Withdraw with `speakerId` missing, `null`, not an integer | 400 | yes || Julian: invalid input |
| D9 | Withdraw a `REJECTED` or `WITHDRAWN` talk, by its own speaker | 409, unchanged | yes || Julian: spec rule, 409 as D3 |
| D10 | Precedence when several things are wrong | 404 talk → 400 body → 403 not the main speaker → 409 state. So a stranger withdrawing a rejected talk gets 403 | yes || Julian: who asks before the state; checks unlikely to combine them |
| D11 | Bad bodies: no body, not JSON, a JSON array, `Content-Type: text/plain` | 400, 400, 400, 415 (Spring binding, as ratings E4) | no || Julian |
| D12 | Response on success | 200 with the full Talk (`status`, `ratingCount`, `averageRating`, `coSpeakerIds`, …), as the API says | no || Julian: the API |
| D13 | "Wherever the talk is shown" | `status` is already on every Talk: `GET /talks/{id}`, `GET /talks` (all filters and `sort=rating`), `GET /speakers/{id}/talks`, `POST /talks`, the co-speaker responses. No filtering by status: withdrawn and rejected talks still show in lists | yes (lists) || Julian: "status shows wherever the talk is shown"; ratings lists "all talks", so no filtering |
| D14 | Rating, co-speakers and the 3-talk limit after a decision or withdrawal | Unchanged: rating a withdrawn or decided talk still works, co-speakers can still change, and a withdrawn talk still counts towards its speaker's 3 talks. The request doesn't say otherwise | yes || Julian: spec silent; smallest change, base features untouched |
| D15 | Concurrency: two committee members deciding at once (e.g. `ACCEPTED` and `REJECTED`), or a decision and a withdrawal at once | Lock the talk row (`SELECT … FOR UPDATE`, the existing `TalkDao.lock`), then read the status, check, write, in one transaction. One wins with 200; the other sees the new state and gets 409 (or 200 if D5 becomes idempotent). Never a final state overwritten | no (the rule is clear) || Julian |
| D16 | Counting reviewers for D4 | `ratingCount` (distinct reviewers; a reviewer has one row per talk, so re-rating counts once). Counted inside the locked transaction | no || Julian |
