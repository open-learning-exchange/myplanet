# Merge plan — open-learning-exchange/myplanet

**BASE** `master` @ `a00781114a0bf834fe0f001256caa242497fc8d8`  
**PR heads fetched** 2026-09-01T18:09:41Z  
**Labels this run** MERGE=`merge` (24) · PRIORITY=`priority` (1) · READY=`ready` (1) · QUEUE=`automerge` (0) · `conflict` (2)

Re-run if BASE moves for any reason other than executing it.

## Previous plan — how far it got

| wave | n | merged | still open | closed unmerged |
|---|---|---|---|---|
| 1 | 13 | **10** | 3 | 0 |
| 2 (held) | 3 | 0 | 3 | 0 |
| 3 (broken) | 18 | 1 | 17 | 0 |

12 commits landed, 10 from wave 1. The 2 outside it were [16620](https://github.com/open-learning-exchange/myplanet/pull/16620) — which was broken at plan time, got rebased, and merged — and [16656](https://github.com/open-learning-exchange/myplanet/pull/16656), a `priority`+`ready` PR that was never in a merge wave.

### The rebase backlog is drained

**All 17 previously-broken PRs that were still open have been rebased clean.** Every one of their heads moved and every one now merges cleanly against BASE; none remain broken.

The broken bucket across the last three plans: **27 → 18 → 2**. The `conflict` labelling applied two runs ago is the visible cause — the labelled list got worked through, and the label was correctly cleared on 15 of the 17.

The two remaining broken PRs are both ones that were *held* in the previous wave 2 rather than labelled: [16398](https://github.com/open-learning-exchange/myplanet/pull/16398) and [16639](https://github.com/open-learning-exchange/myplanet/pull/16639). Held PRs keep decaying; labelled ones got fixed.

## Wave 1 — 18 PRs

Verified: all 18 chained cumulatively onto BASE with **zero** merge failures. 3267 changed lines.

|  |  |  |  |  |  |
|---|---|---|---|---|---|
| [16335](https://github.com/open-learning-exchange/myplanet/pull/16335) | [16367](https://github.com/open-learning-exchange/myplanet/pull/16367) | [16368](https://github.com/open-learning-exchange/myplanet/pull/16368) | [16370](https://github.com/open-learning-exchange/myplanet/pull/16370) | [16442](https://github.com/open-learning-exchange/myplanet/pull/16442) | [16508](https://github.com/open-learning-exchange/myplanet/pull/16508) |
| [16526](https://github.com/open-learning-exchange/myplanet/pull/16526) | [16551](https://github.com/open-learning-exchange/myplanet/pull/16551) | [16554](https://github.com/open-learning-exchange/myplanet/pull/16554) | [16577](https://github.com/open-learning-exchange/myplanet/pull/16577) | [16585](https://github.com/open-learning-exchange/myplanet/pull/16585) | [16595](https://github.com/open-learning-exchange/myplanet/pull/16595) |
| [16599](https://github.com/open-learning-exchange/myplanet/pull/16599) | [16605](https://github.com/open-learning-exchange/myplanet/pull/16605) | [16607](https://github.com/open-learning-exchange/myplanet/pull/16607) | [16655](https://github.com/open-learning-exchange/myplanet/pull/16655) | [16658](https://github.com/open-learning-exchange/myplanet/pull/16658) | [16659](https://github.com/open-learning-exchange/myplanet/pull/16659) |

| # | diff | title |
|---|---|---|
| [16335](https://github.com/open-learning-exchange/myplanet/pull/16335) | 90 | all: smoother url utils credential handling (fixes #16309) |
| [16367](https://github.com/open-learning-exchange/myplanet/pull/16367) | 62 | enterprises: smoother repository context injecting (fixes #16343) |
| [16368](https://github.com/open-learning-exchange/myplanet/pull/16368) | 81 | life: smoother life repository seed inserting (fixes #16328) |
| [16370](https://github.com/open-learning-exchange/myplanet/pull/16370) | 33 | community: smoother home community dialog handling (fixes #16327) |
| [16442](https://github.com/open-learning-exchange/myplanet/pull/16442) | 75 | all: smoother notifications enrichment view modelling (fixes #16402) |
| [16508](https://github.com/open-learning-exchange/myplanet/pull/16508) | 80 | life: smoother life adapting (fixes #16505) |
| [16526](https://github.com/open-learning-exchange/myplanet/pull/16526) | 219 | all: smoother version utils testing (fixes #16504) |
| [16551](https://github.com/open-learning-exchange/myplanet/pull/16551) | 188 | all: smoother notifications repository batch id filtering (fixes #16548) |
| [16554](https://github.com/open-learning-exchange/myplanet/pull/16554) | 105 | all: smoother collection emptiness checking (fixes #16553) |
| [16577](https://github.com/open-learning-exchange/myplanet/pull/16577) | 279 | all: smoother user repository view modelling (fixes #16571) |
| [16585](https://github.com/open-learning-exchange/myplanet/pull/16585) | 1060 | all: smoother dictionary teams achievement view modelling (fixes #16576) |
| [16595](https://github.com/open-learning-exchange/myplanet/pull/16595) | 280 | all: smoother error log tagging (fixes #16358) |
| [16599](https://github.com/open-learning-exchange/myplanet/pull/16599) | 27 | all: smoother notifications type resolving (fixes #16419) |
| [16605](https://github.com/open-learning-exchange/myplanet/pull/16605) | 258 | all: smoother notifications voices repository querying (fixes #16418) |
| [16607](https://github.com/open-learning-exchange/myplanet/pull/16607) | 188 | login: smoother storage category selection view modelling (fixes #16449) |
| [16655](https://github.com/open-learning-exchange/myplanet/pull/16655) | 9 | resources: rethrow CancellationException in downloads (fixes #16654) |
| [16658](https://github.com/open-learning-exchange/myplanet/pull/16658) | 107 | resource: copy file to app storage on add (fixes #16657) |
| [16659](https://github.com/open-learning-exchange/myplanet/pull/16659) | 126 | all: smoother coroutine scope testing (fixes #16653) |

## Wave 2 — 4 held

Five symmetric conflict edges among the candidates (0 asymmetric across all 12 overlapping pairs). These need a rebase, not a reordering.

| held | conflicts with |
|---|---|
| [16096](https://github.com/open-learning-exchange/myplanet/pull/16096) | [16368](https://github.com/open-learning-exchange/myplanet/pull/16368), [16508](https://github.com/open-learning-exchange/myplanet/pull/16508) |
| [16441](https://github.com/open-learning-exchange/myplanet/pull/16441) | [16595](https://github.com/open-learning-exchange/myplanet/pull/16595) |
| [16503](https://github.com/open-learning-exchange/myplanet/pull/16503) | [16526](https://github.com/open-learning-exchange/myplanet/pull/16526) |
| [16600](https://github.com/open-learning-exchange/myplanet/pull/16600) | [16442](https://github.com/open-learning-exchange/myplanet/pull/16442) |

[16096](https://github.com/open-learning-exchange/myplanet/pull/16096) is the highest-degree hub at 2; sacrificing it is what admits both #16368 and #16508.

## Wave 3 — 2 broken against BASE

[16398](https://github.com/open-learning-exchange/myplanet/pull/16398) · [16639](https://github.com/open-learning-exchange/myplanet/pull/16639) — neither carries a `conflict` label.

## Priority — 1 PR, protection still free

[16659](https://github.com/open-learning-exchange/myplanet/pull/16659) (`priority` `ready`) is clean and conflicts with nothing, so greedy MIS is 18 with or without protection — cost **0 PRs**, the sixth consecutive run. It merges inside wave 1.

## Label accuracy — 4 rows are now wrong

| # | label says | reality |
|---|---|---|
| [16577](https://github.com/open-learning-exchange/myplanet/pull/16577) | `conflict` | merges clean (rebased, label not cleared) |
| [16595](https://github.com/open-learning-exchange/myplanet/pull/16595) | `conflict` | merges clean (rebased, label not cleared) |
| [16398](https://github.com/open-learning-exchange/myplanet/pull/16398) | no label | **broken** |
| [16639](https://github.com/open-learning-exchange/myplanet/pull/16639) | no label | **broken** |

Both stale rows are in wave 1, so the label contradicts the plan there.

## Caveats

- Conflict detection is **textual**. A clean merge is not a passing build.
- **Semantic risk is the highest in several runs** — 17 of the 22 candidates were rebased within the last few hours, and six same-file clusters land together in wave 1:
  - `NotificationsRepositoryImpl.kt` — #16551, #16599, #16605 (three PRs)
  - `NotificationsRepositoryImplTest.kt` — #16551, #16599
  - `ConfigurationsRepositoryImpl.kt` — #16554, #16595
  - `VoicesRepositoryImpl.kt` — #16554, #16605
  - `ConfigurationsRepositoryImplTest.kt` — #16595, #16659
  - `ResourcesRepositoryImplTest.kt` — #16658, #16659
  Run tests mid-wave, not only at the end. A clean three-way merge on a file three PRs just rewrote is exactly where textual agreement and semantic agreement diverge.
- [16585](https://github.com/open-learning-exchange/myplanet/pull/16585) is 1060 lines, by far the largest in the wave.
- Every wave-1 title is house style, so nothing to hand off.
