#!/usr/bin/env bash
# Convenience wrapper: run the coverage dashboard (see scripts/coverage.py for all options).
# Examples:
#   scripts/coverage.sh               # summarise the last JaCoCo run
#   scripts/coverage.sh --run --html  # run tests + write target/site/jacoco-dashboard.html
#   scripts/coverage.sh --open        # open the JaCoCo HTML report
set -euo pipefail
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec python3 "$DIR/coverage.py" "$@"
