#!/usr/bin/env bash
# Fails on references to paths that moved in the workflow reorganisation. Historical plans, ADRs and
# reviews are left as written. --before-plans-move: todo/ and done/ haven't moved yet.
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

dirs='skills|todo|done|adrs'
[ "${1:-}" = "--before-plans-move" ] && dirs='skills|adrs'

pattern="(^|[^/[:alnum:]_.])($dirs)/|making_a_plan|making_a_master_plan|phase_execution|skills/PROJECT\.md"

if git grep -nE "$pattern" -- . \
    ':!src' ':!target' ':!todo' ':!done' ':!workflow/plans' ':!workflow/adrs' \
    ':!workflow/scripts/check-paths.sh' ':!**/workflow_reorg_plan*.md'; then
    echo "stale paths found (see above)" >&2
    exit 1
fi
echo "no stale paths"
