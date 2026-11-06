#!/usr/bin/env bash
# Prints the line coverage of the production code from the latest JaCoCo report,
# and the floor (coverage.line.minimum) recorded in pom.xml.
set -euo pipefail
export LC_ALL=C

root=$(git rev-parse --show-toplevel)
csv="$root/target/site/jacoco/jacoco.csv"

if [[ ! -f "$csv" ]]; then
    echo "No coverage report at $csv: run ./mvnw package first." >&2
    exit 1
fi

floor=$(sed -n 's:.*<coverage.line.minimum>\(.*\)</coverage.line.minimum>.*:\1:p' "$root/pom.xml")

awk -F, -v floor="${floor:-not set}" '
    NR == 1 { for (i = 1; i <= NF; i++) column[$i] = i; next }
    { missed += $column["LINE_MISSED"]; covered += $column["LINE_COVERED"] }
    END {
        total = missed + covered
        printf "Lines covered: %d/%d (%.1f %%)\n", covered, total, 100 * covered / total
        printf "Ratio:         %.3f (rounded down, the value for coverage.line.minimum)\n", int(1000 * covered / total) / 1000
        printf "Floor in pom:  %s\n", floor
    }
' "$csv"
