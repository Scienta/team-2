# Plan: Upgrade to Spring Boot 4.x (`spring-boot-4`)

- [x] Phase 0: Prerequisites: JDK 25 installed (`/opt/homebrew/opt/openjdk@25`), `jq`, Hurl, `LOCAL.md`
- [x] Phase 1: Pin today's wire behaviour on Java 11 / Boot 2.7 (`WireFormatTest`, `snapshot-api.sh`, baseline)
- [ ] Phase 2: Java 25 + Spring Boot 3.5.x, tests and snapshot diff green
- [ ] Phase 3: Spring Boot 4.1.2, tests and snapshot diff green; `AGENTS.md` team note
- [ ] Phase 4: Dockerfile and CI on Java 25; container run locally, snapshot diff, Hurl example
- [ ] Phase 5: Docs, rules, ADR, `REVIEW.md`; plan to done; hand over the production checklist

## Context (read this after a restart)

- Spec: `workflow/specs/Upgrade-to-Spring-Boot-4.x.md` (the harness feature request, slug
  `spring-boot-4`, released at tick 40). Claimed? **No.** A person claims, a claim is permanent.
  The rules: run on Boot 4.x on a supported Java; **nothing else changes for users** (speakers,
  talks, ratings specs, formats included); `GET /actuator/info` `versions.springBoot` must tell the
  truth (it comes from `SpringBootVersion.getVersion()` in `VersionInfoContributor`; never fake it).
- Co-speakers is claimed too (tick 68); its behaviour must also survive (`CoSpeakersTest`).
- Branch: `spring-boot-4`. PR via the GitHub UI, never `gh`. Merging to `main` deploys; if
  `/health` doesn't come up with the new SHA in 60 s the rollout puts the old image back.
- Phase 1 commit SHA (the baseline for "assertions unchanged"): `<fill in after Phase 1>`.
- Commands: `./mvnw package`, `./mvnw test -Dtest=<Test>`; machine settings in `LOCAL.md`
  (`JAVA_HOME` lines for JDK 11 and JDK 25, Colima `DOCKER_HOST`). Phase 1 runs on JDK 11,
  everything after on JDK 25.
- Current stack: `pom.xml` imports the `spring-boot-dependencies` BOM (no starter parent, so
  nothing like `-parameters` is configured for us), Boot 2.7.18, Java 11, Testcontainers 1.21.4
  pinned (`testcontainers.version`), compiler plugin 3.10.1 with `<release>11</release>`.
- Code in `src/main/java/no/scienta/cfp/` (all of it is small):
  - `SpeakerController`, `SpeakerForm`: `javax.validation` (`@Valid`, `@NotBlank`, `@Size`).
  - `TalkController`, `SpeakerController`: `@PathVariable` and `@RequestParam` **without names**,
    which need `-parameters` from Spring 6.1 on. Fix with the compiler flag, not by naming them.
  - `TalkDto`: public fields; `submittedAt` is `java.util.Date`, `submittedOn` is `java.sql.Date`.
    Their JSON format is fixed by the talks spec (`yyyy-MM-dd'T'HH:mm:ss.SSS+00:00`, `yyyy-MM-dd`)
    and today comes from Jackson 2 defaults. **No test or Hurl pins it before Phase 1.**
  - `Utils.norwegianDate` uses `new Locale("nb", "NO")` (deprecated on JDK 19+, a warning).
  - `VersionInfoContributor`: `/actuator/info` versions. `HealthController`: `/health`.
  - `application.properties`: `server.error.include-message`, `include-binding-errors`,
    `server.servlet.encoding.force`, `spring.jackson.parser.allow-comments` (all user-visible).
- Tests in `src/test/java/no/scienta/cfp/`: `java.net.http.HttpClient` against `RANDOM_PORT`
  (no `TestRestTemplate`); `TestDatabase` and `HealthTest` use
  `org.testcontainers.containers.PostgreSQLContainer<?>`; `RatingsTest` uses Jackson 2
  `ObjectMapper`/`JsonNode`; `ConferenceServiceTest` uses Mockito and
  `ResponseStatusException.getStatus()`. Test style to copy: `TalksTest`.
