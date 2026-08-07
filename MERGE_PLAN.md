# myPlanet — `ready to merge` PR merge plan

Generated against `origin/master` @ `a8a5252` · 63 `ready to merge` + 13 `ready` PRs analysed.

Conflict detection is **real three-way merging** (`git merge-tree`, the engine GitHub uses) — not file-overlap heuristics. Every pair sharing a file was merged both directions; the full Wave 1 sequence was simulated cumulatively.

> **Two methodology notes**
> 1. This clone was **shallow (50 commits)**, which silently broke `git merge-base` for older branches. History was deepened to 2050 commits before analysis. A shallow clone makes PRs look unmergeable when they are fine.
> 2. An initial pass reported 35 "order-dependent" conflicts. Those were an **artifact** of PRs that already conflict with `master` being untestable. After excluding them the conflict graph is **perfectly symmetric** — ordering never rescues a real conflict. Unlike the previous plan, there are no "merge in this order" chains: a contended file means somebody rebases.

## Summary

| Bucket | Count |
|---|---|
| `ready to merge`, merges clean into `master` | **58** |
| `ready to merge`, already conflicts with `master` | **5** |
| `ready` (pre-human-review) | 13 |
| **Mergeable now, zero rebases** | **46** |

The previous plan's appendix predicted #15274, #15282, #15294, #15302 and #15339 would break. All five did.

---

## Wave 1 — merge now · 46 PRs · ANY order

Simulated all 46 merges cumulatively in sequence: **0 conflicts**, 140 files touched. This is a *maximum independent set* on the conflict graph — 46 is provably the most that can land without a single rebase.

### Wave 1A — 28 PRs with zero conflict edges against anything

Safe for an auto-merge queue in any order, forever. Nothing in this repo currently contends with them.

