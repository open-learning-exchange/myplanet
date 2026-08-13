# Repo-boundary round — 10 low-conflict PR tasks

Focus: reinforce repository boundaries, close cross-feature data leaks, move data access out of UI/service one bite at a time.

Constraints for this round:
- ~10 small PRs (review budget ~10/day)
- Prefer 1–4 files of real logic per PR
- No unused scaffolding
- Avoid parallel PRs that edit the **same hot files** (noted per task)
- DiffUtil / ListAdapter already largely done (`DiffUtils.itemCallback`); only call out real leftovers
- Dispatchers already mostly go through `DispatcherProvider`; not a primary theme this round

Legend:
- **Touches** = expected files (keeps merge conflicts predictable)
- **Do not parallel with** = same-file risk

---

## Task 1 — Introduce `DictionaryRepository` (UI still talks to DAO)

**Why:** Only remaining UI→Room DAO leak. `DictionaryActivity` injects `DictionaryDao`, builds entities, inserts, counts, and searches.

**Smell:**
- `ui/dictionary/DictionaryActivity.kt` injects `DictionaryDao`
- Parse/insert/count/find live in the Activity

**Do:**
1. Add `DictionaryRepository` + `DictionaryRepositoryImpl` with narrow API only:
   - `count()`
   - `findByWord(query)`
   - `importFromJsonArray(JsonArray)` (or `replaceAll(entities)` if you keep parse in UI — prefer parse+insert in repo)
2. Bind in `RepositoryModule`
3. Switch `DictionaryActivity` to the repository (drop DAO inject)
4. Keep download/broadcast UI in the Activity; only move persistence

**Touches:**
- new: `repository/DictionaryRepository.kt`, `repository/DictionaryRepositoryImpl.kt`
- `di/RepositoryModule.kt`
- `ui/dictionary/DictionaryActivity.kt`
- optional test: `DictionaryRepositoryImplTest.kt`

**Avoid:** Compose, ViewModel rewrite, download pipeline changes

**Do not parallel with:** nothing critical (greenfield)

---

## Task 2 — Stop `VoicesRepository` proxying User + Library (+ Fragment bypass)

**Why:** Classic cross-feature leak. Voices owns news, but exposes:
- `getUserById` → lazy `UserRepository`
- `getLibraryResource` → `MyLibraryDao` directly

Call chain today: Adapter/Fragment → Voices/TeamsVoices ViewModel → VoicesRepository → wrong domain.

Also: `VoicesFragment` still `@Inject`s `VoicesRepository` and can bypass the ViewModel for some reads — fold those calls into `VoicesViewModel` in the same PR if they are few; otherwise leave a one-line follow-up only if scope balloons.

**Do:**
1. Delete `getUserById` / `getLibraryResource` from `VoicesRepository` + impl
2. Drop `MyLibraryDao` from `VoicesRepositoryImpl` **if** nothing else needs it (keep only if `getPrivateImageUrlsCreatedAfter` still requires it — if so, move that helper to `ResourcesRepository` in a follow-up, not this PR)
3. Inject `UserRepository` + `ResourcesRepository` into:
   - `VoicesViewModel`
   - `TeamsVoicesViewModel`
4. Point existing `getUserById` / `getLibraryResource` ViewModel methods at the correct repos (or delete ViewModel wrappers and call repos at the fragment sites)
5. If cheap: remove direct `VoicesRepository` inject from `VoicesFragment`; route through ViewModel only
6. Update `VoicesRepositoryImplTest` / related tests

**Touches:**
- `repository/VoicesRepository.kt`, `VoicesRepositoryImpl.kt`
- `ui/voices/VoicesViewModel.kt`
- `ui/teams/voices/TeamsVoicesViewModel.kt`
- call sites already using ViewModel helpers (`VoicesFragment`, `ReplyActivity`, `TeamsVoicesFragment`) only if signatures change
- tests under `VoicesRepository*`

**Avoid:** adapter rewrite (Task 4), news CRUD changes

**Do not parallel with:** Task 4 (shared voices UI construction), large VoicesRepository feature work

