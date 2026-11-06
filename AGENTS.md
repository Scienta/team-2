
# Instructions for Agents

This file contains obligatory reading instructions for coding agents
working on this project.

**How to use these instructions:**
1. When starting a session, you'll be told to "Read AGENTS.md and follow instructions"
2. This triggers reading all skill files and checking the development environment
3. After setup, you'll create a plan (see "Planning and Execution" below)
4. Then execute the plan phase by phase
5. Ensure "No Broken Windows" - all tests pass before finishing

## Reading List

- Read README.md for project overview
- Read skills/PROJECT.md for project-specific development guidelines
- Read skills/making_a_plan.md for planning methodology
- Read skills/phase_execution.md for execution patterns
- Read any other markdown files in `skills/*.md`

IF there is a LOCAL.md file, read that too. This shall contain information
local to the current developer's preferences.

## Default Project Setup

This skill repository assumes the following standard project structure:

### Environment Setup

**Python projects** are assumed to use:
- Virtual environment: `.venv/`
- Source code location: `src/`
- `PYTHONPATH=${PWD}/src` should be set in the shell

**Before starting work:**
1. Check that the venv is activated. **If not, inform the human and stop execution.**
2. Verify `PYTHONPATH` is set correctly. **If not, inform the human and stop execution.**

**Important:** Don't set venv or PYTHONPATH explicitly when running commands, as this
may trigger unnecessary approval requests. Just run commands directly.

### Testing Setup

**Default testing assumptions:**
- Test framework: `pytest`
- Test location: `tests/` directory
- Parallel execution: Use `-n auto` when `pytest-xdist` is available

**If your project differs** from these defaults, update `skills/PROJECT.md` with
project-specific details.

## Development Workflow for Agents

### Running Checks

Different projects may have different check commands. Common patterns include:

**Make-based projects (default):**
```bash
make dev           # Complete workflow with clean summary output
make dev-verbose   # Full verbose output for agents/debugging (preferred for agents)
make dev-logs      # View logs from last dev run
make test          # Run tests
make lint          # Check code style
make fix           # Auto-fix linting and formatting issues
make check-all     # Run all quality checks
```

The base Makefile intelligently detects available tools (ruff, mypy, pytest) and test
directories (`tests/` or `test/`), providing graceful fallbacks.

**Direct tool usage:**
```bash
pytest             # Python testing
npm test           # Node.js testing
cargo test         # Rust testing
```

Always prefer verbose output when running checks programmatically, as it provides
more detailed information for debugging and validation.

### Git Workflow

**Branching:** Follow project branching conventions:
- All changes via PR (Pull Request)
- Direct pushes to `main` are typically blocked
- Create a feature branch, commit, push, then create PR via GitHub UI (not `gh` CLI)
- The pre-commit hook (`.githooks/pre-commit`) prevents accidental commits to main

**Never create a PR using the GitHub CLI (`gh`) unless directly asked to do so.**
PRs must be made using click-ops to conform to organizational git processes.

Generally, refrain from using the GitHub CLI (`gh`) unless explicitly asked.

### CI/CD Awareness

Be aware that most projects have CI/CD pipelines that run on every PR:
- Linting and code style checks
- Security scans
- Type checking
- Tests

All checks must pass before merge. Run these locally before pushing to catch
issues early.

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

**Default setup (Python):**
- Framework: `pytest`
- Test directory: `tests/`
- Run with: `pytest tests/ -v`
- Parallel execution: `pytest tests/ -n auto` (when pytest-xdist is installed)

**Testing best practices:**
- Always run the full test suite after making changes
- Ensure all tests pass before considering work complete
- Run tests in parallel when possible to speed up execution

### Test Types

Projects may have different test types:
- **Unit tests**: Fast, isolated tests for individual components
- **Integration tests**: Tests that verify components work together
- **Regression tests**: Long-running comprehensive tests (often in `regtest/` directory)

For regression tests, shell scripts are often as welcome as framework-based tests.

**Other language defaults:**
- Node.js: `jest`, `mocha`, `vitest` with tests in `__tests__/` or `test/`
- Rust: `cargo test`
- Go: `go test`

Check `skills/PROJECT.md` for project-specific testing requirements.

## Running Bash Commands

**Best practices to avoid approval requests:**
- Avoid newlines and comments in bash commands
- Do not use `${}` constructs in bash commands
- Do not use newlines or comments in code snippets to execute
- Use relative paths or simple command names instead of absolute paths

**For agent-specific tools:**
- If you are `Claude Code`, check `.claude/settings.local.json` for permissions

## Code Conventions

Follow project-specific conventions as defined in `skills/PROJECT.md`. Common conventions include:
- String quote styles (single vs double quotes)
- Indentation (spaces vs tabs)
- Naming conventions
- Documentation standards

You are now ready to have your instructions provided by your Human.
