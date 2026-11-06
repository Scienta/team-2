#!/usr/bin/env bash
# The spec pipeline's issues by state, one per line: `running|blocked|pr-open|queued <n> <slug>`,
# lowest issue number first. Only open issues labelled `pipeline` count. Used by /spec-watch.
# Test override: PIPELINE_ISSUES (a JSON file shaped like `gh issue list --json number,labels`).
set -euo pipefail

if [ -n "${PIPELINE_ISSUES:-}" ]; then issues="$(cat "$PIPELINE_ISSUES")"; else issues="$(gh issue list --label pipeline --state open --limit 200 --json number,labels)"; fi

jq -r '
  sort_by(.number)[]
  | [.labels[].name] as $l
  | select($l | index("pipeline"))
  | ([$l[] | select(startswith("spec:")) | ltrimstr("spec:")][0] // "-") as $slug
  | (if ($l | index("blocked")) then "blocked"
     elif ($l | index("pipeline:running")) then "running"
     elif ($l | index("pipeline:pr-open")) then "pr-open"
     else "queued" end) as $state
  | "\($state) \(.number) \($slug)"' <<< "$issues"
