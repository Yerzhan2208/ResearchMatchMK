#!/usr/bin/env python3
"""ResearchMatch coverage dashboard.

Parses the JaCoCo CSV/XML produced by `mvn ... jacoco:report` and prints a concise coverage
summary, an optional self-contained HTML dashboard, and optionally opens the JaCoCo HTML report.

This is a read-only helper over Maven's own output; it adds no runtime dependencies to the app.

Usage (run from the project root):

    scripts/coverage.py                 # summarise the last run (reads target/site/jacoco)
    scripts/coverage.py --run           # run `mvn clean test jacoco:report` first, then summarise
    scripts/coverage.py --open          # open the JaCoCo HTML report in a browser
    scripts/coverage.py --html          # also write target/site/jacoco-dashboard.html
    scripts/coverage.py --class Facade  # focus the per-class table on matching class names
    scripts/coverage.py --fail-line 100 # exit non-zero if line coverage < 100%
    scripts/coverage.py --fail-branch 85

Exit codes: 0 = ok (and all --fail-* thresholds met), 1 = a threshold failed, 2 = report missing
(unless --run was given).
"""

from __future__ import annotations

import argparse
import csv
import html
import os
import subprocess
import sys
import webbrowser
from dataclasses import dataclass
from pathlib import Path

REPORT_DIR = Path("target/site/jacoco")
CSV_PATH = REPORT_DIR / "jacoco.csv"
HTML_INDEX = REPORT_DIR / "index.html"
DASHBOARD_PATH = Path("target/site/jacoco-dashboard.html")

LINE_GOAL = 100.0          # project policy: 100% line coverage
BRANCH_GATE = 85.0         # project policy: >= 85% branch coverage (mvn verify gate)


@dataclass
class ClassCoverage:
    package: str
    name: str
    line_covered: int
    line_missed: int
    branch_covered: int
    branch_missed: int

    @property
    def line_total(self) -> int:
        return self.line_covered + self.line_missed

    @property
    def branch_total(self) -> int:
        return self.branch_covered + self.branch_missed

    @property
    def line_pct(self) -> float:
        return 100.0 * self.line_covered / self.line_total if self.line_total else 100.0

    @property
    def branch_pct(self) -> float:
        return 100.0 * self.branch_covered / self.branch_total if self.branch_total else 100.0

    @property
    def uncovered(self) -> int:
        return self.line_missed + self.branch_missed


def run_maven() -> None:
    print("Running: mvn -q clean test jacoco:report ...")
    result = subprocess.run(["mvn", "-q", "clean", "test", "jacoco:report"])
    if result.returncode != 0:
        print("Maven build FAILED (tests or compilation). Aborting.", file=sys.stderr)
        sys.exit(result.returncode)


def load(csv_path: Path) -> list[ClassCoverage]:
    if not csv_path.exists():
        print(f"No JaCoCo report found at {csv_path}. Run `mvn clean test jacoco:report` "
              f"or pass --run.", file=sys.stderr)
        sys.exit(2)
    rows: list[ClassCoverage] = []
    with csv_path.open(newline="") as fh:
        for row in csv.DictReader(fh):
            rows.append(ClassCoverage(
                package=row["PACKAGE"].split(".")[-1],
                name=row["CLASS"],
                line_covered=int(row["LINE_COVERED"]),
                line_missed=int(row["LINE_MISSED"]),
                branch_covered=int(row["BRANCH_COVERED"]),
                branch_missed=int(row["BRANCH_MISSED"]),
            ))
    return rows


def totals(rows: list[ClassCoverage]) -> dict[str, float]:
    lc = sum(r.line_covered for r in rows)
    lm = sum(r.line_missed for r in rows)
    bc = sum(r.branch_covered for r in rows)
    bm = sum(r.branch_missed for r in rows)
    line_total = lc + lm
    branch_total = bc + bm
    return {
        "line_covered": lc,
        "line_total": line_total,
        "line_pct": 100.0 * lc / line_total if line_total else 100.0,
        "branch_covered": bc,
        "branch_total": branch_total,
        "branch_pct": 100.0 * bc / branch_total if branch_total else 100.0,
    }


