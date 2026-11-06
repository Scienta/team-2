# CI clean build plan review

Reviewer: `plan-reviewer` agent, 2026-11-06.
Verdict: approve with minor clarifications; no blocking or significant findings.

## Minor

- **M1:** Make YAML/event validation reproducible, including fork PRs, failed/cancelled
  tests, main/manual concurrency and deployment-secret isolation. Preserve `contents: read`
  and use `pull_request`, not `pull_request_target`.
- **M2:** Record pre-existing warnings and report any unmet warning-free completion gate;
  do not silently mark complete or change unrelated upgrade files.
- **M3:** Record run/failure/error/skipped totals: `UtilsTest.nowIsCurrentTime` is disabled.
  Do not re-enable it as part of this CI change.

## What's good

- Correct gate inversion, with Docker builds continuing to skip tests after the Maven gate.
- `clean verify` and Java 25 match the existing Maven suite and compiler.
- One restart-proof phase preserves unrelated dirty files and does not claim live Actions
  validation before a push.
