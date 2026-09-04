# Task work orders — repository-boundary refactor round

date: 2026-09-04 · base commit: `9ff1273` (master, tag v0.69.36) · open PRs checked: 50 open PRs enumerated via GitHub API; every file any of them touches was excluded from task scope. Labelled hot PRs: #16705 [review], #16702 [ready], #16698/#16688/#16686/#16680 [merge], #15699 [ready]. Two open docs PRs (#16716, #16717) are previous rounds of this same exercise; every task they proposed was excluded from this round, and their file `docs/task.md` is avoided.

Round focus: reinforce repository boundaries, call out cross-feature data leaks, move data functions from UI/data/service into repositories one by one, Room/DAO optimizations, smoother repository↔ViewModel relationships. (Compose tasks are impossible this round: the project has zero Compose dependencies and R5 forbids adding any.)

---

### 1. Stop swallowing crypto failures in AndroidDecrypter (roadmap 8+9)

context: `AndroidDecrypter` is the app's PBKDF2/AES utility and is already free of `android.*` imports, but all four failure paths (`decrypt`, `androidDecrypter`, `generateIv`, `generateKey`) catch `Exception`, call `e.printStackTrace()`, and silently return `null`/`false`/`""`. A failed health-record decryption or login key comparison is invisible in logs, and `printStackTrace` output bypasses the app's log pipeline. Serving 9: keeps this file platform-free while giving it real logging.
files: `app/src/main/java/org/ole/planet/myplanet/utils/AndroidDecrypter.kt`; `app/src/test/java/org/ole/planet/myplanet/utils/AndroidDecrypterTest.kt`. Do NOT touch callers (`HealthExaminationViewModel`, `LoginSyncManager`, `UserRepositoryImpl` — some are owned by open PRs).
steps:
1. Add a `private val logger = java.util.logging.Logger.getLogger(AndroidDecrypter::class.java.name)` to the companion.
2. Replace each of the 4 `e.printStackTrace()` calls with `logger.log(Level.WARNING, "<operation> failed", e)`, keeping the existing return contracts (`null` / `false` / `""`) exactly.
3. In `AndroidDecrypterTest`, add tests asserting the contract on bad input: `decrypt` with malformed hex returns null, `androidDecrypter` with a null/invalid `dbPwdKeyValue` returns false, `generateKey`/`generateIv` return non-blank values on the happy path.
4. Run the test class.
5. Confirm `grep -n printStackTrace app/src/main/java/org/ole/planet/myplanet/utils/AndroidDecrypter.kt` returns nothing.
acceptance: `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.utils.AndroidDecrypterTest"` passes; `./gradlew testDefaultDebugUnitTest` stays green; login and health-record viewing behave unchanged (no new crashes, no behavior change beyond log output).
size budget: ~40 changed lines, 2 files.
out of scope: do not change method signatures, do not introduce a Hilt-injected logger, do not touch `SecurePrefs` or `AuthUtils`.

---

### 2. Extract chat-share payload building out of ChatHistoryAdapter (roadmap 1+3, also 9)