- CI: `.github/workflows/deploy.yml` builds the root `Dockerfile` (Maven + Temurin 11 images) and
  runs `./mvnw -B test` after deploy on `setup-java` `java-version: "11"` (not a gate).
- Database: `schema.sql` is all `CREATE ... IF NOT EXISTS`, run on every start
  (`spring.sql.init.mode=always`). The upgrade changes no table, so there is **no data migration**.
- `docs/` and `.claude/rules/` state Java 11 / Boot 2.7 as current facts (no rule that requires
  11): `docs/architecture.md:5,8,69`, `docs/api.md:6`, `docs/known-issues.md` D2,
  `.claude/rules/project.md:8`, `.claude/rules/team-workflow.md:34`. Updated in Phase 5. The
  organisers' text (`AGENTS.md:21` "as shipped", the README fagdag section) stays.

## Decisions for the human to check

- **D1. Java version**: Java **25** (current LTS, supported by Boot 4.1, matches the spec's
  example `25.0.5`). Alternative: 21.
- **D2. Boot version**: **4.1.2**, the latest 4.x on Maven Central today. Framework from its BOM.
- **D3. Path**: 2.7 → 3.5.x → 4.1.2 in two commits, as Spring's migration guides recommend, each
  with the full check, so a failure tells us which jump broke it. Only 4.1.2 is ever deployed.
- **D4. Jackson 3**: Boot 4 uses Jackson 3 (`tools.jackson`), whose defaults differ (alphabetical
  property order, `FAIL_ON_NULL_FOR_PRIMITIVES`, `FAIL_ON_TRAILING_TOKENS`, dates). Use Jackson 3
  with `spring.jackson.use-jackson2-defaults=true` (verify it exists in 4.1; otherwise configure the
  features one by one); `WireFormatTest` is the proof. Not the deprecated Jackson 2 bridge.
- **D5. Trailing slash**: **required** by `workflow/specs/speakers.md:20` (`/speakers/`,
  `/speakers/{id}/` work like without, `POST` included). Spring 6 stopped matching them; keep it
  with Framework's `UrlHandlerFilter.trailingSlashHandler("/**").wrapRequest()` (6.2+), for every
  path, since today every path accepts one. Not a redirect (breaks `POST`).
- **D6. Error bodies**: keys `{timestamp, status, error, message, path}` (plus `errors` on bean
  validation) and status codes stay; **our** reasons (`ResponseStatusException` texts) stay exact.
  Messages Spring or Jackson generate (bad JSON, `/talks/abc`, 415, 405, unknown route, the
  validation summary) will change wording, since Spring 6 and Jackson 3 rewrote them. No
  `ProblemDetail` (`spring.mvc.problemdetails.enabled` stays off). **Ask the PO** whether the
  framework's wording counts as a user-visible change (we can't keep it identical cheaply).
- **D7. `/actuator/info`**: keep `VersionInfoContributor` as is; it reads the real versions at
  runtime. No extra Boot contributors (would change the body).
- **D8. Admin-only output** (`/admin/talks.csv` Norwegian dates, `/admin/stats`): not in any base
  spec. Pinned in Phase 1; if JDK 25's CLDR data changes it, keep the JDK 25 output and record it in
  `docs/known-issues.md` (no harness impact).
- **D9. CI test job**: `setup-java` to 25; it stays after deploy and non-gating (out of scope).
- **D10. `AGENTS.md`**: the person asked for a team note there so other team members' agents learn
  the stack changed. Added under "Team workflow" (our section), in the Phase 3 commit, so it reaches
  `main` together with the upgrade. The organisers' lines above it stay untouched.

## Production migration (on Scaleway, after merge)

No database migration: no table changes, `schema.sql` is idempotent, and the old image still runs
against the same database, so a rollback needs no data step either. What does change in
production, and how each is covered:

