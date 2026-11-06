#!/usr/bin/env bash
set -euo pipefail

script="$(cd "$(dirname "$0")" && pwd)/pipeline-queue.sh"
dir="$(mktemp -d)"
trap 'rm -rf "$dir"' EXIT

cat > "$dir/issues.json" <<'JSON'
[{"number":44,"labels":[{"name":"pipeline"},{"name":"spec:c"}]},
 {"number":40,"labels":[{"name":"pipeline"},{"name":"spec:a"},{"name":"pipeline:running"}]},
 {"number":41,"labels":[{"name":"pipeline"},{"name":"spec:b"},{"name":"pipeline:running"},{"name":"blocked"}]},
 {"number":42,"labels":[{"name":"pipeline"},{"name":"spec:d"},{"name":"pipeline:pr-open"}]},
 {"number":43,"labels":[{"name":"spec:manual"}]}]
JSON

got="$(PIPELINE_ISSUES="$dir/issues.json" "$script" | tr '\n' ';')"
want="running 40 a;blocked 41 b;pr-open 42 d;queued 44 c;"
if [ "$got" = "$want" ]; then
    echo "ok   states, blocked wins over running, issues without the pipeline label are ignored, sorted"
    echo "all pipeline-queue tests passed"
else
    echo "FAIL got '$got', want '$want'"
    exit 1
fi