def badge(ok: bool, text: str) -> str:
    return f"\033[42m\033[30m {text} \033[0m" if ok else f"\033[41m\033[37m {text} \033[0m"


def print_summary(rows: list[ClassCoverage], t: dict[str, float], name_filter: str | None) -> None:
    line_ok = t["line_pct"] >= LINE_GOAL
    branch_ok = t["branch_pct"] >= BRANCH_GATE

    print()
    print("=" * 68)
    print(" ResearchMatch coverage")
    print("=" * 68)
    print(f"  Line coverage  : {t['line_covered']}/{t['line_total']}  "
          f"= {t['line_pct']:.2f}%  (goal {LINE_GOAL:.0f}%)   {badge(line_ok, 'PASS' if line_ok else 'FAIL')}")
    print(f"  Branch coverage: {t['branch_covered']}/{t['branch_total']}  "
          f"= {t['branch_pct']:.2f}%  (gate {BRANCH_GATE:.0f}%)   {badge(branch_ok, 'PASS' if branch_ok else 'FAIL')}")
    print("-" * 68)

    shown = [r for r in rows if name_filter is None or name_filter.lower() in r.name.lower()]
    shown.sort(key=lambda r: (-r.uncovered, r.name))

    if name_filter is not None:
        print(f"  Classes matching '{name_filter}': {len(shown)}")
        print("-" * 68)

    header = f"  {'Class':32} {'Line':>10} {'Branch':>10}"
    print(header)
    print("  " + "-" * 64)
    any_gap = False
    for r in shown:
        gap = r.uncovered > 0
        any_gap = any_gap or gap
        line = f"{r.line_pct:6.1f}%"
        branch = f"{r.branch_pct:6.1f}%" if r.branch_total else "    -  "
        mark = "  <-- gap" if gap else ""
        print(f"  {r.name:32} {line:>10} {branch:>10}{mark}")

    if not any_gap:
        print("  All classes fully covered.")
    print("=" * 68)
    print(f"  HTML report: {HTML_INDEX}   (open with --open, or write a dashboard with --html)")
    print("=" * 68)
    print()


