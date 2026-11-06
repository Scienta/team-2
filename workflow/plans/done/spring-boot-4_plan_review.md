# Review: spring-boot-4_plan.md

Reviewer: `plan-reviewer` agent (Claude Code), 2026-11-06, on the first draft of the plan.
Verdict: sound and restart-friendly, not executable as written. Responses are in the plan's
"Review response" table.

## Blocking

- **B1.** Trailing slash pinned on the wrong URLs, and D5 asks the PO about something the spec
  requires: `workflow/specs/speakers.md:20` says `/speakers/` and `/speakers/{id}/` work like the
  URLs without one, `POST /speakers/` included. Only `examples/speakers.hurl:42` (GET) guards it,
  outside `./mvnw package`. `UrlHandlerFilter` needs Framework 6.2+; use `wrapRequest()`, a redirect
  breaks POST.
- **B2.** With `server.error.include-message=always`, Spring's own exception text reaches users, and
  Spring 6 / Jackson 3 rewrote it (no "nested exception is…", "No static resource …" for unknown
  routes). "Keep identical" and the empty snapshot diff can't hold. Split: our reasons exact,
  framework messages status + keys only, normalised in the snapshot; ask the PO. Add `GET /nope`.
- **B3.** The snapshot runs share the persistent Compose database, so `ux_speaker_email` makes the
  second run 409. Use fresh e-mails per run. Phase 1's check should be "two runs on 2.7 diff empty".

## Significant

- **S1.** Not pinned: unknown properties ignored (`durationMinutes: 999` gives 201 with 45),
  bean-validation 400 body with `errors` (`include-binding-errors=always`), error `timestamp`
  format, trailing JSON tokens (Jackson 3 turns `FAIL_ON_TRAILING_TOKENS` on).
- **S2.** The properties migrator won't reliably catch `spring.jackson.parser.allow-comments`
  (enum-keyed map; Boot 4 has `spring.jackson.json.read.*`); unknown properties are silently
  ignored. Name the expected targets; the tests are the gate.
- **S3.** Testcontainers 2 isn't optional: the BOM import mixes core 2.x with our pinned
  `postgresql:1.21.4`. TC 2's `PostgreSQLContainer` isn't generic (`<?>` won't compile).
- **S4.** JDK 25 / Spring 6 warnings: Mockito's dynamic agent warning, `ResponseStatusException
  .getStatus()` (`ConferenceServiceTest`), Jackson 3 `asText()` deprecation (`RatingsTest`).
- **S5.** "Only import changes" in `WireFormatTest` conflicts with Jackson 3 API changes: assert on
  raw body strings, no Jackson. Record the Phase 1 SHA in the plan.
- **S6.** The snapshot diff runs only in Phase 4: run it (and `examples/run.sh`) in Phases 2 and 3.
- **S7.** Pre-claim evidence too thin: require harness feedback with no new FAULTY base checks over
  some ticks after deploy.
- **S8.** Phase 0 blocks Phase 1 needlessly; PO answers are needed before the claim, not Phase 2.

## Minor

- **M1.** `-parameters` also matters for unnamed `@RequestParam`s.
- **M2.** Plan and spec are untracked; commit them first. Baseline doesn't belong in `specs/`.
- **M3.** REVIEW.md §2 stories go in the commit where the net caught the bug.
- **M4.** `host.docker.internal` under Colima: add `--add-host=host.docker.internal:host-gateway`;
  amd64 build under emulation is slow; `jq` is a prerequisite.
- **M5.** Check `norwegianDate` on JDK 25 in Phase 2, not Phase 5.
