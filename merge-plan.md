# Merge plan — open-learning-exchange/myplanet

**BASE** `master` @ `c359af898470c3679782061ddd6ba4d952e88c65`  
**PR heads fetched** 2026-09-02T01:45:33Z  
**Labels this run** MERGE=`merge` (10) · PRIORITY=`priority` (0) · READY=`ready` (6) · QUEUE=`automerge` (1) · `conflict` (0)

Re-run if BASE moves for any reason other than executing it.

## Previous plan — how far it got

| wave | n | merged | still open | closed unmerged |
|---|---|---|---|---|
| 1 | 6 | **3** | 3 | 0 |
| 2 (held) | 1 | 0 | 1 | 0 |
| 3 (broken) | 5 | 0 | 5 | 0 |

A slow interval: only 3 commits landed (#16607, #16655, #16658), all from wave 1.

### The unlabelled broken PRs have now stalled for a full day

Four of the five broken PRs are the same four as last run, and **none of their heads has moved**:

| # | head committed | commits behind | last updated |
|---|---|---|---|
| [16096](https://github.com/open-learning-exchange/myplanet/pull/16096) | 2026-09-01 17:26 | 19 | 09-01 17:32 |
| [16398](https://github.com/open-learning-exchange/myplanet/pull/16398) | 2026-09-01 14:14 | 15 | 09-01 18:14 |
| [16503](https://github.com/open-learning-exchange/myplanet/pull/16503) | 2026-09-01 17:28 | 15 | 09-01 17:35 |
| [16600](https://github.com/open-learning-exchange/myplanet/pull/16600) | 2026-09-01 11:54 | 27 | 09-01 15:59 |

All four had a `conflict` label that was removed by hand, and none has been touched since. This is the third consecutive interval pointing the same way: **labelled broken PRs get rebased; unlabelled ones sit.** `conflict` currently stands at 0 while 5 PRs are broken.

The one that did move, [16605](https://github.com/open-learning-exchange/myplanet/pull/16605), was the PR carrying `automerge` while conflicting. It rebased, is clean, and is back in wave 1 — the self-correction the previous plan predicted.

## Wave 1 — 5 PRs

Verified: all 5 chained cumulatively onto BASE with **zero** merge failures. 2174 changed lines. **No same-file overlap**, so no co-landing semantic risk.

| # | diff | labels | title |
|---|---|---|---|
| [16397](https://github.com/open-learning-exchange/myplanet/pull/16397) | 162 |  | enterprises: smoother reports dao filtering (fixes #16373) |
| [16585](https://github.com/open-learning-exchange/myplanet/pull/16585) | 1060 | `experiment` | all: smoother dictionary teams achievement view modelling (fixes #16576) |
| [16595](https://github.com/open-learning-exchange/myplanet/pull/16595) | 275 |  | all: smoother error log tagging (fixes #16358) |
| [16605](https://github.com/open-learning-exchange/myplanet/pull/16605) | 247 | `automerge` | teams: smoother voices notifications repositories dao querying (fixes #16418) |
| [16639](https://github.com/open-learning-exchange/myplanet/pull/16639) | 430 |  | sync: smoother upload pipelines unifying (fixes #16638) |

## Wave 2 — 1 held

[16441](https://github.com/open-learning-exchange/myplanet/pull/16441) conflicts with [16595](https://github.com/open-learning-exchange/myplanet/pull/16595) — the same symmetric edge as last run, still unresolved, head unmoved. It is clean against BASE, so it is held rather than broken, but it is now on the same trajectory as the four above.

## Wave 3 — 5 broken against BASE

[16096](https://github.com/open-learning-exchange/myplanet/pull/16096) · [16398](https://github.com/open-learning-exchange/myplanet/pull/16398) · [16503](https://github.com/open-learning-exchange/myplanet/pull/16503) · [16600](https://github.com/open-learning-exchange/myplanet/pull/16600) · [16661](https://github.com/open-learning-exchange/myplanet/pull/16661)

[16661](https://github.com/open-learning-exchange/myplanet/pull/16661) is new to this bucket, and the previous plan predicted exactly that: it was the one `ready` PR flagged as breaking when wave 1 landed.

## What actually gated the queue this interval was CI, not conflicts

[16595](https://github.com/open-learning-exchange/myplanet/pull/16595) was queued with `automerge` at 19:45, picked up a `failing` label at 19:54, and lost `automerge` three seconds later. `failing` was cleared at 01:44 today and it is clean and back in wave 1.

This plan reads **no CI at all**. Conflict detection here is textual, and this is a concrete case where the merge graph said "ready" and the build said otherwise. Treat wave 1 as a merge-order proposal, not a green light.

## Priority

No open PR carries `priority`. Protection has cost 0 PRs in every run where it applied.

## `ready` impact (report only — never merged by this plan)

5 `ready`-only PRs are clean. Landing wave 1 breaks **none** of them.

## Caveats

- Conflict detection is **textual**. A clean merge is not a passing build — see the #16595 case above.
- [16585](https://github.com/open-learning-exchange/myplanet/pull/16585) is still 1060 lines and still carries **`experiment`** (a label with no description in the repo). It has been in the wave for two runs awaiting a human call; this plan does not gate on it.
- [16397](https://github.com/open-learning-exchange/myplanet/pull/16397) re-enters the wave: it lost `merge` at 17:12 yesterday and regained it at 01:26 today. Labels are hints, not gates, so it is included on its current label.
- Every wave-1 title is house style, so nothing to hand off.
