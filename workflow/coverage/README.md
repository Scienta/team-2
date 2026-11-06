# Test coverage

Evidence that a change works: which production code the tests visit, and a rule that line coverage
never goes down.

## What is measured

JaCoCo measures every class in `src/main/java` while the tests run. Test code isn't measured, and
nothing is excluded.

`./mvnw package` writes the report to `target/site/jacoco/`:

- `index.html`: browse by package, class and source line (green covered, red missed, yellow
  partly covered branches)
- `jacoco.csv` and `jacoco.xml`: the same numbers for scripts (`line-coverage.sh` reads the report-wide
  total from `jacoco.xml`, the number the check uses; summing `jacoco.csv` rows can count a line twice)

`./mvnw package -DskipTests` (what the `Dockerfile` runs) writes no report.

## Current coverage

```bash
./mvnw package
workflow/coverage/line-coverage.sh
```

```
Lines covered: 340/430 (79.1 %)
Ratio:         0.790 (rounded down, the value for coverage.line.minimum)
Floor in pom:  0.790
```

## The floor is frozen at 0.862

- `coverage.line.minimum` in `pom.xml` is the floor: `./mvnw package` fails when line coverage is
  below it. A coverage drop is a broken window; fix it with tests.
- **Frozen on 2026-11-06 at 0.862** (issue #43). Until then every change that raised coverage
  raised the floor too, so with several PRs open at once each merge raised it again and every other
  open PR conflicted on that one line of `pom.xml` (PRs #31 and #42 needed a merge each).
- **Don't change the floor.** Agents don't raise it and don't lower it, also when coverage went up.
  Only a person on the team changes it, in its own PR.
- New code still needs tests: a change that drops coverage below 0.862 fails the build.

## Before merging

Update the branch with the latest `main`, then run `./mvnw package`. The floor isn't touched.
