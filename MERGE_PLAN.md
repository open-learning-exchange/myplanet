# Merge plan — `ready to merge` PRs

**Base:** `origin/master` @ `968f8bf` · **Generated:** 2026-08-06 · **Scope:** the **58 open PRs labelled `ready to merge`** (PRs labelled only `ready` are *not* in this plan — see the appendix for how they are affected).

## How this was verified

Every PR head was fetched locally (`refs/pull/*/head`) and the whole plan was **simulated with real 3-way merges** (`git merge-tree --write-tree`), merging each PR into the accumulated result of all previous ones — not just checked pairwise.

| Result | Count |
|---|---|
| `ready to merge` PRs | 58 |
| Merge cleanly into `master` **today**, individually | **58 / 58** |
| Merge cleanly **in the order below, one after another** | **57 / 58** |
| Need a rebase before merging | **1** (#15289) |

Ordering rule applied, in priority order:
1. **Non-`dogi` authors first** (16 PRs) — external contributors don't wait on the maintainer's queue and never have to rebase around it.
2. Zero-file-overlap PRs before overlapping ones.
3. Smallest diff first, then oldest PR number.

---

## Wave 1 — non-dogi, zero overlap · merge in ANY order (9 PRs)

None of these touch a file touched by any other `ready to merge` PR. Safe to merge in parallel / all at once.

| # | PR | Author | Title | Files |
|---|---|---|---|---|
| 1 | [#15222](https://github.com/open-learning-exchange/myplanet/pull/15222) | ragilzakaria | myachievement: center edit achievement button alignment (fixes #15219) | 1 |
| 2 | [#15225](https://github.com/open-learning-exchange/myplanet/pull/15225) | ragilzakaria | mylibrary fix icon size in add resource (fixes #15221) | 1 |
| 3 | [#15247](https://github.com/open-learning-exchange/myplanet/pull/15247) | Okuro3499 | teams: fix MembersAdapter diff and leader refresh (fixes #15246) | 1 |
| 4 | [#15251](https://github.com/open-learning-exchange/myplanet/pull/15251) | Okuro3499 | enterprises: fix reports adapter diff and notify on state change (fixes #15249) | 1 |
| 5 | [#15385](https://github.com/open-learning-exchange/myplanet/pull/15385) | ragilzakaria | prevent logo cropping when switching landscape to portrait (fixes #15262) | 1 |
| 6 | [#15244](https://github.com/open-learning-exchange/myplanet/pull/15244) | ragilzakaria | courses: fix progress filter not filtering courses list (fixes #15242) | 2 |
| 7 | [#15232](https://github.com/open-learning-exchange/myplanet/pull/15232) | dependabot | actions: bump gradle/actions from 6 to 6.2.0 in /.github/workflows | 3 |
| 8 | [#15120](https://github.com/open-learning-exchange/myplanet/pull/15120) | J-S-webskas | fixed the home profile banner | 4 |
| 9 | [#15238](https://github.com/open-learning-exchange/myplanet/pull/15238) | ragilzakaria | login page fully scrollable in landscape (fixes #15235) | 5 |

## Wave 2 — non-dogi, overlapping · merge IN THIS ORDER (7 PRs)

These share files with each other or with later waves. The order below is simulation-verified conflict-free.

| # | PR | Author | Title | Ordering constraint |
|---|---|---|---|---|
| 10 | [#15081](https://github.com/open-learning-exchange/myplanet/pull/15081) | ragilzakaria | settings: standardize capitalization of settings titles and categories (fixes #5488) | `strings.xml` — **before #14963** |
| 11 | [#15245](https://github.com/open-learning-exchange/myplanet/pull/15245) | Okuro3499 | feedback: preserve pending local replies during sync (fixes #15243) | `FeedbackRepositoryImpl.kt` — **before #15289** |
| 12 | [#15387](https://github.com/open-learning-exchange/myplanet/pull/15387) | ragilzakaria | filter window fills screen in landscape mode (fixes #15261) | `ResourcesFragment.kt` — **before #15260** |
| 13 | [#15260](https://github.com/open-learning-exchange/myplanet/pull/15260) | Okuro3499 | resources: reduce delay when leaving (fixes #15255) | after #15387 |
| 14 | [#15224](https://github.com/open-learning-exchange/myplanet/pull/15224) | ragilzakaria | mylife fix hide submenu immediately go back (fixes #15220) | `BaseDashboardFragment.kt`, `MyLifeDao.kt` — **before #14963 / #15287 / #15289** |
| 15 | [#14963](https://github.com/open-learning-exchange/myplanet/pull/14963) | ragilzakaria | dashboard: display clickable placeholder cards for empty dashboard sections (fixes #14950) | after #15081 + #15224; **before #15287** |
| 16 | [#15178](https://github.com/open-learning-exchange/myplanet/pull/15178) | J-S-webskas | fixed 3 buttons still showing in teams even after leader has switched | `TeamsRepositoryImpl.kt` — **before #15271 / #15311 / #15324 / #15289** |

## Wave 3 — dogi, zero overlap · merge in ANY order (26 PRs)

No file collisions with anything else in this plan. Pure throughput — batch them.

| # | PR | Title | Files |
|---|---|---|---|
| 17 | [#15272](https://github.com/open-learning-exchange/myplanet/pull/15272) | Refactor `NetworkUtils` to use `by lazy` for `coreEntryPoint` | 1 |
| 18 | [#15278](https://github.com/open-learning-exchange/myplanet/pull/15278) | Fix shared ViewBinding and unsafe cast in ChatHistoryAdapter | 1 |
| 19 | [#15279](https://github.com/open-learning-exchange/myplanet/pull/15279) | Deduplicate course filter tags without quadratic any checks | 1 |
| 20 | [#15288](https://github.com/open-learning-exchange/myplanet/pull/15288) | Cancel stale username-validation work in BecomeMemberActivity | 1 |
| 21 | [#15293](https://github.com/open-learning-exchange/myplanet/pull/15293) | Refactor DashboardViewModel to remove redundant withContext(io) wrappers | 1 |
| 22 | [#15310](https://github.com/open-learning-exchange/myplanet/pull/15310) | Fix VoicesAdapter full list rebind on parent post removal | 1 |
| 23 | [#15329](https://github.com/open-learning-exchange/myplanet/pull/15329) | ⚡ Optimize dynamic Pattern compilation in ResourceViewerFragment | 1 |
| 24 | [#15338](https://github.com/open-learning-exchange/myplanet/pull/15338) | 🧪 Improve test coverage for RetryRepositoryImpl | 1 |
| 25 | [#15341](https://github.com/open-learning-exchange/myplanet/pull/15341) | 🧪 Add comprehensive unit tests for UploadRepository | 1 |
| 26 | [#15342](https://github.com/open-learning-exchange/myplanet/pull/15342) | ⚡ perf: optimize loop string concatenation in UploadManager | 1 |
| 27 | [#15345](https://github.com/open-learning-exchange/myplanet/pull/15345) | ⚡ Optimize array setters in Achievement model | 1 |
| 28 | [#15352](https://github.com/open-learning-exchange/myplanet/pull/15352) | ⚡ Optimize string concatenations in DialogUtils.kt | 1 |
| 29 | [#15357](https://github.com/open-learning-exchange/myplanet/pull/15357) | 🧪 Add tests for SurveysRepository | 1 |
| 30 | [#15358](https://github.com/open-learning-exchange/myplanet/pull/15358) | 🧪 Add missing tests for TagsRepository | 1 |
| 31 | [#15359](https://github.com/open-learning-exchange/myplanet/pull/15359) | 🧪 Add tests for UploadToShelfService | 1 |
| 32 | [#15360](https://github.com/open-learning-exchange/myplanet/pull/15360) | 🧪 Add missing tests for DownloadRepositoryImpl edge cases | 1 |
| 33 | [#15361](https://github.com/open-learning-exchange/myplanet/pull/15361) | 🧪 Add test suite for ActivitiesRepositoryImpl | 1 |
| 34 | [#15363](https://github.com/open-learning-exchange/myplanet/pull/15363) | 🧪 Add test coverage for NotificationsRepository | 1 |
| 35 | [#15364](https://github.com/open-learning-exchange/myplanet/pull/15364) | 🧪 Add missing unit tests for HealthRepositoryImpl | 1 |
| 36 | [#15365](https://github.com/open-learning-exchange/myplanet/pull/15365) | 🧪 Add VoicePostingPolicy Tests | 1 |
| 37 | [#15381](https://github.com/open-learning-exchange/myplanet/pull/15381) | 🧪 Add missing tests for ProgressRepositoryImpl | 1 |
| 38 | [#15070](https://github.com/open-learning-exchange/myplanet/pull/15070) | 🔒 Fix insecure use of SharedPreferences during migration | 2 |
| 39 | [#15290](https://github.com/open-learning-exchange/myplanet/pull/15290) | Delete AuthUtils.validateUsername wrapper | 2 |
| 40 | [#15308](https://github.com/open-learning-exchange/myplanet/pull/15308) | Refactor: Use WhileSubscribed(5000) for StateFlow SharingStarted | 3 |
| 41 | [#15283](https://github.com/open-learning-exchange/myplanet/pull/15283) | Refactor bare flow collections to use lifecycle-aware helpers in UI Fragments | 5 |
| 42 | [#15323](https://github.com/open-learning-exchange/myplanet/pull/15323) | Shrink ServiceModule constructor-injection bloat | 5 |

## Wave 4 — dogi, overlapping · merge IN THIS ORDER (15 PRs)

Seven small chains, each contending for one file. Within a chain the order matters; the chains are independent of each other.

| # | PR | Title | Chain (contended file) |
|---|---|---|---|
| 43 | [#15309](https://github.com/open-learning-exchange/myplanet/pull/15309) | Refactor ChatViewModel search filtering off main thread | `ChatViewModel.kt` ①/② |
| 44 | [#15335](https://github.com/open-learning-exchange/myplanet/pull/15335) | 🧹 Refactor deeply nested code in observeNetworkForDownloads | `MainApplication.kt` ①/② |
| 45 | [#15350](https://github.com/open-learning-exchange/myplanet/pull/15350) | ⚡ Optimize List.contains in filterCourses loop | `CoursesRepositoryImpl.kt` — before #15289 |
| 46 | [#15292](https://github.com/open-learning-exchange/myplanet/pull/15292) | Refactor ChatHistoryFragment to stop writing Voices data directly | `ChatViewModel.kt` ②/② |
| 47 | [#15313](https://github.com/open-learning-exchange/myplanet/pull/15313) | Refactor ThemeManager to use dependency injection | `MainApplication.kt` ②/② |
| 48 | [#15287](https://github.com/open-learning-exchange/myplanet/pull/15287) | fix: reuse HealthUsersAdapter in dashboard dialogs | `BaseDashboardFragment.kt` — after #15224 + #14963 |
| 49 | [#15367](https://github.com/open-learning-exchange/myplanet/pull/15367) | 🧹 Refactor deep nesting in DownloadService.kt | `DownloadService.kt` ①/③ |
| 50 | [#15377](https://github.com/open-learning-exchange/myplanet/pull/15377) | 🧹 Simplify `canStart` logic in DownloadService | `DownloadService.kt` ②/③ |
| 51 | [#15299](https://github.com/open-learning-exchange/myplanet/pull/15299) | Refactor: Break DownloadUtils back-channel dependency | `DownloadService.kt` ③/③ |
| 52 | [#15349](https://github.com/open-learning-exchange/myplanet/pull/15349) | ⚡ Optimize mergeJsonArray using Set lookups | `UserRepositoryImpl.kt` ①/③ |
| 53 | [#15270](https://github.com/open-learning-exchange/myplanet/pull/15270) | Optimize insertUsersFromSync with linear matching | `UserRepositoryImpl.kt` ②/③ |
| 54 | [#15320](https://github.com/open-learning-exchange/myplanet/pull/15320) | Refactor Achievement synchronization observation to Repository layer | `UserRepositoryImpl.kt` ③/③ |
| 55 | [#15271](https://github.com/open-learning-exchange/myplanet/pull/15271) | Optimize getTeamMemberStatuses in TeamsRepositoryImpl | `TeamsRepositoryImpl.kt` ①/③ (after #15178) |
| 56 | [#15311](https://github.com/open-learning-exchange/myplanet/pull/15311) | refactor: add flowOn to mapping flows in TeamsRepositoryImpl | `TeamsRepositoryImpl.kt` ②/③ |
| 57 | [#15324](https://github.com/open-learning-exchange/myplanet/pull/15324) | Refactor: Move TeamTaskDao and TeamLogDao behind TeamsSyncRepository | `TeamsRepositoryImpl.kt` ③/③ |

## Wave 5 — needs a rebase before it can merge (1 PR)

| PR | Author | Title | Files |
|---|---|---|---|
| [#15289](https://github.com/open-learning-exchange/myplanet/pull/15289) | dogi | Delete dead repository interface surface | 20 |

**#15289 is the only real conflict in the whole set.** It touches 20 repository/DAO files and collides with 11 other `ready to merge` PRs, including two from external contributors:

- **#15224** (ragilzakaria) — `MyLifeDao.kt`, `LifeRepositoryImpl.kt`
- **#15245** (Okuro3499) — `FeedbackRepositoryImpl.kt`

Merging #15289 *first* would be clean, but it would force those two external contributors to rebase. Following the non-dogi-priority rule, **#15289 goes last and dogi rebases it.**

After waves 1–4 land, `git merge master` into #15289 conflicts in exactly three files:

```
app/src/main/java/org/ole/planet/myplanet/data/room/dao/MyLifeDao.kt
app/src/main/java/org/ole/planet/myplanet/repository/FeedbackRepositoryImpl.kt
app/src/main/java/org/ole/planet/myplanet/repository/LifeRepositoryImpl.kt
```

Since #15289 only *deletes* dead interface surface, resolution should be mechanical: keep the incoming (master) implementations, re-apply the deletions on top.

---

## Copy-paste: the full merge order

```
15222 15225 15247 15251 15385 15244 15232 15120 15238
15081 15245 15387 15260 15224 14963 15178
15272 15278 15279 15288 15293 15310 15329 15338 15341 15342 15345 15352 15357
15358 15359 15360 15361 15363 15364 15365 15381 15070 15290 15308 15283 15323
15309 15335 15350 15292 15313 15287 15367 15377 15299 15349 15270 15320 15271 15311 15324
15289   # rebase required
```

Script it (GitHub CLI, squash-merge, stops on the first failure):

```bash
REPO=open-learning-exchange/myplanet
for pr in 15222 15225 15247 15251 15385 15244 15232 15120 15238 \
          15081 15245 15387 15260 15224 14963 15178 \
          15272 15278 15279 15288 15293 15310 15329 15338 15341 15342 15345 15352 15357 \
          15358 15359 15360 15361 15363 15364 15365 15381 15070 15290 15308 15283 15323 \
          15309 15335 15350 15292 15313 15287 15367 15377 15299 15349 15270 15320 15271 15311 15324; do
  echo "=== merging #$pr"
  gh pr merge "$pr" --repo "$REPO" --squash --delete-branch || { echo "STOPPED at #$pr"; break; }
done
# then, after dogi rebases:
# gh pr merge 15289 --repo $REPO --squash --delete-branch
```

> Waves 1 and 3 (35 of the 58) have zero file overlap with anything else — those can go through an auto-merge queue in any order without re-running the analysis.

---

## Appendix — impact on the `ready` PRs (not part of this plan)

63 open PRs carry `ready` (60 dogi, 2 ragilzakaria, 1 J-S-webskas). All 63 merge cleanly into `master` today. **11 of them stop merging cleanly once this plan lands** — worth telling those authors up front, or reviewing them *before* the plan executes.

| PR | Author | Title | Will conflict in | Caused by (plan PRs) |
|---|---|---|---|---|
| [#15158](https://github.com/open-learning-exchange/myplanet/pull/15158) | ragilzakaria | teams: allow deleting calendar events (fixes #15111) | `TeamCalendarFragment.kt` | #15283 |
| [#15274](https://github.com/open-learning-exchange/myplanet/pull/15274) | dogi | Refactor UserRepositoryImpl to use targeted UserDao queries | `UserRepositoryImpl.kt` | #15270, #15289, #15320, #15349 |
| [#15282](https://github.com/open-learning-exchange/myplanet/pull/15282) | dogi | Move sorting logic from adapters to viewmodels | `ResourcesFragment.kt` | #15260, #15387 |
| [#15285](https://github.com/open-learning-exchange/myplanet/pull/15285) | dogi | Remove dead RealtimeSyncManager companion singleton | `ServiceModule.kt`, `RealtimeSyncManager.kt`, `RealtimeSyncMixin.kt` | #15323 |
| [#15294](https://github.com/open-learning-exchange/myplanet/pull/15294) | dogi | Add ViewModels for Leaders, Life, and SendSurvey fragments | `LifeFragment.kt` | #15224 |
| [#15302](https://github.com/open-learning-exchange/myplanet/pull/15302) | dogi | Refactor: Move storage scanning/deletion out of StorageCategory… | `ResourcesRepositoryImpl.kt` | #15260, #15289 |
| [#15303](https://github.com/open-learning-exchange/myplanet/pull/15303) | dogi | Refactor SyncManager server pulls to SyncRepository | `ServiceModule.kt` | #15323 |
| [#15326](https://github.com/open-learning-exchange/myplanet/pull/15326) | dogi | Refactor IO-bound logic to ViewModels | `ResourcesFragment.kt` | #15260, #15387 |
| [#15327](https://github.com/open-learning-exchange/myplanet/pull/15327) | dogi | Refactor RealtimeSyncMixin table updates to Repositories | `TeamsRepositoryImpl.kt` | #15178, #15271, #15289, #15311, #15324 |
| [#15339](https://github.com/open-learning-exchange/myplanet/pull/15339) | dogi | 🧪 Add unit tests for ResourcesRepositoryImpl | `ResourcesRepositoryImplTest.kt` | #15260 |
| [#15356](https://github.com/open-learning-exchange/myplanet/pull/15356) | dogi | ⚡ Optimize date formatting in TeamsRepositoryImpl using DateTimeF… | `TeamsRepositoryImpl.kt` | #15178, #15271, #15289, #15311, #15324 |

**Non-dogi `ready` PRs to prioritise for human review** — approve these and they slot straight into the plan:

| PR | Author | Title | Still clean after the plan? |
|---|---|---|---|
| [#15179](https://github.com/open-learning-exchange/myplanet/pull/15179) | J-S-webskas | fixed remove button function: enabled auto-sync to delete user | ✅ yes |
| [#15169](https://github.com/open-learning-exchange/myplanet/pull/15169) | ragilzakaria | courses leaving rejoining breaks (fixes #15156) | ✅ yes |
| [#15158](https://github.com/open-learning-exchange/myplanet/pull/15158) | ragilzakaria | teams: allow deleting calendar events (fixes #15111) | ⚠️ no — collides with **#15283** on `TeamCalendarFragment.kt`. Approve it in time to merge *before* #15283 (wave 3), or #15158 needs a rebase |

The remaining 52 clean `ready` PRs can be appended to wave 3 / wave 4 as approvals come in, with one caveat: the `TeamsRepositoryImpl.kt`, `UserRepositoryImpl.kt`, `ResourcesFragment.kt` and `DownloadService.kt` chains are already crowded — anything new landing on those files should go through the same chain ordering.

---

## Caveats

- Conflict detection is **textual** (`git merge-tree`, the same engine GitHub uses). A clean merge is not a guarantee that the combined result compiles or that `testDefaultDebugUnitTest` passes — semantic interaction between two PRs touching the same class is still possible, most plausibly in the `UserRepositoryImpl.kt` and `TeamsRepositoryImpl.kt` chains.
- The plan assumes each PR's CI is green and its approvals hold at merge time; squash-merging changes `master`'s SHA on every step, so any PR with a *required up-to-date branch* rule will need a branch update between waves.
- Re-run the analysis if `master` moves for any reason other than executing this plan.
