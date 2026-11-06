# Plan: <feature or chore>

- [ ] Phase 1: <deliverable>
- [ ] Phase 2: <deliverable>
- [ ] Phase 3: Hurl example in `examples/`, run against a local service
- [ ] Phase 4: Update `docs/`, finish (plan to done, claim-log row in `REVIEW.md`)

## Context (read this after a restart)

- Spec: `workflow/specs/<slug>.md` (decisions table at the end). Claimed? <no / at tick N>.
  A person claims, a claim is permanent.
- Branch: `<branch>`. PR via the GitHub UI, never `gh`. Merging to `main` deploys.
- Commands: `./mvnw package`, `./mvnw test -Dtest=<Test>`; machine settings in `LOCAL.md`.
- Code in `src/main/java/no/scienta/cfp/`: <the classes this touches and what they do>.
- Test style: <the test class to copy>.

## Decisions for the human to check

- **D1.** <question>: <proposal>. **Ask the PO** (visible to the harness).

## Phase 1: <name>

1. <step>

Check: <a falsifiable check: a test name, a command and its expected output>. Commit:
`<type>: <message>`.

## Review response

| Finding | Response |
|---|---|
