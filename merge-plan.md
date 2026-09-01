# Merge plan — open-learning-exchange/myplanet

**BASE** `master` @ `9ce716cc90d30d008c820f49224cf8c7bde0d51e` — **unchanged since the previous plan**  
**PR heads fetched** 2026-09-01T13:36:44Z  
**Labels this run** MERGE=`merge` (35) · PRIORITY=`priority` (1) · READY=`ready` (5) · QUEUE=`automerge` (0)

Re-run if BASE moves for any reason other than executing it.

## Previous plan — how far it got

**Nothing landed.** BASE has not moved, so the 5-PR wave 1 was not executed; all 5 are still open.

The movement this interval was in the **broken bucket**, which is what the previous plan named as the bottleneck. It went **30 → 27**:

- **Rebased clean**: [16487](https://github.com/open-learning-exchange/myplanet/pull/16487) and [16564](https://github.com/open-learning-exchange/myplanet/pull/16564) — both now in wave 1.
- **Closed while broken**: [16486](https://github.com/open-learning-exchange/myplanet/pull/16486).
- **Newly broken: 0** — the first run with no decay at all.
- **Still broken: 27.**

Alongside that, `failing` went **6 → 0** and `conflict` **1 → 0**, with new heads pushed to 5 in-play PRs. [16387](https://github.com/open-learning-exchange/myplanet/pull/16387) is the clearest case: it had lost `merge` and carried `conflict`+`failing`, and has now been fixed, re-labelled `merge`, and re-enters as a clean candidate.

So the rebase work has started — but it cleared 2 of 30 while 27 remain.

## Wave 1 — 9 PRs

Verified: all 9 chained cumulatively onto BASE with **zero** merge failures. 1274 changed lines. **Zero conflict edges**, so nothing is held.

|  |  |  |  |  |  |
|---|---|---|---|---|---|
| [16387](https://github.com/open-learning-exchange/myplanet/pull/16387) | [16487](https://github.com/open-learning-exchange/myplanet/pull/16487) | [16545](https://github.com/open-learning-exchange/myplanet/pull/16545) | [16564](https://github.com/open-learning-exchange/myplanet/pull/16564) | [16590](https://github.com/open-learning-exchange/myplanet/pull/16590) | [16609](https://github.com/open-learning-exchange/myplanet/pull/16609) |
| [16620](https://github.com/open-learning-exchange/myplanet/pull/16620) | [16626](https://github.com/open-learning-exchange/myplanet/pull/16626) | [16641](https://github.com/open-learning-exchange/myplanet/pull/16641) |  |  |  |

| # | diff | title |
|---|---|---|
| [16387](https://github.com/open-learning-exchange/myplanet/pull/16387) | 269 | community: smoother configurations repository tab views modelling (fixes #16326) |
| [16487](https://github.com/open-learning-exchange/myplanet/pull/16487) | 72 | sync: smoother time logger summary generating (fixes #16461) |
| [16545](https://github.com/open-learning-exchange/myplanet/pull/16545) | 37 | teams: smoother voices image url flowing (fixes #16527) |
| [16564](https://github.com/open-learning-exchange/myplanet/pull/16564) | 9 | all: smoother utilities hex mapping (fixes #16547) |
| [16590](https://github.com/open-learning-exchange/myplanet/pull/16590) | 127 | life: smoother user repository view modelling (fixes #16575) |
| [16609](https://github.com/open-learning-exchange/myplanet/pull/16609) | 143 | life: smoother health examination formatting (fixes #16452) |
| [16620](https://github.com/open-learning-exchange/myplanet/pull/16620) | 319 | all: smoother chip cloud library migrating (fixes #16648) |
| [16626](https://github.com/open-learning-exchange/myplanet/pull/16626) | 187 | dashboard: smoother fragment navigation streamlining (fixes #16613) |
| [16641](https://github.com/open-learning-exchange/myplanet/pull/16641) | 111 | callbacks: unify interfaces into OnChangedListener (fixes #16640) |

## Wave 2 — empty

No PR is held; the candidate set has a single file overlap and no conflict.

## Wave 3 — 27 broken against BASE, ranked by staleness

Commits behind BASE at each PR's merge base. Median **117**, max **190**. Most branched on 2026-08-27 and have been broken for five days. This is the queue that is not moving; the top rows are both the stalest and, mostly, the smallest.

| # | behind | merge base | files | title |
|---|---|---|---|---|
| [16096](https://github.com/open-learning-exchange/myplanet/pull/16096) | 190 | 2026-08-25 | 8 | life: smoother life layout list loading (fixes #16089) |
| [16595](https://github.com/open-learning-exchange/myplanet/pull/16595) | 122 | 2026-08-27 | 8 | all: smoother error log tagging (fixes #16358) |
| [16398](https://github.com/open-learning-exchange/myplanet/pull/16398) | 122 | 2026-08-27 | 2 | sync: smoother url utils base64 handling (fixes #16372) |
| [16397](https://github.com/open-learning-exchange/myplanet/pull/16397) | 122 | 2026-08-27 | 3 | enterprises: smoother reports dao filtering (fixes #16373) |
| [16370](https://github.com/open-learning-exchange/myplanet/pull/16370) | 122 | 2026-08-27 | 1 | community: smoother home community dialog handling (fixes #163 |
| [16368](https://github.com/open-learning-exchange/myplanet/pull/16368) | 122 | 2026-08-27 | 5 | life: smoother life repository seed inserting (fixes #16328) |
| [16367](https://github.com/open-learning-exchange/myplanet/pull/16367) | 122 | 2026-08-27 | 2 | enterprises: smoother repository context injecting (fixes #163 |
| [16335](https://github.com/open-learning-exchange/myplanet/pull/16335) | 122 | 2026-08-27 | 5 | all: smoother url utils credential handling (fixes #16309) |
| [16332](https://github.com/open-learning-exchange/myplanet/pull/16332) | 122 | 2026-08-27 | 4 | resources: smoother repository dao querying (fixes #16304) |
| [16323](https://github.com/open-learning-exchange/myplanet/pull/16323) | 122 | 2026-08-27 | 5 | sync: smoother url utils auth header caching (fixes #16303) |
| [16526](https://github.com/open-learning-exchange/myplanet/pull/16526) | 117 | 2026-08-27 | 3 | all: smoother version utils testing (fixes #16504) |
| [16513](https://github.com/open-learning-exchange/myplanet/pull/16513) | 117 | 2026-08-27 | 1 | sync: smoother collector-side status throttling (fixes #16468) |
| [16508](https://github.com/open-learning-exchange/myplanet/pull/16508) | 117 | 2026-08-27 | 2 | life: smoother life adapting (fixes #16505) |
| [16503](https://github.com/open-learning-exchange/myplanet/pull/16503) | 117 | 2026-08-27 | 2 | all: smoother version utils comparing (fixes #16500) |
| [16442](https://github.com/open-learning-exchange/myplanet/pull/16442) | 117 | 2026-08-27 | 2 | all: smoother notifications enrichment view modelling (fixes # |
| [16441](https://github.com/open-learning-exchange/myplanet/pull/16441) | 117 | 2026-08-27 | 1 | all: smoother file utils url path resolving (fixes #16409) |
| [16436](https://github.com/open-learning-exchange/myplanet/pull/16436) | 117 | 2026-08-27 | 6 | all: smoother personals repository intent updating (fixes #164 |
| [16585](https://github.com/open-learning-exchange/myplanet/pull/16585) | 115 | 2026-08-27 | 12 | all: smoother dictionary teams achievement view modelling (fix |
| [16577](https://github.com/open-learning-exchange/myplanet/pull/16577) | 115 | 2026-08-27 | 15 | all: smoother user repository view modelling (fixes #16571) |
| [16554](https://github.com/open-learning-exchange/myplanet/pull/16554) | 115 | 2026-08-27 | 19 | all: smoother collection emptiness checking (fixes #16553) |
| [16551](https://github.com/open-learning-exchange/myplanet/pull/16551) | 115 | 2026-08-27 | 2 | all: smoother notifications repository batch id filtering (fix |
| [16607](https://github.com/open-learning-exchange/myplanet/pull/16607) | 114 | 2026-08-27 | 3 | login: smoother storage category selection view modelling (fix |
| [16605](https://github.com/open-learning-exchange/myplanet/pull/16605) | 114 | 2026-08-27 | 7 | teams: smoother notification count via countTeamChats (fixes # |
| [16600](https://github.com/open-learning-exchange/myplanet/pull/16600) | 114 | 2026-08-27 | 2 | all: smoother notifications task date caching (fixes #16414) |
| [16599](https://github.com/open-learning-exchange/myplanet/pull/16599) | 114 | 2026-08-27 | 2 | all: smoother notifications type resolving (fixes #16419) |
| [16438](https://github.com/open-learning-exchange/myplanet/pull/16438) | 74 | 2026-08-30 | 102 | resources: smoother collections tags filtering (fixes #16410) |
| [16495](https://github.com/open-learning-exchange/myplanet/pull/16495) | 53 | 2026-08-31 | 3 | sync: smoother upload coordinating (fixes #16467, fixes #16464 |

Two outliers worth separating: [16438](https://github.com/open-learning-exchange/myplanet/pull/16438) touches **102 files**, so it is a rebase of a different order from the rest; and [16495](https://github.com/open-learning-exchange/myplanet/pull/16495) is the freshest (53 behind, base 08-31) — it was pushed to today and is still broken, so it needs another pass rather than a first one.

## Priority protection — free for the fourth run

One PR carries `priority`: [16620](https://github.com/open-learning-exchange/myplanet/pull/16620), also `merge`. It conflicts with nothing, so greedy MIS is 9 with or without protection — cost **0 PRs**.

## `ready` impact (report only — never merged by this plan)

4 `ready`-only PRs are clean against BASE (#16591, #16594, #16622, #16625). Landing wave 1 breaks **none**.

## Caveats

- Conflict detection is **textual**. A clean merge is not a passing build.
- One same-file overlap in wave 1: `Utilities.kt` — [16564](https://github.com/open-learning-exchange/myplanet/pull/16564) and [16620](https://github.com/open-learning-exchange/myplanet/pull/16620). They merge clean but land together, so test mid-wave, not only at the end.
- `failing` is now clear on every in-play PR, but this plan reads no CI, so that is a label observation and not a green build.
- Squash-merge carries each PR title into BASE permanently.

## Title hygiene — nothing to hand off

Every in-play PR is house style, for the second run running. No worklist and no summon to paste.