| Change | Risk | Covered by |
|---|---|---|
| Base images `maven:3.9-eclipse-temurin-25`, `eclipse-temurin:25-jre` | Image pull / start fails | Phase 4 local `docker build --platform linux/amd64` + run; rollout's auto-rollback |
| Newer pgjdbc + `PGSSLMODE=require` against Scaleway's TLS Postgres | TLS handshake changes | Can't test locally (Compose has no TLS); rollout waits for `/health` 200 (DB reachable) else rolls back |
| Startup time on scale-to-zero (60 s rollout window) | Slower cold start | Phase 4: time from container start to `/health` 200, record it |
| JVM memory in the container | OOM on Java 25 defaults | `logs.sh` after deploy: no `OutOfMemoryError`, no restart loop |
| CI test job on Java 25 | Red tests job | Phase 4 step 2; job is non-gating |

Post-deploy checklist (a person, with the agent; evidence into `REVIEW.md` §3):
1. Actions: deploy job green; `curl $TEAM_URL/health` shows the merge SHA.
2. `curl $TEAM_URL/actuator/info`: `versions.springBoot` `4.1.2`, `versions.java` `25.x`.
3. `examples/run.sh $TEAM_URL`: all files pass, `spring-boot-4.hurl` included.
4. `.github/fagdag/logs.sh --since <deploy time>`: no `ERROR`, no stack traces at start-up.
5. `workflow/scripts/harness.sh feedback` over at least 5 ticks after the deploy: speakers, talks,
   ratings, co-speakers checks all still `OK`; record the tick numbers.
6. Only then `/pre-claim spring-boot-4`; a person claims.
7. Rollback if 3–5 fail: `git revert -m 1 <merge-sha>` via a PR, or re-tag the previous
   `app:<sha>` as `app:latest` and redeploy (README "Deploy"). No database step.

## Phase 0: Prerequisites — done

JDK 25 (`brew install openjdk@25`, 25.0.5), `jq`, Hurl (`brew install hurl`), a JDK 25 build line
in `LOCAL.md`. PO questions (D4, D6) can be asked in parallel; they're needed before the claim, not
before Phase 2.

## Phase 1: Pin today's behaviour (JDK 11 / Boot 2.7, before touching the pom)

The safety net. Everything here must pass on the **current** stack. Assert on **raw body strings**
(regex, `indexOf` order), not Jackson, so the file survives the Jackson 3 switch unchanged.

1. Commit the spec copy, plan and review first (`docs: Plan the Spring Boot 4 upgrade`).
2. New `src/test/java/no/scienta/cfp/WireFormatTest.java` (setup as `TalksTest`), asserting:
   - `submittedAt` matches `\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}\+00:00` and `submittedOn`
     `\d{4}-\d{2}-\d{2}` equal to its date part, on `POST /talks`, `GET /talks/{id}`, `GET /talks`,
     `GET /speakers/{id}/talks`;
   - key order of a talk (`id, title, format, level, speakerId, durationMinutes, status,
     submittedAt, submittedOn, averageRating, ratingCount, coSpeakerIds`), a speaker
     (`id, name, email, bio`) and a rating response; `null`s written out;
   - unknown properties ignored: `POST /talks` with `"durationMinutes": 999` gives 201 and 45;
   - trailing slash (D5): `POST /speakers/` 201, `GET /speakers/{id}/` 200 with the same body,
     `GET /speakers/{id}/talks/` 200, `GET /talks/{id}/` 200;
   - errors, status + keys exact, `message` exact only for our reasons: unknown talk 404 (`talk not
     found`), co-speaker main speaker, bad JSON 400, `text/plain` 415, `/talks/abc` 400,
     `DELETE /talks` 405, `GET /nope` 404, blank speaker name 400 with an `errors` array;
     error `timestamp` format recorded as today's;
   - trailing tokens: `PUT .../ratings/1` with `{"score":3} x` gives today's status;
   - `/* comment */` in a `POST /speakers` body is accepted;
   - the JSON `Content-Type` header value, exactly as today;
   - `/actuator/info` versions equal `System.getProperty("java.version")`,
     `SpringBootVersion.getVersion()`, `SpringVersion.getVersion()`.
3. `UtilsTest`: pin `Utils.norwegianDate` for a fixed instant (D8).
4. New `workflow/scripts/snapshot-api.sh <base-url> <out-file>`: curl + jq, a fixed request
   sequence covering the cases above, fresh unique e-mails per run, then ids, e-mails and
   timestamps replaced by placeholders that keep their *shape* (e.g. `<ts:....-..-..T..:..:..
   .SSS+00:00>`), framework-generated `message`s replaced by `<framework-message>` (D6), and the
   `Content-Type` header kept. Baseline from the local 2.7 jar into
   `workflow/plans/todo/spring-boot-4.baseline.txt`.

