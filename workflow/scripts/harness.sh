#!/usr/bin/env bash
# Harness API client: spec <feature> | feedback | timeline <feature>/<check>
# Reads URL, team and token from .fagdag/. Never prints the token; never use set -x here.
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"
url="$(cat .fagdag/harness-url)"
team="$(cat .fagdag/team)"

case "${1:-}" in
    spec)     [ -n "${2:-}" ] || { echo "usage: $0 spec <feature>" >&2; exit 2; }; path="/specs/$2/spec.md" ;;
    feedback) path="/api/teams/$team/feedback" ;;
    timeline) [ -n "${2:-}" ] || { echo "usage: $0 timeline <feature>/<check>" >&2; exit 2; }; path="/api/teams/$team/feedback/timeline?check=$2" ;;
    *)        echo "usage: $0 spec <feature> | feedback | timeline <feature>/<check>" >&2; exit 2 ;;
esac

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

fetch() {
    curl -sS -D "$tmp/headers" -o "$tmp/body" -w '%{http_code}' \
        -H "Authorization: Bearer $(cat .fagdag/harness-token)" "$url$path"
}

code="$(fetch)"
if [ "$code" = 429 ]; then
    wait="$(awk 'tolower($1) == "retry-after:" { print $2 + 0 }' "$tmp/headers")"
    sleep "${wait:-1}"
    code="$(fetch)"
fi

if [ "${code:0:1}" != 2 ]; then
    echo "HTTP $code for $path" >&2
    cat "$tmp/body" >&2
    exit 1
fi
cat "$tmp/body"
