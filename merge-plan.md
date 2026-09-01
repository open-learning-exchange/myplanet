# Merge plan — open-learning-exchange/myplanet

**BASE** `master` @ `9ce716cc90d30d008c820f49224cf8c7bde0d51e`  
**PR heads fetched** 2026-09-01T08:07:02Z · **BASE re-verified** 08:08:25Z  
**Labels this run** MERGE=`merge` (34) · PRIORITY=`priority` (1) · READY=`ready` (6) · QUEUE=`automerge` (0)

Re-run if BASE moves for any reason other than executing it.

## Previous plan — how far it got

| wave | n | merged | still open | closed unmerged |
|---|---|---|---|---|
| 1 | 30 | **24** | 6 | 0 |
| 2 (held) | 1 | 0 | 1 | 0 |
| 3 (broken) | 27 | 0 | 27 | 0 |

24 commits landed and **all 24 came from wave 1** — nothing merged outside the plan, and nothing was closed unmerged this round. Confirmed from `git log master` subjects, not the API.

### Decay and recovery ledger

The broken-against-BASE bucket went **27 → 30**:

- **Recovered** (rebased): 1 — [16590](https://github.com/open-learning-exchange/myplanet/pull/16590), which is now in wave 1.
- **Newly broken**: 4 — [16332](https://github.com/open-learning-exchange/myplanet/pull/16332), [16486](https://github.com/open-learning-exchange/myplanet/pull/16486), [16508](https://github.com/open-learning-exchange/myplanet/pull/16508), [16605](https://github.com/open-learning-exchange/myplanet/pull/16605).
- **Still broken from before**: 26 of 27.

Two things worth naming. [16508](https://github.com/open-learning-exchange/myplanet/pull/16508) was held for a rebase across three consecutive plans and has now decayed into the broken bucket — the predicted cost of holding rather than rebasing. And 26 of 27 broken PRs did not move at all, so **the broken bucket is not being worked**: 30 of the 40 in-play PRs now conflict with BASE.

## Wave 1 — 5 PRs

Verified: all 5 chained cumulatively onto BASE with **zero** merge failures. 791 changed lines. **Zero conflict edges** in the candidate set, so nothing is held this run.

|  |  |  |  |  |  |
|---|---|---|---|---|---|
| [16545](https://github.com/open-learning-exchange/myplanet/pull/16545) | [16590](https://github.com/open-learning-exchange/myplanet/pull/16590) | [16609](https://github.com/open-learning-exchange/myplanet/pull/16609) | [16620](https://github.com/open-learning-exchange/myplanet/pull/16620) | [16626](https://github.com/open-learning-exchange/myplanet/pull/16626) |  |

| # | diff | title |
|---|---|---|
| [16545](https://github.com/open-learning-exchange/myplanet/pull/16545) | 37 | teams: smoother voices image url flowing (fixes #16527) |
| [16590](https://github.com/open-learning-exchange/myplanet/pull/16590) | 127 | life: smoother user repository view modelling (fixes #16575) |
| [16609](https://github.com/open-learning-exchange/myplanet/pull/16609) | 121 | life: smoother health examination formatting (fixes #16452) |
| [16620](https://github.com/open-learning-exchange/myplanet/pull/16620) | 319 | all: smoother chip cloud library migrating (fixes #16648) |
| [16626](https://github.com/open-learning-exchange/myplanet/pull/16626) | 187 | dashboard: smoother fragment navigation streamlining (fixes #16613) |

## Wave 2 — empty

No PR is held. The only candidate pair overlap count is 0, so there is nothing to sacrifice.

## Wave 3 — 30 broken against BASE

These must rebase regardless of this plan, and 26 of them have needed it for days.

|  |  |  |  |  |  |
|---|---|---|---|---|---|
| [16096](https://github.com/open-learning-exchange/myplanet/pull/16096) | [16323](https://github.com/open-learning-exchange/myplanet/pull/16323) | [16332](https://github.com/open-learning-exchange/myplanet/pull/16332) | [16335](https://github.com/open-learning-exchange/myplanet/pull/16335) | [16367](https://github.com/open-learning-exchange/myplanet/pull/16367) | [16368](https://github.com/open-learning-exchange/myplanet/pull/16368) |
| [16370](https://github.com/open-learning-exchange/myplanet/pull/16370) | [16397](https://github.com/open-learning-exchange/myplanet/pull/16397) | [16398](https://github.com/open-learning-exchange/myplanet/pull/16398) | [16436](https://github.com/open-learning-exchange/myplanet/pull/16436) | [16438](https://github.com/open-learning-exchange/myplanet/pull/16438) | [16441](https://github.com/open-learning-exchange/myplanet/pull/16441) |
| [16442](https://github.com/open-learning-exchange/myplanet/pull/16442) | [16486](https://github.com/open-learning-exchange/myplanet/pull/16486) | [16487](https://github.com/open-learning-exchange/myplanet/pull/16487) | [16495](https://github.com/open-learning-exchange/myplanet/pull/16495) | [16503](https://github.com/open-learning-exchange/myplanet/pull/16503) | [16508](https://github.com/open-learning-exchange/myplanet/pull/16508) |
| [16513](https://github.com/open-learning-exchange/myplanet/pull/16513) | [16526](https://github.com/open-learning-exchange/myplanet/pull/16526) | [16551](https://github.com/open-learning-exchange/myplanet/pull/16551) | [16554](https://github.com/open-learning-exchange/myplanet/pull/16554) | [16564](https://github.com/open-learning-exchange/myplanet/pull/16564) | [16577](https://github.com/open-learning-exchange/myplanet/pull/16577) |
| [16585](https://github.com/open-learning-exchange/myplanet/pull/16585) | [16595](https://github.com/open-learning-exchange/myplanet/pull/16595) | [16599](https://github.com/open-learning-exchange/myplanet/pull/16599) | [16600](https://github.com/open-learning-exchange/myplanet/pull/16600) | [16605](https://github.com/open-learning-exchange/myplanet/pull/16605) | [16607](https://github.com/open-learning-exchange/myplanet/pull/16607) |

## Priority protection — free for the third run

One PR carries `priority`: [16620](https://github.com/open-learning-exchange/myplanet/pull/16620) (`all: smoother chip cloud library migrating`), also `merge`. It conflicts with nothing, so greedy MIS is 5 with or without protection — cost **0 PRs** — and it merges inside wave 1.

## `ready` impact (report only — never merged by this plan)

5 `ready`-only PRs are clean against BASE (#16591, #16594, #16622, #16625, #16641). Landing wave 1 breaks **none** of them.

## Caveats

- Conflict detection is **textual**. A clean merge is not a passing build.
- No two wave-1 PRs touch a common file, so there is **no same-file semantic risk** this run — the first run where that is true.
- [16609](https://github.com/open-learning-exchange/myplanet/pull/16609) is in wave 1 but carries `failing`. This plan reads no CI, so check that before queueing it.
- `failing` is now on 6 PRs: #16387, #16486, #16487, #16495, #16564, #16609.
- Squash-merge carries each PR title into BASE permanently.

## Title hygiene — fully drained, nothing to hand off

**Every in-play PR is house style: 5/5 in wave 1, 30/30 broken, 5/5 ready.** The retitle pass cleared the two rows the previous plan left open, including opening issue #16648 so #16620 could carry a `(fixes #N)` reference. No worklist, and no summon to paste.
