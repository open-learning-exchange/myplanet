# myPlanet — repository-boundaries refactor work orders (10 tasks)

date: 2026-08-27 · base commit: 89fd72c (master, v0.67.58) · open PRs checked: #16274, #16270, #16258, #16257, #16192, #16101, #16096, #15951, #15825, #15824, #15820, #15808, #15699, #15559, #15519, #15267, #15266, #15198, #15158, #15108, #14960, #14893, #14883, #14650, #14427, #13928, #13848, #13657, #13604, #13415, #13355, #13287, #10993, #8175, #4075

Scope note: every open PR's file list was fetched from the GitHub API up front, so
nothing below touches a file an open PR owns. The focus for this round is repository
boundaries — cross-feature leaks pushed across the right interfaces, data logic moved
one function at a time from UI/services into repositories, a couple of DAO cleanups,
and smoother repository↔ViewModel relationships. Every claimed file was opened and
every cited function confirmed to exist before being written here.

### 1. remove two dead DAO queries (roadmap 7+8)
context: `TeamDao.observeByDocType` (app/src/main/java/org/ole/planet/myplanet/data/room/dao/TeamDao.kt:20) and
`UserDao.countByPlanetCode` (app/src/main/java/org/ole/planet/myplanet/data/room/dao/UserDao.kt:19) are never
called anywhere in `app/src/main` — a static scan over the main source set returns zero callers
for both. Dead Room queries still get compiled and validated by KSP on every build, so
removing them shrinks the DAO surface without changing behavior.
files: app/src/main/java/org/ole/planet/myplanet/data/room/dao/TeamDao.kt
(delete `observeByDocType`) and app/src/main/java/org/ole/planet/myplanet/data/room/dao/UserDao.kt
(delete `countByPlanetCode`). Leave the covering imports in place if anything else still
uses `Flow` in TeamDao (audit, then trim).
steps:
1. Delete the `observeByDocType` declaration from `TeamDao` and the `countByPlanetCode`
   declaration from `UserDao`.
2. If `kotlinx.coroutines.flow.Flow` becomes unused in TeamDao after the deletion, drop
   that import too; otherwise keep it.
3. Re-run the KSP build to prove the DAOs still compile.
acceptance: `./gradlew compileDefaultDebugKotlin` and `./gradlew testDefaultDebugUnitTest` stay
green; the app behaves unchanged (queries had no callers).
size budget: ~12 changed lines, 2 files
out of scope: no other DAO methods touched; no repository changes

---

### 2. replace the android LruCache in Achievement with a pure-Kotlin bounded cache (roadmap 9)
context: `Achievement` keeps a companion cache as `android.util.LruCache` (app/src/main/java/org/ole/planet/myplanet/model/Achievement.kt:94),
which pins the whole model to android.util just for a 1000-entry JSON cache. The north-star
zero-android core cannot include a model that imports `android.util.*`; a plain bounded
LinkedHashMap does the same job.
files: app/src/main/java/org/ole/planet/myplanet/model/Achievement.kt only. The cache is
`private` in the companion and only used by `parseStringListToJsonArray` inside the same
file — no other file knows about it, so nothing else is touched.
steps:
1. Replace the `LruCache<String, JsonElement>(1000)` with a private bounded map, e.g. an
   `LinkedHashMap` subclass on a private helper that overrides/remove on insert beyond 1000,
   exposed as `parsedJsonCache`.
2. Remove the `import android.util.LruCache` line.
3. Keep private modifiers identical so the companion API surface is unchanged.
acceptance: `./gradlew testDefaultDebugUnitTest` green; achievement parsing behavior
unchanged (still bounded, still keyed by input string).
size budget: ~10 changed lines, 1 file
out of scope: no cache-clearing API added; no behavior change beyond the implementation swap

---

