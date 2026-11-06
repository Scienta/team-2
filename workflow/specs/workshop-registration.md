# Workshop registration with capacity

Version: 1
Kind: drip feature (released at tick 67)
V: 2
Source: `workflow/scripts/harness.sh spec workshop-registration`, copied verbatim below the line,
then our decisions at the end.

## Context

As an attendee, I want to register for an accepted workshop, and as the organisers we want each workshop to take no more attendees than its room holds, so that nobody turns up to a workshop that has no seat for them.

## What we want

- Attendees register for a workshop with their name and email. Both are required.
- Only workshops (talks with format `WORKSHOP`, [Talk submissions](../talks/spec.md)) that the committee has accepted ([Acceptance state machine](../acceptance/spec.md)) take registrations. If a workshop is withdrawn later, the people already registered stay on its list.
- Every workshop has room for 20 attendees, and never more. Registration opens to everyone at the same moment, so expect a rush.
- One registration per email per workshop. People type their email in all sorts of ways: `ada@example.com` and `ADA@Example.com` are the same person. The same person can register for several workshops.
- The organisers can see who's registered for a workshop, in the order they registered.

## API

```yaml
paths:
  /workshops/{id}/registrations:
    parameters: [ { name: id, in: path, required: true, description: the workshop's talk id, schema: { type: integer } } ]
    post:
      requestBody:
        content:
          application/json:
            schema: { $ref: '#/components/schemas/RegistrationRequest' }
      responses:
        '201': { content: { application/json: { schema: { $ref: '#/components/schemas/Registration' } } } }
    get:
      responses:
        '200':
          content:
            application/json:
              schema: { type: array, items: { $ref: '#/components/schemas/Registration' } }
components:
  schemas:
    RegistrationRequest:
      type: object
      properties:
        name: { type: string }
        email: { type: string }
    Registration:
      type: object
      properties:
        id: { type: integer }
        workshopId: { type: integer }
        name: { type: string }
        email: { type: string, description: as the attendee typed it }
```

## Decisions

The feature request isn't a spec: it doesn't give status codes, edge cases or error handling. Every
answer here is ours until a person has asked the product owner. **Ask the PO** about every row whose
behaviour a harness check can see (status codes, idempotency, ordering) before the claim.

**Approval (2026-11-06):** the team has no product owner to ask, only the harness and the specs, as
for Acceptance (`workflow/specs/acceptance.md` on `11-acceptance`). Håkon approved every row below. We
follow the same conventions: 404 for an unknown id in the path, 400 for invalid input, 409 for a
valid request the current state doesn't allow. **Open risks** to watch first after the claim, where
the spec doesn't decide: D2, D5, D7, D10, D11.

| ID | Question | Our answer | Ask the PO? | Approved by (person, PO answer) |
|---|---|---|---|---|
| D1 | `POST` or `GET` with an unknown talk id | 404, like every other talk endpoint | yes | Håkon, 2026-11-06 |
| D2 | `POST` or `GET` on a talk that exists but isn't a `WORKSHOP` | 404: it isn't a workshop, so that resource doesn't exist | yes | Håkon, 2026-11-06 |
| D3 | `POST` to a workshop that is `SUBMITTED`, `WAITLISTED` or `REJECTED` | 409, nothing stored | yes | Håkon, 2026-11-06 |
| D4 | `POST` to a workshop that was accepted and then `WITHDRAWN` | 409, nothing stored; the people already registered stay | yes | Håkon, 2026-11-06 |
| D5 | `GET` on a workshop that isn't accepted, or is withdrawn | 200 with the list: `[]` if nobody registered, otherwise the people registered before the withdrawal | yes | Håkon, 2026-11-06 |
| D6 | `name` or `email` missing, null, empty or only whitespace | 400, like `POST /speakers` | yes | Håkon, 2026-11-06 |
| D7 | `email` present but not an email (`not-an-email`) | 400, using `Utils.isValidEmail`. `POST /speakers` only checks not-blank today, so this is stricter than it | yes | Håkon, 2026-11-06 |
| D8 | Malformed JSON, empty body, a JSON array, or a non-integer id in the path; `Content-Type: text/plain` | 400 from Spring's binding, before the talk is looked up; 415 for `text/plain`, as ratings and Acceptance | yes | Håkon, 2026-11-06 |
| D9 | `POST` when the workshop already has 20 registrations | 409, nothing stored | yes | Håkon, 2026-11-06 |
| D10 | `POST` with an email already registered for this workshop | 409, nothing changes (not an idempotent 200), like `POST /speakers` | yes | Håkon, 2026-11-06 |
| D11 | Which emails count as the same? | Ignore letter case and surrounding whitespace. Store and return the email exactly as typed | yes | Håkon, 2026-11-06 |
| D12 | Several things wrong at once | 404, then 400, then 409, like ratings (E1: missing talk before validation). Full and duplicate are both 409, so their order isn't visible in the status | yes | Håkon, 2026-11-06 |
| D13 | Length limits on `name` and `email` | None: the request gives none | yes | Håkon, 2026-11-06 |
| D14 | Can a speaker on the workshop register as an attendee? Must an attendee be a registered speaker? | Yes, and no: attendees are independent of speakers | yes | Håkon, 2026-11-06 |
| D15 | Order of `GET` | Registration order, ascending `id`; the row lock in D17 makes `id` order match registration order | no: the rule is explicit | Håkon, 2026-11-06 |
| D16 | Can a registration be removed, freeing a seat? | No: the API has no such operation, so a seat stays taken | no: the API is explicit | Håkon, 2026-11-06 |
| D17 | Concurrency ("expect a rush", "never more") | In one short transaction: lock the talk row (`SELECT … FOR UPDATE`), re-read status and count in new statements (see the note in `TalkDao`), then insert. A unique index on the talk id and the normalised email is the backstop, mapped to 409 like D10. Exactly 20 of a rush get 201 | no: internal, but must be tested with a real rush | Håkon, 2026-11-06 |
| D18 | Dependency on Acceptance | Only `talk.status = 'ACCEPTED'` is read, set by the Acceptance feature. Tests can set it directly until that merges. This feature can't work in production or be claimed before Acceptance is deployed | no | Håkon, 2026-11-06 |