def write_dashboard(rows: list[ClassCoverage], t: dict[str, float]) -> None:
    line_ok = t["line_pct"] >= LINE_GOAL
    branch_ok = t["branch_pct"] >= BRANCH_GATE
    rows_sorted = sorted(rows, key=lambda r: (-r.uncovered, r.name))

    def row_html(r: ClassCoverage) -> str:
        cls = "good" if r.line_pct >= LINE_GOAL else ("warn" if r.line_pct >= 90 else "bad")
        branch = f"{r.branch_pct:.1f}%" if r.branch_total else "—"
        bcls = "good" if (r.branch_total == 0 or r.branch_pct >= BRANCH_GATE) else "bad"
        return (
            f"<tr><td>{html.escape(r.package)}</td><td>{html.escape(r.name)}</td>"
            f"<td class='{cls}'>{r.line_pct:.1f}%</td><td>{r.line_covered}/{r.line_total}</td>"
            f"<td class='{bcls}'>{branch}</td><td>{r.branch_covered}/{r.branch_total}</td>"
            f"<td>{r.uncovered}</td></tr>"
        )

    doc = f"""<!doctype html>
<html lang="en"><head><meta charset="utf-8">
<title>ResearchMatch — coverage dashboard</title>
<style>
 body {{ font-family: -apple-system, Segoe UI, Roboto, sans-serif; margin: 2rem; color:#1f2328; }}
 h1 {{ font-size: 1.4rem; }}
 .gauges {{ display:flex; gap:2rem; margin:1.5rem 0; }}
 .gauge {{ border:1px solid #d0d7de; border-radius:10px; padding:1rem 1.5rem; min-width:180px; }}
 .big {{ font-size:2rem; font-weight:600; }}
 .pass {{ color:#1a7f37; }} .fail {{ color:#cf222e; }}
 table {{ border-collapse:collapse; width:100%; font-size:.9rem; }}
 th,td {{ border-bottom:1px solid #e5e7eb; padding:.4rem .6rem; text-align:left; }}
 th {{ background:#f6f8fa; position:sticky; top:0; }}
 td.good {{ color:#1a7f37; }} td.warn {{ color:#9a6700; }} td.bad {{ color:#cf222e; font-weight:600; }}
 code {{ background:#f6f8fa; padding:.1rem .3rem; border-radius:4px; }}
</style></head><body>
<h1>ResearchMatch — coverage dashboard</h1>
<div class="gauges">
  <div class="gauge"><div>Line coverage</div>
    <div class="big {'pass' if line_ok else 'fail'}">{t['line_pct']:.2f}%</div>
    <div>{t['line_covered']}/{t['line_total']} &middot; goal {LINE_GOAL:.0f}%</div></div>
  <div class="gauge"><div>Branch coverage</div>
    <div class="big {'pass' if branch_ok else 'fail'}">{t['branch_pct']:.2f}%</div>
    <div>{t['branch_covered']}/{t['branch_total']} &middot; gate {BRANCH_GATE:.0f}%</div></div>
</div>
<p>Generated from <code>target/site/jacoco/jacoco.csv</code>. For the line-by-line source view open
<code>target/site/jacoco/index.html</code>.</p>
<table>
<thead><tr><th>Package</th><th>Class</th><th>Line %</th><th>Lines</th><th>Branch %</th><th>Branches</th><th>Uncovered</th></tr></thead>
<tbody>
{''.join(row_html(r) for r in rows_sorted)}
</tbody></table>
</body></html>
"""
    DASHBOARD_PATH.parent.mkdir(parents=True, exist_ok=True)
    DASHBOARD_PATH.write_text(doc)
    print(f"Wrote dashboard: {DASHBOARD_PATH}")


def main() -> int:
    parser = argparse.ArgumentParser(description="ResearchMatch JaCoCo coverage dashboard")
    parser.add_argument("--run", action="store_true", help="run `mvn clean test jacoco:report` first")
    parser.add_argument("--open", action="store_true", help="open the JaCoCo HTML report in a browser")
    parser.add_argument("--html", action="store_true", help="write a self-contained HTML dashboard")
    parser.add_argument("--class", dest="name_filter", default=None,
                        help="only list classes whose name contains this substring")
    parser.add_argument("--fail-line", type=float, default=None,
                        help="exit non-zero if line coverage is below this percentage")
    parser.add_argument("--fail-branch", type=float, default=None,
                        help="exit non-zero if branch coverage is below this percentage")
    args = parser.parse_args()

    if args.run:
        run_maven()

    rows = load(CSV_PATH)
    t = totals(rows)
    print_summary(rows, t, args.name_filter)

    if args.html:
        write_dashboard(rows, t)
    if args.open:
        if HTML_INDEX.exists():
            webbrowser.open(HTML_INDEX.resolve().as_uri())
        else:
            print(f"Cannot open: {HTML_INDEX} not found.", file=sys.stderr)

    failed = False
    if args.fail_line is not None and t["line_pct"] < args.fail_line:
        print(f"Line coverage {t['line_pct']:.2f}% < required {args.fail_line:.2f}%", file=sys.stderr)
        failed = True
    if args.fail_branch is not None and t["branch_pct"] < args.fail_branch:
        print(f"Branch coverage {t['branch_pct']:.2f}% < required {args.fail_branch:.2f}%", file=sys.stderr)
        failed = True
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
