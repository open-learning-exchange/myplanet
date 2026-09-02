# Merge plan — open-learning-exchange/myplanet

**BASE** `master` @ `d06a82b5c8312f848169169604e77003099d71ca`  
**PR heads fetched** 2026-09-02T07:35:50Z  
**Labels this run** MERGE=`merge` (7) · PRIORITY=`priority` (0) · READY=`ready` (6) · QUEUE=`automerge` (0) · `conflict` (0)

Re-run if BASE moves for any reason other than executing it.

## Headline: the merge queue is exhausted, not drained

**5 of the 7 `merge`-labelled PRs are broken against BASE.** Wave 1 is down to 2 PRs, one of which is a 1060-line `experiment` awaiting a human decision. Throughput from here is gated almost entirely on rebasing five PRs, four of which touch one or two files each.

## Previous plan — how far it got

| wave | n | merged | still open | closed unmerged |
|---|---|---|---|---|
| 1 | 5 | **3** | 2 | 0 |
| 2 (held) | 1 | 0 | 1 | 0 |
| 3 (broken) | 5 | 0 | 5 | 0 |

3 commits landed (#16595, #16605, #16639), all from wave 1.

### Every held PR has now decayed — 4 for 4

[16441](https://github.com/open-learning-exchange/myplanet/pull/16441) was held across three consecutive plans, its head never moved, and it has now decayed into the broken bucket. That completes the pattern:

| held PR | outcome |
|---|---|
| [16096](https://github.com/open-learning-exchange/myplanet/pull/16096) | decayed to broken |
| [16503](https://github.com/open-learning-exchange/myplanet/pull/16503) | decayed to broken |
| [16600](https://github.com/open-learning-exchange/myplanet/pull/16600) | decayed to broken |
| [16441](https://github.com/open-learning-exchange/myplanet/pull/16441) | decayed to broken (this run) |

Set against the 17 PRs that carried a `conflict` label and were all rebased clean, the evidence across four intervals is one-directional: **a broken PR with a label gets fixed; a broken PR without one does not.**

## The five stalled PRs

None of these heads has moved since 2026-09-01, and none carries a `conflict` label. Four are one- or two-file rebases.

| # | behind | files | last updated | title |
|---|---|---|---|---|
| [16600](https://github.com/open-learning-exchange/myplanet/pull/16600) | 30 | 2 | 09-01 15:59 | all: smoother notifications task date caching (fixes |
| [16096](https://github.com/open-learning-exchange/myplanet/pull/16096) | 22 | 8 | 09-01 17:32 | life: smoother life layout list loading (fixes #1608 |
| [16398](https://github.com/open-learning-exchange/myplanet/pull/16398) | 18 | 2 | 09-01 18:14 | sync: smoother url utils base64 handling (fixes #163 |
| [16441](https://github.com/open-learning-exchange/myplanet/pull/16441) | 18 | 1 | 09-01 17:38 | all: smoother file utils url path resolving (fixes # |
| [16503](https://github.com/open-learning-exchange/myplanet/pull/16503) | 18 | 2 | 09-01 17:35 | all: smoother version utils comparing (fixes #16500) |

[16661](https://github.com/open-learning-exchange/myplanet/pull/16661) is also broken but is `ready`-only, so it is report-only here.

## Wave 1 — 2 PRs

Verified: both chained cumulatively onto BASE with **zero** merge failures. 1218 changed lines. No conflict edges, nothing held, no same-file overlap.

| # | diff | labels | title |
|---|---|---|---|
| [16397](https://github.com/open-learning-exchange/myplanet/pull/16397) | 158 |  | enterprises: smoother reports dao filtering (fixes #16373) |
| [16585](https://github.com/open-learning-exchange/myplanet/pull/16585) | 1060 | `experiment` | all: smoother dictionary teams achievement view modelling (fixes #16576) |

## Wave 2 — empty

No candidate pair even shares a file this run.

## Wave 3 — 6 broken against BASE

[16096](https://github.com/open-learning-exchange/myplanet/pull/16096) · [16398](https://github.com/open-learning-exchange/myplanet/pull/16398) · [16441](https://github.com/open-learning-exchange/myplanet/pull/16441) · [16503](https://github.com/open-learning-exchange/myplanet/pull/16503) · [16600](https://github.com/open-learning-exchange/myplanet/pull/16600) · [16661](https://github.com/open-learning-exchange/myplanet/pull/16661)

## Priority

No open PR carries `priority`. Protection has cost 0 PRs in every run where it applied.

## `ready` impact (report only — never merged by this plan)

5 `ready`-only PRs are clean. Landing wave 1 breaks **none** of them.

## Caveats

- Conflict detection is **textual**. A clean merge is not a passing build — the previous interval showed this concretely, when #16595 was queued and then pulled for a `failing` label nine minutes later.
- [16585](https://github.com/open-learning-exchange/myplanet/pull/16585) is half the wave by line count (1060 of 1218) and still carries **`experiment`**, a label with no description in the repo. It has now been in the wave for three runs awaiting a call. This plan does not gate on it, but merging a 1060-line experiment is a decision, not a default.
- Both wave-1 titles are house style, so nothing to hand off.
