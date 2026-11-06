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
- When a change raises coverage, the same change raises `coverage.line.minimum` to the ratio
  `line-coverage.sh` prints (in a plan, in its last commit). Otherwise coverage can slide back down
  to the old floor.
- Lowering the floor needs explicit consent from a person on the team (deleting well-tested code
  can lower the ratio without anything being wrong). Never lower it just to make the build pass.
- The floor is strict: with about 400 lines, one new untested line fails the build. New code must
  be covered at least as well as the codebase as a whole.

## Before merging

Parallel PRs can each pass on their own branch and still break `main` together: PR A raises the
floor, PR B adds less-tested code. The deploy still goes through (the `Dockerfile` skips tests), but
the CI `tests` job fails without anyone seeing it (`continue-on-error`), and the next
`./mvnw package` is red. So before merging:

1. Update the branch with the latest `main` (merge or rebase).
2. Run `./mvnw package` and `workflow/coverage/line-coverage.sh`.
3. Set the floor from that run, and commit.

## Quick fix under time pressure

A person on the team may agree to lower the floor to the ratio `line-coverage.sh` prints. Lower it
in the same PR, say so in the PR description, and add the missing tests in a follow-up that raises
the floor again.
