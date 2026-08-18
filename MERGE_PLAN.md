# myPlanet — merge wave plan

**BASE** [`f53e466b696e061dfc02125153ae1324de1f6a28`](https://github.com/open-learning-exchange/myplanet/commit/f53e466b696e061dfc02125153ae1324de1f6a28)
**PR heads fetched** 2026-08-18 05:02:41Z · **git** 2.43.0 · **clone depth** 3103 commits
**Scope** 35 open `merge` · 6 `ready` · **0** `priority` · 0 `automerge` (queue empty)

> Conflict detection here is **textual** — a clean merge is not a passing build. Re-run if BASE moves for
> any reason other than executing this plan.

**No GitHub writes have been made.**

---

## 1. How the previous plan did

The previous plan (BASE `d25620b`) is now executed and measurable. **38 commits landed.**

| Previous bucket | Planned | Landed | Still open |
|---|---:|---:|---:|
| Wave 1 — land now | 39 | 38 | 1 |
| Wave 2 — rebase then land | 7 | 0 | 7 |
| Wave 3 — rebase then land | 2 | 0 | 2 |
| Already broken vs BASE | 6 | 0 | 6 |

**Wave 1 landed 38 of 39 (97%).** Nothing outside the plan was merged, and nothing in waves 2–3 or the
broken bucket landed — exactly as predicted, since those needed rebases first.

**The one hold-out:** [#15526](https://github.com/open-learning-exchange/myplanet/pull/15526) is still open, still `merge`-labelled, with **no** `automerge` label and
untouched since Aug 14. It was held, not dropped by the drainer — it is the only wave-1 member touching
`.github/workflows/release.yml` (it asks for `actions: write`). Worth a decision rather than a silent re-queue.

### The cost of the delay: all 9 rebase-targets decayed

Every PR in the previous waves 2–3 has gone from *"clean against BASE, needs a rebase"* to
**hard-conflicting with BASE**:

[#15529](https://github.com/open-learning-exchange/myplanet/pull/15529) [#15596](https://github.com/open-learning-exchange/myplanet/pull/15596) [#15629](https://github.com/open-learning-exchange/myplanet/pull/15629) [#15664](https://github.com/open-learning-exchange/myplanet/pull/15664) [#15670](https://github.com/open-learning-exchange/myplanet/pull/15670) [#15674](https://github.com/open-learning-exchange/myplanet/pull/15674) [#15677](https://github.com/open-learning-exchange/myplanet/pull/15677) [#15637](https://github.com/open-learning-exchange/myplanet/pull/15637) [#15672](https://github.com/open-learning-exchange/myplanet/pull/15672)

And none of the 6 previously-broken PRs were rebased, so they are still broken. Net effect: the broken bucket
grew from 6 to **18**. That is the measurable price of landing wave 1 without touching the tail.

---

## 2. Wave 1 — land now (17 PRs)

The 17 `merge`-labelled PRs that are clean against BASE have **zero conflict edges between them** — all
17 are mutually independent, so there is a single wave this round, not three. Cumulative chained
simulation (`merge-tree --write-tree` → `commit-tree`) ran **0 failures**, forwards and reversed, both
producing an **identical tree**.

| PR | Title | Size | Files | Author |
|---|---|---|---:|---|
| [#15526](https://github.com/open-learning-exchange/myplanet/pull/15526) | actions: smoother workflow automerge drain cancelling (fixes [#15561](https://github.com/open-learning-exchange/myplanet/issues/15561)) | small | 1 | dogi |
| [#15627](https://github.com/open-learning-exchange/myplanet/pull/15627) | Add Glide onViewRecycled clears + lifecycle-safe request managers | small | 3 | dogi |
| [#15631](https://github.com/open-learning-exchange/myplanet/pull/15631) | Move text-resource file reading and Markdown setup off the main thread | large | 3 | dogi |
| [#15647](https://github.com/open-learning-exchange/myplanet/pull/15647) | Refactor ChatDetailFragment to single UiState and remove repo injection | large | 3 | dogi |
| [#15648](https://github.com/open-learning-exchange/myplanet/pull/15648) | Refactor Teams Tasks to ViewModel | large | 5 | dogi |
| [#15657](https://github.com/open-learning-exchange/myplanet/pull/15657) | refactor: Drop redundant withContext(io) around suspend Retrofit calls in ConfigurationsRepositoryImpl | small | 1 | dogi |
| [#15659](https://github.com/open-learning-exchange/myplanet/pull/15659) | Refactor: Route remaining ApiInterface calls through UploadRepository | small | 6 | dogi |
| [#15680](https://github.com/open-learning-exchange/myplanet/pull/15680) | perf: optimize removed log deletion using batched transaction | small | 3 | dogi |
| [#15681](https://github.com/open-learning-exchange/myplanet/pull/15681) | Optimize deduplication memory consumption in UserRepository | small | 2 | dogi |
| [#15682](https://github.com/open-learning-exchange/myplanet/pull/15682) | Wrap UploadRepositoryImpl.uploadAttachment I/O in withContext | small | 2 | dogi |
| [#15683](https://github.com/open-learning-exchange/myplanet/pull/15683) | fix: suppress download suggestion dialog correctly in courses | small | 4 | dogi |
| [#15686](https://github.com/open-learning-exchange/myplanet/pull/15686) | test: Add unit tests for TeamTask fromJson mapping | medium | 1 | dogi |
| [#15688](https://github.com/open-learning-exchange/myplanet/pull/15688) | Optimize recursive file deletion in FreeSpaceWorker | small | 1 | dogi |
| [#15689](https://github.com/open-learning-exchange/myplanet/pull/15689) | Optimize string builder concatenation in TeamsRepositoryImpl | small | 1 | dogi |
| [#15692](https://github.com/open-learning-exchange/myplanet/pull/15692) | Extract SyncRepository interfaces | medium | 18 | dogi |
| [#15722](https://github.com/open-learning-exchange/myplanet/pull/15722) | all: bump androidx.webkit:webkit from 1.16.0 to 1.17.0 | — | 1 | dependabot[bot] |
| [#15723](https://github.com/open-learning-exchange/myplanet/pull/15723) | actions: bump actions/cache from 4 to 6 | — | 2 | dependabot[bot] |

Two of these are dependabot bumps ([#15722](https://github.com/open-learning-exchange/myplanet/pull/15722), [#15723](https://github.com/open-learning-exchange/myplanet/pull/15723)); the rest are `dogi`.

---

## 3. Needs a rebase before it can land (18 PRs)

All 18 conflict with BASE today. This is a rebase queue, not a merge order — no ordering rescues a
contended file.

| PR | Title | Size | Files | Author |
|---|---|---|---:|---|
| [#15529](https://github.com/open-learning-exchange/myplanet/pull/15529) | Refactor: push down userDao.getAll() filtering to Room queries | large | 5 | dogi |
| [#15591](https://github.com/open-learning-exchange/myplanet/pull/15591) | resources: smoother empty state control visibility (fixes [#15572](https://github.com/open-learning-exchange/myplanet/issues/15572)) | — | 2 | ragilzakaria |
| [#15596](https://github.com/open-learning-exchange/myplanet/pull/15596) | Refactor VoicesAdapter to remove UserRepository dependency | small | 4 | dogi |
| [#15599](https://github.com/open-learning-exchange/myplanet/pull/15599) | Cache list adapters in ResourcesFragment and CoursesFragment | small | 4 | dogi |
| [#15601](https://github.com/open-learning-exchange/myplanet/pull/15601) | Stream file uploads instead of allocating whole files in memory | small | 5 | dogi |
| [#15626](https://github.com/open-learning-exchange/myplanet/pull/15626) | Refactor CoroutineScopes and adapter IO in courses UI | large | 8 | dogi |
| [#15629](https://github.com/open-learning-exchange/myplanet/pull/15629) | Refactor repository interfaces to remove Android Context dependency | small | 13 | dogi |
| [#15635](https://github.com/open-learning-exchange/myplanet/pull/15635) | Sweep hand-rolled repeatOnLifecycle boilerplate to collectWhenStarted | enormous | 10 | dogi |
| [#15637](https://github.com/open-learning-exchange/myplanet/pull/15637) | Refactor: Tighten UserRepository by extracting health profile and leader parsing logic | large | 12 | dogi |
| [#15646](https://github.com/open-learning-exchange/myplanet/pull/15646) | Drop redundant dispatcher ceremony from BaseTeamFragment and siblings | small | 4 | dogi |
| [#15655](https://github.com/open-learning-exchange/myplanet/pull/15655) | Refactor TakeCourseFragment to use TakeCourseViewModel | small | 2 | dogi |
| [#15661](https://github.com/open-learning-exchange/myplanet/pull/15661) | Inject Hilt-provided Gson into repositories | medium | 13 | dogi |
| [#15664](https://github.com/open-learning-exchange/myplanet/pull/15664) | Remove unused coursesRepository property from SyncManager | small | 2 | dogi |
| [#15670](https://github.com/open-learning-exchange/myplanet/pull/15670) | Remove unused applicationScope from TransactionSyncManager | small | 4 | dogi |
| [#15672](https://github.com/open-learning-exchange/myplanet/pull/15672) | Remove unused teamsSyncRepository from SyncManager | small | 2 | dogi |
| [#15674](https://github.com/open-learning-exchange/myplanet/pull/15674) | Remove unused constructor properties in UploadToShelfService | small | 3 | dogi |
| [#15677](https://github.com/open-learning-exchange/myplanet/pull/15677) | Remove unused sharedPrefManager dependency from UploadManager | small | 2 | dogi |
| [#15696](https://github.com/open-learning-exchange/myplanet/pull/15696) | 15695 fix shared message delete community | — | 3 | ragilzakaria |

**Five of these are mechanical.** [#15664](https://github.com/open-learning-exchange/myplanet/pull/15664) [#15670](https://github.com/open-learning-exchange/myplanet/pull/15670) [#15672](https://github.com/open-learning-exchange/myplanet/pull/15672) [#15674](https://github.com/open-learning-exchange/myplanet/pull/15674) [#15677](https://github.com/open-learning-exchange/myplanet/pull/15677) each remove one
unused constructor dependency from `SyncManager`, `TransactionSyncManager`, `UploadManager` or
`UploadToShelfService` — the same files their landed siblings edited, which is precisely why they now conflict.
I verified against current master that **every one still has a live target** (e.g. `teamsSyncRepository` is
still in `SyncManager`'s constructor, `applicationScope` still in `TransactionSyncManager`), so none has been
made redundant by what landed. They should rebase cleanly with no design decisions.

The remaining 13 are genuine refactors and need author attention.

---

## 4. Protection rules — both vacuous this round

**Priority protection: not applicable.** No open PR carries the `priority` label any more — the previous
holder, [#15607](https://github.com/open-learning-exchange/myplanet/pull/15607), landed in wave 1. There is nothing to protect and nothing to hold, so the rule costs 0
by construction rather than by measurement. If a `priority` label reappears, this must be recomputed.

**External-contributor protection: 0 PRs.** The wave pool has zero conflict edges, so no tie-break of any
kind is exercised. For the record the pool is `dogi` (15) and `dependabot[bot]` (2); the human external
contributors in the backlog (`ragilzakaria`, `J-S-webskas`, `Okuro3499`) hold `change`, `ready` or `close?`
on everything except [#15591](https://github.com/open-learning-exchange/myplanet/pull/15591) and [#15696](https://github.com/open-learning-exchange/myplanet/pull/15696), both of which are in the broken bucket.

**No hub to sacrifice.** With no edges among the clean set there is no high-degree contended PR this round.
The leverage has moved entirely into the rebase queue in §3.

---

## 5. `ready` label impact (report only — never merged)

6 PRs carry `ready`. None collides with wave 1.
- [#15656](https://github.com/open-learning-exchange/myplanet/pull/15656) — already broken against BASE, rebase needed regardless
- [#15693](https://github.com/open-learning-exchange/myplanet/pull/15693) — already broken against BASE, rebase needed regardless
- [#15614](https://github.com/open-learning-exchange/myplanet/pull/15614) — clean, no collision with wave 1
- [#15644](https://github.com/open-learning-exchange/myplanet/pull/15644) — clean, no collision with wave 1
- [#15725](https://github.com/open-learning-exchange/myplanet/pull/15725) — clean, no collision with wave 1
- [#15770](https://github.com/open-learning-exchange/myplanet/pull/15770) — clean, no collision with wave 1

---

## 6. Semantic risk in wave 1 — much lower than last round

Only **2 files** are touched by more than one wave-1 PR, against 24 last round:

| PRs | File | Which |
|---:|---|---|
| 2 | `TeamsRepositoryImpl.kt` | [#15648](https://github.com/open-learning-exchange/myplanet/pull/15648) [#15689](https://github.com/open-learning-exchange/myplanet/pull/15689) |
| 2 | `UploadRepositoryImpl.kt` | [#15659](https://github.com/open-learning-exchange/myplanet/pull/15659) [#15682](https://github.com/open-learning-exchange/myplanet/pull/15682) |

The dense `UploadManager` cluster that carried the real risk last round has already landed. Wave 1 spans 55
files with almost no overlap, so a mid-wave test checkpoint is much less urgent than it was — though
[#15631](https://github.com/open-learning-exchange/myplanet/pull/15631), [#15647](https://github.com/open-learning-exchange/myplanet/pull/15647) and [#15648](https://github.com/open-learning-exchange/myplanet/pull/15648) are `large` refactors and still worth a
`./gradlew testDefaultDebugUnitTest` before the tail of the wave.

---

## 7. Squash titles — the pipeline is confirmed working

The drainer squashes with `--subject "$TITLE (#$NUMBER)"`, verbatim, so titles are permanent. The retitle
pipeline demonstrably ran on this batch:

- **38 of 38** landed subjects are perfect house style, though 94% of the PR titles were raw beforehand.
- **37 of 38** reference a `fixes #N` issue numbered *above* their own PR number — the automated-rename signature.
- Concretely: [#15691](https://github.com/open-learning-exchange/myplanet/pull/15691) was titled *"⚡ Optimize matchesAllParts in CoursesRepositoryImpl"* and landed as
  *"courses: smoother repository parts matching (fixes [#15765](https://github.com/open-learning-exchange/myplanet/issues/15765))"*.

**Recommended action: none.** The `merge-prepping` skill is handling this. Building a retitle worklist for the
17 wave-1 PRs would duplicate work already in flight.

The raw/rot correlation again **did not reproduce**: 94% raw in wave 1 versus 94% in the broken bucket —
identical, so title style carries no signal about mergeability here. Do not gate the queue on it.

---

## 8. Decisions needed before any GitHub write

Answer in one line, e.g. `1a 2a 3b`.

**1. Queue wave 1 (17 PRs)?**
   a. *(default)* Add `automerge` to all 17, preserving existing labels; you dispatch the drainer.
   b. Queue all except [#15526](https://github.com/open-learning-exchange/myplanet/pull/15526), which was held last round for an unknown reason.
   c. Queue nothing — the plan is the deliverable.

**2. [#15526](https://github.com/open-learning-exchange/myplanet/pull/15526) — the hold-out**
   a. *(default)* Leave it and tell me why it was held, so the next plan reflects it.
   b. Re-queue it with the rest.
   c. I read its timeline and report what happened to it.

**3. The 18-PR rebase queue**
   a. *(default)* Report only; authors handle their own rebases.
   b. I rebase the 5 mechanical `less` PRs ([#15664](https://github.com/open-learning-exchange/myplanet/pull/15664) [#15670](https://github.com/open-learning-exchange/myplanet/pull/15670) [#15672](https://github.com/open-learning-exchange/myplanet/pull/15672) [#15674](https://github.com/open-learning-exchange/myplanet/pull/15674) [#15677](https://github.com/open-learning-exchange/myplanet/pull/15677)) and push.
   c. Comment on each of the 18 with what it conflicts against.

On label writes: the issues API replaces the whole label array, so I will read each PR's current labels
immediately before writing and send existing + `automerge` — never a list cached from earlier in this session.
Labels moved a lot since the last plan: [#15696](https://github.com/open-learning-exchange/myplanet/pull/15696), [#15692](https://github.com/open-learning-exchange/myplanet/pull/15692), [#15661](https://github.com/open-learning-exchange/myplanet/pull/15661) and others went `ready` → `merge`,
while [#15694](https://github.com/open-learning-exchange/myplanet/pull/15694), [#15650](https://github.com/open-learning-exchange/myplanet/pull/15650) and [#15687](https://github.com/open-learning-exchange/myplanet/pull/15687) went `ready` → `change`/`review`.

---

## 9. Method

- **Depth.** Clone deepened to **3103** commits; at the default depth 50 `merge-base` silently returns nothing
  usable and healthy PRs look unmergeable.
- **Heads.** All PR heads re-fetched via `+refs/pull/*/head:refs/remotes/pr/*` at snapshot time; analysis ran
  locally with no API in the hot loop.
- **Landed set.** Taken from `git log d25620b..master` squash subjects, not the API.
- **Pairs.** Only pairs sharing a changed file tested: **3 of 210** possible, yielding **0 conflict edges**.
  Symmetry check run and passed trivially.
- **Simulation.** Wave 1 chained through `commit-tree` cumulatively, forwards and reversed; both clean, same tree.
- **Obsolescence check.** For the 5 mechanical `less` PRs I grepped current master to confirm each still has a
  live target rather than assuming the rebase is worthwhile.

If a PR shows `mergeable_state: dirty` where this says clean, its ref moved after the snapshot — re-fetch
before re-asserting any number here, including mine.

