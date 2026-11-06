#!/usr/bin/env bash
# Prints the line coverage of the production code from the latest JaCoCo report,
# and the floor (coverage.line.minimum) recorded in pom.xml.
set -euo pipefail
export LC_ALL=C

root=$(git rev-parse --show-toplevel)
xml="$root/target/site/jacoco/jacoco.xml"

if [[ ! -f "$xml" ]]; then
    echo "No coverage report at $xml: run ./mvnw package first." >&2
    exit 1
fi

floor=$(sed -n 's:.*<coverage.line.minimum>\(.*\)</coverage.line.minimum>.*:\1:p' "$root/pom.xml")

# The report-wide LINE counter (the last one in the file) is what the coverage check in pom.xml uses.
# Summing the per-class rows of jacoco.csv counts a line twice when it belongs to two classes
# (e.g. an anonymous class opened on that line), which overstates the ratio.
counter=$(grep -o '<counter type="LINE" missed="[0-9]*" covered="[0-9]*"/>' "$xml" | tail -1)
missed=$(echo "$counter" | sed 's/.*missed="\([0-9]*\)".*/\1/')
covered=$(echo "$counter" | sed 's/.*covered="\([0-9]*\)".*/\1/')

awk -v missed="$missed" -v covered="$covered" -v floor="${floor:-not set}" '
    BEGIN {
        total = missed + covered
        printf "Lines covered: %d/%d (%.1f %%)\n", covered, total, 100 * covered / total
        printf "Ratio:         %.3f (rounded down, the value for coverage.line.minimum)\n", int(1000 * covered / total) / 1000
        printf "Floor in pom:  %s\n", floor
    }
'
