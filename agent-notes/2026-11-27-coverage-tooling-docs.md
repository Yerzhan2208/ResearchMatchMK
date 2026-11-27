# Coverage tooling docs fix — issue #26

**Date:** 2026-11-27
**Author:** Member 5 (QA / tooling) with the AI agent
**Branch:** `fix/issue-26-coverage-tooling-docs`
**Status:** done

---

## Summary

Fixed the documentation inaccuracies reported in issue #26:

1. **Wrong documented path.** `AGENTS.md` referenced `coverage.bat` without the `scripts/` prefix, so
   `cmd /c "coverage.bat"` from the repo root failed. Both `AGENTS.md` (intro + §5) and `README.md` now
   spell out `scripts\coverage.bat`.
2. **Windows / `.sh` usability.** AGENTS.md §5 and README now state the `.sh` wrappers need bash
   (WSL or Git Bash) and point Windows users at `scripts\coverage.bat` (or `test.bat`).
3. **Exit-code semantics.** Gap rows are now documented as informational: the dashboard exits 0 when the
   gated counters pass; only `--fail-line`/`--fail-branch` cause a non-zero exit. (The code already
   behaved this way — `scripts/coverage.py` returns 1 only for missed explicit thresholds — this just
   makes it explicit to prevent regressions.)

## Verification

- `python3 scripts/coverage.py --no-run-first` → line 100.00% PASS, branch 94.75% PASS, exit **0**.
- Docs-only change; no source or test impact.

## Follow-ups

- None. Chose **not** to add a `--strict` flag: `--fail-line`/`--fail-branch` already cover gated failure.