context: `ChatHistoryAdapter.showEditTextAndShareButton` (lines ~249–268) builds a CouchDB-shaped `HashMap<String?, String>` payload — serializing `ChatHistory` fields and conversations with `JsonUtils.gson` — inside a RecyclerView adapter. Serialization of a share document is data-layer work sitting in a view class; it is untestable where it is and blocks the model layer from being platform-clean. The downstream consumer (`ChatViewModel.shareChatToVoices`, owned by an open PR) keeps the exact same map shape, so this is a pure extraction.
files: `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatHistoryAdapter.kt` (function `showEditTextAndShareButton`, `serializeConversation`); NEW `app/src/main/java/org/ole/planet/myplanet/model/ChatSharePayload.kt`; NEW `app/src/test/java/org/ole/planet/myplanet/model/ChatSharePayloadTest.kt`. Do NOT touch `ChatViewModel.kt`, `ChatHistoryFragment.kt` (PR-owned neighbors).
steps:
1. Create `model/ChatSharePayload.kt` with a pure function `buildShareMap(chat: ChatHistory, note: String, team: TeamSummary?, section: String, nowMillis: Long): HashMap<String?, String>` that produces byte-for-byte the same keys/values the adapter builds today (`_id`, `_rev`, `title`, `user`, `aiProvider`, `createdDate`, `updatedDate`, `conversations`, plus the outer `message`/`viewInId`/`viewInSection`/`messageType`/`messagePlanetCode`/`chat`/`news` map). Move `serializeConversation` into it as a private helper.
2. In `ChatHistoryAdapter`, replace the inline building with one call to `ChatSharePayload.buildShareMap(...)`, passing `Date().time` from the call site; delete the now-unused private builder code and unused imports (`Date`, `JsonUtils` if unused).
3. Add `ChatSharePayloadTest` covering: null team (empty `viewInId`/`messageType`/`messagePlanetCode`), populated team, null `_id`/`_rev` → empty strings, and a conversation list round-trip.
4. Run both test classes.
5. Assert in the new test that the produced map's key set is exactly `{message, viewInId, viewInSection, messageType, messagePlanetCode, chat, news}` so the schema cannot drift from the old inline shape.
acceptance: `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.model.ChatSharePayloadTest" --tests "org.ole.planet.myplanet.ui.chat.ChatHistoryAdapterTest"` passes; full `testDefaultDebugUnitTest` green; sharing a chat to a team/enterprise from chat history still posts the voice with the same title, conversations and attribution.
size budget: ~110 changed lines (mostly moved code + new test), 3 files.
out of scope: do not change the `onShareChat` lambda type or the map schema; no ViewModel or repository changes.

---

### 3. Remove dead eager-read `fullName` from UserSessionManager (roadmap 8+4)

context: `UserSessionManager` declares `private val fullName: String` and populates it in an `init` block (`fullName = sharedPrefManager.getUserName()` wrapped in a catch that only rethrows). The property is never read anywhere in the file or its callers, so every injection of this class performs a SharedPreferences read whose result is discarded — constructor-time I/O that also makes the class fragile to construct before login. DI hygiene (4): constructors must not do I/O for values nobody uses.
files: `app/src/main/java/org/ole/planet/myplanet/services/UserSessionManager.kt` (lines ~27–36); `app/src/test/java/org/ole/planet/myplanet/services/UserSessionManagerTest.kt` (only if it stubs `getUserName()` for construction — remove/adjust the stub accordingly). Do NOT touch `SharedPrefManager.kt` or any caller.
steps:
1. Delete the `fullName` property and the entire `init` block.
2. Check `UserSessionManagerTest`: if a stub exists solely to satisfy the removed init read, remove it; otherwise leave the test file unchanged.
3. Verify no other reference to `fullName` remains (`grep -rn "fullName" app/src`).
4. Run the test class.
5. Keep the constructor test green with an unstubbed `SharedPrefManager` mock, proving construction no longer reads preferences.
acceptance: `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.services.UserSessionManagerTest"` passes; full `testDefaultDebugUnitTest` green; app starts, login/logout and resource-open logging still record activity (they go through `activitiesRepository`, unchanged).
size budget: ~15 changed lines, 1–2 files.
out of scope: do not merge `onLogin()`/`onLoginAsync()` or restructure the session API; do not touch `SecurePrefs`.

---

### 4. Parallelize independent repository calls in team-tab ViewModels (roadmap 7+3)