### 3. move HTML parsing out of NotificationListItem into the adapter (roadmap 9+1)
context: `NotificationListItem.Item.parsedText` is a lazy value calling `Html.fromHtml`
(app/src/main/java/org/ole/planet/myplanet/model/NotificationListItem.kt:13-20), which is the
model's only android.text dependency. The only consumer is the notifications adapter's
bind (app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsAdapter.kt:113),
so the parse can live at bind-time there and leave the model pure Kotlin.
files: app/src/main/java/org/ole/planet/myplanet/model/NotificationListItem.kt (remove the
`android.text.Html` import and the `parsedText` lazy property) and
app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsAdapter.kt.
steps:
1. In NotificationsAdapter's `ItemViewHolder.bind`, replace `binding.title.text = item.parsedText`
   with `binding.title.text = Html.fromHtml(item.notification.formattedText.toString(), Html.FROM_HTML_MODE_LEGACY)`.
2. In the model, delete the `parsedText` fromHtml lazy and the `import android.text.Html` line.
3. Nothing else cites `parsedText`, so no other callers need touching.
acceptance: `./gradlew testDefaultDebugUnitTest` green; notification rows still render
their formatted titles on screen identically.
size budget: ~8 changed lines, 2 files
out of scope: don't touch `Notification` or `NotificationsViewModel`; no compile-terminates in the
model — just decoupling

---

### 4. stop exposing getEnrichedLibraries on ResourcesRepository; make it a private impl helper (roadmap 1)
context: `ResourcesRepository.getEnrichedLibraries` (app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepository.kt:99)
is only ever called inside `ResourcesRepositoryImpl`, by `getResourceListModels`. It being
part of the public interface lets downstream layers depend on an internal join pipeline
that even returns a `LibraryWithMetadata` holding raw `JsonObject` ratings — the interface
shouldn't carry that leak, and privatization is cheap.
files: app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepository.kt
(drop the method) and app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImpl.kt
(changing `override suspend fun getEnrichedLibraries` to a private function; keep
`getResourceListModels` calling it normally).
steps:
1. Delete `suspend fun getEnrichedLibraries(...)` from the `ResourcesRepository` interface.
2. In `ResourcesRepositoryImpl`, rename the method into `private fun getEnrichedLibraries(...)`,
   deleting the `override` keyword.
3. Recompile: only the interface and the impl needed changes — the public caller set was
   empty from a source-tree grep of `getEnrichedLibraries`.
acceptance: `./gradlew testDefaultDebugUnitTest` green; the resources screens still
enrich libraries identically (`getResourceListModels` flows the same data).
size budget: ~6 changed lines, 2 files
out of scope: don't touch `LibraryWithMetadata`'s shape beyond the visibility change; no
other methods are touched

---

### 5. move exam-condition JSON parsing from HealthExaminationAdapter into MyHealth (roadmap 3+1)
context: `HealthExaminationAdapter.showAlert` parses the exam's `conditions` JSON inline
around app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationAdapter.kt:145 —
every new examination row re-runs Gson parsing inside the android.ui layer. `MyHealth`
is the data shape owner; a small helper on the model makes the adapter dumb and keeps
JSON semantics in one place that unit tests can reach.
files: app/src/main/java/org/ole/planet/myplanet/model/MyHealth.kt (add a companion helper,
e.g. `parseEnabledConditions(conditionsJson: String?): String`) and
app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationAdapter.kt.
steps:
1. In the `MyHealth` file, add `companion object` if absent, with a helper that parses the
   conditions JsonObject, filters `asBoolean == true` keys, and returns a comma-joined
   string — mirroring the logic currently in the adapter.
2. In `HealthExaminationAdapter.showAlert`, replace the inline Gson block with a call
   to the helper.
3. Confirm the adapter no longer imports `JsonUtils`/`JsonObject` for that block —
   trim imports accordingly.
acceptance: `./gradlew testDefaultDebugUnitTest` green; health examination alert dialogs
show enabled conditions exactly as before.
size budget: ~25 changed lines, 2 files
out of scope: no change to `MyHealth`'s persisted model fields; no DAO or repository edits

---

### 6. read the current user via UserSessionManager instead of SharedPrefManager (roadmap 1+8)
context: `TeamCoursesFragment.setupCoursesList` reads `sharedPrefManager.getUserId()`
directly (app/src/main/java/org/ole/planet/myplanet/ui/teams/courses/TeamCoursesFragment.kt:46)
to compute `canRemove`. UI fragments shouldn't poke raw preferences for session identity —
`UserSessionManager.getUserModel()` is the session authority and already returns a
`UserEntity`, so this is both a cross-layer leak fix and a behavior normalization.
files: app/src/main/java/org/ole/planet/myplanet/ui/teams/courses/TeamCoursesFragment.kt
only.
steps:
1. Inject `UserSessionManager` in the fragment (field injection, like the existing
   `SharedPrefManager` field) if not already present.
