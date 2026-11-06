# Plan: Workshop registration

- [x] Phase 0: Commit the approved spec, this plan and its review
- [x] Phase 1: Registrations table, `POST` and `GET /workshops/{id}/registrations`, eligibility and validation
- [x] Phase 2: Capacity, duplicates, a real rush, and the lock test
- [ ] Phase 3: Hurl example in `examples/`
- [ ] Phase 4: Update `docs/`, raise the coverage floor, finish (draft claim-log row, plan to done)

## Context (read this after a restart)

- Spec: `workflow/specs/workshop-registration.md` (decisions D1 to D18 at the end). Claimed? No. A
  person claims, a claim is permanent.
- Branch: `workshop-registration`, on top of `main` at `50ea65c`, which has Acceptance.
- **Issue:** `.claude/rules/issues.md` wants one issue per task, a branch `<issue-number>-<short-name>`
  and `Closes #<n>` in the PR. `gh` isn't installed on this machine, so a person checks the open issues
  and creates this one in the GitHub UI. Issue number: `TODO(human)`. **Before the first push:**
  `git branch -m workshop-registration <n>-workshop-registration`, and update this line.
- PR via the GitHub UI, never `gh`. Merging to `main` deploys, and a rollout has cost points each time
  (`REVIEW.md` §4), so this is one PR.
- Commands: `./mvnw package`, `./mvnw test -Dtest=WorkshopRegistrationTest`,
  `workflow/coverage/line-coverage.sh`, `examples/run.sh [base-url]`. Docker must run. No `LOCAL.md`.
- Hurl isn't on this machine's `PATH`. Don't claim it ran if it didn't.
- Code in `src/main/java/no/scienta/cfp/`. New classes, and no changes to `ConferenceService`,
  `TalkController` or `TalkDao`:
  - `WorkshopController`: the two endpoints.
  - `WorkshopService`: the rules, one `@Transactional` register method.
  - `RegistrationDao`: SQL (`JdbcTemplate`, as `TalkDao`).
  - `RegistrationDto`: public fields `id`, `workshopId`, `name`, `email`, as `TalkDto`.
  - We call the existing `TalkDao.findById` and `TalkDao.lock`.
- Test style: `RatingsTest` (`@Autowired JdbcTemplate`, `send` with a content type, the bad-body
  shape) and `AcceptanceTest` (`CompletableFuture` concurrency, `rateAll`, `decide`). Fresh database
  with `TestDatabase.fresh(registry)`. Tests share one database, so each makes its own speakers and
  talks (a speaker has at most 3 talks). Set a status directly with
  `jdbc.update("UPDATE talk SET status = ? WHERE id = ?", ...)`.

## Decisions for the human to check

Rows D1 to D18 in the spec are approved by Håkon on 2026-11-06 (D6, D7 and D17 were changed by the plan
review afterwards, and await confirmation). Håkon says the team has no product owner to ask, as
Acceptance did. `AGENTS.md` says a person can ask the organisers' product owner about
business rules. **So pre-claim item 1 doesn't pass.** A person must either get answers, or record an
explicit exception in `REVIEW.md` §3 when claiming. An agent can't make that call.

Before any claim, deploy and read `workflow/scripts/harness.sh feedback`: unclaimed features are
checked too. So the risky decisions can be tested before the permanent click:

| Decision | Our answer | Fallback if a check goes FAULTY on it |
|---|---|---|
| D2 not a workshop | 404 | 409 |
| D5 `GET` on a workshop not accepted | 200 `[]` | 404 |
| D7 malformed email | 400 | Relax the pattern to `Utils.isValidEmail` |
| D10 duplicate | 409 | None we'd guess: read the transcript |
| D11 surrounding whitespace is the same email | same | Ignore case only |

## Phase 0: Commit what's approved

1. Check the spec's decisions match this plan, then commit the spec, the plan and the review.

Check: `git status` is clean. Commit: `docs: Add workshop registration plan and review`.

## Phase 1: Endpoints, eligibility and validation

1. Append to `src/main/resources/schema.sql` (bottom, `IF NOT EXISTS`, no old statements touched):

   ```sql
   -- 2026-11 workshop registration; id gives the order they registered in, email_key the same person
   CREATE TABLE IF NOT EXISTS workshop_registration (
       id        BIGSERIAL PRIMARY KEY,
       talk_id   INTEGER NOT NULL,
       name      TEXT NOT NULL,
       email     TEXT NOT NULL,
       email_key TEXT NOT NULL,
       UNIQUE (talk_id, email_key)
   );
   ```

   `email` is as typed. `email_key` is the trimmed, lower-cased email (step 3). The unique constraint's
   index also serves count and list by talk.
2. `RegistrationDao`:
   - `Long insert(talkId, name, email, emailKey)`: `INSERT ... ON CONFLICT (talk_id, email_key) DO
     NOTHING RETURNING id`, read with `queryForList` as `TalkDao.insertIfBelow`. Null means duplicate.
   - `int count(talkId)`.
   - `List<RegistrationDto> findByWorkshop(talkId)`, `ORDER BY id`.
