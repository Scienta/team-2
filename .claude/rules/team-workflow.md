# Team workflow

**Every agent must read all of `.claude/rules/*.md` before doing anything else.** Claude Code loads
them automatically; other agents read them explicitly. Skills are in `.claude/skills/`, plans in
`workflow/plans/` (`workflow/plans/todo/`, `workflow/plans/done/`), the process in `workflow/README.md`.

The organisers' facts in `AGENTS.md` (deploy contract, commands, platform gotchas, hands off) are
binding. What follows is how this team works; where it conflicts with those facts, the facts win.

**How to use these instructions:**
1. When starting a session, you'll be told to "Read AGENTS.md and follow instructions"
2. This means: read `AGENTS.md`, everything in `.claude/rules/`, and the Reading List below, then
   check the development environment
3. After setup, you'll create a plan (see "Planning and Execution" below)
4. Then execute the plan phase by phase
5. Ensure "No Broken Windows" - all tests pass before finishing

**Keep `REVIEW.md` current**, in the same commit as the work: `.claude/rules/review-log.md` says
which event goes in which section, and why it decides whether we can win.

## Reading List

- Read README.md for project overview
- Read .claude/rules/project.md for project-specific development guidelines
- Read `.claude/skills/make-plan/SKILL.md` (`/make-plan`) for planning methodology
- Read `.claude/skills/execute-plan/SKILL.md` (`/execute-plan`) for execution patterns
- Skim the other skills in `.claude/skills/*/SKILL.md` and the agents in `.claude/agents/`

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
./mvnw package                      # build, run all tests, coverage report and floor
workflow/coverage/line-coverage.sh  # line coverage vs the floor in pom.xml
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
   - **NO COVERAGE DROP**: line coverage at or above `coverage.line.minimum` in `pom.xml`
     (the build checks it), and the floor raised if coverage went up
3. If tests or builds fail, fix them before completing your work

### Code Quality

- Only comment code that needs clarification
- Do not over-comment otherwise
- Make the smallest possible changes to achieve the goal
- Be surgical and precise with modifications

## Planning and Execution

When planning how to implement a feature or complete a task:
1. Follow the `make-plan` skill (`.claude/skills/make-plan/SKILL.md`; `make-master-plan` for large tasks)
2. When executing the plan, follow the `execute-plan` skill (`.claude/skills/execute-plan/SKILL.md`)

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