2. Replace `val currentUserId = sharedPrefManager.getUserId().ifEmpty { "--" }` with
   `val currentUserId = userSessionManager.getUserModel()?.id?.takeIf { it.isNotEmpty() } ?: "--"`.
3. Remove the now-unused `SharedPrefManager` import/field injection if it's referenced
   nowhere else in the fragment.
acceptance: `./gradlew testDefaultDebugUnitTest` green; the "can remove" flag still
correctly reflects creator vs current user on a team course list.
size budget: ~12 changed lines, 1 file
out of scope: no adapter or team-page layout changes; no change to the comparison
semantics (still ignoreCase equals with creator)

---

### 7. stop reading SharedPrefManager in LeadersViewModel; move parse into UserSessionManager (roadmap 1+8)
context: `LeadersViewModel` reads `sharedPrefManager.getCommunityLeaders()` and parses the
JSON in `UserEntity.parseLeadersJson` from inside the viewmodel
(app/src/main/java/org/ole/planet/myplanet/ui/community/LeadersViewModel.kt ~line 33-38).
The session manager already has `SharedPrefManager` and the `UserRepository` injected —
moving the read there gets UI layer free of raw preference access and puts one authority
on session/community data.
files: app/src/main/java/org/ole/planet/myplanet/services/UserSessionManager.kt
(add `getCommunityLeaders(): List<UserEntity>` returning parsed list or empty list) and
app/src/main/java/org/ole/planet/myplanet/ui/community/LeadersViewModel.kt (consume it).
steps:
1. In `UserSessionManager`, add `suspend fun getCommunityLeaders(): List<UserEntity>`
   built from `sharedPrefManager.getCommunityLeaders()` + `UserEntity.parseLeadersJson`,
   returning `emptyList()` when the stored string is empty.
