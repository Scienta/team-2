# 0001: The team workflow lives in `.claude/` and `workflow/`

Status: accepted, 2026-11-06

## Context

At 17:00 a team is eligible to win only if something in `.claude/` or `workflow/` changed and every
heading of `REVIEW.md` has text; the showcase points at files in those two folders. Our workflow
lived in `skills/`, `todo/`, `done/`, `adrs/` and the bottom half of `AGENTS.md`, none of which
counts. Several tools work in this repo: Claude Code loads `.claude/rules/` by itself, but Copilot
CLI reads only `CLAUDE.md` → `@AGENTS.md`.

## Decision

- Team rules in `.claude/rules/` (`team-workflow.md`, `project.md`); skills in `.claude/skills/`;
  review agents in `.claude/agents/`; shared permissions in `.claude/settings.json`.
- Specs, templates, checklists, scripts, plans (`workflow/plans/todo|done/`) and ADRs in `workflow/`.
- `AGENTS.md` stays the entry point for every agent: the organisers' facts, then an imperative
  "read all of `.claude/rules/*.md`" and a short list of non-negotiables.

## Consequences

- Non-Claude agents depend on following the pointer in `AGENTS.md`; the non-negotiables cover the
  essentials if they don't.
- Old paths are guarded by `workflow/scripts/check-paths.sh`; historical plans keep their old paths.
- Branches made before the move must rebase (paths moved).
