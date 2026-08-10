#!/usr/bin/env python3
"""Summarise Gradle unit-test timings as a GitHub Actions step summary.

Usage: test_timing_summary.py <test-results-dir>

Reads the JUnit XML that Gradle writes to app/build/test-results/<task>/ and
prints a markdown report of the slowest test classes and individual tests, so
CI shows where the test wall time actually goes.
"""
import glob
import os
import sys
import xml.etree.ElementTree as ET

TOP_N = 15


def main() -> int:
    if len(sys.argv) != 2:
        print(f"usage: {os.path.basename(sys.argv[0])} <test-results-dir>", file=sys.stderr)
        return 2

    results_dir = sys.argv[1]
    files = glob.glob(os.path.join(results_dir, "*.xml"))
    if not files:
        print(f"No test result XML found in `{results_dir}`.")
        return 0

    classes = []
    cases = []
    total = 0.0

    for path in files:
        try:
            root = ET.parse(path).getroot()
        except ET.ParseError as exc:
            print(f"Skipping unreadable result file `{path}`: {exc}", file=sys.stderr)
            continue
        elapsed = float(root.get("time") or 0)
        classes.append((elapsed, root.get("name") or "?"))
        total += elapsed
        for case in root.iter("testcase"):
            owner = (case.get("classname") or "?").rsplit(".", 1)[-1]
            cases.append((float(case.get("time") or 0), f"{owner}.{case.get('name') or '?'}"))

    classes.sort(reverse=True)
    cases.sort(reverse=True)

    print("## Unit test timing")
    print()
    print(f"{len(cases)} tests in {len(classes)} classes, {total:.1f}s of test time summed across forks.")
    print()
    print(f"### {TOP_N} slowest test classes")
    print()
    print("| Class | Seconds | % of total |")
    print("| --- | --- | --- |")
    for elapsed, name in classes[:TOP_N]:
        share = (elapsed / total * 100) if total else 0.0
        print(f"| `{name}` | {elapsed:.1f} | {share:.1f}% |")
    print()
    print(f"### {TOP_N} slowest individual tests")
    print()
    print("| Test | Seconds |")
    print("| --- | --- |")
    for elapsed, name in cases[:TOP_N]:
        print(f"| `{name}` | {elapsed:.1f} |")
    return 0


if __name__ == "__main__":
    sys.exit(main())
