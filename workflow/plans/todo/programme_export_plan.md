# Plan: Programme export (`GET /programme`, `GET /programme.csv`)

- [ ] Phase 1: `ProgrammeCsv`, the RFC 4180 writer, with unit tests (no database, no scheduling needed)
- [ ] Phase 0: Gate. Scheduling (#16) code available; rebase, fill in the names, D1–D9 approved (blocks Phases 2–5)
- [ ] Phase 2: `ProgrammeDao`, `ProgrammeRow`, `ProgrammeController` and HTTP tests, one commit
- [ ] Phase 3: Hurl example `examples/programme-export.hurl` that cleans up after itself, prod-like run twice
- [ ] Phase 4: Update `docs/`, decisions into the spec, finish (plan to done, `REVIEW.md`)

## Context (read this after a restart)

- Spec: `workflow/specs/programme-export.md` (the feature request as released at tick 133, also on
  branch `origin/programme-export-spec`). It has no decisions table yet. The decisions are below,
  and Phase 4 moves the approved table into the spec. Claimed? **No.** A person claims, and a claim
  is permanent. **Don't claim before scheduling's own harness checks are green**: every export
  check has to schedule talks first.
- Issue: **#25** (`https://github.com/Scienta/team-2/issues/25`).
  `TODO(human): assign #25 to yourself. The agent's token can't set assignees.`
- **Depends on Programme scheduling (#16, eirikma-scienta).** When this plan was written, #16 was
  open and branch `16-scheduling` wasn't pushed. The export only reads what scheduling writes.
  Phase 1 needs no scheduling. Phase 0 gates the rest. If scheduling isn't on `main` but its branch
  is pushed, base Phases 2–3 on `origin/16-scheduling` and rebase onto `main` once it's merged.
  `TODO(human): set a time limit. If scheduling's code isn't available by then, decide whether to
  drop the export.`
- Branch: `25-programme-export`. PR via the GitHub UI, never `gh`. Merging to `main` deploys. Don't
  commit `workflow/plans/todo/known_issues_triage_plan.md` here (it belongs to #23): `git add`
  files by name.
- Commands: `./mvnw package`, `./mvnw test -Dtest=<Test>`, `workflow/coverage/line-coverage.sh`,
  `workflow/scripts/snapshot-api.sh <base-url> <out-file>`, `workflow/scripts/local-rollout.sh`.
  Machine settings (JDK 25, Colima `DOCKER_HOST`) are in `LOCAL.md`.
- ADRs read: `0001` (workflow lives in `.claude/` and `workflow/`), `0002` (Java 25 / Boot 4.1,
  Jackson 3 `tools.jackson.*`; **`WireFormatTest` must pass unchanged and `snapshot-api.sh` must
  show no diff**). This plan only adds endpoints.
- Code in `src/main/java/no/scienta/cfp/`:
  - `TalkDao`: `COLUMNS` reads co-speaker ids in the order they were added (`talk_co_speaker.id`).
    Copy that `ARRAY(SELECT … ORDER BY c.id)` pattern, but for names.
  - `ReportController.talksCsv`: the old semicolon CSV. **Don't reuse `Utils.csv`**: it's for `;`
    and turns `"` into `'`.
  - `ConferenceService.DURATIONS`: the formats, stored as is in `talk.format`.
  - `SpeakerController`: an undocumented admin `PUT /speakers/{id}` renames a speaker. The body
    needs `name` and a valid `email`.
  - `application.properties`: `spring.servlet.encoding.force=true` adds `charset=UTF-8` to every
    response.
  - `WebConfig`: strips a trailing slash on every path before routing.
  - Scheduling (fill in during Phase 0): `<table name and columns>`, `<the Slot type with
    start/end>`, `<the scheduling test's helpers for accepting and scheduling a talk>`,
    `<what scheduling does with a withdrawn talk>`.
- Test style: `CoSpeakersTest` (`RANDOM_PORT`, `TestDatabase.fresh`, `java.net.http.HttpClient`,
  raw-string asserts). `TestDatabase` is fresh **per test class**, not per method. Fixtures: a new
  speaker per talk (max 3 talks per speaker), and two ratings from different reviewers before
  `ACCEPTED`. **Copy** `AcceptanceTest`'s private `rateAll`/`decide`, don't call them.
- Spring 7 notes:
  - **No `produces=`** on either mapping: it brings back `Accept` matching and a `406`.
  - A `Content-Type` set on the `ResponseEntity` makes Spring pick the converter without reading
    `Accept`.
  - No suffix pattern matching, so `/programme.csv` is a literal mapping of its own.
  - `/programme/{room}/{slot}` (three segments) can't catch `/programme` or `/programme.csv`.

## Decisions for the human to check

No product owner: a person on the team decides (`.claude/rules/no-product-owner.md`). The ones a
harness check is most likely to hit come first. **Approve them before Phase 2** (D5–D7 are fixed by
the spec, so Phase 1 can go ahead).

**D1. A talk on the programme was later withdrawn or rejected (or its title, speaker name or
co-speakers changed). Is it in the export?** (harness-visible)
- a) Yes, with its current title, format and speakers. Scheduling says "if a talk changes
  afterwards, the programme stays as it is", and the export is "everything on the programme" with
  speakers "as they are right now".
- b) No, only talks that are still `ACCEPTED`.
- Recommendation: a), **if #16 keeps withdrawn talks on the programme (check this in Phase 0)**.
  The export must match `GET /programme/{room}/{slot}`. Fallback b).