context: three team-tab ViewModels issue independent suspend repository calls strictly sequentially, paying one full round-trip per call on screen open. `RequestsViewModel.fetchMembers` (lines ~34–41) awaits `getRequestedMembers`, `getJoinedMemberCount`, `getUserModel` in sequence before `isTeamLeader` (only the last depends on the user). `TeamResourcesViewModel.loadResources` (lines ~29–34) awaits `getTeamResources` then `isTeamLeader`, which are independent. `TeamCoursesViewModel.loadCourses` (lines ~30–36) awaits `getTeamCourseIds` before `getTeamCreator`, which is independent of the IDs. Serving 3: keeps the repository interfaces untouched while making the VM↔repository relationship concurrent where the data allows.
files: `app/src/main/java/org/ole/planet/myplanet/ui/teams/members/RequestsViewModel.kt`; `app/src/main/java/org/ole/planet/myplanet/ui/teams/resources/TeamResourcesViewModel.kt`; `app/src/main/java/org/ole/planet/myplanet/ui/teams/courses/TeamCoursesViewModel.kt`. Do NOT touch `TeamsRepository.kt`, `TeamsMembersRepository.kt`, or any DAO (PR-owned).
steps:
1. In each `load*`/`fetch*` function wrap the independent calls in `coroutineScope { }` with `async {}` per call, keeping every dependency edge (e.g. `isTeamLeader` still awaits the user; `getCoursesByIds` still awaits the IDs).
2. Preserve exact emitted-state semantics: same single `MutableStateFlow` assignment per load, same values, no intermediate emissions.
3. Remove any newly-unused imports; add `kotlinx.coroutines.async`/`coroutineScope` imports.
4. Run the three existing test classes.
5. Re-check each emitted state object is identical in content to the sequential version (same fields, same single emission per load).
acceptance: `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.ui.teams.members.RequestsViewModelTest" --tests "org.ole.planet.myplanet.ui.teams.resources.TeamResourcesViewModelTest" --tests "org.ole.planet.myplanet.ui.teams.courses.TeamCoursesViewModelTest"` passes; full `testDefaultDebugUnitTest` green; members/requests, team resources and team courses tabs render identical data.
size budget: ~45 changed lines, 3 files.
out of scope: no repository/DAO signature changes; do not convert the public `suspend fun` APIs (`addCourses`, `removeCourse`, `getAvailableResources`) to flow-based APIs.

---

### 5. Replace printStackTrace with structured logging in app-entry and sync classes (roadmap 8)

