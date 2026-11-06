# Pre-claim checklist

A claim is permanent and a person makes it on the harness. Before they click, every item holds
and has evidence a person can check. Run it with `/pre-claim <slug>`.

1. **Decisions answered.** Every row in the decisions table of `workflow/specs/<slug>.md` marked
   "Ask the PO" has the product owner's answer, and the code follows it.
2. **Spec coverage.** The `spec-checker` agent maps every rule and decision to a test or Hurl
   assertion; nothing uncovered.
3. **Build.** `./mvnw package` is green on `main` with the feature merged, which includes the
   coverage floor (`workflow/coverage/README.md`).
4. **Deployed.** The deploy workflow for the merge is green, and `GET $TEAM_URL/health` returns
   `{"status": "ok", "version": "<merged SHA>"}`.
5. **Examples against our URL.** `examples/run.sh https://fagsem-h2026-team-2.scienta.cloud` is
   green, including `examples/<slug>.hurl`.
6. **Harness.** `workflow/scripts/harness.sh feedback` shows no FAULTY check for the claimed
   features after the deploy. An unclaimed feature has **no checks** (seen for acceptance at tick
   147), so the harness can't confirm our guesses before the claim: the PR's risk list
   (`/list-risks`) says what to watch right after it.
7. **Claim log.** A row for the feature is drafted in `REVIEW.md` §3 with this evidence.
