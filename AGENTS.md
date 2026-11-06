# AGENTS.md

Facts about this repo and the platform it runs on, from the fagdag organisers. It's not a workflow:
how you work is your team's call, and your own instructions go in `.claude/rules/` (see
`.claude/README.md`). This file doesn't count towards eligibility; `.claude/` and `workflow/` do.

## Source of truth

- **The deploy contract** (below, in full in the README's organiser section), **the base specs**
  (talks, speakers, ratings) and **the feature requests** for the drip features, all on the harness.
  They don't change during the game.
- **A feature request isn't a spec.** It gives the business rules and the API surface, not status
  codes, edge cases or error handling. The team decides those and writes them down; a person on the
  team can ask the organisers' product owner about business rules.
- **Agents can't claim features.** A person on the team claims with a button on the harness, and a
  claim is permanent. Never tell anyone a feature is done without evidence they can check.
- The game rules (scoring, conduct) are on the printed rules sheet, not here.

## Commands

- Build and test: `./mvnw package` (as shipped: Java 11, Spring Boot 2.7). Tests start Postgres with
  Testcontainers, so Docker must be running.
- Run locally: `docker compose up -d` (Postgres on `localhost:5432`, database, user and password
  `cfp`), then `java -jar target/cfp.jar` (port 8080).
- Examples: `examples/run.sh [base-url]` (default `http://localhost:8080`; needs Hurl) runs every
  `examples/*.hurl`. It ships with the base features' happy paths only, not what the harness checks.
  Drip features have no published examples: write your own.
- Deployed service: the `TEAM_URL` repo variable, `https://fagsem-h2026-<team>.scienta.cloud`.
- Logs: `.github/fagdag/logs.sh --tail 200 --since 2026-11-06T13:00:00Z` (the deployed container's
  stdout, from Scaleway Cockpit). **The base app doesn't log requests**, so as shipped the harness's
  traffic barely shows up. Adding request logging (method, path, status, maybe bodies) is allowed and
  is the main way to see what the checks send.
- Harness API: the URL is in `.fagdag/harness-url`, the team slug in `.fagdag/team`, and the team
  token in `.fagdag/harness-token` (a person pastes it from the harness feedback page; it's
  gitignored, never commit it or print it). Feature slugs are on the harness `/roadmap`.

  ```bash
  H=$(cat .fagdag/harness-url) TEAM=$(cat .fagdag/team) TOKEN=$(cat .fagdag/harness-token)
  curl -fsS -H "Authorization: Bearer $TOKEN" "$H/specs/<feature>/spec.md"
  curl -fsS -H "Authorization: Bearer $TOKEN" "$H/api/teams/$TEAM/feedback"
  curl -fsS -H "Authorization: Bearer $TOKEN" "$H/api/teams/$TEAM/feedback/timeline?check=<feature>/<check>"
  ```

  Rate-limited (bursts of 5, then 1 request/s; `429` with `Retry-After`). A spec is `404` until
  its release. Checks have opaque names (`<feature>/check-3`). A FAULTY check gives the request it
  failed on (method and templated path, not what it expected) and a `transcript`: the requests of its
  latest faulty run and what this service answered.

## Deploy contract (push to `main` deploys)

- `Dockerfile` at the repo root, built for `linux/amd64`, taking `--build-arg GIT_SHA` and baking it in.
- Listen on port **8080**.
- `GET /health`: 200 `{"status": "ok", "version": "<GIT_SHA>"}` only if the database answers within
  5 s, else 503 `{"status": "db-unavailable", "version": "<GIT_SHA>"}`.
- Database from `PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER`, `PGPASSWORD`; it keeps its data across deploys.
- The filesystem is ephemeral. No other external dependencies. Any stack is allowed.
- One instance, may scale to zero. Beyond 80 concurrent requests the platform answers `503` itself.

## Platform gotchas

- Connection pool capped at 5 (`spring.datasource.hikari.maximum-pool-size`).
- Advisory locks are not guaranteed on this database: use row locks (`SELECT … FOR UPDATE`) or
  conditional writes.
- A session `SET` leaks to whoever gets the pooled connection next: use `SET LOCAL` inside the transaction.
- `sslmode` is off for local Compose Postgres; the `Dockerfile` sets `PGSSLMODE=require`.

## Hands off

- The repo secrets `SCW_ACCESS_KEY`, `SCW_SECRET_KEY` and variables `SCW_REGISTRY`,
  `SCW_CONTAINER_ID`, `SCW_ORGANIZATION_ID`, `TEAM_URL`.
- The committed files in `.fagdag/`.
- Anything outside this team's own service, container and database. No API keys in the repo or in
  Actions, and no agents in CI.

---

# Team workflow (skills)

Everything above is the organisers' facts and is binding. What follows is how this team works;
where it conflicts with the facts above, the facts win.

**How to use these instructions:**
1. When starting a session, you'll be told to "Read AGENTS.md and follow instructions"
2. This triggers reading all skill files and checking the development environment
3. After setup, you'll create a plan (see "Planning and Execution" below)
4. Then execute the plan phase by phase
5. Ensure "No Broken Windows" - all tests pass before finishing

## Reading List

- Read README.md for project overview
- Read .claude/rules/project.md for project-specific development guidelines
- Read skills/making_a_plan.md for planning methodology
- Read skills/phase_execution.md for execution patterns
- Read any other markdown files in `skills/*.md`

IF there is a LOCAL.md file, read that too. This shall contain information
local to the current developer's preferences.

## Project Setup

This is a Java 11 / Spring Boot 2.7 project built with the Maven wrapper (see "Commands" above).

- Source code: `src/main/java/`, tests: `src/test/java/`
- Build and test: `./mvnw package` (JUnit; Postgres via Testcontainers)

**Before starting work:** check that Docker is running (`docker info`). **If not, inform the human
and stop execution**, since the tests can't run without it.

**If anything here differs from reality**, update `.claude/rules/project.md` with the project-specific
details.

## Development Workflow for Agents

### Running Checks

```bash
./mvnw package                      # build and run all tests
./mvnw test -Dtest=SomeTest         # run a single test class
examples/run.sh [base-url]          # Hurl examples against a running service
```

Always prefer verbose output when running checks programmatically, as it provides
more detailed information for debugging and validation.

### Git Workflow

**Branching:** Follow project branching conventions:
- All changes via PR (Pull Request)
- **Merging to `main` deploys** (see "Deploy contract" above), so `main` must always build
- Create a feature branch, commit, push, then create PR via GitHub UI (not `gh` CLI)

**Never create a PR using the GitHub CLI (`gh`) unless directly asked to do so.**
PRs must be made using click-ops to conform to organizational git processes.

Generally, refrain from using the GitHub CLI (`gh`) unless explicitly asked.

### CI/CD Awareness

Every push to `main` runs the deploy workflow (`.github/workflows/deploy.yml`). Run
`./mvnw package` locally before pushing to catch issues early.

## Git Best Practices

Remember to use adequate git file commands when moving/renaming/deleting files:
- `git mv` for moving/renaming files
- `git rm` for deleting files

This ensures git properly tracks file history.

## General Development Principles

### No Broken Windows

When you make changes to files:
1. **Always run all tests afterwards** to verify functionality
2. Before finishing, ensure:
   - **NO FAILING TESTS**
   - **NO WARNINGS**
3. If tests or builds fail, fix them before completing your work

### Code Quality

- Only comment code that needs clarification
- Do not over-comment otherwise
- Make the smallest possible changes to achieve the goal
- Be surgical and precise with modifications

## Planning and Execution

When planning how to implement a feature or complete a task:
1. Follow the instructions in `skills/making_a_plan.md`
2. When executing the plan, follow the instructions in `skills/phase_execution.md`

This structured approach helps maintain clarity and enables recovery from context resets.

## Testing

Create tests for code that you generate.

**Testing best practices:**
- Always run the full test suite after making changes
- Ensure all tests pass before considering work complete
- For HTTP behaviour, add Hurl files to `examples/` (picked up by `examples/run.sh`)

### Test Types

- **Unit tests**: Fast, isolated tests for individual components
- **Integration tests**: Spring Boot tests against Testcontainers Postgres
- **Regression tests**: Long-running comprehensive tests (often in `regtest/` directory)

For regression tests, shell scripts are often as welcome as framework-based tests.

Check `.claude/rules/project.md` for project-specific testing requirements.

## Running Bash Commands

**Best practices to avoid approval requests:**
- Avoid newlines and comments in bash commands
- Do not use `${}` constructs in bash commands
- Do not use newlines or comments in code snippets to execute
- Use relative paths or simple command names instead of absolute paths

**For agent-specific tools:**
- If you are `Claude Code`, check `.claude/settings.local.json` for permissions

## Code Conventions

Follow project-specific conventions as defined in `.claude/rules/project.md`, and match the style of the
surrounding code.
