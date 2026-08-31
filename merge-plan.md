# Merge plan — open-learning-exchange/myplanet

**BASE** `master` @ `88eeedb821f0b8bda94be5f734684497ae55f3da`  
**PR heads fetched** 2026-08-31T18:04:44Z  
**Labels this run** MERGE=`merge` (55) · PRIORITY=`priority` (2) · READY=`ready` (10) · QUEUE=`automerge` (0)

Supersedes the plan dated 2026-08-28 (BASE `e8dcb5e`). Re-run if BASE moves for any reason other than executing it.

## Previous plan — how far it got

| wave | n | merged | still open | closed unmerged |
|---|---|---|---|---|
| 1 | 102 | **88** | 13 | 1 (#16331) |
| 2 (held) | 12 | 0 | 12 | 0 |
| 3 (broken) | 3 | 0 | 3 | 0 |

Merges confirmed from `git log master` subjects, not the API. 90 commits landed; the 2 beyond wave 1 were #16628 and #16630, opened after that snapshot.

**Decay — the measurable cost of a plan sitting unexecuted:**

- 5 of the 13 wave-1 stragglers fell into the broken bucket: #16438, #16487, #16495, #16564, #16590.
- 10 of the 12 wave-2 held PRs did too: #16096, #16335, #16367, #16368, #16397, #16398, #16442, #16503, #16551, #16577. Only #16486 and #16508 survived.
- All **7** `ready` PRs the previous plan predicted wave 1 would break did break, and all 7 now carry `merge`: #16441, #16526, #16554, #16595, #16599, #16600, #16607.
- In-play PRs conflicting with BASE went from **3 of 144** to **27 of 65**.

## Wave 1 — 27 PRs, merge order left to right

Verified: all 27 chained cumulatively onto BASE with **zero** merge failures. 2411 changed lines.

|  |  |  |  |  |  |
|---|---|---|---|---|---|
| [16324](https://github.com/open-learning-exchange/myplanet/pull/16324) | [16332](https://github.com/open-learning-exchange/myplanet/pull/16332) | [16339](https://github.com/open-learning-exchange/myplanet/pull/16339) | [16390](https://github.com/open-learning-exchange/myplanet/pull/16390) | [16422](https://github.com/open-learning-exchange/myplanet/pull/16422) | [16475](https://github.com/open-learning-exchange/myplanet/pull/16475) |
| [16480](https://github.com/open-learning-exchange/myplanet/pull/16480) | [16486](https://github.com/open-learning-exchange/myplanet/pull/16486) | [16494](https://github.com/open-learning-exchange/myplanet/pull/16494) | [16501](https://github.com/open-learning-exchange/myplanet/pull/16501) | [16534](https://github.com/open-learning-exchange/myplanet/pull/16534) | [16539](https://github.com/open-learning-exchange/myplanet/pull/16539) |
| [16545](https://github.com/open-learning-exchange/myplanet/pull/16545) | [16558](https://github.com/open-learning-exchange/myplanet/pull/16558) | [16566](https://github.com/open-learning-exchange/myplanet/pull/16566) | [16572](https://github.com/open-learning-exchange/myplanet/pull/16572) | [16593](https://github.com/open-learning-exchange/myplanet/pull/16593) | [16596](https://github.com/open-learning-exchange/myplanet/pull/16596) |
| [16597](https://github.com/open-learning-exchange/myplanet/pull/16597) | [16598](https://github.com/open-learning-exchange/myplanet/pull/16598) | [16601](https://github.com/open-learning-exchange/myplanet/pull/16601) | [16602](https://github.com/open-learning-exchange/myplanet/pull/16602) | [16603](https://github.com/open-learning-exchange/myplanet/pull/16603) | [16604](https://github.com/open-learning-exchange/myplanet/pull/16604) |
| [16606](https://github.com/open-learning-exchange/myplanet/pull/16606) | [16608](https://github.com/open-learning-exchange/myplanet/pull/16608) | [16609](https://github.com/open-learning-exchange/myplanet/pull/16609) |  |  |  |

| # | diff | title |
|---|---|---|
| [16324](https://github.com/open-learning-exchange/myplanet/pull/16324) | 52 | Raise RealtimeSyncManager flow buffer and drop oldest on overflow (fixes #16306) |
| [16332](https://github.com/open-learning-exchange/myplanet/pull/16332) | 159 | resources: smoother repository dao querying (fixes #16304) |
| [16339](https://github.com/open-learning-exchange/myplanet/pull/16339) | 61 | chat: smoother history on resume reloading (fixes #16319) |
| [16390](https://github.com/open-learning-exchange/myplanet/pull/16390) | 9 | actions: smoother release workflow version handling (fixes #16349) |
| [16422](https://github.com/open-learning-exchange/myplanet/pull/16422) | 44 | teams: smoother members display name binding (fixes #16416) |
| [16475](https://github.com/open-learning-exchange/myplanet/pull/16475) | 217 | dashboard: smoother voice date count query (fixes #16455) |
| [16480](https://github.com/open-learning-exchange/myplanet/pull/16480) | 83 | all: smoother feedback messages caching (fixes #16473) |
| [16486](https://github.com/open-learning-exchange/myplanet/pull/16486) | 197 | sync: smoother upload coordinator retry building (fixes #16464) |
| [16494](https://github.com/open-learning-exchange/myplanet/pull/16494) | 78 | jsonutils: quieter safeGet on hot parse path (fixes #16479) |
| [16501](https://github.com/open-learning-exchange/myplanet/pull/16501) | 70 | life: smoother repository dao visibility filtering (fixes #16447) |
| [16534](https://github.com/open-learning-exchange/myplanet/pull/16534) | 4 | login: smoother shared preferences team id name managing (fixes #16532) |
| [16539](https://github.com/open-learning-exchange/myplanet/pull/16539) | 101 | Dedupe course membership in one collection pass (fixes #16517) |
| [16545](https://github.com/open-learning-exchange/myplanet/pull/16545) | 37 | voices: stop NewsViewModel image-url emits from suspending on a slow collector |
| [16558](https://github.com/open-learning-exchange/myplanet/pull/16558) | 134 | teams: smoother voices view modelling (fixes #16557) |
| [16566](https://github.com/open-learning-exchange/myplanet/pull/16566) | 41 | all: smoother bottom sheet dialog configuring (fixes #16546) |
| [16572](https://github.com/open-learning-exchange/myplanet/pull/16572) | 323 | all: smoother life server address voices adapting (fixes #16542) |
| [16593](https://github.com/open-learning-exchange/myplanet/pull/16593) | 141 | Use HTTP HEAD for MainApplication reachability probes (fixes #16579) |
| [16596](https://github.com/open-learning-exchange/myplanet/pull/16596) | 120 | Move finance totals calculation into EnterprisesFinancesViewModel (fixes #16586) |
| [16597](https://github.com/open-learning-exchange/myplanet/pull/16597) | 46 | retry: smoother dead queue api surface (fixes #16337) |
| [16598](https://github.com/open-learning-exchange/myplanet/pull/16598) | 35 | courses: smoother progress binding (fixes #16413) |
| [16601](https://github.com/open-learning-exchange/myplanet/pull/16601) | 161 | chat: smoother history adapting (fixes #16415) |
| [16602](https://github.com/open-learning-exchange/myplanet/pull/16602) | 25 | Deduplicate UserDataWorker upload logic in SyncRepositoryImpl (fixes #16450) |
| [16603](https://github.com/open-learning-exchange/myplanet/pull/16603) | 20 | utils: smoother markdown image rewriting (fixes #16498) |
| [16604](https://github.com/open-learning-exchange/myplanet/pull/16604) | 23 | Hoist share dialog data map in ChatHistoryAdapter (fixes #16448) |
| [16606](https://github.com/open-learning-exchange/myplanet/pull/16606) | 26 | perf: reuse one CustomLinkMovementMethod instance (fixes #16499) |
| [16608](https://github.com/open-learning-exchange/myplanet/pull/16608) | 80 | resources: smoother search query normalizing (fixes #16515) |
| [16609](https://github.com/open-learning-exchange/myplanet/pull/16609) | 124 | health: smoother conditions JSON parsing in HealthExamination (fixes #16452) |

## Wave 2 — 1 held

[16508](https://github.com/open-learning-exchange/myplanet/pull/16508) — conflicts with [16572](https://github.com/open-learning-exchange/myplanet/pull/16572) (`LifeAdapter.kt`, `LifeAdapterTest.kt`). Needs a rebase, not a reordering.

## Wave 3 — 27 already broken against BASE

These must rebase regardless of this plan.

|  |  |  |  |  |  |
|---|---|---|---|---|---|
| [16096](https://github.com/open-learning-exchange/myplanet/pull/16096) | [16323](https://github.com/open-learning-exchange/myplanet/pull/16323) | [16335](https://github.com/open-learning-exchange/myplanet/pull/16335) | [16367](https://github.com/open-learning-exchange/myplanet/pull/16367) | [16368](https://github.com/open-learning-exchange/myplanet/pull/16368) | [16370](https://github.com/open-learning-exchange/myplanet/pull/16370) |
| [16397](https://github.com/open-learning-exchange/myplanet/pull/16397) | [16398](https://github.com/open-learning-exchange/myplanet/pull/16398) | [16436](https://github.com/open-learning-exchange/myplanet/pull/16436) | [16438](https://github.com/open-learning-exchange/myplanet/pull/16438) | [16441](https://github.com/open-learning-exchange/myplanet/pull/16441) | [16442](https://github.com/open-learning-exchange/myplanet/pull/16442) |
| [16487](https://github.com/open-learning-exchange/myplanet/pull/16487) | [16495](https://github.com/open-learning-exchange/myplanet/pull/16495) | [16503](https://github.com/open-learning-exchange/myplanet/pull/16503) | [16513](https://github.com/open-learning-exchange/myplanet/pull/16513) | [16526](https://github.com/open-learning-exchange/myplanet/pull/16526) | [16551](https://github.com/open-learning-exchange/myplanet/pull/16551) |
| [16554](https://github.com/open-learning-exchange/myplanet/pull/16554) | [16564](https://github.com/open-learning-exchange/myplanet/pull/16564) | [16577](https://github.com/open-learning-exchange/myplanet/pull/16577) | [16585](https://github.com/open-learning-exchange/myplanet/pull/16585) | [16590](https://github.com/open-learning-exchange/myplanet/pull/16590) | [16595](https://github.com/open-learning-exchange/myplanet/pull/16595) |
| [16599](https://github.com/open-learning-exchange/myplanet/pull/16599) | [16600](https://github.com/open-learning-exchange/myplanet/pull/16600) | [16607](https://github.com/open-learning-exchange/myplanet/pull/16607) |  |  |  |

## Priority protection

2 PRs carry `priority`, both also `ready`: [16619](https://github.com/open-learning-exchange/myplanet/pull/16619) (flexbox migration) and [16620](https://github.com/open-learning-exchange/myplanet/pull/16620) (chip cloud migration).
Both are clean against BASE and **conflict with nothing** in the candidate set, so protecting them held zero PRs — greedy MIS is 27 either way. Both also survive wave 1 landing.

## `ready` impact (report only — never merged by this plan)

10 `ready`-only PRs are clean against BASE. Landing wave 1 breaks **1**: [16624](https://github.com/open-learning-exchange/myplanet/pull/16624) — `search: smoother offline full text search matching`.

## Caveats

- Conflict detection is **textual**. A clean merge is not a passing build.
- Only 3 file-overlapping pairs exist in the whole candidate set, so semantic risk is low this run. Two clean-but-overlapping pairs still warrant a mid-wave test rather than one at the end:
  - `ChatHistoryAdapter.kt` — #16601, #16604
  - `MarkdownUtils.kt` / `MarkdownUtilsTest.kt` — #16603, #16606
- Squash-merge carries each PR title into BASE permanently. 7 wave-1 titles are not house style — see below.
- 4 PRs carry a `failing` label (#16387, #16487, #16495, #16564); #16387 is in neither wave here because it is clean but the label suggests red CI. Labels are hints, not gates — this plan does not read CI.

## Title hygiene — now unhandled

The two-stage retitle pipeline observed on 2026-08-28 (stage 1 adds `(fixes #N)`, stage 2 converts to house style) **last acted at 2026-08-28T19:48 and has been idle for 3 days**. The previous plan correctly declined to hand this off while the pass was live; that no longer holds.

7 wave-1 PRs would squash a non-house title into BASE:

| # | needs | current title |
|---|---|---|
| [16324](https://github.com/open-learning-exchange/myplanet/pull/16324) | `area:` prefix | Raise RealtimeSyncManager flow buffer and drop oldest on overflow (fixes #16306) |
| [16539](https://github.com/open-learning-exchange/myplanet/pull/16539) | `area:` prefix | Dedupe course membership in one collection pass (fixes #16517) |
| [16593](https://github.com/open-learning-exchange/myplanet/pull/16593) | `area:` prefix | Use HTTP HEAD for MainApplication reachability probes (fixes #16579) |
| [16596](https://github.com/open-learning-exchange/myplanet/pull/16596) | `area:` prefix | Move finance totals calculation into EnterprisesFinancesViewModel (fixes #16586) |
| [16602](https://github.com/open-learning-exchange/myplanet/pull/16602) | `area:` prefix | Deduplicate UserDataWorker upload logic in SyncRepositoryImpl (fixes #16450) |
| [16604](https://github.com/open-learning-exchange/myplanet/pull/16604) | `area:` prefix | Hoist share dialog data map in ChatHistoryAdapter (fixes #16448) |
| [16545](https://github.com/open-learning-exchange/myplanet/pull/16545) | a tracking issue | voices: stop NewsViewModel image-url emits from suspending on a slow collector |

The two `priority` PRs (#16619, #16620) also lack issue links. They are house-prefixed, and classifying them as untriaged would be the exact trap of gating on title style: the rule would eject the maintainer's own priority PRs.
