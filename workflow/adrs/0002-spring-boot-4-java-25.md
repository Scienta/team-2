# 0002: Spring Boot 4.1 on Java 25, with nothing changed on the wire

Status: accepted, 2026-11-06

## Context

The harness feature request `spring-boot-4` asks for Spring Boot 4.x on a supported Java, with
nothing else changing for users. The speakers, talks and ratings specs, formats included, must keep
working exactly as before (co-speakers is claimed too). `/actuator/info` must report the versions
actually running. We were on Java 11 and Boot 2.7.18. The jump crosses `javax` → `jakarta`, Spring
6 and 7, Jackson 2 → 3 and Testcontainers 1 → 2, each of which changes defaults that users can see.
Before the upgrade nothing pinned the formats the talks spec fixes (`submittedAt`, `submittedOn`).

## Decision

- **Versions:** Java 25 (LTS), Spring Boot 4.1.2, Spring Framework 7.0.10, Maven wrapper 3.9.16, and
  the Temurin 25 images in the `Dockerfile`.
- **Pin first, then upgrade in two steps** (2.7 → 3.5.16 → 4.1.2), each step green and committed:
  - `WireFormatTest` was written on Boot 2.7. It asserts on raw response strings, so the JSON
    library swap doesn't touch it. It was checked by breaking each behaviour it guards.
  - `workflow/scripts/snapshot-api.sh` records a normalised baseline
    (`workflow/plans/done/spring-boot-4.baseline.txt`).
  - Every step had to pass `WireFormatTest` with its assertions unchanged (one test was added,
    `errorJsonIsUtf8`) and diff empty against the baseline.
- **Jackson 3 with `spring.jackson.use-jackson2-defaults=true`**, not the deprecated Jackson 2
  bridge, plus two explicit fixes the defaults don't cover. Both were found by `WireFormatTest`:
  - `TalkDto.submittedOn` is written with `ToStringSerializer`. Jackson 3 writes `java.sql.Date` as
    a timestamp, the wrong format and, outside UTC, the wrong day.
  - `FieldErrorMixin` keeps Boot 2.7's key order in the entries of a 400's `errors` array.
- **Trailing slash:** `WebConfig` registers `UrlHandlerFilter` (`wrapRequest`, not a redirect) for
  every path. The speakers spec requires `/speakers/` to work, and Spring 6 stopped matching it.
- **Error charset:** a `CharacterEncodingFilter` on the error dispatch keeps
  `application/json;charset=UTF-8` on error bodies.
- **Renamed properties:**
  - `server.error.include-message` → `spring.web.error.include-message`
  - `server.error.include-binding-errors` → `spring.web.error.include-binding-errors`
  - `server.servlet.encoding.force` → `spring.servlet.encoding.force`
  - `spring.jackson.parser.allow-comments` → `spring.jackson.json.read.allow-java-comments`
- **Framework error wording:** the wording of messages Spring or Jackson generate (bad JSON, type
  mismatch, 405, 415, unknown route, validation summary) is accepted as changed, pending the product
  owner's answer. Status codes, keys and our own reasons are unchanged.

## Consequences

- No database migration: `schema.sql` is unchanged and idempotent. Rolling back to the previous
  image needs no data step.
- Boot 4 ignores unknown property names silently, so a missed rename only shows up as changed
  behaviour. `WireFormatTest` is the guard.
- Every build needs a JDK 25. The CI test job uses `setup-java` 25.
- Jackson 3 code goes in `tools.jackson.*`; annotations stay in `com.fasterxml.jackson.annotation`.
- For any later framework or JDK change, run `WireFormatTest` and diff `snapshot-api.sh` against a
  baseline taken before the change.