---

## Task 3 — Remove `TeamsRepository.getAssignee` passthrough

**Why:** Interface method is a pure user lookup:

```kotlin
suspend fun getAssignee(userId: String): UserEntity? =
    userRepository.getUserById(userId)
```

**Do:**
1. Remove `getAssignee` from `TeamsRepository` + impl
2. At call site(s) (`ui/teams/tasks/TeamsTasksFragment.kt` ~line using `teamsRepository.getAssignee`) call `userRepository.getUserById` (already injected via base / nearby)
3. Update `TeamsRepositoryImplTest` if it covers assignee

**Touches:**
- `repository/TeamsRepository.kt`, `TeamsRepositoryImpl.kt`
- `ui/teams/tasks/TeamsTasksFragment.kt` (or Task 8 ViewModel if you sequence after Task 8)
- tests as needed

**Avoid:** task CRUD, TeamsRepository split

**Do not parallel with:** Task 8 if both edit `TeamsTasksFragment` — prefer Task 3 first **or** fold assignee fix into Task 8

---

## Task 4 — `VoicesAdapter` must not depend on `UserRepository`

**Why:** Adapter layer leak. `VoicesAdapter` takes `UserRepository` and calls `userRepository.parseLeadersJson(raw)`.

**Do:**
1. Replace `UserRepository` constructor dep with a pure function / pre-parsed data:
   - e.g. `parseLeaders: (String) -> List<UserEntity>` supplied by fragment/VM, **or**
   - parse once in ViewModel/Fragment and pass `List<UserEntity>`
2. Prefer reusing `UserRepository.parseLeadersJson` **outside** the adapter (VM/Fragment)
3. Update construction in `VoicesFragment`, `ReplyActivity`, `TeamsVoicesFragment`

**Touches:**
- `ui/voices/VoicesAdapter.kt`
- construction sites: `VoicesFragment.kt`, `ReplyActivity.kt`, `TeamsVoicesFragment.kt`

**Avoid:** DiffUtil changes (already on `ListAdapter` + `DiffUtils.itemCallback`), news binding logic refactors

**Do not parallel with:** Task 2 (same construction sites)

---

## Task 5 — `HealthRepositoryImpl`: stop owning `UserDao`

**Why:** Health reaches into user table directly (`userDao.getById/getAll/search/upsert`) while `UserRepository` already owns users. Cross-domain write risk on `saveExamination` (`userDao.upsert`).

**Do:**
1. Replace `UserDao` inject with `UserRepository` (or `Lazy<UserRepository>` if cycles)
2. Map:
   - patient read/search/sort → existing `UserRepository` APIs (`getUserById`, `getAllUsers`, `getUsersSortedBy`, …)
   - if search API is missing, add **one** narrow method on `UserRepository` (`searchUsers(query)`) instead of keeping DAO in Health
3. `saveExamination`: do **not** silently upsert full users from Health unless product-required; if required, call an explicit `UserRepository` update API
4. Keep `HealthExaminationDao` in Health
5. Fix `HealthRepositoryImplTest`

**Touches:**
- `repository/HealthRepositoryImpl.kt` (+ interface only if signatures change)
- possibly `UserRepository.kt` / `UserRepositoryImpl.kt` (one search method)
- `HealthRepositoryImplTest.kt`

**Avoid:** health UI, encryption/upload batch changes

**Do not parallel with:** broad `UserRepositoryImpl` refactors; Task 6 is OK if UserRepository API additive only

---

## Task 6 — `SubmissionsRepositoryImpl`: stop owning `UserDao`

**Why:** Submissions loads users via `userDao.getById` in serialize/export paths. Same boundary issue as Health, different file — good parallel PR.

**Do:**
1. Swap `UserDao` → `UserRepository` (`getUserById`)
2. Leave exam/answer/submission/photo DAOs as-is
3. Update `SubmissionsRepositoryImplTest`

**Touches:**
- `repository/SubmissionsRepositoryImpl.kt`
- test file

