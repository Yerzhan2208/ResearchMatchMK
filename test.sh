#!/usr/bin/env bash
# One-shot: run the whole test suite and generate the coverage dashboard.
#
# Usage:
#   ./test.sh          # run tests, print summary, write + open the HTML dashboard
#   ./test.sh --no-open # run tests and write the dashboard, but do not open a browser
#
# Extra args are passed through to scripts/coverage.py (e.g. --fail-line 100 --fail-branch 85).
set -euo pipefail
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

OPEN=1
PASSTHRU=()
for arg in "$@"; do
  if [ "$arg" = "--no-open" ]; then
    OPEN=0
  else
    PASSTHRU+=("$arg")
  fi
done

ARGS=(--run --html)
if [ "$OPEN" = "1" ]; then
  ARGS+=(--open)
fi
exec python3 "$DIR/scripts/coverage.py" "${ARGS[@]}" "${PASSTHRU[@]+"${PASSTHRU[@]}"}"