**D2. `Content-Type` of `/programme.csv`.** (harness-visible)
- a) `text/csv;charset=UTF-8` (what Spring writes with `encoding.force=true`). The test compares
  the parsed media type: `text/csv`, charset UTF-8.
- b) `text/csv` with no parameter. That needs a `byte[]` body and an exception to the global
  encoding setting. Costs: more code, and a risk to `WireFormatTest`.
- Recommendation: a). The spec asks for UTF-8 and `text/csv`, and a) gives both. Fallback b).

**D3. `/programme.csv` with `Accept: application/json` (or any `Accept`): 200 `text/csv`.** Fixed
by the spec, no choice. Listed so it gets a test.

**D4. `/programme` with `Accept: text/csv`.** (possibly harness-visible)
- a) 200 JSON anyway, with the `Content-Type` preset as in D3 (the mirror of the spec's CSV rule).
- b) Spring's default `406`.
- Recommendation: a). A read-only view shouldn't fail on `Accept`. Fallback b).

**D5. CSV quoting: fixed by the spec.**
- Quote a field only if it contains `,`, `"`, `\r` or `\n`, and double `"` inside.
- Spaces are written as they are, and line breaks inside a field are kept as they are.
- Lines end with `\n`, the last one too, with no blank line after it and no BOM.
- The joined `speakers` field is quoted as one field.

**D6. JSON field order: `slot, start, end, room, talkId, title, format, speakers`.**
- a) `@JsonPropertyOrder` in the CSV header's order.
- b) No annotation (Jackson's declaration order, the same in practice).
- Recommendation: a). It's explicit, and a refactor can't reorder it. JSON order isn't significant,
  so either is safe.

**D7. Order: `ORDER BY slot, room`.**
- `S1`…`S5` sort in the same order as their start times, and `R1`…`R3` as rooms.
- If scheduling stores slots in some other form, use `array_position(ARRAY['S1',…], slot)` in SQL.
- Fixed by the spec, no choice.

**D8. No `Content-Disposition` header.**
- a) None: the spec doesn't ask for one.
- b) `attachment; filename=programme.csv`.
- Recommendation: a). It's the smallest change. Test that the header is absent.

**D9. Trailing slash: `/programme/` and `/programme.csv/` work like the URLs without it.**
- a) Yes. `WebConfig` already does it for every path, as the speakers spec asks for `/speakers/`.
- b) `404`.
- Recommendation: a). It's free and consistent. One test line.

| # | Decision | Approved by |
|---|---|---|
| D1 | a) withdrawn or changed talks stay, current data (if #16 agrees) | |
| D2 | a) `text/csv;charset=UTF-8` | |
| D3 | CSV whatever the `Accept` (spec) | spec |
| D4 | a) JSON whatever the `Accept` | |
| D5 | RFC 4180 minimal quoting (spec) | spec |
| D6 | a) `@JsonPropertyOrder` | |
| D7 | slot, then room (spec) | spec |
| D8 | a) no `Content-Disposition` | |
| D9 | a) trailing slash works | |

`TODO(human): approve D1, D2, D4, D6, D8 and D9 (who, when, which option) before Phase 2.`

## Phase 1: `ProgrammeCsv`

1. `ProgrammeCsv` (package-private, static methods):
   - `HEADER = "slot,start,end,room,talkId,title,format,speakers"`.
   - `field(String)` quotes per D5 (`null` → empty).
   - `line(List<String>)` joins the fields with `,` and appends `\n`.