| PR | Title | Author |
|---|---|---|
| [#15070](https://github.com/open-learning-exchange/myplanet/pull/15070) | 🔒 Fix insecure use of SharedPreferences during migration | dogi |
| [#15271](https://github.com/open-learning-exchange/myplanet/pull/15271) | Optimize getTeamMemberStatuses in TeamsRepositoryImpl | dogi |
| [#15277](https://github.com/open-learning-exchange/myplanet/pull/15277) | Optimize survey ID scanning when grouping submissions | dogi |
| [#15281](https://github.com/open-learning-exchange/myplanet/pull/15281) | Optimize SimpleDateFormat allocations by caching formatters | dogi |
| [#15295](https://github.com/open-learning-exchange/myplanet/pull/15295) | Refactor: Remove legacy Realm terminology from models and BaseTeamFragment | dogi |
| [#15298](https://github.com/open-learning-exchange/myplanet/pull/15298) | Refactor TeamFragment to route repository calls through TeamViewModel | dogi |
| [#15301](https://github.com/open-learning-exchange/myplanet/pull/15301) | Remove unused repository injections | dogi |
| [#15304](https://github.com/open-learning-exchange/myplanet/pull/15304) | Refactor: Remove extra scope hop in RealtimeSyncMixin.refreshRecyclerView | dogi |
| [#15308](https://github.com/open-learning-exchange/myplanet/pull/15308) | Refactor: Use WhileSubscribed(5000) for StateFlow SharingStarted | dogi |
| [#15312](https://github.com/open-learning-exchange/myplanet/pull/15312) | Refactor: Replace notifyDataSetChanged with DiffUtil in PagerAdapters | dogi |
| [#15320](https://github.com/open-learning-exchange/myplanet/pull/15320) | life: smoother user repository achievement view modelling (fixes #15433) | dogi |
| [#15338](https://github.com/open-learning-exchange/myplanet/pull/15338) | 🧪 Improve test coverage for RetryRepositoryImpl | dogi |
| [#15341](https://github.com/open-learning-exchange/myplanet/pull/15341) | 🧪 Add comprehensive unit tests for UploadRepository | dogi |
| [#15344](https://github.com/open-learning-exchange/myplanet/pull/15344) | 🧪 test: Improve DownloadService error handling testing | dogi |
| [#15347](https://github.com/open-learning-exchange/myplanet/pull/15347) | ⚡ Optimize `UserEntity.isManager` and `isLeader` memory and CPU overhead | dogi |
| [#15351](https://github.com/open-learning-exchange/myplanet/pull/15351) | ⚡ Optimize RetryInterceptor by replacing Thread.sleep with LockSupport.parkNanos | dogi |
| [#15357](https://github.com/open-learning-exchange/myplanet/pull/15357) | 🧪 Add tests for SurveysRepository | dogi |
| [#15358](https://github.com/open-learning-exchange/myplanet/pull/15358) | 🧪 [testing improvement] Add missing tests for TagsRepository | dogi |
| [#15359](https://github.com/open-learning-exchange/myplanet/pull/15359) | 🧪 Add tests for UploadToShelfService | dogi |
| [#15360](https://github.com/open-learning-exchange/myplanet/pull/15360) | 🧪 [Test Improvement] Add missing tests for DownloadRepositoryImpl edge cases | dogi |
| [#15361](https://github.com/open-learning-exchange/myplanet/pull/15361) | 🧪 [testing improvement] Add test suite for ActivitiesRepositoryImpl | dogi |
| [#15363](https://github.com/open-learning-exchange/myplanet/pull/15363) | 🧪 Add test coverage for NotificationsRepository | dogi |
| [#15364](https://github.com/open-learning-exchange/myplanet/pull/15364) | 🧪 [testing improvement] Add missing unit tests for HealthRepositoryImpl | dogi |
| [#15365](https://github.com/open-learning-exchange/myplanet/pull/15365) | 🧪 [Testing Improvement] Add VoicePostingPolicy Tests | dogi |
| [#15366](https://github.com/open-learning-exchange/myplanet/pull/15366) | 🧹 [code health] Refactor `DownloadService` nested logic using early returns | dogi |
| [#15368](https://github.com/open-learning-exchange/myplanet/pull/15368) | 🧹 Refactor DownloadService alternative URL resolution to improve code health | dogi |
| [#15378](https://github.com/open-learning-exchange/myplanet/pull/15378) | 🧹 [Refactor] Extract ApkLog instantiation in MainApplication | dogi |
| [#15381](https://github.com/open-learning-exchange/myplanet/pull/15381) | 🧪 Add missing tests for ProgressRepositoryImpl | dogi |

### Wave 1B — 18 PRs that win a contended file

These merge clean, but each one is *why* something in Wave 2 has to rebase. Listed with what it displaces.

| PR | Title | Author | Displaces |
|---|---|---|---|
| [#15268](https://github.com/open-learning-exchange/myplanet/pull/15268) | course: improve step navigation and UI (fixes #15254) | **Okuro3499** | [#15300](https://github.com/open-learning-exchange/myplanet/pull/15300) |
| [#15275](https://github.com/open-learning-exchange/myplanet/pull/15275) | Refactor TeamsRepositoryImpl to push down getAll() filtering to SQL | dogi | [#15316](https://github.com/open-learning-exchange/myplanet/pull/15316) |
| [#15283](https://github.com/open-learning-exchange/myplanet/pull/15283) | Refactor bare flow collections to use lifecycle-aware helpers in UI Fragments | dogi | — *(see #15158 note)* |
| [#15285](https://github.com/open-learning-exchange/myplanet/pull/15285) | Remove dead RealtimeSyncManager companion singleton | dogi | [#15323](https://github.com/open-learning-exchange/myplanet/pull/15323), [#15327](https://github.com/open-learning-exchange/myplanet/pull/15327) |
| [#15286](https://github.com/open-learning-exchange/myplanet/pull/15286) | Fix leaked CoroutineScope in CourseFilterController | dogi | [#15307](https://github.com/open-learning-exchange/myplanet/pull/15307) |
| [#15296](https://github.com/open-learning-exchange/myplanet/pull/15296) | Keep crash-log sweeping on one IO context | dogi | [#15331](https://github.com/open-learning-exchange/myplanet/pull/15331) |
| [#15303](https://github.com/open-learning-exchange/myplanet/pull/15303) | Refactor SyncManager server pulls to SyncRepository | dogi | [#15323](https://github.com/open-learning-exchange/myplanet/pull/15323), [#15325](https://github.com/open-learning-exchange/myplanet/pull/15325) |
| [#15305](https://github.com/open-learning-exchange/myplanet/pull/15305) | Fix: Prevent redundant user model reloads on screen setup | dogi | [#15307](https://github.com/open-learning-exchange/myplanet/pull/15307), [#15327](https://github.com/open-learning-exchange/myplanet/pull/15327) |
| [#15311](https://github.com/open-learning-exchange/myplanet/pull/15311) | refactor: add flowOn to mapping flows in TeamsRepositoryImpl | dogi | [#15327](https://github.com/open-learning-exchange/myplanet/pull/15327) |
| [#15314](https://github.com/open-learning-exchange/myplanet/pull/15314) | Refactor upload personal routing to PersonalsRepository | dogi | [#15340](https://github.com/open-learning-exchange/myplanet/pull/15340) |
| [#15321](https://github.com/open-learning-exchange/myplanet/pull/15321) | Refactor repository queries to use COUNT(*) | dogi | [#15316](https://github.com/open-learning-exchange/myplanet/pull/15316) |
| [#15322](https://github.com/open-learning-exchange/myplanet/pull/15322) | Refactor: Move direct SharedPrefManager reads out of UI | dogi | [#15327](https://github.com/open-learning-exchange/myplanet/pull/15327), [#15353](https://github.com/open-learning-exchange/myplanet/pull/15353) |
| [#15324](https://github.com/open-learning-exchange/myplanet/pull/15324) | Refactor: Move TeamTaskDao and TeamLogDao behind TeamsSyncRepository | dogi | [#15327](https://github.com/open-learning-exchange/myplanet/pull/15327), [#15356](https://github.com/open-learning-exchange/myplanet/pull/15356) |
| [#15328](https://github.com/open-learning-exchange/myplanet/pull/15328) | Replace SettingsViewModel service dependencies with repository boundaries | dogi | [#15327](https://github.com/open-learning-exchange/myplanet/pull/15327) |
| [#15332](https://github.com/open-learning-exchange/myplanet/pull/15332) | ⚡ perf: Reuse singleton Gson instance in EventsRepositoryImpl | dogi | — *(see #15158 note)* |
| [#15333](https://github.com/open-learning-exchange/myplanet/pull/15333) | ⚡ Optimize Gson instantiation to improve performance | dogi | [#15273](https://github.com/open-learning-exchange/myplanet/pull/15273) |
| [#15334](https://github.com/open-learning-exchange/myplanet/pull/15334) | ⚡ Refactor Gson initialization in CoursesProgressFragment | dogi | [#15273](https://github.com/open-learning-exchange/myplanet/pull/15273) |
| [#15374](https://github.com/open-learning-exchange/myplanet/pull/15374) | 🧹 Refactor deeply nested tryConnect logic in MainApplication | dogi | [#15379](https://github.com/open-learning-exchange/myplanet/pull/15379) |

The single biggest lever was dropping **[#15327](https://github.com/open-learning-exchange/myplanet/pull/15327)** (`RealtimeSyncMixin` → Repositories), a degree-7 hub. Sacrificing that one PR unlocks six others.

### Copy-paste

```bash
REPO=open-learning-exchange/myplanet
for pr in 15070 15268 15271 15275 15277 15281 15283 15285 15286 15295 15296 15298 15301 15303 \
          15304 15305 15308 15311 15312 15314 15320 15321 15322 15324 15328 15332 15333 15334 \
          15338 15341 15344 15347 15351 15357 15358 15359 15360 15361 15363 15364 15365 15366 \
          15368 15374 15378 15381; do
  echo "=== merging #$pr"
  gh pr merge "$pr" --repo "$REPO" --squash --delete-branch || { echo "STOPPED at #$pr"; break; }
done
```

---

## Wave 2 — 12 PRs · each needs a rebase

Verified: none auto-merge after Wave 1, and none chain cleanly with each other. Rebase [#15327](https://github.com/open-learning-exchange/myplanet/pull/15327) **first** — it is the only one with real surface area; the rest are largely mechanical.

| PR | Title | Author | Blocked by | Files |
|---|---|---|---|---|
| [#15327](https://github.com/open-learning-exchange/myplanet/pull/15327) | Refactor RealtimeSyncMixin table updates to Repositories | dogi | [#15285](https://github.com/open-learning-exchange/myplanet/pull/15285), [#15305](https://github.com/open-learning-exchange/myplanet/pull/15305), [#15311](https://github.com/open-learning-exchange/myplanet/pull/15311), [#15322](https://github.com/open-learning-exchange/myplanet/pull/15322), [#15324](https://github.com/open-learning-exchange/myplanet/pull/15324), [#15328](https://github.com/open-learning-exchange/myplanet/pull/15328) | 15 |
| [#15316](https://github.com/open-learning-exchange/myplanet/pull/15316) | Refactor: Split LegacyEntityDaos.kt into per-DAO files | dogi | [#15275](https://github.com/open-learning-exchange/myplanet/pull/15275), [#15321](https://github.com/open-learning-exchange/myplanet/pull/15321) | 9 |
| [#15300](https://github.com/open-learning-exchange/myplanet/pull/15300) | Refactor: Remove cross-repo passthroughs on CoursesRepository | dogi | [#15268](https://github.com/open-learning-exchange/myplanet/pull/15268) | 6 |
| [#15323](https://github.com/open-learning-exchange/myplanet/pull/15323) | all: less service module injection is more (fixes #15434) | dogi | [#15285](https://github.com/open-learning-exchange/myplanet/pull/15285), [#15303](https://github.com/open-learning-exchange/myplanet/pull/15303) | 5 |
| [#15325](https://github.com/open-learning-exchange/myplanet/pull/15325) | Refactor dashboard-triggered key sync out of BaseDashboardFragment | dogi | [#15303](https://github.com/open-learning-exchange/myplanet/pull/15303) | 5 |
| [#15307](https://github.com/open-learning-exchange/myplanet/pull/15307) | Stop re-creating adapters in `BaseRecyclerFragment` | dogi | [#15286](https://github.com/open-learning-exchange/myplanet/pull/15286), [#15305](https://github.com/open-learning-exchange/myplanet/pull/15305) | 4 |
| [#15273](https://github.com/open-learning-exchange/myplanet/pull/15273) | Use injected Gson instead of constructing one at call sites | dogi | [#15333](https://github.com/open-learning-exchange/myplanet/pull/15333), [#15334](https://github.com/open-learning-exchange/myplanet/pull/15334) | 4 |
| [#15331](https://github.com/open-learning-exchange/myplanet/pull/15331) | ⚡ Optimize N+1 database insertion bottlenecks during crash log sweep operations | dogi | [#15296](https://github.com/open-learning-exchange/myplanet/pull/15296) | 2 |
| [#15353](https://github.com/open-learning-exchange/myplanet/pull/15353) | ⚡ Optimize SimpleDateFormat creation with cached DateTimeFormatter | dogi | [#15322](https://github.com/open-learning-exchange/myplanet/pull/15322) | 1 |
| [#15356](https://github.com/open-learning-exchange/myplanet/pull/15356) | ⚡ Optimize date formatting in TeamsRepositoryImpl using DateTimeFormatter | dogi | [#15324](https://github.com/open-learning-exchange/myplanet/pull/15324) | 1 |
| [#15379](https://github.com/open-learning-exchange/myplanet/pull/15379) | 🧹 [Code Health] Reduce deep nesting in MainApplication ANR listener | dogi | [#15374](https://github.com/open-learning-exchange/myplanet/pull/15374) | 1 |
| [#15340](https://github.com/open-learning-exchange/myplanet/pull/15340) | 🧪 [Testing Improvement] Add tests for uploadPersonalDocument in PersonalsRepository | dogi | [#15314](https://github.com/open-learning-exchange/myplanet/pull/15314) | 1 |

---

## Wave 3 — 5 PRs already broken against today's `master`

These need a rebase before they are mergeable **at all**, independent of this plan.

| PR | Title | Author | Files |
|---|---|---|---|
| [#15289](https://github.com/open-learning-exchange/myplanet/pull/15289) | Delete dead repository interface surface | dogi | 20 |
| [#15302](https://github.com/open-learning-exchange/myplanet/pull/15302) | Refactor: Move storage scanning and deletion out of StorageCategoryDetailFragment | dogi | 7 |
| [#15294](https://github.com/open-learning-exchange/myplanet/pull/15294) | Add ViewModels for Leaders, Life, and SendSurvey fragments | dogi | 7 |
| [#15282](https://github.com/open-learning-exchange/myplanet/pull/15282) | Move sorting logic from adapters to viewmodels | dogi | 6 |
| [#15339](https://github.com/open-learning-exchange/myplanet/pull/15339) | 🧪 Add unit tests for ResourcesRepositoryImpl | dogi | 1 |

[#15289](https://github.com/open-learning-exchange/myplanet/pull/15289) (*Delete dead repository interface surface*, 20 files) is once again the worst offender — it was Wave 5 in the previous plan and still has not landed.

---

## Impact on the 13 `ready` (pre-review) PRs

**12 of 13 are unaffected.** Wave 1 leaves them exactly as they are — including every ragilzakaria / Okuro3499 PR that currently merges clean.

| PR | Title | Author | Today | After Wave 1 |
|---|---|---|---|---|
| [#15158](https://github.com/open-learning-exchange/myplanet/pull/15158) | teams: allow deleting calendar events (fixes #15111) | **ragilzakaria** | clean | **CONFLICT** |
| [#15169](https://github.com/open-learning-exchange/myplanet/pull/15169) | courses leaving rejoining breaks (fixes #15156) | **ragilzakaria** | clean | clean |
| [#15258](https://github.com/open-learning-exchange/myplanet/pull/15258) | settings: ensure no button is hidden (fixes #15257) | **Okuro3499** | conflict | conflict |
| [#15267](https://github.com/open-learning-exchange/myplanet/pull/15267) | prevent download popup dialog cropping when text size is large (fixes #15263) | **ragilzakaria** | clean | clean |
| [#15269](https://github.com/open-learning-exchange/myplanet/pull/15269) | Optimize `ResourceSearchUtils` by caching normalized titles to fix search lag | dogi | clean | clean |
| [#15274](https://github.com/open-learning-exchange/myplanet/pull/15274) | Refactor UserRepositoryImpl to use targeted UserDao queries | dogi | conflict | conflict |
| [#15280](https://github.com/open-learning-exchange/myplanet/pull/15280) | Refactor UI State Collection in BaseDashboardFragment | dogi | clean | clean |
| [#15291](https://github.com/open-learning-exchange/myplanet/pull/15291) | Refactor bare lifecycleScope flow collections | dogi | clean | clean |
| [#15315](https://github.com/open-learning-exchange/myplanet/pull/15315) | Optimize NotificationUtils.getInstance allocations | dogi | clean | clean |
| [#15337](https://github.com/open-learning-exchange/myplanet/pull/15337) | 🧪 Add tests for ConfigurationsRepository | dogi | clean | clean |
| [#15354](https://github.com/open-learning-exchange/myplanet/pull/15354) | ⚡ perf: cache SimpleDateFormat instances to reduce allocation overhead | dogi | clean | clean |
| [#15355](https://github.com/open-learning-exchange/myplanet/pull/15355) | ⚡ perf: Optimize SyncTimeLogger timestamp formatting | dogi | clean | clean |
| [#15383](https://github.com/open-learning-exchange/myplanet/pull/15383) | A31. Use payloads instead of full rebinds in VoicesAdapter | dogi | clean | clean |

### ⚠️ The one casualty — [#15158](https://github.com/open-learning-exchange/myplanet/pull/15158)

**teams: allow deleting calendar events (fixes #15111)** (ragilzakaria) is clean today and **breaks under Wave 1**, via [#15283](https://github.com/open-learning-exchange/myplanet/pull/15283) (`TeamCalendarFragment.kt`) and [#15332](https://github.com/open-learning-exchange/myplanet/pull/15332) (`EventsRepositoryImpl.kt`).

The mitigation was tested: pull #15283 + #15332 out of Wave 1, merge #15158 first, re-add them. Result — **#15283 and #15332 then conflict instead.** The conflict is symmetric. It is strictly *1 external PR rebasing* vs *2 dogi PRs rebasing*; no ordering saves both.

**Recommendation: merge all 46 and ask ragilzakaria to rebase.** #15158 is pre-review and bumps the Room schema to version 6 — given master's recent churn it needs a fresh look regardless, and holding two review-complete PRs hostage to an unreviewed one inverts the queue.

If you would rather protect the external contributor, the 44-PR variant is verified working: drop `15283 15332` from the Wave 1 list, merge #15158 once approved, then merge those two (they will need a rebase).

Note also that [#15274](https://github.com/open-learning-exchange/myplanet/pull/15274) and [#15258](https://github.com/open-learning-exchange/myplanet/pull/15258) already conflict with `master` today, before this plan touches anything.

---

## Caveats

- Conflict detection is **textual**. A clean merge does not guarantee the combined result compiles or that `testDefaultDebugUnitTest` passes.
- Highest semantic risk in Wave 1: the **`TeamsRepositoryImpl.kt` cluster** ([#15271](https://github.com/open-learning-exchange/myplanet/pull/15271), [#15275](https://github.com/open-learning-exchange/myplanet/pull/15275), [#15311](https://github.com/open-learning-exchange/myplanet/pull/15311), [#15322](https://github.com/open-learning-exchange/myplanet/pull/15322), [#15324](https://github.com/open-learning-exchange/myplanet/pull/15324) all land together) and the **Gson cluster** ([#15332](https://github.com/open-learning-exchange/myplanet/pull/15332), [#15333](https://github.com/open-learning-exchange/myplanet/pull/15333), [#15334](https://github.com/open-learning-exchange/myplanet/pull/15334) land while [#15273](https://github.com/open-learning-exchange/myplanet/pull/15273) is deferred). Run the test suite mid-wave, not only at the end.
- Squash-merging moves `master`'s SHA at every step; any PR with a *required up-to-date branch* rule needs a branch update between waves.
- Re-run this analysis if `master` moves for any reason other than executing this plan.