**Avoid:** PDF export rewrite, upload config, photo DAO moves

**Do not parallel with:** large Submissions feature PRs; fine beside Task 5

---

## Task 7 — Peel progress/survey delegates off `CoursesRepository`

**Why:** Courses interface re-exports other domains:
- `getCurrentProgress` → `ProgressRepository`
- `isStepCompleted` / `hasUnfinishedSurveys` → `SubmissionsRepository`
- `getCourseProgress(courseId, userId)` / `deleteCourseProgress` sit beside Progress domain

This widens Courses and hides the real owner.

**Do (minimal, method-by-method):**
1. Find call sites of the delegate methods on `CoursesRepository`
2. Point callers at `ProgressRepository` / `SubmissionsRepository` directly
3. Remove wrappers from `CoursesRepository` + impl
4. Keep true course-owned APIs (steps, join/leave, tags, etc.)
5. If `deleteCourseProgress` only clears course-linked progress rows, move implementation into `ProgressRepository` then delete from Courses

**Touches:**
- `repository/CoursesRepository.kt`, `CoursesRepositoryImpl.kt`
- call sites (search before coding — likely course UI / base resource fragment / ViewModels)
- `ProgressRepository*` only if moving `deleteCourseProgress`
- related tests

**Avoid:** full CoursesRepository split, sync bulk insert

**Do not parallel with:** other CoursesRepository behavior PRs

---

## Task 8 — Move Teams task data work from Fragment → `TeamsTasksViewModel`

**Why:** `TeamsTasksViewModel` is deadline-only UI state. `TeamsTasksFragment` still calls `teamsRepository` / `userRepository` for load/create/update/assign/complete/delete and name maps — repository use in UI layer.

**Do:**
1. Inject `TeamsRepository` (+ `UserRepository` if needed) into `TeamsTasksViewModel`
2. Move suspend data operations out of the Fragment into VM methods / StateFlows
3. Fragment keeps dialogs, pickers, adapter wiring, and collects VM state
4. Optionally absorb Task 3 (`getAssignee` → `userRepository`) here if Task 3 not done yet

**Touches:**
- `ui/teams/tasks/TeamsTasksViewModel.kt`
- `ui/teams/tasks/TeamsTasksFragment.kt`
- optional small test for VM

**Avoid:** adapter DiffUtil (already done), calendar/task notification workers

**Do not parallel with:** Task 3 (same fragment)

---

## Task 9 — Close remaining upload DAO holes **in repositories only**

**Why:** `services/upload/UploadConfigs.kt` still injects DAOs while most configs already use repositories:

| DAO still in UploadConfigs | Natural owner |
|----------------------------|---------------|
| `NewsLogDao` | `VoicesRepository` or `ActivitiesRepository` |
| `CourseProgressDao` | `ProgressRepository` |
| `SearchActivityDao` | `ResourcesRepository` (already has `saveSearchActivity`) |
| `ResourceActivityDao` | `ActivitiesRepository` (already writes resource activities) |
| `ApkLogDao` | new tiny `ApkLogRepository` **or** `ActivitiesRepository` |
| `SubmitPhotosDao` | **already** on `SubmissionsRepository` (`getUnuploadedPhotos` / `markPhotoUploaded`) — no new API needed |

**Do (this PR = repository surface only, no UploadConfigs edit):**
1. Add narrow pending/markUploaded APIs on the owning repos (mirror existing patterns like `getPendingCourseActivityUploads`)
2. For photos: confirm Submissions APIs match UploadConfigs needs; adapt names only if required
3. For ApkLog: add `insert` + `getPending` + `markUploaded` on a small repo (MainApplication still on DAO until Task 10)
4. Unit-test the new methods

**Touches (split acceptable if still one PR):**
- `ProgressRepository*` (course progress pending)
- `ActivitiesRepository*` (resource activity pending; optional news log)
- `ResourcesRepository*` (search activity pending)
- `VoicesRepository*` **or** Activities (news log pending)
- optional new `ApkLogRepository*` + `RepositoryModule` bind
- tests only

