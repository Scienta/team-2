#!/usr/bin/env bash
# Prints one slug per line: harness features that are released, unclaimed, and have no GitHub
# issue labelled spec:<slug> (open or closed). Used by /spec-watch.
# Test overrides: NEW_SPECS_FEEDBACK and NEW_SPECS_ISSUES (JSON files), NEW_SPECS_RELEASED (a
# command run as `$NEW_SPECS_RELEASED <slug>`, exit 0 when the spec is released).
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"
scripts=workflow/scripts

if [ -n "${NEW_SPECS_FEEDBACK:-}" ]; then feedback="$(cat "$NEW_SPECS_FEEDBACK")"; else feedback="$("$scripts/harness.sh" feedback)"; fi
if [ -n "${NEW_SPECS_ISSUES:-}" ]; then issues="$(cat "$NEW_SPECS_ISSUES")"; else issues="$(gh issue list --state all --limit 500 --json labels)"; fi

released() {
    if [ -n "${NEW_SPECS_RELEASED:-}" ]; then "$NEW_SPECS_RELEASED" "$1"; else "$scripts/harness.sh" spec "$1" > /dev/null 2>&1; fi
}

labelled="$(jq -r '.[].labels[].name | select(startswith("spec:")) | ltrimstr("spec:")' <<< "$issues" | sort -u)"

jq -r '.features[] | select(.claimed | not) | .slug' <<< "$feedback" | while read -r slug; do
    grep -qxF "$slug" <<< "$labelled" && continue
    released "$slug" && echo "$slug"
done
exit 0