context: `MainApplication` (4 sites: lines ~130 `androidId` getter, ~293 `handleUncaughtException`, ~351 file cleanup, ~429 `initApp`), `ui/sync/SyncActivity` (6 sites, e.g. `authenticateUser` line ~409), and `services/NotificationActionReceiver` (lines 85, 101, 111) all call `e.printStackTrace()`, which writes to stderr with no tag, no level, and no chance of reaching the diagnostics pipeline. Each site already has a meaningful surrounding operation to log against.
files: `app/src/main/java/org/ole/planet/myplanet/MainApplication.kt`; `app/src/main/java/org/ole/planet/myplanet/ui/sync/SyncActivity.kt`; `app/src/main/java/org/ole/planet/myplanet/services/NotificationActionReceiver.kt`. Do NOT touch `MainApplicationTest.kt` assertions on behavior (logging is unobservable there) and do not touch `CrashLogStore.kt`.
steps:
1. In each of the 13 sites replace `e.printStackTrace()` with `Log.e(TAG, "<operation that failed>", e)` (use each class's existing TAG constant where present, otherwise add one).
2. In `MainApplication.handleUncaughtException`, keep the existing `persistCriticalLog` call untouched — only replace the printStackTrace line.
3. Keep every catch clause's control flow (return values, fall-through) identical.
4. Run the full unit test suite.
5. Confirm `grep -rn printStackTrace` on the three files returns nothing.
acceptance: `./gradlew testDefaultDebugUnitTest` green; `./gradlew assembleDefaultDebug` succeeds; failures in these paths now appear under their class tags in logcat instead of raw stderr dumps.
size budget: ~30 changed lines, 3 files.
out of scope: do not route these through `DiagnosticsRepository` or `CrashLogStore`; no behavior or signature changes.

---

### 6. Clean dead code and logging in ServerUrlMapper (roadmap 8+5)

context: `ServerUrlMapper.processUrl` contains an empty `.also { }` block on the `serverMappings[baseUrl]` lookup (lines ~45–49) — dead code left from a removed log line — and `extractBaseUrl` swallows malformed-URL errors with `e.printStackTrace()`. This class is part of the sync/upload URL-failover path (5): `SubmissionsUploader` and `MainApplication` reachability checks depend on it, so silent failures here hide why a device never fails over to a clone server.
files: `app/src/main/java/org/ole/planet/myplanet/services/sync/ServerUrlMapper.kt`; `app/src/test/java/org/ole/planet/myplanet/services/sync/ServerUrlMapperTest.kt`. Do NOT touch `SubmissionsUploader.kt`, `MainApplication.kt` (owned by task 5), or `UrlUtils.kt`.
steps:
1. Delete the empty `.also { }` block so `alternativeUrl` is a plain `serverMappings[baseUrl]` lookup.
2. Replace the `printStackTrace()` in `extractBaseUrl` with `Log.w(TAG, "Could not extract base url from $url", e)`; add a TAG constant.
3. Extend `ServerUrlMapperTest` with cases for: a mapped primary URL returning its clone, an unmapped host returning `alternativeUrl == null`, a URL with a non-default port preserving the port in `extractedBaseUrl`, and a malformed string returning `extractedBaseUrl == null` without throwing.
4. Run the test class.
5. Confirm `processUrl` still returns the original input string as `primaryUrl` for every fixture above.
acceptance: `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.services.sync.ServerUrlMapperTest"` passes; full `testDefaultDebugUnitTest` green; server failover during login/sync behaves unchanged.
size budget: ~50 changed lines, 2 files.
out of scope: do not change `UrlMapping` fields, `updateUrlPreferences`, or the `serverMappings` contents; no BuildConfig changes.

---

### 7. Extract community-service route resolution into a tested pure function (roadmap 2+8)

context: `CommunityServicesFragment.setRecyclerView` (lines ~88–113) parses service link routes inline: `rawRoute.split("/")` then `segments[3]` as a team id, with the http/https check and the WebView fallback interleaved with click-listener code. Route resolution is navigation policy buried in a view — the kind of logic a global navigation layer (2) must own, and today it has zero tests.
files: `app/src/main/java/org/ole/planet/myplanet/ui/community/CommunityServicesFragment.kt` (`setRecyclerView`); NEW `app/src/main/java/org/ole/planet/myplanet/ui/community/CommunityServiceRoute.kt`; NEW `app/src/test/java/org/ole/planet/myplanet/ui/community/CommunityServiceRouteTest.kt`. Do NOT touch `TeamDetailFragment.kt`, `WebViewActivity.kt`, `FragmentNavigator.kt` (neighbors; `TeamDetailFragment` is PR-owned).
steps:
1. Create `CommunityServiceRoute.kt` with a sealed type (`ExternalLink(url)`, `TeamLink(teamId)`, `Unhandled(url)`) and a pure `fun resolve(route: String): CommunityServiceRoute` reproducing today's rules: http/https prefix → ExternalLink; otherwise split on "/" and take segment index 3 when `segments.size >= 4` → TeamLink; else Unhandled (today's WebView fallback).
2. In the fragment's click listener, replace the inline parsing with `when` over `CommunityServiceRoute.resolve(rawRoute)`, keeping the exact same resulting actions (WebView intent, `TeamDetailFragment` navigation with the membership check, WebView fallback).
3. Add `CommunityServiceRouteTest` covering: https URL, http URL, `/teams/view/<id>`-style route, route with fewer than 4 segments, empty string.
4. Run the test class.
5. Confirm the fragment no longer contains a `rawRoute.split("/")` call (`grep -n 'split' CommunityServicesFragment.kt` returns nothing).
acceptance: `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.ui.community.CommunityServiceRouteTest"` passes; full `testDefaultDebugUnitTest` green; community services tab still opens team links in `TeamDetailFragment` and web links in `WebViewActivity`.
size budget: ~90 changed lines (new file + test + fragment edit), 3 files.
out of scope: do not change `teamsRepository.getTeamLinks()`/`isMember` calls or the membership navigation arguments; no new navigation framework.