2. In `LeadersViewModel`, swap the constructor param `sharedPrefManager` for
   `userSessionManager` (if it's not already injected — check the constructor first) and
   route through the new helper.
3. Drop any now-unused `SharedPrefManager` import in the viewmodel.
acceptance: `./gradlew testDefaultDebugUnitTest` green; community leaders list behaves
identically (including the empty-string → empty branch).
size budget: ~30 changed lines, 2 files
out of scope: no CommunityLeadersAdapter changes; no UserEntity.parseLeadersJson changes
(it stays the model's JSON helper the session manager now uses)

---

### 8. encapsulate data-reset preference clearing inside ConfigurationsRepository.clearAllData (roadmap 1+4)
context: Both `SettingsViewModel` (app/src/main/java/org/ole/planet/myplanet/ui/settings/SettingsViewModel.kt:47)
and SyncActivity already clear preferences separately after invoking
`configurationsRepository.clearAllData()`. The current split means the data-wipe dance
lives in the UI layer and every new caller has to know to do both steps. The repository
already injects `SharedPrefManager`, so it can own both halves of the reset.
files: app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationsRepositoryImpl.kt
(call `sharedPrefManager.clearPreferences()` inside `clearAllData()` after `appDatabase.clearAllTables()`)
and app/src/main/java/org/ole/planet/myplanet/ui/settings/SettingsViewModel.kt
(drop its own `sharedPrefManager.clearPreferences()` call + import if unused elsewhere).
steps:
1. In `ConfigurationsRepositoryImpl.clearAllData`, after the `withContext` returns from
   `appDatabase.clearAllTables()`, invoke `sharedPrefManager.clearPreferences()`.
2. In `SettingsViewModel.clearAllData`, remove the separate prefs call that will now be
   redundant.
3. Verify `SyncActivity` still compiles — its signature-wise call stays the same, only the
   semantics become uniform.
acceptance: `./gradlew testDefaultDebugUnitTest` green; "clear all data" still wipes the
DB and now wipes session/prefs consistently for every caller, not just the settings one.
size budget: ~6 changed lines, 2 files
out of scope: no config-file broadening; SyncActivity itself isn't edited (it invokes the
same repository method, and gets the consolidated behavior for free)

---

### 9. consolidate storage-category extension knowledge inside the storage UI layer (roadmap 8+3)
context: `StorageBreakdownFragment` hard-codes the `categories` definition including each
category's extension set, while `StorageCategoryDetailFragment` re-carries those sets as
Bundle args (`ARG_EXTENSIONS`/`ARG_ALL_KNOWN`) and pushes them directly into the
`StorageCategoryViewModel`'s `getOfflineResourceItems(oleDirPath, extensions, allKnownExtensions)`.
Having extension semantics in two fragments and thread through two `Set<String>`s makes
repo boundary management fuzzy and duplication prone.
files: app/src/main/java/org/ole/planet/myplanet/ui/settings/StorageBreakdownFragment.kt,
app/src/main/java/org/ole/planet/myplanet/ui/settings/StorageCategoryDetailFragment.kt, and
app/src/main/java/org/ole/planet/myplanet/ui/settings/StorageCategoryViewModel.kt
— define one category (`StorageCategory`) data type in the storage package and feed the
ViewModel the category object instead of two raw sets.
steps:
1. Introduce a single source of category metadata (enum-like typed holder or data class)
   owned by the storage UI package — no repository edits.
2. Change `StorageCategoryDetailFragment.newInstance` to receive the category
   identifier, and derive extension sets via the typed holder inside the VM/fragment.
3. Update `StorageCategoryViewModel.loadResources` to accept the typed holder and pass
   the derived sets directly to the repository call.
acceptance: `./gradlew testDefaultDebugUnitTest` green; storage categories still filter
files by the same extension logic and the detail fragment opens identically.
size budget: ~80 changed lines, 3 files
out of scope: no `ResourcesRepository` signature change here — boundary shift is only
inside the storage UI layer to stop the duplication

---

### 10. use the shared formatter in MembersAdapter instead of a per-instance DateTimeFormatter (roadmap 8)
context: `MembersAdapter` builds a reused `DateTimeFormatter` field (app/src/main/java/org/ole/planet/myplanet/ui/teams/members/MembersAdapter.kt:34)
and formats `memberData.lastVisitDate` at bind time (line 113). `TimeUtils.getFormattedDate(Long?)`
already exists at app/src/main/java/org/ole/planet/myplanet/utils/TimeUtils.kt:79 and is
the shared formatter for this kind of timestamp; duplicating a formatter is noise and a
thread-safety hazard on every update.
files: app/src/main/java/org/ole/planet/myplanet/ui/teams/members/MembersAdapter.kt only.
steps:
1. Replace `dateFormatter.format(Instant.ofEpochMilli(memberData.lastVisitDate))` with
   `TimeUtils.getFormattedDate(memberData.lastVisitDate)`.
2. Remove the `dateFormatter` field and the `Instant`/`ZoneId`/`DateTimeFormatter` imports
   that are no longer referenced afterward.
3. Confirm nothing else in the adapter used the removed imports — `MembersAdapter` only
   touched the field for last-visit.
acceptance: `./gradlew testDefaultDebugUnitTest` green; the last-visit row still shows
the same formatted date, and the "no visit" fallback branch still fires.
size budget: ~12 changed lines, 1 file
out of scope: no TimeUtils source edits (it's only consumed); no adapter logic beyond
the formatter swap

---

## self-check
- exactly 10 tasks: yes
- no file in two tasks: file sets are disjoint (TeamDao/UserDao · Achievement ·
  NotificationListItem+NotificationsAdapter · ResourcesRepository+Impl · MyHealth+
  HealthExaminationAdapter · TeamCoursesFragment · UserSessionManager+LeadersViewModel ·
  ConfigurationsRepositoryImpl+SettingsViewModel · storage 3-fragment set · MembersAdapter)
- every cited path opened and confirmed to exist: yes — every file above was opened with
  file_editor/view or grep evidence before being cited
- every task has all 7 template sections: yes
- no task under 15 lines: each task has a title + 7 sections ≥ 15 lines
- no task touches a file from the open-PR list: every open PR's file list was fetched via
  the GitHub API before drafting and respected
- one markdown document in `docs/`: this file
- dedicated branch committed and pushed: `openhands/repository-boundary-task-brief`
