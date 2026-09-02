# Merge plan — open-learning-exchange/myplanet

**BASE** `master` @ `f172c7913c3da9ef9016c031f9cebf2efdfd5fba`  
**PR heads fetched** 2026-09-02T22:22:02Z  
**Labels this run** MERGE=`merge` (10) · PRIORITY=`priority` (1) · READY=`ready` (2) · QUEUE=`automerge` (0) · `conflict` (0)

Re-run if BASE moves for any reason other than executing it.

## Previous plan — how far it got

| wave | n | merged | still open | closed unmerged |
|---|---|---|---|---|
| 1 | 6 | **5** | 1 | 0 |
| 3 (broken) | 1 | 0 | 1 | 0 |

5 commits landed and **all 5 were the PRs labelled `conflict` the previous morning**: #16096, #16398, #16441, #16503, #16600.

### The full cycle, end to end, in about 24 hours

| stage | when | state |
|---|---|---|
| stalled, unlabelled | 09-01, ~16h | 18–30 commits behind, untouched |
| labelled `conflict` | 09-02 07:41Z | — |
| rebased clean | 09-02 by 13:56Z | 1 commit behind |
| entered wave 1 | 09-02 14:08Z | all 5 clean |
| **merged** | 09-02 by 22:22Z | all 5 landed |

Label to merged in roughly fifteen hours, for five PRs that had not moved in a day. Nothing else in this plan's history has produced that.

## Wave 1 — 9 PRs

Verified: all 9 chained cumulatively onto BASE with **zero** merge failures. 2570 changed lines. **Zero conflict edges**, nothing held.

| # | diff | labels | title |
|---|---|---|---|
| [16585](https://github.com/open-learning-exchange/myplanet/pull/16585) | 1060 | `experiment` | all: smoother dictionary teams achievement view modelling (fixes #16576) |
| [16592](https://github.com/open-learning-exchange/myplanet/pull/16592) | 270 |  | resources: smoother library filter state preserving (fixes #16269) |
| [16625](https://github.com/open-learning-exchange/myplanet/pull/16625) | 268 |  | resources: smoother media playback progress saving (fixes #16614) |
| [16660](https://github.com/open-learning-exchange/myplanet/pull/16660) | 330 |  | teams: smoother submission stamping (fixes #16664) |
| [16662](https://github.com/open-learning-exchange/myplanet/pull/16662) | 245 |  | teams: smoother survey team detail landscape handling (fixes #16633) |
| [16675](https://github.com/open-learning-exchange/myplanet/pull/16675) | 92 | `priority` | sync: smoother courses resources uploading (fixes #16670) |
| [16678](https://github.com/open-learning-exchange/myplanet/pull/16678) | 15 |  | sync: smoother submit photos dao batch updating (fixes #16685) |
| [16679](https://github.com/open-learning-exchange/myplanet/pull/16679) | 172 |  | all: smoother file utils uri path resolving (fixes #16651) |
| [16684](https://github.com/open-learning-exchange/myplanet/pull/16684) | 118 |  | all: smoother repositories sync documents mapping (fixes #16643) |

## Wave 2 — empty

No held PRs. One candidate pair shares a file and merges clean.

## Wave 3 — 1 broken against BASE

[16663](https://github.com/open-learning-exchange/myplanet/pull/16663) — **44 commits behind**, the stalest head in the set. It moved from `ready` to `merge` during this interval, so it is a merge candidate for the first time, and it arrives already broken and unlabelled.

## Priority — 1 PR, protection free for the seventh run

[16675](https://github.com/open-learning-exchange/myplanet/pull/16675) (`priority` `merge`, 92 lines) is clean and conflicts with nothing, so greedy MIS is 9 with or without protection — cost **0 PRs**. It merges inside wave 1.

## `ready` impact (report only — never merged by this plan)

2 `ready`-only PRs. Landing wave 1 breaks **1**: [15699](https://github.com/open-learning-exchange/myplanet/pull/15699).

[16661](https://github.com/open-learning-exchange/myplanet/pull/16661), broken in the previous plan, moved from `ready` to `change` and is out of play entirely — it is no longer this plan's concern.

## Caveats

- Conflict detection is **textual**. A clean merge is not a passing build.
- One same-file overlap: `SubmissionsRepositoryImpl.kt` — [16660](https://github.com/open-learning-exchange/myplanet/pull/16660) and [16684](https://github.com/open-learning-exchange/myplanet/pull/16684). They merge clean but land together, so test mid-wave.
- Six of the nine wave-1 PRs are `enormous`, and the wave totals 2570 lines. This is a heavier wave than its PR count suggests.
- Every wave-1 title is house style, so nothing to hand off.

## On #16585

[16585](https://github.com/open-learning-exchange/myplanet/pull/16585) (1060 lines) got `experiment` at 2026-09-01 18:00, one minute after its `conflict` label was removed, and **nothing has happened on it since** — no push, no comment, no label change in over 28 hours, while it drifted to 25 commits behind. Read plainly, `experiment` looks like a park rather than a gate.

It still carries `merge`, and `merge` is this plan's gate, so it stays in wave 1 and is listed above. This plan will stop flagging it as an open question; if it should be excluded, drop the `merge` label and it leaves the wave automatically.
