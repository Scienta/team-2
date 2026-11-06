
# Project-Specific Development Guidelines

This file contains **overrides and additions** to the team workflow described in `.claude/rules/team-workflow.md`.

## Stack

- Java 11, Spring Boot 2.7, Maven wrapper (`./mvnw`)
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
- Coverage: JaCoCo report in `target/site/jacoco/index.html` after `./mvnw package`. Line coverage
  of `src/main/java` may not drop below `coverage.line.minimum` in `pom.xml`; raise the floor when
  coverage goes up, lower it only with a person's consent. See `workflow/coverage/README.md`
