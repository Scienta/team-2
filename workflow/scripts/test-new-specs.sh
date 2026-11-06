#!/usr/bin/env bash
set -euo pipefail

script="$(cd "$(dirname "$0")" && pwd)/new-specs.sh"
dir="$(mktemp -d)"
trap 'rm -rf "$dir"' EXIT

cat > "$dir/feedback.json" <<'EOF'
{"features":[
 {"slug":"talks","claimed":true,"checks":[{"status":"OK"}]},
 {"slug":"open-issue","claimed":false,"checks":null},
 {"slug":"closed-issue","claimed":false},
 {"slug":"unreleased","claimed":false,"checks":null},
 {"slug":"brand-new","claimed":false,"checks":null},
 {"slug":"other-label","claimed":false,"checks":null}
]}
EOF
cat > "$dir/issues.json" <<'EOF'
[{"labels":[{"name":"spec:open-issue"},{"name":"enhancement"}]},
 {"labels":[{"name":"spec:closed-issue"}]},
 {"labels":[{"name":"other-label"}]},
 {"labels":[]}]
EOF
printf '#!/bin/sh\n[ "$1" != unreleased ]\n' > "$dir/released"
chmod +x "$dir/released"

got="$(NEW_SPECS_FEEDBACK="$dir/feedback.json" NEW_SPECS_ISSUES="$dir/issues.json" NEW_SPECS_RELEASED="$dir/released" "$script" | tr '\n' ' ')"
want="brand-new other-label "
if [ "$got" = "$want" ]; then
    echo "ok   claimed, labelled (open or closed) and unreleased are skipped; a plain label doesn't match"
    echo "all new-specs tests passed"
else
    echo "FAIL got '$got', want '$want'"
    exit 1
fi
