# Merge plan — open-learning-exchange/myplanet

**BASE** `master` @ `88eeedb821f0b8bda94be5f734684497ae55f3da` — **unchanged since the previous plan**  
**PR heads fetched** 2026-08-31T19:56:14Z  
**Labels this run** MERGE=`merge` (58) · PRIORITY=`priority` (1) · READY=`ready` (6) · QUEUE=`automerge` (1)

Re-run if BASE moves for any reason other than executing it.

## Previous plan — how far it got

**Nothing landed.** BASE has not moved (0 new commits), so wave 1 was not executed. One PR was queued by hand: [16324](https://github.com/open-learning-exchange/myplanet/pull/16324) gained `automerge`.

There is therefore no decay to report from this interval — no PR moved from a clean wave into the broken bucket, and the broken count held at 27. What changed is labels, not merge state:

| PR | change | effect |
|---|---|---|
| [16620](https://github.com/open-learning-exchange/myplanet/pull/16620) | `priority`+`ready` → `priority`+`merge` | now a merge candidate carrying priority — the displacing case |
| [16619](https://github.com/open-learning-exchange/myplanet/pull/16619) | lost `priority` and `ready` → `change` | out of play; only one priority PR remains |
| [16618](https://github.com/open-learning-exchange/myplanet/pull/16618), [16626](https://github.com/open-learning-exchange/myplanet/pull/16626) | gained `merge` | new wave-1 members |
| [16623](https://github.com/open-learning-exchange/myplanet/pull/16623), [16624](https://github.com/open-learning-exchange/myplanet/pull/16624) | lost `ready` → `change` | out of play |
| [16387](https://github.com/open-learning-exchange/myplanet/pull/16387) | **lost `merge`**, gained `conflict`+`failing`+`jules` | someone removed the label and handed it to another agent; not re-added here |

## Wave 1 — 30 PRs, merge order left to right

Verified: all 30 chained cumulatively onto BASE with **zero** merge failures. 3115 changed lines.

|  |  |  |  |  |  |
|---|---|---|---|---|---|
| [16324](https://github.com/open-learning-exchange/myplanet/pull/16324) | [16332](https://github.com/open-learning-exchange/myplanet/pull/16332) | [16339](https://github.com/open-learning-exchange/myplanet/pull/16339) | [16390](https://github.com/open-learning-exchange/myplanet/pull/16390) | [16422](https://github.com/open-learning-exchange/myplanet/pull/16422) | [16475](https://github.com/open-learning-exchange/myplanet/pull/16475) |
| [16480](https://github.com/open-learning-exchange/myplanet/pull/16480) | [16486](https://github.com/open-learning-exchange/myplanet/pull/16486) | [16494](https://github.com/open-learning-exchange/myplanet/pull/16494) | [16501](https://github.com/open-learning-exchange/myplanet/pull/16501) | [16534](https://github.com/open-learning-exchange/myplanet/pull/16534) | [16539](https://github.com/open-learning-exchange/myplanet/pull/16539) |
| [16545](https://github.com/open-learning-exchange/myplanet/pull/16545) | [16558](https://github.com/open-learning-exchange/myplanet/pull/16558) | [16566](https://github.com/open-learning-exchange/myplanet/pull/16566) | [16572](https://github.com/open-learning-exchange/myplanet/pull/16572) | [16593](https://github.com/open-learning-exchange/myplanet/pull/16593) | [16596](https://github.com/open-learning-exchange/myplanet/pull/16596) |
| [16597](https://github.com/open-learning-exchange/myplanet/pull/16597) | [16598](https://github.com/open-learning-exchange/myplanet/pull/16598) | [16601](https://github.com/open-learning-exchange/myplanet/pull/16601) | [16602](https://github.com/open-learning-exchange/myplanet/pull/16602) | [16603](https://github.com/open-learning-exchange/myplanet/pull/16603) | [16604](https://github.com/open-learning-exchange/myplanet/pull/16604) |
| [16606](https://github.com/open-learning-exchange/myplanet/pull/16606) | [16608](https://github.com/open-learning-exchange/myplanet/pull/16608) | [16609](https://github.com/open-learning-exchange/myplanet/pull/16609) | [16618](https://github.com/open-learning-exchange/myplanet/pull/16618) | [16620](https://github.com/open-learning-exchange/myplanet/pull/16620) | [16626](https://github.com/open-learning-exchange/myplanet/pull/16626) |

| # | diff | title |
|---|---|---|
| [16324](https://github.com/open-learning-exchange/myplanet/pull/16324) | 52 | sync: smoother realtime flow buffer managing (fixes #16306) |
| [16332](https://github.com/open-learning-exchange/myplanet/pull/16332) | 159 | resources: smoother repository dao querying (fixes #16304) |
| [16339](https://github.com/open-learning-exchange/myplanet/pull/16339) | 61 | chat: smoother history on resume reloading (fixes #16319) |
| [16390](https://github.com/open-learning-exchange/myplanet/pull/16390) | 9 | actions: smoother release workflow version handling (fixes #16349) |
| [16422](https://github.com/open-learning-exchange/myplanet/pull/16422) | 44 | teams: smoother members display name binding (fixes #16416) |
| [16475](https://github.com/open-learning-exchange/myplanet/pull/16475) | 217 | dashboard: smoother voice date count query (fixes #16455) |
| [16480](https://github.com/open-learning-exchange/myplanet/pull/16480) | 83 | all: smoother feedback messages caching (fixes #16473) |
| [16486](https://github.com/open-learning-exchange/myplanet/pull/16486) | 197 | sync: smoother upload coordinator retry building (fixes #16464) |
| [16494](https://github.com/open-learning-exchange/myplanet/pull/16494) | 78 | all: smoother json utils log tagging (fixes #16479) |
| [16501](https://github.com/open-learning-exchange/myplanet/pull/16501) | 70 | life: smoother repository dao visibility filtering (fixes #16447) |
| [16534](https://github.com/open-learning-exchange/myplanet/pull/16534) | 4 | login: smoother shared preferences team id name managing (fixes #16532) |
| [16539](https://github.com/open-learning-exchange/myplanet/pull/16539) | 101 | courses: smoother membership repository filtering (fixes #16517) |
| [16545](https://github.com/open-learning-exchange/myplanet/pull/16545) | 37 | teams: smoother voices image url flowing (fixes #16527) |
| [16558](https://github.com/open-learning-exchange/myplanet/pull/16558) | 134 | teams: smoother voices view modelling (fixes #16557) |
| [16566](https://github.com/open-learning-exchange/myplanet/pull/16566) | 41 | all: smoother bottom sheet dialog configuring (fixes #16546) |
| [16572](https://github.com/open-learning-exchange/myplanet/pull/16572) | 323 | all: smoother life server address voices adapting (fixes #16542) |
| [16593](https://github.com/open-learning-exchange/myplanet/pull/16593) | 141 | all: smoother server reachability checking (fixes #16579) |
| [16596](https://github.com/open-learning-exchange/myplanet/pull/16596) | 120 | enterprises: smoother finances totals view modelling (fixes #16586) |
| [16597](https://github.com/open-learning-exchange/myplanet/pull/16597) | 46 | sync: less retry queue dead api is more (fixes #16337) |
| [16598](https://github.com/open-learning-exchange/myplanet/pull/16598) | 35 | courses: smoother progress binding (fixes #16413) |
| [16601](https://github.com/open-learning-exchange/myplanet/pull/16601) | 161 | chat: smoother history adapting (fixes #16415) |
| [16602](https://github.com/open-learning-exchange/myplanet/pull/16602) | 25 | sync: smoother repository user data uploading (fixes #16450) |
| [16603](https://github.com/open-learning-exchange/myplanet/pull/16603) | 20 | all: smoother markdown image rewriting (fixes #16498) |
| [16604](https://github.com/open-learning-exchange/myplanet/pull/16604) | 23 | chat: smoother history share dialog caching (fixes #16448) |
| [16606](https://github.com/open-learning-exchange/myplanet/pull/16606) | 26 | all: smoother markdown utils link movement caching (fixes #16499) |
| [16608](https://github.com/open-learning-exchange/myplanet/pull/16608) | 80 | resources: smoother search query normalizing (fixes #16515) |
| [16609](https://github.com/open-learning-exchange/myplanet/pull/16609) | 124 | life: smoother health examination conditions formatting (fixes #16452) |
| [16618](https://github.com/open-learning-exchange/myplanet/pull/16618) | 197 | mysubmissions landscape scroll (fixes #16617) |
| [16620](https://github.com/open-learning-exchange/myplanet/pull/16620) | 320 | all: smoother chip cloud library migrating |
| [16626](https://github.com/open-learning-exchange/myplanet/pull/16626) | 187 | dashboard: smoother fragment navigation streamlining (fixes #16613) |

## Wave 2 — 1 held

[16508](https://github.com/open-learning-exchange/myplanet/pull/16508) — conflicts with [16572](https://github.com/open-learning-exchange/myplanet/pull/16572) over `LifeAdapter.kt` and its test. Third consecutive run with this same symmetric edge; it needs a rebase, and no ordering fixes it.

## Wave 3 — 27 already broken against BASE

|  |  |  |  |  |  |
|---|---|---|---|---|---|
| [16096](https://github.com/open-learning-exchange/myplanet/pull/16096) | [16323](https://github.com/open-learning-exchange/myplanet/pull/16323) | [16335](https://github.com/open-learning-exchange/myplanet/pull/16335) | [16367](https://github.com/open-learning-exchange/myplanet/pull/16367) | [16368](https://github.com/open-learning-exchange/myplanet/pull/16368) | [16370](https://github.com/open-learning-exchange/myplanet/pull/16370) |
| [16397](https://github.com/open-learning-exchange/myplanet/pull/16397) | [16398](https://github.com/open-learning-exchange/myplanet/pull/16398) | [16436](https://github.com/open-learning-exchange/myplanet/pull/16436) | [16438](https://github.com/open-learning-exchange/myplanet/pull/16438) | [16441](https://github.com/open-learning-exchange/myplanet/pull/16441) | [16442](https://github.com/open-learning-exchange/myplanet/pull/16442) |
| [16487](https://github.com/open-learning-exchange/myplanet/pull/16487) | [16495](https://github.com/open-learning-exchange/myplanet/pull/16495) | [16503](https://github.com/open-learning-exchange/myplanet/pull/16503) | [16513](https://github.com/open-learning-exchange/myplanet/pull/16513) | [16526](https://github.com/open-learning-exchange/myplanet/pull/16526) | [16551](https://github.com/open-learning-exchange/myplanet/pull/16551) |
| [16554](https://github.com/open-learning-exchange/myplanet/pull/16554) | [16564](https://github.com/open-learning-exchange/myplanet/pull/16564) | [16577](https://github.com/open-learning-exchange/myplanet/pull/16577) | [16585](https://github.com/open-learning-exchange/myplanet/pull/16585) | [16590](https://github.com/open-learning-exchange/myplanet/pull/16590) | [16595](https://github.com/open-learning-exchange/myplanet/pull/16595) |
| [16599](https://github.com/open-learning-exchange/myplanet/pull/16599) | [16600](https://github.com/open-learning-exchange/myplanet/pull/16600) | [16607](https://github.com/open-learning-exchange/myplanet/pull/16607) |  |  |  |

## Priority protection — free again

One PR carries `priority`: [16620](https://github.com/open-learning-exchange/myplanet/pull/16620) (`all: smoother chip cloud library migrating`), which now also carries `merge`. That is normally the expensive case — a priority PR that merges and displaces — but it touches 17 files and **conflicts with nothing** in the candidate set. Greedy MIS is 30 with or without protection, so the rule cost **0 PRs**, and #16620 simply merges inside wave 1.

## `ready` impact (report only — never merged by this plan)

6 `ready`-only PRs are clean against BASE. Landing wave 1 breaks **1**: [16605](https://github.com/open-learning-exchange/myplanet/pull/16605).

## Caveats

- Conflict detection is **textual**. A clean merge is not a passing build.
- Only 3 file-overlapping pairs exist across the whole candidate set, so semantic risk is low. Two clean-but-overlapping pairs still warrant a mid-wave test rather than one at the end:
  - `ChatHistoryAdapter.kt` — #16601, #16604
  - `MarkdownUtils.kt` / `MarkdownUtilsTest.kt` — #16603, #16606
- Squash-merge carries each PR title into BASE permanently.
- 4 PRs carry `failing` (#16387, #16487, #16495, #16564). This plan reads no CI at all, so treat that label as a hint to check before queueing.

## Title hygiene — handled, do not hand off

The retitle pass was idle for 3 days at the previous plan, which flagged 7 wave-1 titles. **It resumed at 2026-08-31T18:58–18:59 and fixed all 7**, including opening a tracking issue for #16545. Verified live: all 7 are house style.

Two wave-1 titles are still not house style, both of which entered the merge set at 19:24 — after that pass ran — and neither has ever been renamed:

| # | needs | current title |
|---|---|---|
| [16618](https://github.com/open-learning-exchange/myplanet/pull/16618) | `area:` prefix | mysubmissions landscape scroll (fixes #16617) |
| [16620](https://github.com/open-learning-exchange/myplanet/pull/16620) | a tracking issue | all: smoother chip cloud library migrating |

These are the newest arrivals to a set the pass demonstrably works, not a stalled backlog, so nothing is handed off. They matter only if the queue drains before the next pass, since the title would then squash into BASE as-is. Note also that #16620 is the `priority` PR: classifying it as untriaged for lacking an issue link would be the exact trap of gating on title style.
