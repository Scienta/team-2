# Review: programme_export_plan.md

Reviewer: `plan-reviewer` agent, 2026-11-06. Verdict: revise before executing. The CSV and Spring
design is sound, but the test fixtures and the restart and coverage gates are not.

## Blocking

- **B1.** `TestDatabase` is fresh per test *class*, so the methods of `ProgrammeExportTest` share
  one programme. The "empty" test and the exact-order test can't both pass. Isolate the empty case
  (in its own class), give each method its own cells, and assert order only on the method's own
  `talkId`s. Spell out the fixture rules: a new speaker per talk (max 3 talks), two ratings before
  acceptance (`AcceptanceTest.rateAll`), and a room for the S4 talk.
- **B2.** The Hurl example runs against a database that keeps its data (`local-rollout.sh`) and
  against prod (`/pre-claim`). Taken cells give `409` on a second run, and cells it never frees eat
  the 15 that the harness also uses. It should find a free cell, `DELETE` only its own entries, or
  stay local-only.

## Significant

- **S1.** "One row per scheduled talk, nothing else" has no test. Cover: an accepted but
  unscheduled talk is left out, a talk taken out disappears, and a talk moved shows up once.
- **S2.** D1 depends on what #16 does on withdrawal: check that in Phase 0. "Rename a speaker"
  needs the undocumented `PUT /speakers/{id}` (with a valid email). Add a test for a co-speaker
  added after scheduling.
- **S3.** `spring.servlet.encoding.force=true` adds `charset=UTF-8` to every response, so D2's
  fallback b) needs a `byte[]` body. Pin down the exact string on the wire, or compare the parsed
  media type.
- **S4.** Phase 2 commits code with no tests, and its Check runs `-DskipTests`, so it never sees
  the coverage gate. Merge Phase 2 into Phase 3, and run the full `./mvnw package` in Phase 1.
- **S5.** The plan and spec are untracked: commit them (not `known_issues_triage_plan.md`).
- **S6.** The blocking dependency on scheduling has no time limit and no workaround. Ask #16 for
  their branch or schema, build on their branch, set a time limit, and don't claim before
  scheduling's own checks are green.
- **S7.** CSV cases with no test: a speaker name with a comma or quote (the whole joined field is
  quoted), no BOM, `\r\n` and a lone `\r`, and quotes with no comma. "No `\r` outside quotes" needs
  an exact expected string.

## Minor

- **M1.** In the checklist, Phase 1 can come before the Phase 0 gate.
- **M2.** The approval gate comes too late for D5–D7. D3, D5 and D6 have only one option: label
  them spec-fixed.
- **M3.** No `produces=` (it brings back the `406`). The preset Content-Type picks the converter.
  `/programme.csv` must be a literal mapping. Check that scheduling has no `@GetMapping("/programme")`.
  Assert there's no `Content-Disposition` header.
- **M4.** Trailing slashes (`/programme/`, `/programme.csv/`) work through `WebConfig`: decide
  that and test it.
- **M5.** `snapshot-api.sh` needs a base URL and an out file, run before and after. It doesn't call
  `/programme`.
- **M6.** D7's fallback, ordering by start time, can't be done on a Java enum in SQL. Use
  `CASE`/`array_position` in SQL, or sort in Java.
- **M7.** Once approved, move the decisions table into the spec.
- **M8.** Phase 1's Check should be the full `./mvnw package`.
- **M9.** "One snapshot" holds only within a single response.
- **M10.** `AcceptanceTest.decide`/`rateAll` are private: copy them, don't reuse them.
