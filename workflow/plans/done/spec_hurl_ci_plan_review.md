# Review: spec_hurl_ci_plan.md

Reviewer: `plan-reviewer` agent (Claude Code), 2026-11-06. It ran **after** execution (the
background agent couldn't start it), on the plan and the implementation in PR #30 (issue #26).
Verdict: the suite and CI job are sound and re-run on a persistent database, but not signed off.
The known-failing speaker rules are recorded only here, the docs overstate coverage, "blocks the
merge" depends on an unconfirmed branch-protection setting, and the review came after execution.
Responses go in the plan's "Review response" table.

## Blocking

- **B1. The two known-failing speaker rules are recorded only in a plan in `done/`**
  (`spec_hurl_ci_plan.md:39-43`).
  - The rules: an invalid e-mail gets 201, and `"name": 5` gets 201, stored as `"5"`.
  - No fix plan or issue exists. `docs/known-issues.md` R5 covers the e-mail but not the numeric name.
  - `examples/speakers-rules.hurl` gives no sign the rules are left out on purpose.
  - Fix:
    - an issue;
    - `workflow/plans/todo/speakers_validation_plan.md`;
    - a known-issues row for the name;
    - a `# KNOWN GAP (issue #n)` block with the two requests commented out after `speakers-rules.hurl:89`.
- **B2. Executed and moved to `done/` without the review the plan requires**
  (`spec_hurl_ci_plan.md:102-106`, `make-plan/SKILL.md:34-36`).
  - `REVIEW.md:65-66` claims plans are reviewed before code.
  - Fix: fill in the Review response table, have a person record their D1–D4 decisions with name and date, and don't merge until then.

## Significant

- **S1. The docs promise coverage the files don't deliver.** `.claude/rules/project.md:52-53`
  says "every rule and every decision row". There's no assertion for:
  - acceptance:
    - D1 on withdraw (an unknown talk should give 404);
    - D10 precedence (a stranger withdrawing a REJECTED talk should get 403, an unknown talk with a bad body 404);
    - D3: ACCEPTED→WAITLISTED, WITHDRAWN→REJECTED and WITHDRAWN→WAITLISTED;
    - D8 `-1` and `1.5`;
    - D14: a co-speaker change after a decision, with a `GET` asserting `ratingCount`/`status`;
  - ratings:
    - scores `0` and `4.0`;
    - tied talks staying oldest first;
    - ranking on the rounded average;
  - talks: `submittedOn` equal to `submittedAt`'s UTC date, and a missing `format`.

  Fix: add these, or reword `project.md` to "the rules listed in each file's header".
- **S2. "A red PR check blocks the merge" is a convention, not a gate.**
  - Nothing shows `hurl` is a required check.
  - On `main`, `deploy` runs in parallel with `hurl`.
  - Fix: a person makes `hurl` and `tests` required checks on `main` and records it in D1, or D1 says plainly that the gate isn't met.
- **S3. Some 404 assertions can't tell the rule from a missing route.**
  - `speakers-rules.hurl:116,119` (trailing slash) and `co-speakers-rules.hurl:149-153` (D6) would pass even if the route were gone.
  - Fix: assert `$.message` as well.
- **S4. The restart run doesn't check anything persisted** (`deploy.yml:64-67`).
  - Every file uses fresh `{{newUuid}}` data, so run 2 would pass on an empty database.
  - Fix:
    - before the restart, save a speaker id and e-mail;
    - after it, `curl` the speaker and check the e-mail with `jq -e`.

## Minor

- **M1.** The Hurl `.deb` isn't checksummed (`deploy.yml:59-60`). Pin its SHA-256 and check it with `sha256sum -c`.
- **M2.** A deleted `*-rules.hurl` doesn't fail CI. Fix: `nullglob` and a minimum file count in `examples/run.sh`.
- **M3.** The evidence doesn't match the files: the worktree has 12 files and 207 requests, CI reported 13 and 227. Record the CI run URL and SHA in the plan.
  - Responder's note: CI tests the PR merged with main, which had one more Hurl file. Run 42235369499 on `655ce6c`.
- **M4.** The failure step shows only the app's logs. Add `docker compose logs postgres`.
- **M5.** Fixture details:
  - the fixed e-mail at `speakers-rules.hurl:145`;
  - the precedence case "a bad `reviewerId` gives 400 before an unknown talk gives 404" is untested (`PUT /talks/2147483647/ratings/0`).
- **M6.** Machine-specific details in the plan:
  - mark the Homebrew `JAVA_HOME` (line 11) as one machine's;
  - name who asked for the `gh` draft PR (line 10).
- **M7.** Hurl `--test` may run files in parallel (unverified). Note in `project.md` that files must never depend on each other's data.

## Keep

- Unique data, lists filtered by `speakerId`, no global counts, a sentinel id for "unknown".
- The 500/501-character bio boundary.
- State checked as unchanged after refused requests.
- The D5 open-risk rows pinned.
- The CI job: no secrets, a timeout, pinned runner and version, and it reuses `local-rollout.sh`.
