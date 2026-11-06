# Test coverage

Evidence that a change works: which production code the tests visit, and a rule that line coverage
never goes down.

## What is measured

JaCoCo measures every class in `src/main/java` while the tests run. Test code isn't measured, and
nothing is excluded.

`./mvnw package` writes the report to `target/site/jacoco/`:

- `index.html`: browse by package, class and source line (green covered, red missed, yellow
  partly covered branches)
- `jacoco.csv` and `jacoco.xml`: the same numbers for scripts

`./mvnw package -DskipTests` (what the `Dockerfile` runs) writes no report.

## Current coverage

```bash
./mvnw package
workflow/coverage/line-coverage.sh
```

```
Lines covered: 278/385 (72.2 %)
Ratio:         0.722 (rounded down, the value for coverage.line.minimum)
Floor in pom:  0.722
```

## The ratchet

- `coverage.line.minimum` in `pom.xml` is the floor: `./mvnw package` fails when line coverage is
  below it. A coverage drop is a broken window; fix it with tests.
- When a change raises coverage, the same change raises the floor to the ratio the script prints.
  Otherwise coverage can slide back down to the old floor.
- Lowering the floor needs explicit consent from a person on the team (deleting well-tested code
  can lower the ratio without anything being wrong). Never lower it just to make the build pass.
