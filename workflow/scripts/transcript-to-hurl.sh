#!/usr/bin/env bash
# Turns a harness check's latest transcript into a Hurl file, to replay its requests against a local service.
#
#   workflow/scripts/transcript-to-hurl.sh <feature>/<check> [feedback.json] > /tmp/replay.hurl
#   hurl --test --variable base_url=http://localhost:8080 /tmp/replay.hurl
#
# Without a file it fetches the team feedback through harness.sh (which never prints the token). The
# transcript lives in the feedback, not in the timeline. It has method, path and body, but no headers,
# and the IDs in the paths are the ones production handed out. So every request asserts nothing
# (HTTP *), and production's answer goes in a comment: fix the IDs (captures) and add asserts before
# saving it to examples/.
set -euo pipefail

check="${1:-}"
case "$check" in
    */*) ;;
    *) echo "usage: $0 <feature>/<check> [feedback.json]" >&2; exit 2 ;;
esac
feature="${check%%/*}"
name="${check#*/}"

if [ -n "${2:-}" ]; then
    feedback="$(cat "$2")"
else
    feedback="$("$(dirname "$0")/harness.sh" feedback)"
fi

found="$(jq --arg f "$feature" --arg c "$name" \
    '[.features[]? | select(.slug == $f) | .checks[]? | select(.name == $c) | select(.transcript != null)] | first // empty' \
    <<<"$feedback")"
if [ -z "$found" ]; then
    echo "no transcript for $check in the feedback" >&2
    exit 1
fi

jq -r --arg check "$check" '
  def oneline: tostring | gsub("[\r\n]+"; " ") | if length > 200 then .[0:200] + "…" else . end;
  "# Replay of \($check), status \(.status), transcript from tick \(.transcript.tick)"
    + (if (.transcript.omitted // 0) > 0 then ", \(.transcript.omitted) request(s) omitted by the harness" else "" end) + ".",
  "# Paths hold production IDs and the transcript has no headers: fix both before trusting a replay.",
  "",
  (.transcript.requests[] |
    "\(.method) {{base_url}}\(.path)",
    (if (.requestBody // "") != "" then
        (if (.requestBody | test("^\\s*[\\[{]")) then "Content-Type: application/json" else empty end),
        "```",
        .requestBody,
        "```"
     else empty end),
    "# production answered: \(.status // "unknown")\(if .responseBody then " " + (.responseBody | oneline) else "" end)",
    "HTTP *",
    "")
' <<<"$found"
