# Merge plan — open-learning-exchange/myplanet

**BASE** `master` @ `32f95629a8ac7a3586bf2dc697dccf952acf7b3b` — unchanged since the previous plan  
**PR heads fetched** 2026-09-01T16:02:44Z  
**Labels this run** MERGE=`merge` (34) · PRIORITY=`priority` (3) · READY=`ready` (3) · QUEUE=`automerge` (2) · `conflict` (18)

Re-run if BASE moves for any reason other than executing it.

## Previous plan — how far it got

**7 of the 9 wave-1 PRs merged** (#16387, #16487, #16545, #16564, #16590, #16626, #16641), all via `automerge`. The remaining two, [16609](https://github.com/open-learning-exchange/myplanet/pull/16609) and [16620](https://github.com/open-learning-exchange/myplanet/pull/16620), are still open and both now carry `automerge`.

### The `conflict` labelling worked as a work signal

All 27 PRs I labelled last turn are accounted for, and the buckets sum:

| bucket | n |
|---|---|
| open, still labelled | 18 |
| open, label removed by someone | 9 |
| merged | 0 |
| closed unmerged | 0 |

**All 9 whose label was removed are now clean against BASE** — someone worked the list, rebased them, and cleared the label: [16323](https://github.com/open-learning-exchange/myplanet/pull/16323), [16332](https://github.com/open-learning-exchange/myplanet/pull/16332), [16398](https://github.com/open-learning-exchange/myplanet/pull/16398), [16436](https://github.com/open-learning-exchange/myplanet/pull/16436), [16438](https://github.com/open-learning-exchange/myplanet/pull/16438), [16442](https://github.com/open-learning-exchange/myplanet/pull/16442), [16495](https://github.com/open-learning-exchange/myplanet/pull/16495), [16513](https://github.com/open-learning-exchange/myplanet/pull/16513), [16600](https://github.com/open-learning-exchange/myplanet/pull/16600).

That took the broken bucket from **27 → 18** and roughly doubled wave 1. It is the first interval where the rebase queue moved faster than it decayed.

## Wave 1 — 13 PRs

Verified: all 13 chained cumulatively onto BASE with **zero** merge failures. 2918 changed lines.

|  |  |  |  |  |  |
|---|---|---|---|---|---|
| [16323](https://github.com/open-learning-exchange/myplanet/pull/16323) | [16332](https://github.com/open-learning-exchange/myplanet/pull/16332) | [16436](https://github.com/open-learning-exchange/myplanet/pull/16436) | [16438](https://github.com/open-learning-exchange/myplanet/pull/16438) | [16442](https://github.com/open-learning-exchange/myplanet/pull/16442) | [16495](https://github.com/open-learning-exchange/myplanet/pull/16495) |
| [16513](https://github.com/open-learning-exchange/myplanet/pull/16513) | [16594](https://github.com/open-learning-exchange/myplanet/pull/16594) | [16605](https://github.com/open-learning-exchange/myplanet/pull/16605) | [16609](https://github.com/open-learning-exchange/myplanet/pull/16609) | [16622](https://github.com/open-learning-exchange/myplanet/pull/16622) | [16637](https://github.com/open-learning-exchange/myplanet/pull/16637) |
| [16644](https://github.com/open-learning-exchange/myplanet/pull/16644) |  |  |  |  |  |

| # | diff | title |
|---|---|---|
| [16323](https://github.com/open-learning-exchange/myplanet/pull/16323) | 130 | sync: smoother url utils auth header caching (fixes #16303) |
| [16332](https://github.com/open-learning-exchange/myplanet/pull/16332) | 165 | resources: smoother repository dao querying (fixes #16304) |
| [16436](https://github.com/open-learning-exchange/myplanet/pull/16436) | 71 | all: smoother personals repository intent updating (fixes #16432) |
| [16438](https://github.com/open-learning-exchange/myplanet/pull/16438) | 3 | resources: smoother collections tags filtering (fixes #16410) |
| [16442](https://github.com/open-learning-exchange/myplanet/pull/16442) | 75 | all: smoother notifications enrichment view modelling (fixes #16402) |
| [16495](https://github.com/open-learning-exchange/myplanet/pull/16495) | 826 | sync: smoother upload coordinating (fixes #16467)(fixes #16464) |
| [16513](https://github.com/open-learning-exchange/myplanet/pull/16513) | 7 | sync: smoother collector-side status throttling (fixes #16468) |
| [16594](https://github.com/open-learning-exchange/myplanet/pull/16594) | 526 | labels: smoother size labeller fetching (fixes #16344) |
| [16605](https://github.com/open-learning-exchange/myplanet/pull/16605) | 259 | teams: smoother notification count via countTeamChats (fixes #16418) |
| [16609](https://github.com/open-learning-exchange/myplanet/pull/16609) | 139 | life: smoother health examination formatting (fixes #16452) |
| [16622](https://github.com/open-learning-exchange/myplanet/pull/16622) | 247 | dashboard: smoother back press navigating (fixes #16611) |
| [16637](https://github.com/open-learning-exchange/myplanet/pull/16637) | 356 | resources: smoother HTML download and view (fixes #16636) |
| [16644](https://github.com/open-learning-exchange/myplanet/pull/16644) | 114 | all: smoother skill agents summoning (fixes #16649) |

## Wave 2 — 3 held

Three symmetric conflict edges among the candidates (0 asymmetric across all 6 overlapping pairs). These need a rebase, not a reordering.

| held | conflicts with |
|---|---|
| [16398](https://github.com/open-learning-exchange/myplanet/pull/16398) | [16323](https://github.com/open-learning-exchange/myplanet/pull/16323) |
| [16600](https://github.com/open-learning-exchange/myplanet/pull/16600) | [16442](https://github.com/open-learning-exchange/myplanet/pull/16442) |
| [16639](https://github.com/open-learning-exchange/myplanet/pull/16639) | [16495](https://github.com/open-learning-exchange/myplanet/pull/16495) |

Note [16398](https://github.com/open-learning-exchange/myplanet/pull/16398) and [16600](https://github.com/open-learning-exchange/myplanet/pull/16600) were among the 9 just rebased out of the broken bucket, and have landed straight into a pairwise conflict instead.

## Wave 3 — 18 broken against BASE

|  |  |  |  |  |  |
|---|---|---|---|---|---|
| [16096](https://github.com/open-learning-exchange/myplanet/pull/16096) | [16335](https://github.com/open-learning-exchange/myplanet/pull/16335) | [16367](https://github.com/open-learning-exchange/myplanet/pull/16367) | [16368](https://github.com/open-learning-exchange/myplanet/pull/16368) | [16370](https://github.com/open-learning-exchange/myplanet/pull/16370) | [16397](https://github.com/open-learning-exchange/myplanet/pull/16397) |
| [16441](https://github.com/open-learning-exchange/myplanet/pull/16441) | [16503](https://github.com/open-learning-exchange/myplanet/pull/16503) | [16508](https://github.com/open-learning-exchange/myplanet/pull/16508) | [16526](https://github.com/open-learning-exchange/myplanet/pull/16526) | [16551](https://github.com/open-learning-exchange/myplanet/pull/16551) | [16554](https://github.com/open-learning-exchange/myplanet/pull/16554) |
| [16577](https://github.com/open-learning-exchange/myplanet/pull/16577) | [16585](https://github.com/open-learning-exchange/myplanet/pull/16585) | [16595](https://github.com/open-learning-exchange/myplanet/pull/16595) | [16599](https://github.com/open-learning-exchange/myplanet/pull/16599) | [16607](https://github.com/open-learning-exchange/myplanet/pull/16607) | [16620](https://github.com/open-learning-exchange/myplanet/pull/16620) |

## Priority — 3 PRs, protection still free

| # | labels | state |
|---|---|---|
| [16620](https://github.com/open-learning-exchange/myplanet/pull/16620) | `priority` `merge` `automerge` `failing` | **broken against BASE** |
| [16656](https://github.com/open-learning-exchange/myplanet/pull/16656) | `priority` `ready` | clean, report-only |
| [16659](https://github.com/open-learning-exchange/myplanet/pull/16659) | `priority` `ready` | clean, report-only |

The two clean priority PRs conflict with nothing, so greedy MIS is 13 with or without protection — cost **0 PRs**, the fifth consecutive run.

## Two label discrepancies, neither introduced by this plan

- [16605](https://github.com/open-learning-exchange/myplanet/pull/16605) carries `conflict` but now **merges clean** — it was rebased and the label was not cleared. It is in wave 1. Stale label.
- [16620](https://github.com/open-learning-exchange/myplanet/pull/16620) is **broken** but carries no `conflict` label. It broke after the labelling pass. It also carries `automerge` while conflicting, so the drainer will hit it: per the repo's own workflow a conflicting PR loses `automerge` and gains `conflict`, so this should self-correct, but it will cost a queue cycle.

## `ready` impact (report only — never merged by this plan)

3 `ready`-only PRs are clean (#16625, #16656, #16659). Landing wave 1 breaks **none**.

## Caveats

- Conflict detection is **textual**. A clean merge is not a passing build.
- One same-file overlap in wave 1: `ResourcesRepositoryImpl.kt` and its test — [16332](https://github.com/open-learning-exchange/myplanet/pull/16332) and [16637](https://github.com/open-learning-exchange/myplanet/pull/16637). They merge clean but land together, so test mid-wave.
- [16495](https://github.com/open-learning-exchange/myplanet/pull/16495) is the largest at 826 lines and [16594](https://github.com/open-learning-exchange/myplanet/pull/16594) at 526; both are freshly rebased, so they are the likeliest to break if the wave is executed slowly.
- Squash-merge carries each PR title into BASE permanently. Every wave-1 title is house style, so nothing to hand off.