Check: `./mvnw package` (JDK 11) green; `snapshot-api.sh` run **twice** against the same 2.7
service and the two outputs diff empty (normalisation works). Commit:
`test: Pin wire formats before the Boot upgrade`. Record the SHA in Context.

## Phase 2: Java 25 + Spring Boot 3.5.x

1. `pom.xml`: `spring-boot.version` → latest 3.5.x; `java.version`, `maven.compiler.release`,
   compiler `<release>` → 25; compiler plugin → current 3.x with `<parameters>true</parameters>`.
2. Testcontainers: drop our version pin and `testcontainers.version`; use the BOM's version.
3. `javax.validation` → `jakarta.validation` (`SpeakerController`, `SpeakerForm`).
4. `Utils.norwegianDate`: `Locale.of("nb", "NO")`; run `UtilsTest` on JDK 25 now (M5, D8).
5. D5: `UrlHandlerFilter` as a `Filter` bean in a new small `WebConfig`.
6. Warnings: `ConferenceServiceTest` `getStatus()` → `getStatusCode()`; Mockito's dynamic-agent
   warning via surefire `argLine` `-javaagent:${org.mockito:mockito-core:jar}` (with
   `maven-dependency-plugin:properties`); anything else the compiler or surefire prints.

Check: on JDK 25, `./mvnw package` green; no `[WARNING]` from the compiler and no `WARNING` lines
in surefire output (Spring's logged WARNs from deliberate bad requests excepted); `WireFormatTest`
unchanged; `snapshot-api.sh` against `java -jar target/cfp.jar` diffs empty against the baseline;
`examples/run.sh` passes. Commit: `build: Java 25 and Spring Boot 3.5`.

## Phase 3: Spring Boot 4.1.2

1. `spring-boot.version` → `4.1.2`. `spring-boot-starter-web` → `spring-boot-starter-webmvc`; add
   the test starters Boot 4 splits out if the tests need them. Keep `actuator`, `validation`, `jdbc`.
2. Properties (S2): expected `spring.jackson.parser.allow-comments` →
   `spring.jackson.json.read.allow-java-comments=true` and `server.servlet.encoding.force` →
   `spring.servlet.encoding.force=true`; verify both in Boot 4.1's properties appendix. Also run once
   with `spring-boot-properties-migrator` and read its report, then remove it. Boot ignores unknown
   properties silently, so the comment and `Content-Type` assertions are the gate.
3. Jackson 3 (D4): `spring.jackson.use-jackson2-defaults=true`. `RatingsTest`:
   `com.fasterxml.jackson.databind` → `tools.jackson.databind`, `asText()` → `asString()`. If
   `WireFormatTest` fails on dates, order or tokens, configure (`JsonMapperBuilderCustomizer`,
   `@JsonPropertyOrder`) until it passes **without editing its assertions**.
4. Testcontainers 2: `testcontainers-postgresql`, `org.testcontainers.postgresql.PostgreSQLContainer`
   without `<?>` in `TestDatabase` (field and `pointAt`) and `HealthTest`.
5. `AGENTS.md`, end of "Team workflow" (D10): a short "Stack changed" note: Java 25 and Spring Boot
   4.1 since this PR (the "as shipped" line above is history), Jackson 3 (`tools.jackson`),
   Testcontainers 2, build with a JDK 25 (`LOCAL.md`), details in ADR 0002.
6. Fix the remaining compile errors and warnings.

Check: as Phase 2, on 4.1.2; `git diff <phase-1-sha> -- src/test/java/no/scienta/cfp/WireFormatTest.java`
is empty. Commit: `build: Spring Boot 4.1.2`.

## Phase 4: Dockerfile, CI, container check, Hurl

1. `Dockerfile`: `maven:3.9-eclipse-temurin-25` and `eclipse-temurin:25-jre`; nothing else.
2. `.github/workflows/deploy.yml` test job: `java-version: "25"` (D9).
3. `docker build --platform linux/amd64 --build-arg GIT_SHA=test -t cfp:test .` (slow under
   emulation), run against the Compose Postgres with
   `--add-host=host.docker.internal:host-gateway -e PGHOST=host.docker.internal -e PGSSLMODE=disable`
   plus `PGUSER/PGPASSWORD/PGDATABASE=cfp`; time to `/health` 200 with `"version":"test"`.
4. `snapshot-api.sh` against the container: diff against the baseline empty.
5. `examples/spring-boot-4.hurl`: `GET /actuator/info` 200, `versions.springBoot` `^4\.`,
   `versions.springFramework` `^7\.`, `versions.java` `^25`; a talk round trip with the
   `submittedAt` / `submittedOn` regexes; `POST /speakers/` 201. `examples/run.sh` against the
   container: all pass.

Check: steps 3–5 as stated, start-up time recorded. Commit:
`build: Java 25 images and Boot 4 Hurl example`.

## Phase 5: Docs and finish

1. `docs/architecture.md` (stack, Dockerfile, the renamed Jackson property), `docs/api.md:6`
   (framework error wording, D6), `docs/known-issues.md` (D2 note now JDK 25, D8 outcome, D5
   filter), `.claude/rules/project.md` "Stack", `.claude/rules/team-workflow.md` "Project Setup".
2. ADR `workflow/adrs/0002-spring-boot-4-java-25.md`: versions, Jackson 3 with Jackson 2 defaults,
   trailing-slash filter, framework error wording, why two steps, no DB migration.
3. `REVIEW.md`: §5 `snapshot-api.sh`; §2 stories in the commit where the net caught something
   (Phases 2–4), not batched here; §3 draft row (`TODO(human)` for tick until claimed); §1 if the
   workflow changed. Run `workflow/scripts/check-review.sh`, report what it lists.
4. `git mv` plan, review and baseline to `workflow/plans/done/`; commit. Ask the human to push and
   open the PR, and hand over the "Production migration" checklist above.

Check: `./mvnw package` green, `check-review.sh` output reported. Commit:
`docs: Spring Boot 4 upgrade done`.

## Review response

| Finding | Response |
|---|---|
| B1 trailing slash on wrong URLs | Phase 1 pins `POST /speakers/`, `GET /speakers/{id}/`, `.../talks/`; D5 reworded "required by speakers.md:20", `wrapRequest()`, 6.2+ |
| B2 framework error text changes | D6 split: our reasons exact, framework text status + keys only, normalised in snapshot, PO asked; `GET /nope` added |
| B3 snapshot e-mail collisions | Fresh e-mails per run; Phase 1 check is "two runs diff empty" |
| S1 unpinned input behaviour | Unknown properties, validation `errors`, error `timestamp`, trailing tokens added to Phase 1 |
| S2 migrator misses Jackson property | Expected targets named in Phase 3 step 2, tests are the gate |
| S3 Testcontainers 2 | Phase 2 drops our pin; Phase 3 always moves to TC 2, removes `<?>` |
| S4 JDK 25 / Spring 6 warnings | Mockito agent, `getStatusCode()`, `asString()` listed; Check covers surefire output |
| S5 Jackson in `WireFormatTest` | Raw-string assertions; Phase 1 SHA recorded in Context; diff must be empty |
| S6 diff too late | Snapshot diff and `examples/run.sh` in Phase 2 and 3 Checks |
| S7 thin pre-claim evidence | Post-deploy checklist step 5: harness feedback ≥ 5 ticks, all base checks OK |
| S8 Phase 0 blocks | Phase 0 done; PO questions in parallel, needed before claim |
| M1 `@RequestParam` | In Context: fix with `-parameters`, not names |
| M2 untracked, baseline location | Phase 1 step 1 commits them; baseline in `plans/todo/`, moves to `done/` |
| M3 REVIEW.md timing | Phase 5 step 3: §2 in the commit where it happened |
| M4 Colima, emulation, jq | `--add-host` in Phase 4; slow build noted; `jq`, Hurl in Phase 0 |
| M5 `norwegianDate` on JDK 25 | Phase 2 step 4 |