**Avoid:** editing `UploadConfigs.kt` here (that's Task 10 — prevents dual-edit conflicts)

**Do not parallel with:** Task 2 if both edit VoicesRepository — sequence or put NewsLog on Activities instead

---

## Task 10 — Point `UploadConfigs` (+ ApkLog entry) at repositories

**Why:** Completes Task 9. Service layer should not hold Room DAOs when repositories exist.

**Do:**
1. Replace the six DAO constructor deps in `UploadConfigs` with repository calls
2. `SubmitPhotos` config → `SubmissionsRepository.getUnuploadedPhotos` / `markPhotoUploaded`
3. Remove now-unused DAO imports/params
4. Bonus same PR if small: `CoreDependenciesEntryPoint.apkLogDao()` → `apkLogRepository()` and update `MainApplication` crash/log persistence to repository (keeps DAO out of app entry + workers)
5. Adjust any UploadConfigs-related tests

**Touches:**
- `services/upload/UploadConfigs.kt`
- `di/CoreDependenciesEntryPoint.kt` (if ApkLog moved)
- `MainApplication.kt` (if ApkLog moved)
- tests if present

**Depends on:** Task 9 merged first

**Avoid:** UploadCoordinator redesign, batch size changes, endpoint renames

**Do not parallel with:** any other UploadConfigs / MainApplication DI PR

---

# Suggested merge order (conflict-safe)

```
Wave A (parallel — different files)
  T1 DictionaryRepository
  T5 Health UserDao → UserRepository
  T6 Submissions UserDao → UserRepository
  T7 Courses peel progress/survey delegates

Wave B (parallel after A, or with A if careful)
  T2 Voices stop user/library proxy
  T3 Remove Teams.getAssignee          ┐
  T8 TeamsTasksFragment → ViewModel    ┘ pick ONE order; don't parallel
  T4 VoicesAdapter drop UserRepository (after T2)

Wave C (sequential)
  T9 Add pending-upload APIs on repos
  T10 UploadConfigs + ApkLog entry use repos
```

---

# Explicitly deferred (not this round)

| Item | Reason |
|------|--------|
| Global navigation architecture | Large, cross-cutting |
| Jetpack Compose migration | High churn, not boundary-focused |
| Split `TeamsRepositoryImpl` (~1.4k) | High conflict, not low-hanging |
| Full sync/upload workflow redesign | Task 9–10 is the thin slice |
| DiffUtil migration pass | Almost all list adapters already use `ListAdapter` + `DiffUtils.itemCallback`; leftovers are pagers (`OnboardingAdapter`, `CommunityPagerAdapter`) |
| Hard-coded `Dispatchers.*` hunt | Production already centralized in `DispatcherProvider` |
| Delete vestigial `DatabaseService` | Only DI + tests reference it; fine as a tiny later PR, low boundary value |
| `ResourcesRepository.updateLibraryItem(id, updater: (MyLibrary) -> Unit)` leaky lambda API | Good later tighten; needs careful call-site audit |
| Long-lived `BroadcastReceiver`s (Dashboard/Dictionary) | Lifecycle mostly OK via `collectWhenStarted`; not the highest boundary ROI |

---

# One-line checklist (copy into issue/project board)

- [ ] T1 DictionaryRepository; Activity drops DictionaryDao
- [ ] T2 VoicesRepository drops getUserById + getLibraryResource
- [ ] T3 TeamsRepository drops getAssignee
- [ ] T4 VoicesAdapter drops UserRepository dependency
- [ ] T5 HealthRepositoryImpl uses UserRepository not UserDao
- [ ] T6 SubmissionsRepositoryImpl uses UserRepository not UserDao
- [ ] T7 CoursesRepository drops progress/survey passthroughs
- [ ] T8 TeamsTasks data access moves into TeamsTasksViewModel
- [ ] T9 Repos gain pending-upload APIs for NewsLog/CourseProgress/Search/ResourceActivity/ApkLog
- [ ] T10 UploadConfigs (+ ApkLog entry point) stop injecting those DAOs