2. `ProgrammeCsvTest` (plain JUnit):
   - a plain value;
   - a comma (`Kotlin, really fast` → `"Kotlin, really fast"`);
   - quotes and a comma (`Kotlin, "really" fast` → `"Kotlin, ""really"" fast"`);
   - quotes only (`Say "hi"` → `"Say ""hi"""`);
   - `\n`, `\r\n` and a lone `\r` (quoted and kept);
   - leading spaces (unquoted);
   - the empty string and `null` (empty field);
   - non-ASCII (`Ærlig talt`, unquoted);
   - `line(...)` ends in exactly one `\n`;
   - a joined `Grace Hopper; Lovelace, Ada` → one quoted field.

Check: the full `./mvnw package` (with `LOCAL.md`'s env) is green, with no warnings and no
coverage drop. Commit: `feat: RFC 4180 CSV writer for the programme`.

## Phase 0: Gate on scheduling

1. `gh issue view 16`, `git fetch`. If scheduling is on `main`: `git rebase origin/main`. Else, if
   `origin/16-scheduling` exists, rebase onto it and note that here. Else stop, ask on #16 for the
   branch or schema, and tell the human.
2. Read the scheduling code and test. Fill in the four `<…>` placeholders under "Context".
3. Routing: make sure scheduling has no `@GetMapping("/programme")` and no `/programme/**` mapping.
   Two handlers on one mapping stop the app from starting.
4. Check D1 against what scheduling does with a withdrawn talk, and adjust D1 if it differs.
5. The human approves D1–D9 (fill in "Approved by"). If they haven't, stop.
6. Record the "before" API snapshot from a local service on this base:
   `workflow/scripts/snapshot-api.sh http://localhost:8080 <scratch>/before.txt`.

Check: placeholders filled, decisions approved, `./mvnw package` green on the new base. Commit:
`docs: Programme export plan, scheduling names`.

## Phase 2: DAO, row, controller, HTTP tests (one commit: the coverage gate needs the tests)

1. `ProgrammeRow`: public fields `slot, start, end, room, talkId (long), title, format,
   speakers (List<String>)`, `@JsonPropertyOrder` per D6, and `csvLine()` built on `ProgrammeCsv`
   (speakers joined with `"; "`, then quoted as one field).
2. `ProgrammeDao.findAll()`: **one** query (no N+1, cf. #23).
   - Join the scheduling table with `talk` and the main `speaker`, plus
     `ARRAY(SELECT s.name FROM talk_co_speaker c JOIN speaker s ON s.id = c.speaker_id WHERE c.talk_id = t.id ORDER BY c.id)`.
   - Order per D7. `start`/`end` come from scheduling's `Slot` type (don't duplicate the times).
   - Speakers: main speaker first, then co-speakers.
   - One query makes each response consistent within itself. Two requests can still differ if the
     programme changes in between.
3. `ProgrammeController`:
   - `GET /programme` → `ResponseEntity.ok().contentType(APPLICATION_JSON).body(rows)` (D4).
   - `GET /programme.csv` → `ResponseEntity<String>` with `Content-Type: text/csv;charset=UTF-8`
     (D2/D3). The body is `HEADER + "\n"` plus one `csvLine()` per row.
   - No `produces=`.
4. `ProgrammeEmptyTest` (its own class, so its own fresh database):
   - `/programme` → `[]`;
   - `/programme.csv` → exactly `slot,start,end,room,talkId,title,format,speakers\n`, first byte `s`.
5. `ProgrammeExportTest` (one shared database: **each method uses its own cells, listed in a comment
   at the top of the class, and asserts only on its own `talkId`s**, filtering the rows by id):
   - order and content: talks in `R2/S3`, `R1/S3` and `R3/S1` → `S1 R3`, `S3 R1`, `S3 R2` in both
     JSON and CSV. `start`/`end` in `HH:mm` (`09:00`); `talkId` a number; `format`.
   - a workshop in `R1/S4`.
   - "nothing else" (S1):
     - an accepted talk that isn't scheduled is absent;
     - a talk taken out with `DELETE` is absent;
     - a talk taken out and put back in another cell appears once, in the new cell.
   - speakers: main speaker first, then two co-speakers in the order they were added (the
     higher-id speaker added first). The CSV field is `A; B; C`.
   - changes after scheduling (D1):
     - rename the main speaker via `PUT /speakers/{id}` (name and valid email);
     - add a co-speaker;
     - withdraw the talk (if D1 a).
     The row shows the current data.
   - CSV quoting over HTTP:
     - the title `Kotlin, "really" fast`;
     - the title `Say "hi"`;
     - a title with `\r\n`;
     - a speaker name with a comma.
     Compare each row's line against an exact expected string.
   - UTF-8: the speaker `Åse Ødegård`. The raw bytes decode as UTF-8, and the media type is
     `text/csv` with charset UTF-8.
   - The whole CSV ends with `\n` and not `\n\n`, with no BOM.
   - `Accept: application/json` on `/programme.csv` → 200 `text/csv` (D3).
   - `Accept: text/csv` on `/programme` → 200 JSON (D4).
   - No `Content-Disposition` (D8). `/programme/` and `/programme.csv/` → 200 (D9).
6. `./mvnw package`: green, no warnings, coverage not dropped. `WireFormatTest` unchanged.
   `snapshot-api.sh` "after" against a local service, then `diff` with "before": no diff. (The
   script doesn't call `/programme`, so this only proves nothing else moved.)

Check: `./mvnw test -Dtest='Programme*Test'` green, then the full `./mvnw package`. Commit:
`feat: Programme export as JSON and CSV`.

## Phase 3: Hurl and a prod-like run

1. `examples/programme-export.hurl` runs against databases that keep their data, prod included:
   - Unique emails.
   - Find a free cell first with `GET /programme/{room}/{slot}`: use the free answer from Phase 0
     (`404` or empty). Prefer a cell the harness is less likely to use, for example `R3/S5` and
     `R3/S2`.
   - Schedule two talks, GET both views, and assert with JSONPath filters on our `talkId`s:
     the order, `speakers`, the CSV `Content-Type` and the CSV with `Accept: application/json`.
   - **At the end, `DELETE` both of our entries** (only ours).
   - If no reliably free cell can be found, keep the file out of `examples/` (local only) and say so
     here.
2. `workflow/scripts/local-rollout.sh` **twice in a row**: the second run proves a rerun passes.

Check: both `local-rollout.sh` runs end with every Hurl file green. Commit:
`test: Hurl example for programme export`.

## Phase 4: Docs and finish

1. `docs/api.md` (both endpoints, with D1–D9 in a line each), `docs/architecture.md` (the new
   classes).
2. Move the approved decisions table into `workflow/specs/programme-export.md` (spec-checker and
   pre-claim read it there).
3. `/execute-plan` "Before finalising" and "After completing":
   - update from `main`, and raise the coverage floor if it went up;
   - `git mv` the plan and review to `done/`;
   - `REVIEW.md`: §2 for B1/B2 (this review caught them), and a §3 draft row, unticked;
   - `workflow/scripts/check-review.sh`.
4. Before claiming: scheduling's harness checks green, then `/pre-claim programme-export` after
   the deploy (`/health` shows the SHA).

Check: `check-review.sh` output reported to the human. Commit: `docs: Programme export in docs, plan done`.

## Review response

| Finding | Response |
|---|---|
| B1 shared DB per class | `ProgrammeEmptyTest` in its own class; each method has its own cells and filters on its own ids; fixture rules in Context |
| B2 Hurl not rerunnable, pollutes prod | Phase 3: free cell, `DELETE` only our entries, run twice; local-only fallback |
| S1 "nothing else" untested | Phase 2.5: unscheduled, taken out, moved |
| S2 D1 depends on #16; renaming | Phase 0.4 checks D1; `PUT /speakers/{id}` named in Context; co-speaker added after scheduling |
| S3 D2 fallback, exact header | D2 rewritten (forced charset, `byte[]` fallback, parsed media type in test) |
| S4 Phase 2 untested commit | DAO + controller + tests merged into one phase; Phase 1 runs full `./mvnw package` |
| S5 plan untracked | Plan, review and spec committed on `25-programme-export`; triage plan excluded |
| S6 open-ended dependency | Base on `origin/16-scheduling` if pushed; `TODO(human)` time limit; no claim before scheduling is green |
| S7 CSV edge cases | Added to Phase 1 unit tests and Phase 2 HTTP tests (exact expected strings, no BOM) |
| M1 checklist order | Phase 1 first, Phase 0 gates 2–4 |
| M2 approval timing, single options | Approval before Phase 2; D3/D5/D7 marked spec-fixed; D6 has b) |
| M3 Spring 7 notes | In Context; Phase 0.3 checks for clashing mappings; D8 tested |
| M4 trailing slash | D9 + test |
| M5 snapshot usage | Phase 0.6 before, Phase 2.6 after, with args and limits |
| M6 D7 fallback | `array_position` in SQL |
| M7 decisions in spec | Phase 4.2 |
| M8 Phase 1 Check | Full `./mvnw package` |
| M9 "one snapshot" | Reworded in Phase 2.2 |
| M10 private helpers | "Copy" in Context; Phase 0 fills placeholders |
