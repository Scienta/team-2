#!/usr/bin/env bash
set -euo pipefail

script="$(cd "$(dirname "$0")" && pwd)/transcript-to-hurl.sh"
dir="$(mktemp -d)"
trap 'rm -rf "$dir"' EXIT
failures=0

ok() { echo "ok   $1"; }
fail() { echo "FAIL $1"; failures=$((failures + 1)); }

cat > "$dir/feedback.json" <<'JSON'
{"features": [
  {"slug": "talks", "checks": [{"name": "check-1", "status": "OK", "transcript": null}]},
  {"slug": "co-speakers", "checks": [{"name": "check-2", "status": "FAULTY", "transcript": {"tick": 81, "omitted": 2, "requests": [
    {"method": "PUT", "path": "/talks/12/co-speakers/7", "requestBody": "{\"note\":\"x\"}", "status": 409, "responseBody": "{\"error\":\"conflict\",\n\"why\":\"main speaker\"}"},
    {"method": "DELETE", "path": "/talks/12/co-speakers/7", "requestBody": null, "status": null, "responseBody": null},
    {"method": "POST", "path": "/talks/12/ratings", "requestBody": "not json", "status": 400, "responseBody": null}
  ]}}]}
]}
JSON

out="$("$script" co-speakers/check-2 "$dir/feedback.json")"
echo "$out" > "$dir/replay.hurl"

expect_line() {
    if grep -qxF -- "$1" "$dir/replay.hurl"; then ok "has: $1"; else fail "missing: $1"; fi
}
expect_line '# Replay of co-speakers/check-2, status FAULTY, transcript from tick 81, 2 request(s) omitted by the harness.'
expect_line 'PUT {{base_url}}/talks/12/co-speakers/7'
expect_line 'Content-Type: application/json'
expect_line '{"note":"x"}'
expect_line '# production answered: 409 {"error":"conflict", "why":"main speaker"}'
expect_line 'DELETE {{base_url}}/talks/12/co-speakers/7'
expect_line '# production answered: unknown'
expect_line 'not json'
[ "$(grep -c '^HTTP \*$' "$dir/replay.hurl")" -eq 3 ] && ok "three requests, no asserts" || fail "want three HTTP * lines"
[ "$(grep -c '^Content-Type' "$dir/replay.hurl")" -eq 1 ] && ok "content type only on the JSON body" || fail "want one Content-Type line"

if command -v hurlfmt > /dev/null; then
    if hurlfmt --out hurl "$dir/replay.hurl" > /dev/null; then
        ok "hurlfmt parses it"
    else
        fail "hurlfmt can't parse it"
    fi
else
    echo "skip hurlfmt not installed"
fi

for missing in co-speakers/check-9 talks/check-1 nope/check-1; do
    code=0
    "$script" "$missing" "$dir/feedback.json" > /dev/null 2>&1 || code=$?
    [ "$code" -eq 1 ] && ok "$missing: exit 1" || fail "$missing: exit $code, want 1"
done

code=0
"$script" no-slash > /dev/null 2>&1 || code=$?
[ "$code" -eq 2 ] && ok "usage: exit 2" || fail "usage: exit $code, want 2"

[ "$failures" -eq 0 ] && echo "all transcript-to-hurl tests passed"
