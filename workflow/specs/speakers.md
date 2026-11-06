# Speakers

Version: 1
Kind: base feature (claimed from tick 0)
V: 1

## Context

As a speaker, I want to register a profile once with my name, email and a short bio, so that I can submit talks under it and the organisers can contact me.

The base app has this feature. This is what the business expects of it.

## What we want

- A speaker registers with a name and an email, and may add a bio. They can look up their profile again later, exactly as they registered it.
- A speaker needs a real name and a real email to register. The bio is optional.
- A bio is at most **500** characters.
- One email is one speaker. Emails are the same whatever the letter case: `ada@example.com` and `ADA@Example.com` are one person.
- Registered speakers stay registered, also after the service is updated or restarted.
- The legacy web client is still out there, and some of its links and forms put a slash at the end of the speaker URLs (`/speakers/` and `/speakers/{id}/`). They must keep working just like the URLs without one.

## API

```yaml
paths:
  /speakers:
    post:
      requestBody:
        content:
          application/json:
            schema: { $ref: '#/components/schemas/NewSpeaker' }
      responses:
        '201': { content: { application/json: { schema: { $ref: '#/components/schemas/Speaker' } } } }
  /speakers/{id}:
    get:
      parameters: [ { name: id, in: path, required: true, schema: { type: integer } } ]
      responses:
        '200': { content: { application/json: { schema: { $ref: '#/components/schemas/Speaker' } } } }
components:
  schemas:
    NewSpeaker:
      type: object
      properties:
        name: { type: string }
        email: { type: string }
        bio: { type: string, nullable: true }
    Speaker:
      type: object
      properties:
        id: { type: integer }
        name: { type: string }
        email: { type: string }
        bio: { type: string, nullable: true }
```

## Decisions

"A speaker needs a real name and a real email" (issue #33). Before this, `POST /speakers` answered
201 for `"email": "not-an-email"` and for `"name": 5` (stored as `"5"`). The answers below keep the
23 `POST /speakers` bodies the harness has sent at 201 (`SpeakerValidationTest`, from its feedback
transcripts): names like `Ada Dijkstra 0737`, e-mails like `x-0808034cb556@example.test`.

| ID | Question | Our answer | Ask the PO? | Approved by (person, PO answer) |
|---|---|---|---|---|
| D1 | What is a real email? | Workshop registration's rule (its D7), now `Utils.isWellFormedEmail`: one `@`, no whitespace, text before the `@`, a dot with text on both sides after it, checked on the value trimmed at the edges; stored as typed. So `not-an-email`, `@example.com`, `a b@c.d` and `ada@localhost` are 400 `invalid email`. Options: a) this, b) the old `Utils.isValidEmail` (accepted `@example.com`), c) Jakarta `@Email` (accepts `ada@localhost`). a): one rule for the whole service. Fallback if a harness check fails: b) | **yes, most likely to be checked** | agent, pending Julian |
| D2 | A `name` or `email` that isn't a JSON string (`5`, `true`, an object, an array) | 400, as workshop D6, instead of coercing `5` to `"5"`. Per field (`StrictStringDeserializer` on `SpeakerForm`), so Jackson's global settings and `WireFormatTest` are unchanged. A non-string `bio` on `POST` is still coerced (not asked for) | yes | agent, pending Julian |
| D3 | Status and order | 400 for all of them. Malformed body (Spring) → missing, blank or non-string fields → e-mail format → 409 for a taken e-mail | yes | agent, pending Julian |
| D4 | A name of only whitespace | 400, a no-break space and other Unicode spaces included (`@NotBlank` let `" "` through), as workshop D6 | yes | agent, pending Julian |
| D5 | `PUT /speakers/{id}` (admin, not in this spec) | The same name and e-mail rules, and a non-string `name`, `email` or `bio` is 400: a typo fix shouldn't store what registration refuses. Its bio limit stays 1000 (known issue R4) | no (not in the spec) | agent, pending Julian |
