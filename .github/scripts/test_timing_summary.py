#!/usr/bin/env python3
"""Summarise Gradle unit-test timings as a GitHub Actions step summary.

Usage: test_timing_summary.py <test-results-dir> [--shard N/M] [--warn-over SECONDS]

Reads the JUnit XML that Gradle writes to app/build/test-results/<task>/ and
prints a markdown report of:
  - The slowest test classes by total time (including test count and median test time)
  - The slowest test classes by median test time (for classes with >=3 tests)
  - The slowest individual tests (flagging likely warm-up tests exceeding 5x class median)

CI shows where the test wall time actually goes and highlights Robolectric sandbox boot costs.

--shard labels the report with which CI shard produced it, for the optional
sharded run (-PtestShardTotal/-PtestShardIndex). --warn-over prints a loud
warning when the summed test time exceeds the given seconds, so a suite that
has grown past what one CI job should carry gets noticed.
"""
import argparse
import glob
import math
import os
import statistics
import sys
import xml.etree.ElementTree as ET

TOP_N = 15

def _parse_time(time_str: str | None, path: str) -> float:
    try:
        if time_str is None:
            return 0.0
        val = float(time_str)
        if not math.isfinite(val) or val < 0.0:
            print(f"Warning: malformed time '{time_str}' in {os.path.basename(path)}, defaulting to 0.0", file=sys.stderr)
            return 0.0
        return val
    except ValueError:
        print(f"Warning: malformed time '{time_str}' in {os.path.basename(path)}, defaulting to 0.0", file=sys.stderr)
        return 0.0

def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("results_dir")
    parser.add_argument("--shard", default=None, help="shard label, e.g. 1/2")
    parser.add_argument("--warn-over", type=float, default=None,
                        help="warn when summed test seconds exceed this")
    args = parser.parse_args()

    results_dir = args.results_dir
    files = glob.glob(os.path.join(results_dir, "*.xml"))
    if not files:
        print(f"No test result XML found in `{results_dir}`.")
        return 0

    class_map = {}
    total = 0.0

    for path in files:
        try:
            root = ET.parse(path).getroot()
        except ET.ParseError as exc:
            print(f"Skipping unreadable result file `{path}`: {exc}", file=sys.stderr)
            continue
        elapsed = _parse_time(root.get("time"), path)
        class_name = root.get("name") or "?"
        total += elapsed

        if class_name not in class_map:
            class_map[class_name] = {
                "elapsed": 0.0,
                "cases": []
            }
        class_map[class_name]["elapsed"] += elapsed

        for case in root.iter("testcase"):
            owner = (case.get("classname") or "?").rsplit(".", 1)[-1]
            case_name = case.get("name") or "?"
            case_elapsed = _parse_time(case.get("time"), path)
            class_map[class_name]["cases"].append((case_elapsed, f"{owner}.{case_name}"))

    classes = []
    cases = []

    for class_name, data in class_map.items():
        class_elapsed = data["elapsed"]
        class_cases = data["cases"]
        test_times = [c[0] for c in class_cases]
        test_count = len(test_times)
        class_median = statistics.median(test_times) if test_times else 0.0

        classes.append((class_elapsed, class_name, test_count, class_median))

        for case_elapsed, case_display_name in class_cases:
            is_warmup = "yes" if (test_count >= 3 and case_elapsed > 5 * class_median) else ""
            cases.append((case_elapsed, case_display_name, is_warmup))

    classes.sort(key=lambda x: (x[0], x[1]), reverse=True)
    cases.sort(key=lambda x: (x[0], x[1]), reverse=True)

    classes_by_median = [
        (median_val, name, count, elapsed)
        for elapsed, name, count, median_val in classes
        if count >= 3
    ]
    classes_by_median.sort(key=lambda x: (x[0], x[3], x[1]), reverse=True)

    shard_suffix = f" (shard {args.shard})" if args.shard else ""
    print(f"## Unit test timing{shard_suffix}")
    print()
    print(f"{len(cases)} tests in {len(classes)} classes, {total:.1f}s of test time summed across forks.")
    print()
    if args.warn_over is not None and total > args.warn_over:
        subject = f"shard {args.shard}" if args.shard else "the suite"
        print(f"> ⚠️ **Slow tests**: {subject} summed {total:.0f}s, over the "
              f"{args.warn_over:.0f}s threshold. Speed up the slowest classes below "
              f"(Robolectric classes dominate), or split the run across shards "
              f"(-PtestShardTotal/-PtestShardIndex, see test.yml) — then update "
              f"--warn-over in test.yml.")
        print()
    print(f"### {TOP_N} slowest test classes")
    print()
    print("| Class | Seconds | % of total | Tests | Median s/test |")
    print("| --- | --- | --- | --- | --- |")
    for elapsed, name, count, median_val in classes[:TOP_N]:
        share = (elapsed / total * 100) if total else 0.0
        print(f"| `{name}` | {elapsed:.1f} | {share:.1f}% | {count} | {median_val:.2f} |")
    print()
    print(f"### {TOP_N} slowest classes per test (median, ≥3 tests)")
    print()
    print("| Class | Median s/test | Tests | Seconds |")
    print("| --- | --- | --- | --- |")
    for median_val, name, count, elapsed in classes_by_median[:TOP_N]:
        print(f"| `{name}` | {median_val:.2f} | {count} | {elapsed:.1f} |")
    print()
    print(f"### {TOP_N} slowest individual tests")
    print()
    print("| Test | Seconds | Likely warm-up |")
    print("| --- | --- | --- |")
    for elapsed, name, warmup in cases[:TOP_N]:
        print(f"| `{name}` | {elapsed:.1f} | {warmup} |")
    return 0


if __name__ == "__main__":
    sys.exit(main())
