#!/usr/bin/env bash
# Every "## " heading in REVIEW.md needs a line of our own text: non-blank, outside any HTML
# comment (comments may span lines), and without TODO. Not a build gate: run it before 17:00.
set -euo pipefail

file="${1:-REVIEW.md}"

awk '
    function close_heading() {
        if (heading != "" && !filled) { print "empty: " heading; empty++ }
    }
    /^## / { close_heading(); heading = $0; filled = 0; next }
    {
        line = $0; text = ""
        while (length(line) > 0) {
            if (incomment) {
                end = index(line, "-->")
                if (end == 0) { line = "" } else { line = substr(line, end + 3); incomment = 0 }
            } else {
                start = index(line, "<!--")
                if (start == 0) { text = text line; line = "" }
                else { text = text substr(line, 1, start - 1); line = substr(line, start + 4); incomment = 1 }
            }
        }
        if (heading != "" && text ~ /[^[:space:]]/ && text !~ /TODO/) filled = 1
    }
    END {
        close_heading()
        if (empty) exit 1
    }
' "$file" && echo "$file: all headings filled"