3. `WorkshopService.register(long id, Map<String, Object> body)`, `@Transactional`, in this order
   (D12: 404, then 400, then 409). The body is a `Map`, so a non-string field reaches the service and an
   unknown id wins over a bad field, as `rate` and `decide`:
   1. `talkDao.findById(id)`: null, or `format` not `WORKSHOP`, gives 404 (D1, D2).
   2. `name` and `email` must be `String`s with text in them, and no control characters, else 400 (D6).
      One helper trims both: `s.replaceAll("^[\\s\\p{Z}]+|[\\s\\p{Z}]+$", "")`, which also takes a
      no-break space. A control character is `\\p{Cntrl}` anywhere in the trimmed value, so a NUL can't
      reach Postgres and give a 500.
   3. The trimmed email must match `^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$`, else 400 (D7). `Utils.isValidEmail`
      stays as it is: `updateSpeaker` uses it, and it accepts `@example.com`.
   4. `talkDao.lock(id)`.
   5. Read the status in a **new** statement, `talkDao.findById(id).status`. **Don't reuse the talk from
      step 1:** it can be from before the lock wait, and a registration could get in after a withdrawal
      (`TalkDao.lock` Javadoc). Not `ACCEPTED` gives 409 (D3, D4).
   6. Phase 2 adds capacity and duplicates here.
   7. Insert, with `name` and `email` as typed. Return a `RegistrationDto`.
4. `WorkshopService.registrations(long id)`: 404 as step 1, otherwise the list. No status check (D5), no
   lock.
5. `WorkshopController`: `@PostMapping("/workshops/{id}/registrations")` with
   `@ResponseStatus(HttpStatus.CREATED)` and `@RequestBody Map<String, Object> body`, plus the
   `@GetMapping`. `@PathVariable Long id`, as `TalkController`. Spring gives 400 for bad JSON, an array,
   JSON `null`, no body and a non-numeric id, and 415 for `text/plain` (D8).
6. `WorkshopRegistrationTest`:
   - `registersAndReturnsTheRegistration`: 201 with `id`, `workshopId`, `name`, `email`.
   - `listIsInRegistrationOrder`: three registrations, `GET` gives them by ascending `id`.
   - `emailIsReturnedAsTyped`: `"  ADA@Example.com "` unchanged from `POST` and `GET` (D11).
   - `unknownTalkIs404`: `POST` and `GET`, with `2147483647` (D1).
   - `talkThatIsNotAWorkshopIs404`: `PRESENTATION`, accepted, `POST` and `GET` (D2).
   - `workshopThatIsNotAcceptedIs409`: `SUBMITTED`, `WAITLISTED`, `REJECTED`; the list stays `[]` (D3, D5).
   - `withdrawnWorkshopKeepsItsListAndTakesNoMore`: through the real API: rate twice, `PUT` status
     `ACCEPTED`, register, `POST` withdraw, then `POST` 409 and `GET` still lists them (D4, D5, D18).
   - `missingOrBlankFieldsAre400`: `{}`, and each of `name`, `email` missing, null, `""`, `"  "`,
     `" "`, a number, `"a\u0000"` (D6).
   - `malformedEmailIs400`: `not-an-email`, `@example.com`, `ada@example.`, `@.`, `a b@c.d`, `a@b@c.d`,
     `ada@localhost` (D7).
   - `badBodies`: no body, not JSON, an array, JSON `null` give 400; `text/plain` 415; non-numeric id 400;
     unknown id with bad JSON 400 (D8).
   - `precedence`: unknown talk with a bad field is 404; a non-accepted workshop with a bad field is 400
     (D12).

Check: `./mvnw package` is green, with all 11 tests in `WorkshopRegistrationTest`. Commit:
`feat: Register for accepted workshops`.

## Phase 2: Capacity, duplicates, a rush, and the lock test

1. In `WorkshopService.register`, at step 6: `count >= MAX_ATTENDEES` (20) gives 409 (D9). A null from
   `insert` gives 409 (D10). So a duplicate on a full workshop is 409 too, and there's no separate
   duplicate query or `catch`.
2. Tests:
   - `twentyFitAndTheTwentyFirstIs409`: 20 give 201, the next 409, the list has 20 (D9).
   - `sameEmailTwiceIs409`: `ada@example.com`, then `ADA@Example.com`, `" ada@example.com "` and
     `" ada@example.com"`, all 409 after the first; the list has one (D10, D11).
   - `sameEmailOnAnotherWorkshopIsFine`: two workshops, both 201.
   - `aRushNeverOverfills`: 60 parallel `POST`s with distinct emails: exactly 20 give 201, 40 give 409,
     no other status, the list has 20 with unique ids (D17). Log the elapsed time (see "After the deploy").
   - `aRushWithOneEmailRegistersOnce`: 20 parallel `POST`s, the same email in different case: exactly one
     201, 19 give 409 (D17).
   - `aRegistrationWaitingForTheLockSeesTheWithdrawal` (review S1): on its own connection from
     `jdbc.getDataSource()`, with auto-commit off, lock an accepted workshop's row. Send a `POST`
     asynchronously. Poll `pg_stat_activity` until a session has `wait_event_type = 'Lock'`. Set
     `WITHDRAWN` on that connection and commit. Assert 409 and an empty list.
