# Plan: Measure test coverage, and don't let it go down

- [x] Phase 1: JaCoCo in `pom.xml`, coverage report on every `./mvnw package` (no rule yet)
- [x] Phase 2: Script that prints the line coverage, `workflow/coverage/line-coverage.sh`
- [x] Phase 3: Ratchet rule, `./mvnw package` fails if line coverage drops below the recorded floor
- [x] Phase 4: Put the rule into the workflow (`execute-plan`, `make-plan`, `.claude/rules/`, `workflow/`)
- [x] Phase 5: Prove the rule, record it in `REVIEW.md`, move the plan to `workflow/plans/done/`

## Context (read this after a restart)

- Goal: stronger evidence that a change works. Measure and report which production code the tests
  visit, and **don't let line coverage of the production code go down** when we make a change.
- Branch: `coverage-gate`. PR via the GitHub UI, never `gh`. Merging to `main` deploys.
- "Harness" here means **our own gates**. The organisers' harness is hands off (`AGENTS.md`).

### Decisions agreed with the human (2026-11-06)

- Measure test coverage of the production code (`src/main/java`), all of it.
- Rule: **line coverage must not go down** with a change.
- **No CI changes**: `.github/workflows/deploy.yml` and the `Dockerfile` stay as they are.

### Decisions this plan makes (check them)

- D1. Tool: JaCoCo (`org.jacoco:jacoco-maven-plugin` 0.8.16; 0.8.12 can't read JDK 27 class files
  and floods the test log with instrumentation stack traces), nothing else. No Python, no new
  tools on the developer machine.
- D2. The "floor" is a property in `pom.xml`, `coverage.line.minimum`, a ratio with 3 decimals
  (start: the value `line-coverage.sh` prints when Phase 3 starts; `main` keeps moving). JaCoCo's `check` goal fails the build below it.
- D3. **Ratchet:** when a change raises coverage, the same change raises the floor to the new value
  (rounded down to 3 decimals). A change that doesn't raise the floor when it could is a review
  finding, not a build failure.
- D4. The rule is bound to `./mvnw package` (report and check in phase `test`), so it's part of
  "No broken windows". `-DskipTests` (the `Dockerfile`) skips it, since there is no coverage data.
  Side effect: the CI `tests` job (`./mvnw test`, runs after deploy, doesn't block it) also fails
  when coverage drops. That's a signal, not a deploy change.
- D5. Measured over the whole `src/main/java`, no exclusions. Test code isn't measured.
- D6. Lowering the floor needs the human's explicit consent (e.g. deleting well-tested code can
  lower the ratio). Never lower it to make the build pass.

### Baseline (2026-11-06)

After Phase 1, on `origin/main` 4ee0b38: lines 278/385 (72.2 %). Earlier, at 3b64455: lines 210/369 (56.9 %), branches 66/152 (43.4 %). Weakest: `ConferenceService` 87/179,
`ReportController` 3/25, `Utils` 15/37, `VersionInfoContributor` 1/7.

## Phase 1: JaCoCo report

- In `pom.xml`: property `jacoco.version` `0.8.16` next to `testcontainers.version`. Plugin
  `org.jacoco:jacoco-maven-plugin` with executions `prepare-agent` (default phase) and `report`
  bound to phase `test`.
- Surefire 3.5.4 picks up `argLine` from `prepare-agent`; there's no custom `argLine` today. If one
  is added later, it must start with `@{argLine}`.
- Verify: `./mvnw package` green without new warnings; `target/site/jacoco/index.html` and
  `jacoco.csv` exist; `./mvnw -q package -DskipTests` still works (that's what the `Dockerfile` runs).
- No unit test: build config. The report is the evidence.
- Commit: `build: Add JaCoCo coverage report`.

## Phase 2: Coverage summary script

- `workflow/coverage/line-coverage.sh` (executable, `set -euo pipefail`, plain `awk` over
  `target/site/jacoco/jacoco.csv`): prints covered/total lines and the ratio with 3 decimals rounded
  down (the value to put in the floor), and the current floor from `pom.xml`. Clear message if the
  CSV is missing ("run ./mvnw package first").
- Test by hand: output matches JaCoCo's HTML report totals (`target/site/jacoco/index.html`).
- `workflow/coverage/README.md`: what is measured, where the HTML report is, how the ratchet works
  (D3, D6).
- Commit: `feat: Add line coverage summary script`.

## Phase 3: Ratchet rule

- In `pom.xml`: property `coverage.line.minimum`, set to the ratio `line-coverage.sh` prints. Add a `check` execution (phase `test`,
  after `report`) with a rule on element `BUNDLE`, limit counter `LINE`, value `COVEREDRATIO`,
  minimum `${coverage.line.minimum}`.
- Verify: `./mvnw package` passes. Then prove it fails: temporarily run
  `./mvnw package -Dcoverage.line.minimum=0.99` and see "lines covered ratio is 0.7x, but expected
  minimum is 0.99". No source changes needed for the proof.
- Commit: `build: Fail the build if line coverage drops`.

## Phase 4: Workflow

- `.claude/skills/execute-plan/SKILL.md`: step 4 and "Before Finalising the Plan": a coverage drop is a
  broken window; fix it with tests. After the plan is done, run `workflow/coverage/line-coverage.sh`
  and raise `coverage.line.minimum` if coverage went up (D3). Never lower it without the human (D6).
- `.claude/skills/make-plan/SKILL.md`: plans include the tests that keep coverage from dropping.
- `.claude/rules/team-workflow.md` ("Running Checks", "No Broken Windows") and
  `.claude/rules/project.md` (Testing Conventions): the report path, the script, the ratchet.
- `AGENTS.md` team section, non-negotiables: the build includes the coverage floor.
- `workflow/README.md` artifacts table: `coverage/`. `workflow/checklists/pre-claim.md` item 3:
  the build includes the coverage floor.
- Commit: `docs: Make the coverage ratchet part of the workflow`.

## Phase 5: Evidence

- `./mvnw package` and the script, both green.
- `REVIEW.md` section 2 (how we reviewed): the ratchet. Section 5 (reusable artifacts): the
  JaCoCo ratchet and the script.
- Move this plan to `workflow/plans/done/` with `git mv`, commit, ask the human about pushing.