---

### 8. Key dashboard myLife click routing off stable imageId, not localized titles (roadmap 8+2)

context: `DashboardPluginFragment.handleClickMyLife` (lines ~70–85) routes clicks with `when (title)` against hardcoded English strings ("mySubmissions", "References", "myHealth", …). But the title passed from `getLayout` (line ~117) is `DashboardItem.title`, which comes from `MyLife.defaultItems` — i.e. `getString(R.string.*)` localized text (`model/MyLife.kt` lines ~56–70). Any non-English locale (the app ships ar/es/fr/ne/so) — and even fresh English installs whose seeded titles no longer match the legacy literals — falls into `else → Utilities.toast(R.string.feature_not_available)`, so dashboard shortcuts silently break. The stable key exists: `DashboardItem.imageId` ("ic_myhealth", "my_achievement", …), already used by `imageResourceMap` in the same file.
files: `app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardPluginFragment.kt` (`handleClickMyLife`, `getLayout`); NEW `app/src/test/java/org/ole/planet/myplanet/ui/dashboard/DashboardMyLifeRouteTest.kt`. Do NOT touch `BaseDashboardFragment.kt`, `BellDashboardFragment.kt` (PR-owned callers), `model/MyLife.kt`, or the open `handleClick` signature.
steps:
1. Add an `internal` pure function (companion or top-level in the same file) `myLifeRouteFor(imageId: String?): MyLifeRoute?` mapping each stable id ("ic_submissions", "ic_references", "ic_calendar", "ic_my_survey", "my_achievement", "ic_mypersonals", "ic_myhealth") to a route descriptor; return null for unknown ids.
2. Change `getLayout` to pass `obj.imageId` (not `title`) into `handleClickMyLife`, and rewrite the `when` to key on imageId via `myLifeRouteFor`, preserving today's guest-gating (`openIfLoggedIn`) per route and the `feature_not_available` toast for null routes.
3. Add `DashboardMyLifeRouteTest` asserting every known imageId maps to a route and unknown/blank ids map to null.
4. Run the test class.
5. Confirm the `when` in `handleClickMyLife` no longer references any hardcoded English title literal.
acceptance: `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.ui.dashboard.DashboardMyLifeRouteTest"` passes; full `testDefaultDebugUnitTest` green; with the device set to Spanish or French, tapping myHealth / mySubmissions / calendar shortcuts on the dashboard opens the feature instead of the "feature not available" toast.
size budget: ~70 changed lines, 2 files.
out of scope: do not rename `imageId` values or touch `MyLife.defaultItems`; do not change `getLayout`'s public signature or the badge-count rendering.

---

### 9. Stop rebuilding the achievement id in EditAchievementFragment (roadmap 3+1)