3. Falsify it once, by hand: temporarily use the talk from step 1 for the status. The lock test must go
   red. Put it back.

Check: `./mvnw package` green. Run `./mvnw test -Dtest=WorkshopRegistrationTest` three times, since a race
shows up intermittently: `aRushNeverOverfills` must never report more than 20. Commit:
`feat: Cap workshops at 20 attendees, one registration per email`.

## Phase 3: Hurl example

1. `examples/workshop-registration.hurl`: two speakers (`{{newUuid}}` in their emails only, since the
   limit is 3 talks each). An accepted workshop (two ratings, `PUT /talks/{id}/status`, see
   `examples/acceptance.hurl`), a non-workshop, a workshop not accepted. Then 201 with a literal
   `ada@example.com` (the workshop is new on every run, and uniqueness is per talk), list order, 409 for
   `ADA@Example.com`, `name` missing 400, unknown id 404, non-workshop 404, not accepted 409. The capacity
   limit stays in Java.
2. `./mvnw package -DskipTests`, `docker compose up -d`, `java -jar target/cfp.jar`, then `examples/run.sh`,
   if Hurl is available. If it isn't, say so in the commit and the report, and leave it for a person: this
   is the file pre-claim item 5 runs against our URL.

Check: `examples/run.sh` passes every file, or the report says it wasn't run. Commit:
`test: Add Hurl example for workshop registration`.

## Phase 4: Docs and finish

1. `docs/api.md`: the two endpoints and their status codes. `docs/data-model.md`: the table.
   `docs/architecture.md`: the new classes, and the lock.
2. Merge the latest `origin/main`, then `./mvnw package` and `workflow/coverage/line-coverage.sh`. Set
   `coverage.line.minimum` to the printed ratio if it went up (`workflow/coverage/README.md`).
3. `workflow/scripts/check-paths.sh`. Then `REVIEW.md`: a §2 story for what the review caught; a draft §3
   row with the evidence, with status and tick left as pending the harness, never guessed;
   `workflow/scripts/check-review.sh`, and report what it lists.
4. Check off the boxes, then `git mv` the plan and review to `workflow/plans/done/`, as the last step.
5. Ask the person whether to push.

Check: `./mvnw package` green, no warnings, no coverage drop. Commit:
`docs: Workshop registration in docs, plan done`.

## After the deploy (for the person, not a phase)

1. Rename the branch (see Context), PR in the GitHub UI with `Closes #<n>`.
2. `/health` shows the merged SHA.
3. `workflow/scripts/harness.sh feedback`: read `workshop-registration` for FAULTY checks. Use the fallback
   table above.
4. Compare the rush's local time with the logs and timeline: if `/health` or other checks go FAULTY during a
   rush, the serial section is too long (review S5). The next step is a single conditional insert.
5. `/pre-claim workshop-registration`. A person claims, only if it says yes or the exception for item 1 is
   recorded.

## Review response

| Finding | Response |
|---|---|
| B1 no PO | Not ours to settle. "Decisions" states that item 1 doesn't pass; a person gets answers or records an exception |
| S1 re-read untested | Added `aRegistrationWaitingForTheLockSeesTheWithdrawal`, and a by-hand falsification |
| S2 blocked Phase 3 | Gone: Acceptance merged (`50ea65c`) while the review ran. Plan to done is the last step |
| S3 nothing committed | Phase 0 |
| S4 watch after the claim | Fallback table, read before the claim |
| S5 health and the rush | Fewer round trips (M1), elapsed time logged, checked after the deploy |
| S6 issue setup | In Context, with `TODO(human)`, and the rename before the first push. The base is now `50ea65c` |
| S7 draft §3 row | Phase 4 drafts it, status and tick pending |
| S8 weak email check | Stricter private pattern, D7 updated, cases pinned in `malformedEmailIs400` |
| M1 backstop | `ON CONFLICT DO NOTHING RETURNING id`, D17 updated |
| M2 whitespace | One trim helper, and written ` ` |
| M3 NUL | Control characters give 400, D6 updated |
| M4 unpinned cases | Added to `missingOrBlankFieldsAre400` and `badBodies`, D6 and D8 updated |
| M5 Hurl recipe | Literal email, two speakers |
| M6 test pointers | `RatingsTest` and `AcceptanceTest` in Context |
| M7 commands | Added; phase checks use `./mvnw package`. `AcceptanceTest` is on `main` now |
| M8 stale reference | Removed: `REVIEW.md` §4 is on `main` |
| M9 cutoff | Gone with S2: Acceptance has merged |
