#!/usr/bin/env python3
"""Summarise Gradle unit-test timings as a GitHub Actions step summary.

Usage: test_timing_summary.py <test-results-dir> [--shard N/M] [--warn-over SECONDS] [--warn-class-over SECONDS]

Reads the JUnit XML that Gradle writes to app/build/test-results/<task>/ and
prints a markdown report of:
  - The slowest test classes by total time (with test count, first-test excess, and median test time)
  - The slowest test classes with the first-test excess subtracted
  - The slowest test classes by median test time (for classes with >=3 tests)
  - The slowest individual tests (showing each class's first-test excess)

"First-test excess" is how much longer the first <testcase> of a result file
(document order, which is execution order) took than the median of that
file's remaining tests: max(0, first - median(rest)). It approximates one-off
setup paid by whichever test runs first (Robolectric sandbox boot for that
class's config, MockK/agent instrumentation, class init). A class with a single
test has no baseline, so its excess is reported as 0 and its whole time stays
in the "excluding" ranking. Robolectric's per-fork, per-SDK sandbox boot is
not a per-class cost: it lands on whichever class happens to run first in its
fork, and the JUnit XML does not record the fork, so a fork-leading class shows
that boot as excess and the classes after it do not.

--shard labels the report with which CI shard produced it, for the optional
sharded run (-PtestShardTotal/-PtestShardIndex). --warn-over prints a loud
warning when the summed test time exceeds the given seconds, so a suite that
has grown past what one CI job should carry gets noticed. --warn-class-over
prints a warning for each test class whose runtime excluding first-test
excess exceeds the given threshold (default: 15s).
"""
import argparse
import glob
import math
import os
import statistics
import sys
import xml.etree.ElementTree as ET

TOP_N = 15

def _first_test_excess(times: list[float]) -> float:
    """Excess of the first test over the median of the rest; 0 with no baseline."""
    if len(times) < 2:
        return 0.0
    return max(0.0, times[0] - statistics.median(times[1:]))

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
    parser.add_argument("--warn-class-over", type=float, default=15.0,
                        help="warn for each class whose seconds excluding first-test excess exceed this; <= 0 disables")
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
                "excess": 0.0,
                "cases": []
            }
        class_map[class_name]["elapsed"] += elapsed

        file_cases = []
        for case in root.iter("testcase"):
            owner = (case.get("classname") or "?").rsplit(".", 1)[-1]
            case_name = case.get("name") or "?"
            case_elapsed = _parse_time(case.get("time"), path)
            file_cases.append((case_elapsed, f"{owner}.{case_name}"))
        excess = _first_test_excess([c[0] for c in file_cases])
        class_map[class_name]["excess"] += excess
        for i, (case_elapsed, case_display_name) in enumerate(file_cases):
            class_map[class_name]["cases"].append(
                (case_elapsed, case_display_name, excess if i == 0 and excess > 0 else None))

    classes = []
    cases = []

    for class_name, data in class_map.items():
        class_elapsed = data["elapsed"]
        class_cases = data["cases"]
        test_times = [c[0] for c in class_cases]
        test_count = len(test_times)
        class_median = statistics.median(test_times) if test_times else 0.0
        warmup = data["excess"]
        excl = max(0.0, class_elapsed - warmup)

        classes.append((class_elapsed, class_name, test_count, class_median, warmup, excl))
        cases.extend(class_cases)

    classes.sort(key=lambda x: (x[0], x[1]), reverse=True)
    cases.sort(key=lambda x: (x[0], x[1]), reverse=True)

    classes_by_excl = sorted(classes, key=lambda x: (x[5], x[1]), reverse=True)

    classes_by_median = [
        (median_val, name, count, elapsed)
        for elapsed, name, count, median_val, warmup, excl in classes
        if count >= 3
    ]
    classes_by_median.sort(key=lambda x: (x[0], x[3], x[1]), reverse=True)

    warmup_total = sum(c[4] for c in classes)
    measured = sum(1 for c in classes if c[2] >= 2)

    shard_suffix = f" (shard {args.shard})" if args.shard else ""
    print(f"## Unit test timing{shard_suffix}")
    print()
    print(f"{len(cases)} tests in {len(classes)} classes, {total:.1f}s of test time summed across forks.")
    print(f"{warmup_total:.1f}s of that is first-test excess over the median of the remaining tests "
          f"({measured} classes with ≥2 tests measured; single-test classes count as 0).")
    print()
    if args.warn_over is not None and total > args.warn_over:
        subject = f"shard {args.shard}" if args.shard else "the suite"
        print(f"> ⚠️ **Slow tests**: {subject} summed {total:.0f}s, over the "
              f"{args.warn_over:.0f}s threshold. Speed up the slowest classes below "
              f"(Robolectric classes dominate), or split the run across shards "
              f"(-PtestShardTotal/-PtestShardIndex, see test.yml) — then update "
              f"--warn-over in test.yml.")
        print()
    if args.warn_class_over > 0:
        slow = [row for row in classes_by_excl if row[5] > args.warn_class_over]
        if slow:
            print(f"> ⚠️ **Slow test classes**: {len(slow)} classes over "
                  f"{args.warn_class_over:.0f}s excluding first-test excess")
            for elapsed, name, count, median_val, warmup, excl in slow[:TOP_N]:
                print(f"> - `{name}` {excl:.1f}s ({count} tests)")
            print()
    print(f"### {TOP_N} slowest test classes")
    print()
    print("| Class | Seconds | First-test excess s | % of total | Tests | Median s/test |")
    print("| --- | --- | --- | --- | --- | --- |")
    for elapsed, name, count, median_val, warmup, excl in classes[:TOP_N]:
        share = (elapsed / total * 100) if total else 0.0
        print(f"| `{name}` | {elapsed:.1f} | {warmup:.1f} | {share:.1f}% | {count} | {median_val:.2f} |")
    print()
    print(f"### {TOP_N} slowest test classes excluding first-test excess")
    print()
    print("| Class | Seconds excl. first-test excess | First-test excess s | Seconds | Tests |")
    print("| --- | --- | --- | --- | --- |")
    for elapsed, name, count, median_val, warmup, excl in classes_by_excl[:TOP_N]:
        print(f"| `{name}` | {excl:.1f} | {warmup:.1f} | {elapsed:.1f} | {count} |")
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
    print("| Test | Seconds | First-test excess s |")
    print("| --- | --- | --- |")
    for elapsed, name, excess in cases[:TOP_N]:
        excess_cell = f"{excess:.1f}" if excess is not None else ""
        print(f"| `{name}` | {elapsed:.1f} | {excess_cell} |")
    return 0


if __name__ == "__main__":
    sys.exit(main())
