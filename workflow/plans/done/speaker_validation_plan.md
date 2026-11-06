# Plan: Speakers reject an invalid e-mail and a non-string name (#33)

- [x] Phase 1: Validation on `POST` and `PUT /speakers`, with Java tests (rejected cases, and the
  harness's own bodies still accepted)
- [x] Phase 2: Hurl checks in `examples/speakers-rules.hurl`, run twice via `local-rollout.sh`
- [x] Phase 3: Decisions table in the spec, `docs/`, `.claude/rules/project.md`, `REVIEW.md` §2; plan to done

## Context (read this after a restart)

- Issue #33. Spec: `workflow/specs/speakers.md`: "A speaker needs a real name and a real email to
  register." Speakers is **claimed** and its harness checks pass, so the main risk is rejecting
  something the harness sends. The harness's own `POST /speakers` bodies (23, from a feedback
  snapshot) are in `src/test/resources/harness/speakers-post-bodies.json`, and a test keeps them 201.
- Found by the spec-based Hurl suite (PR #30, `workflow/plans/done/spec_hurl_ci_plan_review.md` B1):
  `"email": "not-an-email"` and `"name": 5` both get 201 today (known issue R5 for the e-mail).
- Branch `33-speaker-validation`. A person opens the PR in the GitHub UI. Merging deploys.
- Build with JDK 25 (`JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home` on
  Julian's machine).
- Code: `SpeakerForm` (POST body, bean validation), `SpeakerController`, `ConferenceService.registerSpeaker`
  / `updateSpeaker`, `Utils.isValidEmail` (weak: accepts `@example.com`), `WorkshopService.EMAIL`
  (the stricter pattern from workshop registration D7).
- Test style: `SpeakersTest` (`@SpringBootTest(RANDOM_PORT)`, `TestDatabase.fresh`).
- Written by a background agent: the `plan-reviewer` agent can't run from here. A person reviews.

## Decisions for the human to check

Recorded in the spec's decisions table; Julian delegated the decision to Claude, which kept D1 on harness evidence (workshop registration's e-mail checks pass with the same rule). The coverage floor commits were superseded by the freeze at 0.870 (#44).

- **D1. Which e-mail check on create? (harness-visible)**
  - a) Workshop registration's rule (its D7): one `@`, no whitespace, text before the `@`, a dot with
    text on both sides after it; checked on the value trimmed at the edges, stored as typed.
    Moved to `Utils` and shared with `WorkshopService`.
  - b) `Utils.isValidEmail`, which `PUT` uses today: accepts `@example.com` and `a b@c.d`.
  - c) Jakarta `@Email`: accepts `ada@localhost`.
  - Recommendation a): one rule for the whole service, and all 23 harness shapes (`x-<hex>@example.test`)
    pass. Fallback if a check fails: b).
- **D2. A name or e-mail that isn't a JSON string (`5`, `true`, an object): 400.** As workshop D6.
  Today Jackson coerces `5` to `"5"`. Scoped to the two `SpeakerForm` fields (a field deserializer),
  not a global Jackson change, so `WireFormatTest` and other endpoints are untouched.
- **D3. Status and order.** 400 for both. Order: malformed body (Spring, 400) → blank or non-string
  fields (400) → e-mail format (400 `invalid email`, as `PUT` and workshops) → duplicate e-mail (409).
- **D4. Names of only whitespace, including a no-break space: 400.** `@NotBlank` already rejects
  ASCII whitespace; a no-break space slips through. Workshop D6 rejects it too.
- **D5. `PUT /speakers/{id}` (admin, not in the spec) gets the same name and e-mail rules.**
  Consistency: a typo fix shouldn't store what registration refuses. Its bio limit (1000, vs 500 on
  create) is out of scope and stays.
- `PUT` rejects a non-string `bio` too (one helper reads all three fields); on `POST` a non-string `bio` is still coerced. Out of scope: the SQL built from a string in `SpeakerDao.search`.

## Phase 1: Validation and Java tests

1. `Utils.isWellFormedEmail(String)`: workshop's pattern on the trimmed value; `WorkshopService` uses
   it (keeps its own 254 limit). `Utils.isValidEmail` goes if nothing else uses it.
2. `StrictStringDeserializer` (Jackson 3 `ValueDeserializer<String>`): only `VALUE_STRING`, else a
   mismatch (400). On `SpeakerForm.name` and `.email`.
3. `ConferenceService.registerSpeaker`: blank name with Unicode spaces → 400 `name is required`;
   e-mail not well formed → 400 `invalid email`; before the duplicate check.
4. `PUT`: body `Map<String, Object>`; non-string name or e-mail → 400; same blank and e-mail rules.
5. Tests in `SpeakersTest`: invalid e-mails (`not-an-email`, `@example.com`, `a b@c.d`, `ada@localhost`,
   `ada@example.`), non-string name and e-mail, NBSP-only name → 400, nothing stored; harness bodies
   → 201; `PUT` with the same cases; `UtilsTest` for `isWellFormedEmail`.

Check: `./mvnw package` green, no warnings, coverage not below the floor. Commit:
`fix: Speakers reject an invalid e-mail and a non-string name (#33)`.

## Phase 2: Hurl

1. `examples/speakers-rules.hurl`: invalid e-mail, numeric name, non-string e-mail, NBSP name → 400.
2. Remove any "left out" note about them in `examples/*-rules.hurl`.

Check: `APP_PORT=18083 PG_PORT=55435 COMPOSE_PROJECT_NAME=s33 workflow/scripts/local-rollout.sh`
twice, every file passes. Commit: `test: Hurl checks for speaker validation (#33)`.

## Phase 3: Docs and finish

1. Decisions table in `workflow/specs/speakers.md`; `docs/known-issues.md` R5 resolved; `docs/api.md`
   speakers rules; `.claude/rules/project.md` "except" note; `REVIEW.md` §2 story with SHAs.
2. `workflow/scripts/check-review.sh`; plan to `workflow/plans/done/`.

Check: `check-review.sh` reports all headings filled. Commit: `docs: Speaker validation decisions (#33)`.

## Review response

| Finding | Response |
|---|---|
| (no review yet) | The `plan-reviewer` agent can't run from a background agent; a person reviews |
