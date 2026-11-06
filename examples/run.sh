#!/usr/bin/env bash
# Runs every examples/*.hurl against a running service: the published happy-path examples and our
# spec-based checks (*-rules.hurl). CI runs them against the built image (job hurl in deploy.yml).
# Passing them doesn't mean the harness's checks pass: those are opaque.
#
# Usage: examples/run.sh [base-url]        (default http://localhost:8080)
# Env:   HURL  the hurl binary (default: hurl on your PATH; https://hurl.dev)
#
# Specs released during the day publish their examples at /specs/<slug>/examples.hurl; save
# them here as <slug>.hurl and they're picked up too.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
base="${1:-http://localhost:8080}"
"${HURL:-hurl}" --test --variable "base_url=$base" "$here"/*.hurl
