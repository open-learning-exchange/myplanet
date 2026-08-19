# myPlanet — merge wave plan

**BASE** [`321f5ecaaf1962d744d2964cd07a17a9411723ee`](https://github.com/open-learning-exchange/myplanet/commit/321f5ecaaf1962d744d2964cd07a17a9411723ee)
**PR heads fetched** 2026-08-19 01:53:05Z · **git** 2.43.0 · **clone depth** 3134 commits
**Scope** 10 open `merge` · 6 `ready` · **0** `priority` · 0 `automerge` (queue empty)

> Conflict detection here is **textual** — a clean merge is not a passing build. Re-run if BASE moves for
> any reason other than executing this plan.

**No GitHub writes have been made.**

---

## 1. How the previous plan did

Previous plan was BASE `f53e466`. **31 commits landed.** This is the best round yet — and the first where the
rebase queue actually got worked.

| Previous bucket | Planned | Landed | Still open |
|---|---:|---:|---:|
| Wave 1 — land now | 17 | 16 | 1 |
| Rebase queue | 18 | 13 | 5 |
| `ready` (never merged by plan) | 6 | 1 | 5 |

**Wave 1 landed 16 of 17.** And **13 of the 18 rebase-queue PRs were rebased and landed** — the tail got
worked this time instead of rotting, which is why the backlog has collapsed from 35 open `merge` PRs to 10.

Two items landed outside the plan: [#15789](https://github.com/open-learning-exchange/myplanet/pull/15789) (opened after the snapshot) and [#15725](https://github.com/open-learning-exchange/myplanet/pull/15725), a dependabot bump
that was `ready`, not `merge` — worth knowing that something merges `ready` PRs, since this plan never does.

### ⚠️ One PR was closed without merging while still labelled `merge`

**[#15631](https://github.com/open-learning-exchange/myplanet/pull/15631)** — *"resources: smoother viewer markdown utils loading"* — is `state: closed`, `merged: false`,
and **still carries the `merge` label**. Its `mergeable_state` was `unstable` (CI red) and it was closed
2026-08-18T20:01:22Z after 6 commits and 7 comments.

This is the failure mode nobody notices: it was in wave 1, it was prepped and retitled, and it left the queue
without landing. It is not counted as landed anywhere above. If closing it was deliberate, the `merge` label
should come off so it stops appearing in future plans; if not, it needs reopening and a CI fix.

---

## 2. Wave 1 — land now (4 PRs)

Of the 10 `merge`-labelled PRs, 4 are clean against BASE and have **zero conflict edges between
them**. Cumulative chained simulation ran **0 failures** forwards and reversed, producing an **identical tree**.

| PR | Title | Size | Files | Author |
|---|---|---|---:|---|
| [#15687](https://github.com/open-learning-exchange/myplanet/pull/15687) | Fix N+1 query bottleneck in ResourcesRepository batch insertions | small | 2 | dogi |
| [#15768](https://github.com/open-learning-exchange/myplanet/pull/15768) | finances: reset date filter when leaving finances page (fixes [#15767](https://github.com/open-learning-exchange/myplanet/issues/15767)) | — | 1 | ragilzakaria |
| [#15770](https://github.com/open-learning-exchange/myplanet/pull/15770) | empty resource navigation (fixes [#15728](https://github.com/open-learning-exchange/myplanet/issues/15728)) | — | 5 | ragilzakaria |
| [#15773](https://github.com/open-learning-exchange/myplanet/pull/15773) | edited filter logic so the fromData & toDate mustn't exceed today, and toDate has to be greater or equal to fromDate (fixes [#15766](https://github.com/open-learning-exchange/myplanet/issues/15766)) | — | 1 | J-S-webskas |

**This wave is mostly external contributors** — [#15768](https://github.com/open-learning-exchange/myplanet/pull/15768) and [#15770](https://github.com/open-learning-exchange/myplanet/pull/15770) from `ragilzakaria`, [#15773](https://github.com/open-learning-exchange/myplanet/pull/15773)
from `J-S-webskas`, and only [#15687](https://github.com/open-learning-exchange/myplanet/pull/15687) from `dogi`. That is a real shift: previous rounds were almost
entirely maintainer/agent PRs. The backlog is now dominated by outside human work.

---

## 3. Needs a rebase before it can land (6 PRs)

| PR | Title | Size | Files | Author |
|---|---|---|---:|---|
| [#15646](https://github.com/open-learning-exchange/myplanet/pull/15646) | teams: smoother base team dispatcher wrapping (fixes [#15799](https://github.com/open-learning-exchange/myplanet/issues/15799)) | small | 4 | dogi |
| [#15655](https://github.com/open-learning-exchange/myplanet/pull/15655) | courses: smoother take course view modelling (fixes [#15800](https://github.com/open-learning-exchange/myplanet/issues/15800)) | small | 2 | dogi |
| [#15661](https://github.com/open-learning-exchange/myplanet/pull/15661) | all: smoother gson injecting (fixes [#15801](https://github.com/open-learning-exchange/myplanet/issues/15801)) | medium | 13 | dogi |
| [#15664](https://github.com/open-learning-exchange/myplanet/pull/15664) | sync: less sync manager courses repository is more (fixes [#15802](https://github.com/open-learning-exchange/myplanet/issues/15802)) | small | 2 | dogi |
| [#15693](https://github.com/open-learning-exchange/myplanet/pull/15693) | Refactor SyncActivity to extract provisioning logic into ConfigurationsRepository | small | 4 | dogi |
| [#15696](https://github.com/open-learning-exchange/myplanet/pull/15696) | community: smoother shared message deleting (fixes [#15695](https://github.com/open-learning-exchange/myplanet/issues/15695)) | — | 3 | ragilzakaria |

**Five of these have been stuck for two consecutive plans** — [#15646](https://github.com/open-learning-exchange/myplanet/pull/15646) [#15655](https://github.com/open-learning-exchange/myplanet/pull/15655) [#15661](https://github.com/open-learning-exchange/myplanet/pull/15661) [#15664](https://github.com/open-learning-exchange/myplanet/pull/15664) [#15696](https://github.com/open-learning-exchange/myplanet/pull/15696) were in the
last plan's rebase queue too, while 13 of their peers got rebased and landed around them.

Notably they **have** been worked, just not rebased: [#15646](https://github.com/open-learning-exchange/myplanet/pull/15646), [#15655](https://github.com/open-learning-exchange/myplanet/pull/15655), [#15661](https://github.com/open-learning-exchange/myplanet/pull/15661), [#15664](https://github.com/open-learning-exchange/myplanet/pull/15664) and
[#15696](https://github.com/open-learning-exchange/myplanet/pull/15696) all carry freshly retitled house-style names now (e.g. [#15664](https://github.com/open-learning-exchange/myplanet/pull/15664) became *"sync: less sync manager
courses repository is more (fixes [#15802](https://github.com/open-learning-exchange/myplanet/issues/15802))"*). So the prepping agent reached them and the rebase did not.
**Prepping is not rebasing** — a retitled PR can still be unmergeable, and title quality is no signal of
readiness.

[#15693](https://github.com/open-learning-exchange/myplanet/pull/15693) is new to this queue: it moved `ready` → `merge` and is already conflicting.

---

## 4. Protection rules

**Priority protection: not applicable.** Still no open PR carries `priority`. Costs 0 by construction, not by
measurement. Recompute if the label reappears.

**External-contributor protection: 0 PRs, but no longer vacuous.** For the first time the wave pool contains
real external contributors — 3 of the 4 wave-1 PRs. With zero conflict edges no tie-break is exercised,
so protection is free; but if a future round has contention, this rule will finally have teeth and should be
checked rather than assumed free.

**No hub.** Zero edges among the clean set means no contended PR to sacrifice. All remaining leverage is the
rebase queue in §3.

---

## 5. `ready` label impact (report only — never merged)

6 PRs carry `ready`.
- [#15594](https://github.com/open-learning-exchange/myplanet/pull/15594) — **broken against BASE**, needs a rebase regardless
- [#15656](https://github.com/open-learning-exchange/myplanet/pull/15656) — **broken against BASE**, needs a rebase regardless
- [#15614](https://github.com/open-learning-exchange/myplanet/pull/15614) — clean, no collision with wave 1
- [#15650](https://github.com/open-learning-exchange/myplanet/pull/15650) — clean, no collision with wave 1
- [#15698](https://github.com/open-learning-exchange/myplanet/pull/15698) — clean, no collision with wave 1
- [#15772](https://github.com/open-learning-exchange/myplanet/pull/15772) — clean, no collision with wave 1

---

## 6. Semantic risk in wave 1 — low, and checked

Exactly **one** file is touched by two wave-1 PRs:
`EnterprisesFinancesFragment.kt` by [#15768](https://github.com/open-learning-exchange/myplanet/pull/15768) and [#15773](https://github.com/open-learning-exchange/myplanet/pull/15773).

Both concern the finances date filter, so I read the two diffs rather than trusting the clean merge:
[#15768](https://github.com/open-learning-exchange/myplanet/pull/15768) extracts the reset path into a `resetFilterAndSort()` helper, [#15773](https://github.com/open-learning-exchange/myplanet/pull/15773) adds a `maxDate` bound
to the date picker. Different code paths, no functional collision. **Genuine low risk.**

Wave 1 spans 8 files total. A mid-wave test checkpoint is not warranted at this size; a single
`./gradlew testDefaultDebugUnitTest` after the wave is enough.

---

## 7. Squash titles — pipeline confirmed, with a caveat

Titles are permanent: the drainer squashes `--subject "$TITLE (#$NUMBER)"` verbatim.

- **31 of 31** landed subjects are house style.
- **29 of 31** reference a `fixes #N` issue numbered above their own PR number — the automated-rename signature.
- The prepping agent also reached 5 of the 6 unmergeable PRs in §3, retitling them in place.

**Recommended action: none** — the `merge-prepping` skill is clearly active and ahead of the queue.

The caveat from §3 is the useful new finding: because prepping now runs ahead of merging, **house-style titles
have appeared on PRs that cannot merge at all.** Title style was already useless as a merge gate; this round it
would actively mislead, ranking 5 unmergeable PRs above 3 clean external-contributor PRs whose titles are raw
or partial.

---

## 8. Decisions needed before any GitHub write

Answer in one line, e.g. `1a 2b 3a`.

**1. Queue wave 1 (4 PRs)?**
   a. *(default)* Add `automerge` to all 4, preserving existing labels; you dispatch the drainer.
   b. Queue nothing — the plan is the deliverable.

**2. [#15631](https://github.com/open-learning-exchange/myplanet/pull/15631) — closed, unmerged, still `merge`-labelled**
   a. *(default)* You decide; I take no action and keep reporting it.
   b. Remove the `merge` label so it stops surfacing in future plans (label read fresh, then existing minus `merge`).
   c. I read its comments and CI failure and report why it was closed.

**3. The 6-PR rebase queue, 5 of them stuck two rounds**
   a. *(default)* Report only; authors handle their own rebases.
   b. I rebase [#15664](https://github.com/open-learning-exchange/myplanet/pull/15664) (the one mechanical `less` PR) and push.
   c. I rebase all 6 onto current master and push each.

On label writes: the issues API replaces the whole label array, so I will read each PR's current labels
immediately before writing and send existing + `automerge` — never a cached list. Labels moved again this round:
[#15770](https://github.com/open-learning-exchange/myplanet/pull/15770) and [#15693](https://github.com/open-learning-exchange/myplanet/pull/15693) went `ready` → `merge`, [#15768](https://github.com/open-learning-exchange/myplanet/pull/15768) and [#15773](https://github.com/open-learning-exchange/myplanet/pull/15773) `change`/none → `merge`,
while [#15684](https://github.com/open-learning-exchange/myplanet/pull/15684), [#15644](https://github.com/open-learning-exchange/myplanet/pull/15644) and [#15699](https://github.com/open-learning-exchange/myplanet/pull/15699) left `ready`/`review` for `change`.

---

## 9. Method

- **Depth.** Clone deepened to **3134** commits; at default depth 50 `merge-base` silently returns nothing
  usable and healthy PRs look unmergeable.
- **Heads.** All PR heads re-fetched at snapshot time; analysis ran locally, no API in the hot loop.
- **Landed set.** From `git log f53e466..master` squash subjects, not the API. The one closed-unmerged PR was
  caught by reconciling the open list against the planned buckets and reading that PR directly.
- **Pairs.** Only pairs sharing a changed file tested: **1 of 28** possible, yielding **0 conflict edges**.
- **Simulation.** Wave 1 chained through `commit-tree` forwards and reversed; both clean, identical tree.
- **Semantic check.** The single shared file's two diffs were read rather than assumed safe.

Buckets reconcile: 4 + 6 = 10 = the open `merge` count. If a PR shows
`mergeable_state: dirty` where this says clean, its ref moved after the snapshot — re-fetch before re-asserting
any number here, including mine.

