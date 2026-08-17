# myPlanet — merge wave plan

**BASE** [`d25620b0d285a78e6e8feb7dadb8b464c5ad07d2`](https://github.com/open-learning-exchange/myplanet/commit/d25620b0d285a78e6e8feb7dadb8b464c5ad07d2)
**PR heads fetched** 2026-08-17 10:16:52Z · **git** 2.43.0 · **clone depth** 3064 commits
**Scope** 54 open `merge` · 23 `ready` · 1 `priority` · 0 `automerge` (queue drained during this run)

> Re-run this plan if BASE moves for any reason other than executing it. BASE moved *during* this
> analysis and cost 3 PRs. Conflict detection here is **textual** — a clean merge is not a passing build.

**No GitHub writes have been made.**

---

## 1. Where the last plan got to

No prior plan artifact exists in this session, so I am not inventing per-wave landed/open figures for one.
What is measurable from `git log master` (merge subjects, not the API):

| Metric | Value |
|---|---:|
| PRs landed since Aug 10 | 69 |
| Landed subjects in house style | 69 / 69 |
| Decayed out of wave 1 during this session | 3 |
| `automerge` queue depth now | 0 |

The decay is not historical — it happened while I worked. First pass took BASE at `32cab53` and found 41
independent mergeable PRs. Six minutes later BASE was `d25620b`, one commit: [#15579](https://github.com/open-learning-exchange/myplanet/pull/15579) landing as
*"resources: smoother thumbnail preview loading"*. That single merge pushed three PRs into the broken bucket:

- [#15599](https://github.com/open-learning-exchange/myplanet/pull/15599) — Cache list adapters in ResourcesFragment and CoursesFragment
- [#15601](https://github.com/open-learning-exchange/myplanet/pull/15601) — Stream file uploads instead of allocating whole files in memory
- [#15626](https://github.com/open-learning-exchange/myplanet/pull/15626) — Refactor CoroutineScopes and adapter IO in courses UI

My pairwise pass predicted the [#15579](https://github.com/open-learning-exchange/myplanet/pull/15579) ↔ [#15599](https://github.com/open-learning-exchange/myplanet/pull/15599) edge *before* the merge landed. Everything below
was recomputed from scratch against `d25620b` with re-fetched heads; nothing is carried over from the stale pass.

---

## 2. Wave 1 — land now (39 PRs)

Mutually independent and clean against BASE. Cumulative chained simulation
(`merge-tree --write-tree` → `commit-tree`) ran **0 failures across all 39**, forwards *and* reversed,
both producing an **identical tree**. There is no merge order to get wrong.

| PR | Title | Size | Files | Author |
|---|---|---|---:|---|
| [#15526](https://github.com/open-learning-exchange/myplanet/pull/15526) | actions: smoother workflow automerge drain cancelling (fixes [#15561](https://github.com/open-learning-exchange/myplanet/issues/15561)) | small | 1 | dogi |
| [#15587](https://github.com/open-learning-exchange/myplanet/pull/15587) | Refactor: Move IO-bound work in ResourceViewer to ViewModel | small | 2 | dogi |
| [#15597](https://github.com/open-learning-exchange/myplanet/pull/15597) | Hoist per-call Regex compilations and extract normalization logic | small | 6 | dogi |
| [#15598](https://github.com/open-learning-exchange/myplanet/pull/15598) | Refactor Flow chains in CoursesRepositoryImpl | medium | 2 | dogi |
| [#15604](https://github.com/open-learning-exchange/myplanet/pull/15604) | Remove vestigial DatabaseService and DatabaseModule | small | 6 | dogi |
| [#15607](https://github.com/open-learning-exchange/myplanet/pull/15607) | all: smoother notification destination routing (fixes [#15606](https://github.com/open-learning-exchange/myplanet/issues/15606)) | — | 11 | Okuro3499 |
| [#15608](https://github.com/open-learning-exchange/myplanet/pull/15608) | Optimize VoicesAdapter bind path | medium | 2 | dogi |
| [#15615](https://github.com/open-learning-exchange/myplanet/pull/15615) | Remove unused ApiInterface from UploadToShelfService | small | 3 | dogi |
| [#15616](https://github.com/open-learning-exchange/myplanet/pull/15616) | Refactor submissions grouping in fetchCourseData to use HashSet | medium | 2 | dogi |
| [#15617](https://github.com/open-learning-exchange/myplanet/pull/15617) | Refactor TransactionSyncManager intermediate sync checkpoints to use apply() | small | 2 | dogi |
| [#15618](https://github.com/open-learning-exchange/myplanet/pull/15618) | Add payload-only rebinds for MembersAdapter and EnterprisesReportsAdapter | large | 4 | dogi |
| [#15620](https://github.com/open-learning-exchange/myplanet/pull/15620) | Replace toJson/fromJson round-trips with toJsonTree | small | 3 | dogi |
| [#15621](https://github.com/open-learning-exchange/myplanet/pull/15621) | fix: cache LifeFragment adapter and touch helper | small | 1 | dogi |
| [#15622](https://github.com/open-learning-exchange/myplanet/pull/15622) | Precompute HealthExaminationAdapter display data before binding | large | 2 | dogi |
| [#15623](https://github.com/open-learning-exchange/myplanet/pull/15623) | Refactor VoicesRepository to remove cross-feature lookups | small | 8 | dogi |
| [#15624](https://github.com/open-learning-exchange/myplanet/pull/15624) | Refactor SyncRepository processShelfParallel to use injected ApiInterface | small | 3 | dogi |
| [#15625](https://github.com/open-learning-exchange/myplanet/pull/15625) | Refactor ResourcesFragment to use ViewModel for data writes | small | 2 | dogi |
| [#15628](https://github.com/open-learning-exchange/myplanet/pull/15628) | Refactor BellDashboardFragment survey-reminder logic into ViewModel | large | 3 | dogi |
| [#15630](https://github.com/open-learning-exchange/myplanet/pull/15630) | Refactor: Inject UserRepository into Health and Submissions repositories | small | 7 | dogi |
| [#15632](https://github.com/open-learning-exchange/myplanet/pull/15632) | Refactor ProgressRepository to return typed CourseProgressState | small | 8 | dogi |
| [#15640](https://github.com/open-learning-exchange/myplanet/pull/15640) | Refactor Feedback save logic | small | 3 | dogi |
| [#15643](https://github.com/open-learning-exchange/myplanet/pull/15643) | Refactor JSON parsing inside repository loops | medium | 4 | dogi |
| [#15645](https://github.com/open-learning-exchange/myplanet/pull/15645) | Refactor ViewModel CPU-bound sorting off main dispatcher | medium | 4 | dogi |
| [#15649](https://github.com/open-learning-exchange/myplanet/pull/15649) | refactor: Move UploadConfigs DAO access behind repositories | enormous | 18 | dogi |
| [#15651](https://github.com/open-learning-exchange/myplanet/pull/15651) | Refactor: Parallelize independent loads in ViewModels | medium | 3 | dogi |
| [#15653](https://github.com/open-learning-exchange/myplanet/pull/15653) | Refactor: Eliminate no-op diff churn and submitList() list copying | small | 3 | dogi |
| [#15654](https://github.com/open-learning-exchange/myplanet/pull/15654) | Refactor adapter notify calls to use targeted payloads | small | 2 | dogi |
| [#15658](https://github.com/open-learning-exchange/myplanet/pull/15658) | Refactor: Replace UserSessionManager/SharedPrefManager reads with UserRepository | small | 7 | dogi |
| [#15665](https://github.com/open-learning-exchange/myplanet/pull/15665) | Remove unused teamsRepository from SyncManager | small | 2 | dogi |
| [#15667](https://github.com/open-learning-exchange/myplanet/pull/15667) | Remove unused teamsRepository from UploadManager | small | 2 | dogi |
| [#15668](https://github.com/open-learning-exchange/myplanet/pull/15668) | [code health] Remove unused personalsRepository from UploadManager | small | 2 | dogi |
| [#15669](https://github.com/open-learning-exchange/myplanet/pull/15669) | Remove unused submissionsRepository from UploadManager | small | 2 | dogi |
| [#15671](https://github.com/open-learning-exchange/myplanet/pull/15671) | [Remove unused eventsRepository from SyncManager] | small | 2 | dogi |
| [#15673](https://github.com/open-learning-exchange/myplanet/pull/15673) | [Code Health] Remove unused context parameter from RetryQueue | small | 2 | dogi |
| [#15675](https://github.com/open-learning-exchange/myplanet/pull/15675) | Remove unused chatRepository property in UploadManager | small | 2 | dogi |
| [#15676](https://github.com/open-learning-exchange/myplanet/pull/15676) | Remove unused setIndeterminate from DialogUtils.kt | small | 1 | dogi |
| [#15678](https://github.com/open-learning-exchange/myplanet/pull/15678) | Remove unused userRepository in LoginSyncManager | small | 2 | dogi |
| [#15679](https://github.com/open-learning-exchange/myplanet/pull/15679) | [code health improvement] Remove unused teamsRepository from TransactionSyncManager | small | 4 | dogi |
| [#15691](https://github.com/open-learning-exchange/myplanet/pull/15691) | Optimize matchesAllParts in CoursesRepositoryImpl | small | 1 | dogi |

---

## 3. Wave 2 — rebase, then land (7 PRs)

Each of these merges cleanly into master *today*. They are held only because they contend with a wave-1
member. Stacked onto the simulated post-wave-1 tree, **all 7 fail** — so this is a rebase list, not a
"merge these second" list. No ordering rescues a contended file.

| PR | Title | Size | Files | Author |
|---|---|---|---:|---|
| [#15529](https://github.com/open-learning-exchange/myplanet/pull/15529) | Refactor: push down userDao.getAll() filtering to Room queries | large | 5 | dogi |
| [#15596](https://github.com/open-learning-exchange/myplanet/pull/15596) | Refactor VoicesAdapter to remove UserRepository dependency | small | 4 | dogi |
| [#15629](https://github.com/open-learning-exchange/myplanet/pull/15629) | Refactor repository interfaces to remove Android Context dependency | small | 13 | dogi |
| [#15664](https://github.com/open-learning-exchange/myplanet/pull/15664) | Remove unused coursesRepository property from SyncManager | small | 2 | dogi |
| [#15670](https://github.com/open-learning-exchange/myplanet/pull/15670) | Remove unused applicationScope from TransactionSyncManager | small | 4 | dogi |
| [#15674](https://github.com/open-learning-exchange/myplanet/pull/15674) | Remove unused constructor properties in UploadToShelfService | small | 3 | dogi |
| [#15677](https://github.com/open-learning-exchange/myplanet/pull/15677) | Remove unused sharedPrefManager dependency from UploadManager | small | 2 | dogi |

---

## 4. Wave 3 — rebase, then land (2 PRs)

| PR | Title | Size | Files | Author |
|---|---|---|---:|---|
| [#15637](https://github.com/open-learning-exchange/myplanet/pull/15637) | Refactor: Tighten UserRepository by extracting health profile and leader parsing logic | large | 12 | dogi |
| [#15672](https://github.com/open-learning-exchange/myplanet/pull/15672) | Remove unused teamsSyncRepository from SyncManager | small | 2 | dogi |

Residual edges surviving into waves 2–3:

- [#15672](https://github.com/open-learning-exchange/myplanet/pull/15672) ↔ [#15664](https://github.com/open-learning-exchange/myplanet/pull/15664)
- [#15637](https://github.com/open-learning-exchange/myplanet/pull/15637) ↔ [#15596](https://github.com/open-learning-exchange/myplanet/pull/15596)
- [#15637](https://github.com/open-learning-exchange/myplanet/pull/15637) ↔ [#15529](https://github.com/open-learning-exchange/myplanet/pull/15529)

**Hub:** [#15637](https://github.com/open-learning-exchange/myplanet/pull/15637) has the highest conflict degree in the set (4). Rebasing that one PR unlocks the most
others — the highest-leverage piece of manual work available.

---

## 5. Not a wave — already broken vs BASE (6 PRs)

These need a rebase before any of this plan applies, regardless of label.

| PR | Title | Size | Files | Author |
|---|---|---|---:|---|
| [#15599](https://github.com/open-learning-exchange/myplanet/pull/15599) | Cache list adapters in ResourcesFragment and CoursesFragment | small | 4 | dogi |
| [#15601](https://github.com/open-learning-exchange/myplanet/pull/15601) | Stream file uploads instead of allocating whole files in memory | small | 5 | dogi |
| [#15626](https://github.com/open-learning-exchange/myplanet/pull/15626) | Refactor CoroutineScopes and adapter IO in courses UI | large | 8 | dogi |
| [#15635](https://github.com/open-learning-exchange/myplanet/pull/15635) | Sweep hand-rolled repeatOnLifecycle boilerplate to collectWhenStarted | enormous | 10 | dogi |
| [#15646](https://github.com/open-learning-exchange/myplanet/pull/15646) | Drop redundant dispatcher ceremony from BaseTeamFragment and siblings | small | 4 | dogi |
| [#15655](https://github.com/open-learning-exchange/myplanet/pull/15655) | Refactor TakeCourseFragment to use TakeCourseViewModel | small | 2 | dogi |

**Bucket check:** 39 + 7 + 2 + 6 = **54**, the exact count of open `merge`-labelled PRs. Nothing unexplained, nothing double-counted.

---

## 6. Protection costs — both free this run

**Priority protection: 0 PRs.** The only `priority` PR is [#15607](https://github.com/open-learning-exchange/myplanet/pull/15607) (*all: smoother notification
destination routing*), which also carries `merge`. Its conflict degree is **0** — it contends with nothing.
Greedy MIS computes to 39 both with and without protection. It merges in wave 1 and displaces nobody,
so the expensive case does not arise.

**External-contributor protection: 0 PRs.** The pool has exactly two authors: `dogi` (46, org maintainer) and
`Okuro3499` (2), who holds `write`. There are **no** external contributors in the mergeable pool, so the
tie-break is inert. External PRs in the backlog (`ragilzakaria`, `J-S-webskas`) carry `ready`, `change` or
`close?` — never `merge`.

---

## 7. `ready` label impact (report only — never merged)

23 PRs carry `ready`. **18 of 23 are entirely unaffected** by wave 1. The five that are not:
- [#15591](https://github.com/open-learning-exchange/myplanet/pull/15591) — already broken against BASE, rebase needed regardless
- [#15694](https://github.com/open-learning-exchange/myplanet/pull/15694) — already broken against BASE, rebase needed regardless
- [#15661](https://github.com/open-learning-exchange/myplanet/pull/15661) — clean today, collides with [#15630](https://github.com/open-learning-exchange/myplanet/pull/15630), [#15643](https://github.com/open-learning-exchange/myplanet/pull/15643)
- [#15693](https://github.com/open-learning-exchange/myplanet/pull/15693) — clean today, collides with [#15658](https://github.com/open-learning-exchange/myplanet/pull/15658)
- [#15696](https://github.com/open-learning-exchange/myplanet/pull/15696) — clean today, collides with [#15623](https://github.com/open-learning-exchange/myplanet/pull/15623), [#15643](https://github.com/open-learning-exchange/myplanet/pull/15643)

---

## 8. Semantic risk in wave 1

Everything above is **textual**. `merge-tree` says git can combine two diffs without a conflict marker; it says
nothing about whether the result compiles. **24 files are touched by more than one wave-1 PR**, eight by three
or more:

| PRs | File | Which |
|---:|---|---|
| 4 | `UploadManager.kt` + `UploadManagerTest.kt` | [#15667](https://github.com/open-learning-exchange/myplanet/pull/15667) [#15668](https://github.com/open-learning-exchange/myplanet/pull/15668) [#15669](https://github.com/open-learning-exchange/myplanet/pull/15669) [#15675](https://github.com/open-learning-exchange/myplanet/pull/15675) |
| 3 | `SyncManager.kt` | [#15624](https://github.com/open-learning-exchange/myplanet/pull/15624) [#15665](https://github.com/open-learning-exchange/myplanet/pull/15665) [#15671](https://github.com/open-learning-exchange/myplanet/pull/15671) |
| 3 | `SyncManagerTest.kt` | [#15604](https://github.com/open-learning-exchange/myplanet/pull/15604) [#15665](https://github.com/open-learning-exchange/myplanet/pull/15665) [#15671](https://github.com/open-learning-exchange/myplanet/pull/15671) |
| 3 | `ProgressRepositoryImpl.kt` | [#15616](https://github.com/open-learning-exchange/myplanet/pull/15616) [#15632](https://github.com/open-learning-exchange/myplanet/pull/15632) [#15649](https://github.com/open-learning-exchange/myplanet/pull/15649) |
| 3 | `SubmissionsRepositoryImpl.kt` | [#15630](https://github.com/open-learning-exchange/myplanet/pull/15630) [#15643](https://github.com/open-learning-exchange/myplanet/pull/15643) [#15649](https://github.com/open-learning-exchange/myplanet/pull/15649) |
| 3 | `VoicesRepositoryImpl.kt` | [#15623](https://github.com/open-learning-exchange/myplanet/pull/15623) [#15643](https://github.com/open-learning-exchange/myplanet/pull/15643) [#15649](https://github.com/open-learning-exchange/myplanet/pull/15649) |
| 3 | `CoursesAdapter.kt` | [#15632](https://github.com/open-learning-exchange/myplanet/pull/15632) [#15653](https://github.com/open-learning-exchange/myplanet/pull/15653) [#15654](https://github.com/open-learning-exchange/myplanet/pull/15654) |

The `UploadManager` cluster is the sharpest: four PRs each remove a *different* unused constructor dependency
from the same class and its test. Any two are textually independent; landing all four is exactly the shape that
compiles individually and fails together — and with Room/Hilt constructor graphs that surfaces as a KSP error,
not a merge conflict.

**Recommendation:** run `./gradlew testDefaultDebugUnitTest` mid-wave, not only at the end. Natural checkpoint
is after the 17 `less`-labelled dependency removals, before the ViewModel/repository refactors. CI is default
flavour only and shards two ways, so a mid-wave check costs ~3 min wall clock against bisecting 39 squashed commits.

---

## 9. Squash titles — permanent, and already handled

The drainer squashes with `--subject "$TITLE (#$NUMBER)"`, verbatim from
[`.github/scripts/automerge.sh`](https://github.com/open-learning-exchange/myplanet/blob/master/.github/scripts/automerge.sh). Whatever the title says at merge
time is what master carries forever.

By raw count that looks alarming: **52 of 54** `merge` PRs (96%) have raw, never-triaged titles. But **69 of 69**
subjects landed since Aug 10 are perfect house style. Both are true — so the retitle happens between labelling
and draining.

**This is already delegated. Recommended action: none.** Three independent signals agree:

1. The drainer provably does not retitle (passes `$TITLE` through), yet 100% of landed subjects are house style.
2. **43 of 69** landed PRs reference a `fixes #N` issue numbered *above* their own PR number — e.g.
   [#15330](https://github.com/open-learning-exchange/myplanet/pull/15330) fixes [#15584](https://github.com/open-learning-exchange/myplanet/issues/15584). An issue that opens after the PR is the signature of an automated rename.
3. Caught live: [#15579](https://github.com/open-learning-exchange/myplanet/pull/15579) was *"grid thumbnail previewing"* in my 10:10Z snapshot and landed six minutes
   later as *"thumbnail preview loading"*.

A `merge-prepping` skill is maintained in its own repo and shared across Claude Code, OpenHands and Copilot per
`docs/AGENT_SPELLBOOK.md`. Handing over 52 retitle rows would duplicate work in progress and cost 52 sessions of noise.

Two traps also make title style useless as a gate: **attribution** (doer agents post under the summoning user's
account, so a house-style title bearing the maintainer's handle proves nothing — check the PR timeline), and the
raw/rot correlation **did not reproduce** this run (94% raw in wave 1 vs 100% in the broken bucket, n=6 — no
signal). Gating on it would eject [#15607](https://github.com/open-learning-exchange/myplanet/pull/15607), the maintainer's own priority PR, which is raw.

---

## 10. Decisions needed before any GitHub write

Answer in one line, e.g. `1a 2b 3a 4b`.

**1. Queue wave 1?**
   a. *(default)* Add `automerge` to all 39 wave-1 PRs, preserving existing labels; you dispatch the drainer.
   b. Start with the 17 `less`-labelled dependency removals only, verify green, then queue the rest.
   c. Queue nothing — the plan is the deliverable.

**2. The `UploadManager` cluster**
   a. *(default)* Queue all four ([#15667](https://github.com/open-learning-exchange/myplanet/pull/15667) [#15668](https://github.com/open-learning-exchange/myplanet/pull/15668) [#15669](https://github.com/open-learning-exchange/myplanet/pull/15669) [#15675](https://github.com/open-learning-exchange/myplanet/pull/15675)) together, rely on CI.
   b. Queue one, let it land, then the other three — isolates a KSP failure to a single PR.

**3. Waves 2–3 and the broken bucket (15 PRs)**
   a. *(default)* Leave them; the rebase list above is the whole output.
   b. Rebase the hub [#15637](https://github.com/open-learning-exchange/myplanet/pull/15637) only.
   c. Comment on each with what it conflicts with, so authors know.

**4. Retitling**
   a. *(default)* Nothing — evidence says the prepping agent owns this.
   b. Build the 52-row worklist anyway, with duplication risk flagged per row.

On label writes: the issues API replaces the whole label array, so I will read each PR's current labels
immediately before writing and send existing + `automerge` — never a list cached from earlier in this session.

---

## 11. Method

- **Depth.** Clone arrived shallow at 50 commits, where `merge-base` silently returns nothing usable and healthy
  PRs look unmergeable. Deepened to **3064** before any comparison.
- **Heads.** All 8330 refs fetched once via `+refs/pull/*/head:refs/remotes/pr/*`; analysis ran locally, no API
  in the hot loop.
- **PR list.** `list_pull_requests` paginated 100/page, labels filtered locally. Page 2 held 25 stale
  WIP/on-hold PRs carrying none of the four target labels — checked, then ignored. Label *search* was not used
  as source of truth; its index lags writes.
- **Pairs.** Only pairs sharing a changed file tested: **121 of 2628** possible, yielding **20 conflict edges**.
  Every edge re-tested with arguments reversed — all 20 symmetric.
- **Simulation.** Wave 1 chained through `commit-tree` cumulatively, forwards and reversed; both clean, same tree.

If a PR shows `mergeable_state: dirty` where this says clean, its ref moved after the snapshot — re-fetch before
re-asserting any number here, including mine.

