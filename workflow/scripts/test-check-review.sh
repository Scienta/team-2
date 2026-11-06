#!/usr/bin/env bash
set -euo pipefail

check="$(cd "$(dirname "$0")" && pwd)/check-review.sh"
dir="$(mktemp -d)"
trap 'rm -rf "$dir"' EXIT
failures=0

expect() {
    local want="$1" name="$2" got=0
    "$check" "$dir/$name.md" > /dev/null || got=$?
    if [ "$got" -eq "$want" ]; then echo "ok   $name"; else echo "FAIL $name: exit $got, want $want"; failures=$((failures + 1)); fi
}

printf '# R\n<!-- intro -->\n## 1. A\nOur text.\n## 2. B\n<!-- prompt --> and ours\n' > "$dir/filled.md"
expect 0 filled

printf '# R\n## 1. A\nOur text.\n## 2. B\n<!-- only a prompt -->\n\n' > "$dir/one-line-comment.md"
expect 1 one-line-comment

printf '# R\n## 1. A\nOur text.\n## 2. B\nTODO(human)\n' > "$dir/todo.md"
expect 1 todo

printf '# R\n## 1. A\n<!-- A prompt with a table:\n\n| Feature | Tick |\n|---|---|\n| Speakers | 3 |\n-->\n## 2. B\nOur text.\n' > "$dir/multi-line-comment-with-table.md"
expect 1 multi-line-comment-with-table

[ "$failures" -eq 0 ] && echo "all check-review tests passed"
