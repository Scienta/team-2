#!/usr/bin/env bash
# A prod-like rollout on this machine, like .github/fagdag/rollout.sh does on Scaleway:
#   1. build the root Dockerfile for linux/amd64 with this commit's SHA baked in (-dirty if the tree is),
#   2. start it with docker compose (profile app) against the pgdata Postgres, which keeps its data,
#   3. poll GET /health until it answers 200 with that SHA, for up to ROLLOUT_GATE_SECONDS (60),
#   4. run examples/run.sh against it (skip with --no-examples).
# The service stays up afterwards. docker compose --profile app down stops it; add -v to empty the database.
#
# Env: APP_PORT (8080), PG_PORT (5432), ROLLOUT_GATE_SECONDS (60), COMPOSE_PROJECT_NAME (the directory name).
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"
run_examples=1
[ "${1:-}" = "--no-examples" ] && run_examples=0

sha="$(git rev-parse HEAD)"
[ -z "$(git status --porcelain)" ] || sha="$sha-dirty"
url="http://localhost:${APP_PORT:-8080}"
gate="${ROLLOUT_GATE_SECONDS:-60}"

echo "Building and starting $sha"
GIT_SHA="$sha" docker compose --profile app up -d --build app

deadline=$((SECONDS + gate))
body=""
while [ "$SECONDS" -lt "$deadline" ]; do
    body="$(curl -fsS --max-time 5 "$url/health" 2>/dev/null || true)"
    if [ "$(jq -r '.version? // empty' <<<"$body" 2>/dev/null)" = "$sha" ]; then
        echo "✅ $url/health: $body"
        break
    fi
    sleep 2
done

if [ "$(jq -r '.version? // empty' <<<"$body" 2>/dev/null)" != "$sha" ]; then
    echo "❌ $url/health didn't report $sha within $gate s (last answer: ${body:-none})" >&2
    docker compose --profile app logs --tail 100 app >&2
    exit 1
fi

if [ "$run_examples" = 1 ]; then
    examples/run.sh "$url"
fi
