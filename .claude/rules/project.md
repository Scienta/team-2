
# Project-Specific Development Guidelines

This file contains **overrides and additions** to the team workflow described in `.claude/rules/team-workflow.md`.

## Stack

- Java 25, Spring Boot 4.1 (Spring Framework 7, Jackson 3, Testcontainers 2), Maven wrapper (`./mvnw`);
  upgraded from Java 11 / Boot 2.7, see `workflow/adrs/0002-spring-boot-4-java-25.md`
- Build with a JDK 25: `LOCAL.md` has the `JAVA_HOME` (and Docker settings) for this machine
- Jackson 3 lives in `tools.jackson.*`; its annotations are still `com.fasterxml.jackson.annotation`
- Boot 4 property names (`spring.web.error.*`, `spring.servlet.encoding.*`, `spring.jackson.json.read.*`):
  Boot ignores unknown property names silently, so a wrong name only shows up in `WireFormatTest`
- Postgres, schema in `src/main/resources/schema.sql`
- Code in `src/main/java/no/scienta/cfp/`, tests in `src/test/java/no/scienta/cfp/`
- Background docs on the base app in `docs/` (API, architecture, data model, known issues, operations)

## Code Conventions

Match the existing code:

- 4-space indentation, no tabs
- Constructor injection with `private final` fields, no field `@Autowired`
- Imports grouped as `java.*` first, then third-party, separated by a blank line; no wildcard imports
- Javadoc only on classes and methods whose purpose isn't obvious from the name

## Testing Conventions

- JUnit 5 (`org.junit.jupiter`)
- HTTP-level tests use `@SpringBootTest(webEnvironment = RANDOM_PORT)` with a fresh database from
  `TestDatabase.fresh(registry)` in a `@DynamicPropertySource` method; see `TalksTest`
- Docker must be running: the tests start Postgres with Testcontainers
- Behaviour visible over HTTP also gets a Hurl example in `examples/`
- Line coverage may not drop: see `workflow/coverage/README.md`