context: the achievement document id `"<userId>@<planetCode>"` is derived in two layers: `AchievementViewModel.loadUserAndAchievement` (line ~57) builds it to load the achievement, and `EditAchievementFragment`'s save click handler (line ~166) rebuilds the same string from `user?.id + "@" + user?.planetCode` to save. The id format is a persistence concern leaking into the view; if it ever changes, the fragment silently writes a different document than the ViewModel reads.
files: `app/src/main/java/org/ole/planet/myplanet/ui/user/AchievementViewModel.kt`; `app/src/main/java/org/ole/planet/myplanet/ui/user/EditAchievementFragment.kt`; `app/src/test/java/org/ole/planet/myplanet/ui/user/AchievementViewModelTest.kt`. Do NOT touch `UserRepository.kt`/`UserRepositoryImpl.kt` (PR-owned), `AchievementFragment.kt`, or `AchievementSaveRequest`'s field list.
steps:
1. In `AchievementViewModel`, expose the id derived during `loadUserAndAchievement` (e.g. a `val achievementId: StateFlow<String?>` set alongside `_achievement`), so the derivation exists exactly once.
2. In `EditAchievementFragment`'s save handler, consume the ViewModel-exposed id instead of rebuilding the string; if the id is unavailable (user not loaded), keep the save button's current guard behavior — no save with a fabricated id.
3. Extend `AchievementViewModelTest` to assert the exposed id matches the one used for `initializeAchievement`.
4. Run both test classes.
5. Confirm `EditAchievementFragment` no longer builds the `id@planetCode` string itself (no `+ "@" +` expression remains in the save handler).
acceptance: `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.ui.user.AchievementViewModelTest" --tests "org.ole.planet.myplanet.ui.user.EditAchievementFragmentTest"` passes; full `testDefaultDebugUnitTest` green; editing and saving an achievement updates the same document that the achievement screen displays.
size budget: ~35 changed lines, 3 files.
out of scope: do not change `UserRepository.updateAchievement` or the `AchievementSaveRequest` schema; no JSON-structure changes.

---

### 10. Replace android.text.TextUtils with Kotlin stdlib checks in five free UI files (roadmap 8, also 9)

context: five UI files import `android.text.TextUtils` for a single emptiness check each, where the Kotlin stdlib idiom (`isNullOrEmpty`) is null-safe, allocation-free and house style — and every removed `android.text` dependency is one less platform coupling on the road to a portable core (9). Sites: `HealthUsersAdapter.kt:55`, `UserArrayAdapter.kt:65`, `SendSurveyFragment.kt:29`, `MyHealthFragment.kt:260`, `EnterprisesReportsFragment.kt:350–370` (6 checks in its form-validation `when`).
files:
- `app/src/main/java/org/ole/planet/myplanet/ui/health/HealthUsersAdapter.kt` (`bindImage`, line 55)
- `app/src/main/java/org/ole/planet/myplanet/ui/user/UserArrayAdapter.kt` (line 65)
- `app/src/main/java/org/ole/planet/myplanet/ui/surveys/SendSurveyFragment.kt` (line 29)
- `app/src/main/java/org/ole/planet/myplanet/ui/health/MyHealthFragment.kt` (`txtDob` assignment, line 260)
- `app/src/main/java/org/ole/planet/myplanet/ui/enterprises/EnterprisesReportsFragment.kt` (validation `when`, lines 350–370)
Do NOT touch any other TextUtils site (the rest are PR-owned), and do not touch `TimeUtils.kt`.
steps:
1. In each listed file replace `TextUtils.isEmpty(x)` with `x.isNullOrEmpty()` (and `!TextUtils.isEmpty(x)` with `!x.isNullOrEmpty()`), preserving surrounding logic exactly.
2. In `EnterprisesReportsFragment` apply the same swap to all 6 checks in the validation `when` (lines ~350–370), keeping the existing string-template arguments unchanged.
3. Remove the now-unused `import android.text.TextUtils` from each of the five files.
4. Run the full unit test suite.
5. Confirm `grep -l "android.text.TextUtils"` over the five files returns nothing.
acceptance: `./gradlew testDefaultDebugUnitTest` green; `./gradlew assembleDefaultDebug` succeeds; health user list, member dropdowns, survey sending, my-health DOB display and enterprise report validation all behave identically (empty fields still rejected, filled fields accepted).
size budget: ~25 changed lines, 5 files.
out of scope: no logic rewrites of the validation `when`; do not convert these files' other Android APIs; no string-resource changes.
