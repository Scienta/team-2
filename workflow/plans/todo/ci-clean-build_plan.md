# Plan: Clean Maven builds on PRs and main

- [ ] Phase 1: Build and test PRs and main alongside independent deployment; record and validate.

## Context (read this after a restart)

- Request: run a clean Maven build and all Maven tests on PRs and pushes to main.
- Branch: `spring-boot-4` (existing feature branch). Keep unrelated pending upgrade changes intact.
  PR creation is by a person in the GitHub UI; do not push without approval.
- Files: `.github/workflows/deploy.yml`, `REVIEW.md`, and this plan/review.
- The current workflow deploys first, then runs non-blocking tests. Docker builds skip tests.
- `pom.xml` already requires Java 25. Preserve the pending workflow change to Java 25;
  it is directly required for this build. Do not stage other pending upgrade files.
- ADRs: `workflow/adrs/0001-workflow-lives-in-claude-and-workflow.md`,
  `workflow/adrs/0002-spring-boot-4-java-25.md`.
- Docker must be available. Use the JDK 25 and Colima settings in `LOCAL.md`.

## Decisions for the human to check

- Run `./mvnw -B clean verify`: clean, compile, all configured Maven tests, package and verify.
  Hurl examples are not Maven tests and are outside this CI change.
- Run on all pull requests, pushes to main, and existing manual workflow dispatches.
- User explicitly requested no main deployment barrier for competition speed.
  Deploy independently of tests for a non-PR event on `refs/heads/main`.
  Manual dispatches on other branches build/test only. Main test failures fail the workflow
  but do not prevent deployment.
- Serialize only deployment jobs; tests must not delay newer main deployments.
  Never cancel a running deployment.
- No application/API behaviour changes, product-owner decisions, or harness claim.

## Phase 1: Independent build/test and deployment

1. Add the PR trigger to `deploy.yml`, move concurrency from the workflow to the deploy job.
2. Remove tests' dependency on deployment, cancellation override, and `continue-on-error`.
   Run `./mvnw -B clean verify` on Java 25.
3. Keep deploy independent of tests and restrict deploy to non-PR events on main.
   Preserve registry, Docker build, rollout and logs steps.
4. Parse YAML with Ruby/Psych and assert triggers, command, job dependencies, permissions,
   deploy-only secrets, and concurrency. Use actionlint if installed.
   Check the event/dependency matrix below, including failed/cancelled tests.
5. Run `./mvnw -B clean verify` with `LOCAL.md` JDK 25 settings, covering the required
   package phase and full test suite. Record tests/failures/errors/skips and any warnings.
   Pre-existing warnings leave the warning-free completion gate unmet; report them instead
   of modifying unrelated upgrade files. An existing disabled test remains disabled.
6. Update `REVIEW.md` sections 1 and 5 with the CI gate and reusable workflow.
   Run `workflow/scripts/check-review.sh` and report remaining empty headings.
7. Mark the phase complete, move plan and review to done using `git mv`, and commit only
   the workflow, review log and this plan/review.

Check: valid workflow with PR builds, main builds, no PR deploy, and deployment independent
of tests; Maven clean verify succeeds with enabled tests passing. Live GitHub runs remain
unverified until pushed. Commit: `ci: run clean Maven builds on PRs and main`.

## Review response

| Finding | Response |
|---|---|
| M1 | Explicit YAML assertions and event/dependency matrix below; keep permissions and secret isolation. |
| M2 | Record warnings and leave completion unchecked if the warning-free gate is unmet. |
| M3 | Record skipped counts; do not re-enable existing disabled tests. |

## Expected event/dependency matrix

| Event | Tests | Independent deploy |
|---|---|---|
| PR (same repo or fork) | Yes, no deployment secrets | No |
| Push to main | Yes | Yes |
| Manual main | Yes | Yes |
| Manual other branch | Yes | No |
| Main, failed tests | Failed | Yes, not gated by tests |
| Workflow cancelled | Cancelled | May be cancelled too |

Main push and manual main deploy jobs use the `deploy` concurrency group.
`cancel-in-progress` stays false. There is no workflow-level concurrency, so running tests
cannot delay a newer deployment.

The initial agent review covered a deployment gate. The user subsequently removed that
requirement explicitly; the implementation and validation now require no dependency in
either direction.

## Local evidence

- `./mvnw -B clean verify` with the JDK 25/Colima settings: BUILD SUCCESS,
  60 tests, 0 failures, 0 errors, 1 existing skipped test (2026-11-06).
- Ruby/Psych parsed the workflow and checked triggers, clean verify, independent jobs,
  failing-test reporting, read-only permissions, secret isolation and deployment-only
  concurrency. `actionlint` is not installed.
- Warning-free completion remains unmet: the run emitted warnings for disabled Ryuk,
  JVM class-data sharing, intentionally invalid HTTP requests and the existing skipped test.
  These are outside the CI edit; no application/test/environment changes made.
- `workflow/scripts/check-review.sh`: only `## 6. One thing we'd do differently` is empty.
- The user requested a push after the warning report. Commit and push the CI changes on
  `spring-boot-4`; keep this phase unchecked because the warning-free gate remains unmet.
  Live Actions execution has not been verified.
