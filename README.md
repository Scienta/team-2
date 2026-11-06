# Introduction

A reusable skill repository and setup for agentic coding projects.
This repository provides a standardized structure for integrating coding agents into software development workflows.

This section describes the typical workflow when running with this skill set.

For an introduction to the contents and structure of the repo,
see the below [Agentic Coding Setup](#agentic-coding-setup) section.

## Workflow

The skills and agent instructions in this repository are crafted to support a plan first, then execute, workflow.
A typical session runs as follows. We assume that the coding agent is just re-started.

### 1. Initial instructions

Make the agent read the skills:

```sh
> Read AGENTS.md and follow instructions.
```

This triggers the agent to read all necessary skill files and other necessary documentation about the project,
as well as checking that the development environment is sound and activated.

### 2. Planning

The next step is to instruct the agent to prepare for the next development task, and this is done through creating
a step-by-step plan for how to implement the next feature or do the next chore. The `making_a_plan.md` file is part of
the skill-set, but repeating the file name makes sure this is how the plan is made.

```sh
> We have to add logging to the application. We shall use standard python logging, and the logging should be configurable
  using the file `config/logging.yaml`, where we can specify output files, priorities on loggers etc. All main events in the
  code should be logged on INFO level.
  Recall `making_a_plan.md` and create the plan. Place the plan in `todo/log_instrumentation_plan.md`.
```

The plan should be sufficiently complete to allow an agent restart (or crash). This is part of the instructions of making a plan,
but it helps to ask:

```sh
> Is the plan robust enough to withstand an agent restart with a cleared context?
```

When done, re-start the agent if the context window is starting to fill up.

### 3. Plan Execution

Assuming a restart:

```sh
> Read AGENTS.md and follow instructions. Then, read todo/log_instrumentation_plan.md.
```

When the agent has read the plan, and feels confident about the task:

```sh
> Recall `phase_execution.md` and execute the plan.
```

The agent is instructed to halt between each phase in the plan, but sometimes fail to do so.
Anyways, eventually, the plan shall be executed, and the agent is already instructed to leave
no broken windows, so all tests should run flawlessly at this point.

Now all you have to do is to push, merge and go back to the top and start over.

# Agentic Coding Setup

## Repository Overview

This repository provides a standardized starting point for agentic coding projects with:

- **`AGENTS.md`**: Master instructions file for coding agents with default assumptions
- **`skills/`**: Reusable skill files for agent workflows and project-specific overrides
- Standard project structure conventions

### Local Variations in `LOCAL.md`

If you have personal preferences that the coding agent should know about, place them in a file
`LOCAL.md` at the repo root. The file is ignored by git, but will be read as part of the
master instructions.

### Default Assumptions

The skill repository assumes these defaults for new projects:

**Python projects:**
- Virtual environment: `.venv/`
- Source code: `src/`
- Environment: `PYTHONPATH=${PWD}/src`
- Testing: `pytest` in `tests/` directory
- Dependencies: `requirements.txt` (and optionally `requirements-dev.txt`)
- Build automation: `Makefile` with standard targets

**Quick start:**
```bash
make setup          # First-time setup
source .venv/bin/activate
source set_env.sh   # If project has this file
make dev            # Run all checks
```

**If your project differs from these defaults**, update `skills/PROJECT.md` to document the differences.

## Getting Started

**For coding agents:** Start by reading **`AGENTS.md`** which contains obligatory reading instructions and workflow guidance.

**For human developers:**
1. Copy this structure to your new project
2. Review `AGENTS.md` for default assumptions
3. Update `skills/PROJECT.md` with project-specific overrides
4. The `skills/` directory contains documentation on planning, execution, and quality standards

## Repository Structure

```
.
├── AGENTS.md                   # Master instructions with default assumptions
├── Makefile                    # Standard build/test automation
├── .githooks/
│   └── pre-commit             # Prevents direct commits to main
├── scripts/
│   └── setup-hooks.sh         # Configure git hooks
├── skills/
│   ├── PROJECT.md             # Project-specific overrides (edit this!)
│   ├── making_a_plan.md       # Planning methodology
│   └── phase_execution.md     # Execution patterns
└── README.md                   # This file
```

## Usage

### For New Projects

1. **Copy the structure:** Copy `AGENTS.md`, `skills/`, and `README.md` to your project
2. **Review defaults:** Read through `AGENTS.md` to understand default assumptions
3. **Document differences:** Edit `skills/PROJECT.md` to specify how your project differs from defaults
4. **Keep general content:** Don't edit `AGENTS.md` - keep it generic and reusable

### Customization Guide

**Edit `skills/PROJECT.md` when your project:**
- Uses a different directory structure (e.g., `lib/` instead of `src/`)
- Uses a different test directory (e.g., `test/` instead of `tests/`)
- Has different environment requirements
- Uses different testing tools or frameworks
- Has specific code conventions (quote styles, naming, etc.)
- Requires additional setup steps

**Keep `AGENTS.md` generic** - it contains universal agent instructions that apply across projects.

### Examples of Customization

**Example 1: Different test directory**
```markdown
## Testing Overrides
**Test directory:** `test/` (instead of default `tests/`)
```

**Example 2: Additional environment setup**
```markdown
## Environment Overrides
**Database:** Requires PostgreSQL connection via `DATABASE_URL` environment variable
**API Keys:** Set `API_KEY` for external service integration
```

**Example 3: Different source structure**
```markdown
## Environment Overrides
**Source location:** `lib/` (instead of default `src/`)
**PYTHONPATH:** Set to `${PWD}/lib`
```

**Example 4: Additional Makefile targets**
```markdown
## Build Overrides
**Additional targets:** `make local-up` - starts local database and services
See project Makefile for details.
```
