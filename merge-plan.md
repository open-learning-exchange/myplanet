# Merge plan — open-learning-exchange/myplanet

**BASE** `master` @ `9193cfa247013a5aaa08cab881a66fc6abf47ef5`  
**PR heads fetched** 2026-09-01T19:49:02Z  
**Labels this run** MERGE=`merge` (12) · PRIORITY=`priority` (0) · READY=`ready` (3) · QUEUE=`automerge` (5) · `conflict` (0)

Re-run if BASE moves for any reason other than executing it.

## Previous plan — how far it got

| wave | n | merged | still open | closed unmerged |
|---|---|---|---|---|
| 1 | 18 | **12** | 6 | 0 |
| 2 (held) | 4 | 0 | 4 | 0 |
| 3 (broken) | 2 | 0 | 2 | 0 |

12 commits landed and **all 12 came from wave 1**.

### Held PRs rot — now confirmed by a natural experiment

The previous plan noted that labelled PRs were getting rebased while held ones decayed. This interval tested it cleanly:

| group | n | outcome |
|---|---|---|
| previously **labelled** `conflict` | 17 | all 17 rebased clean |
| previously **held** in wave 2 | 4 | **3 decayed into broken**, heads never moved |

The three that decayed — [16096](https://github.com/open-learning-exchange/myplanet/pull/16096), [16503](https://github.com/open-learning-exchange/myplanet/pull/16503), [16600](https://github.com/open-learning-exchange/myplanet/pull/16600) — have unmoved heads, so nobody touched them; BASE simply advanced past them. Only [16441](https://github.com/open-learning-exchange/myplanet/pull/16441) survived. A held PR carries no visible signal that it needs work, so nothing gets worked. That is an argument for labelling held PRs, not only base-broken ones.

## Wave 1 — 6 PRs

Verified: all 6 chained cumulatively onto BASE with **zero** merge failures. 2074 changed lines. **No same-file overlap at all**, so no semantic risk from co-landing this run.

| # | diff | labels | title |
|---|---|---|---|
| [16585](https://github.com/open-learning-exchange/myplanet/pull/16585) | 1060 | `enormous` `experiment` | all: smoother dictionary teams achievement view modelling (fixes #16576) |
| [16595](https://github.com/open-learning-exchange/myplanet/pull/16595) | 280 | `automerge` `enormous` | all: smoother error log tagging (fixes #16358) |
| [16607](https://github.com/open-learning-exchange/myplanet/pull/16607) | 188 | `automerge` `enormous` | login: smoother storage category detail view modelling (fixes #16449) |
| [16639](https://github.com/open-learning-exchange/myplanet/pull/16639) | 430 | `enormous` | sync: smoother upload pipelines unifying (fixes #16638) |
| [16655](https://github.com/open-learning-exchange/myplanet/pull/16655) | 9 | `automerge` `small` | sync: smoother download repository service cancelling (fixes #16654) |
| [16658](https://github.com/open-learning-exchange/myplanet/pull/16658) | 107 | `automerge` `large` | resources: smoother repository file copying (fixes #16657) |

## Wave 2 — 1 held

[16441](https://github.com/open-learning-exchange/myplanet/pull/16441) conflicts with [16595](https://github.com/open-learning-exchange/myplanet/pull/16595) (1 symmetric edge, 0 asymmetric across the single overlapping pair). Given the finding above, this one deserves a `conflict` label rather than a silent hold.

## Wave 3 — 5 broken against BASE

[16096](https://github.com/open-learning-exchange/myplanet/pull/16096) · [16398](https://github.com/open-learning-exchange/myplanet/pull/16398) · [16503](https://github.com/open-learning-exchange/myplanet/pull/16503) · [16600](https://github.com/open-learning-exchange/myplanet/pull/16600) · [16605](https://github.com/open-learning-exchange/myplanet/pull/16605)

## The `conflict` label has gone fully out of sync

**5 PRs are broken and 0 carry the label.** Every `conflict` label was cleared during this interval, including on PRs that then broke again:

- [16398](https://github.com/open-learning-exchange/myplanet/pull/16398) — labelled 15:24, cleared by hand at 15:44, rebased, and is **broken again** now.
- [16096](https://github.com/open-learning-exchange/myplanet/pull/16096), [16503](https://github.com/open-learning-exchange/myplanet/pull/16503), [16600](https://github.com/open-learning-exchange/myplanet/pull/16600) — decayed out of the held set, never labelled.
- [16605](https://github.com/open-learning-exchange/myplanet/pull/16605) — broke after landing in the previous wave 1.

The label was the mechanism that drained 27 broken PRs down to 2 over two intervals. It is now carrying no information.

## A queued PR is conflicting

[16605](https://github.com/open-learning-exchange/myplanet/pull/16605) carries `automerge` **and** is broken against BASE. Per `.github/scripts/automerge.sh` a conflicting PR loses `automerge`, gains `conflict`, and the drain moves on — so it self-corrects, but it costs a queue cycle. The other four queued PRs ([16595](https://github.com/open-learning-exchange/myplanet/pull/16595), [16607](https://github.com/open-learning-exchange/myplanet/pull/16607), [16655](https://github.com/open-learning-exchange/myplanet/pull/16655), [16658](https://github.com/open-learning-exchange/myplanet/pull/16658)) are all clean and all in wave 1.

## Priority

No open PR carries `priority` this run, so protection is moot. It has cost 0 PRs in every run where it applied.

## `ready` impact (report only — never merged by this plan)

3 `ready`-only PRs are clean. Landing wave 1 breaks **1**: [16661](https://github.com/open-learning-exchange/myplanet/pull/16661).

## Caveats

- Conflict detection is **textual**. A clean merge is not a passing build.
- [16585](https://github.com/open-learning-exchange/myplanet/pull/16585) is the largest in the wave at 1060 lines and carries an **`experiment`** label, added 18:00 today. That label has no description in the repo, so this plan does not treat it as a gate — but it is worth a human decision before queueing a 1060-line experiment.
- [16639](https://github.com/open-learning-exchange/myplanet/pull/16639) is 430 lines and was broken at the previous plan; it has been rebased and is clean now, so it is freshly landed work and the likeliest in the wave to break again if execution is slow.
- Every wave-1 title is house style, so nothing to hand off.
