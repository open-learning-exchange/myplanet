# Performance quick-win work orders

**Date:** 2026-09-24  
**Base commit:** `14da1ba6bcec7b8affbf047ac78f16c511f5b357`  
**Open PRs checked:** #17435, #17430, #17356, #17254, #17187, #16624, #16623, #16594, #15951, #15825, #15824, #15820, #15808, #15559, #15267, #15266, #15226, #15108, #14883, #14650, #14427, #13928, #13848, #13657, #13604, #13415, #13355, #13287, #10993, #8175, #4075  
**Ranking method:** Ordered by expected suite-runtime savings divided by blast radius: isolated UI tests first, then broader fragment tests, then in-memory Room suites. All listed files were checked against every file reported by the open PRs above; none collide.

### 1. Run the life adapter tests in the shared default Robolectric sandbox (roadmap 7+8)

context: `app/src/test/java/org/ole/planet/myplanet/ui/life/LifeAdapterTest.kt:21-23` pins `LifeAdapterTest` to SDK 34 even though its tests exercise ViewBinding, text, alpha, and drag callbacks rather than an SDK-34 branch. The pin creates a separate Robolectric sandbox instead of reusing the suite default, adding classloading and memory overhead without strengthening the assertions; this improves test performance and code health but does not directly change north stars 9 or 10.

files: Touch only `app/src/test/java/org/ole/planet/myplanet/ui/life/LifeAdapterTest.kt`, specifically the class-level `@Config` on `LifeAdapterTest`; leave `setUp`, `adapterWith`, and every test method unchanged. Leave all production adapters, resources, and Gradle configuration alone.

steps:
1. Remove only the `sdk = [34]` argument from the class-level `@Config`, retaining `application = Application::class` so the test still bypasses the real Hilt application.
2. Keep the `Config` import because the remaining application override still uses it.
3. Run the focused class and confirm all binding, visibility, reorder, and re-entry assertions pass on the default SDK.
4. Run the complete default-debug unit suite to catch shared-sandbox state leakage.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.ui.life.LifeAdapterTest"` and `./gradlew testDefaultDebugUnitTest` pass. User-visible behavior is unchanged: life cards still bind titles and visibility, and drag reordering still invokes its callback exactly once.

size budget: About 1 changed line in 1 file; remain well below 150 lines and 5 files, with no dependency changes.

out of scope: Do not change `LifeAdapter`, reorder semantics, themes, test data, or other Robolectric annotations. Do not combine this with another SDK-pin cleanup.

---

### 2. Run the server address adapter tests in the shared default Robolectric sandbox (roadmap 7+8)

context: `app/src/test/java/org/ole/planet/myplanet/ui/sync/ServerAddressAdapterTest.kt:16-18` pins `ServerAddressAdapterTest` to SDK 34 while the covered behavior is ordinary ViewBinding, button text, and selection payload handling. Reusing the default sandbox avoids an unnecessary SDK-specific test environment; this supports faster code-health feedback and does not directly alter north stars 9 or 10.

files: Touch only `app/src/test/java/org/ole/planet/myplanet/ui/sync/ServerAddressAdapterTest.kt`, specifically the class-level `@Config` on `ServerAddressAdapterTest`; leave `setUp` and all four test methods unchanged. Leave the production server adapter and sync screens alone.

steps:
1. Delete only `sdk = [34]` from the class-level `@Config` and retain the `Application` override.
2. Preserve the existing Material Components theme setup and selection payload coverage.
3. Run the focused class on the default Robolectric SDK and verify holder inflation, label binding, and selected-state assertions.
4. Run the full default-debug unit suite to expose any state interaction in the shared sandbox.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.ui.sync.ServerAddressAdapterTest"` and `./gradlew testDefaultDebugUnitTest` pass. User-visible behavior remains the same: configured server names render correctly and selection, revert, and clear operations remain safe.

size budget: About 1 changed line in 1 file; no new files, dependencies, or production code.

out of scope: Do not redesign server selection, alter adapter payload constants, or change synchronization behavior. Do not remove the test application override.

---

### 3. Run the personals adapter cache test in the shared default Robolectric sandbox (roadmap 7+8)

context: `app/src/test/java/org/ole/planet/myplanet/ui/personals/PersonalsAdapterTest.kt:21-23` forces SDK 34, but its sole test verifies that repeated `onBindViewHolder` calls reuse a formatted date. Dropping that unexplained pin lets this micro-optimization regression test share the default sandbox; it advances performance testing and code health, with no direct north-star 9 or 10 change.

files: Touch only `app/src/test/java/org/ole/planet/myplanet/ui/personals/PersonalsAdapterTest.kt`, specifically the `PersonalsAdapterTest` class annotation; leave `setUp`, `tearDown`, and `onBindViewHolder caches formatted date when binding same item twice` unchanged. Leave production date formatting and adapter caching alone.

steps:
1. Remove the `sdk = [34]` argument while retaining `application = Application::class` in `@Config`.
2. Keep the existing `TimeUtils` mock lifecycle so shared static state is reset after the test.
3. Run the class and confirm two binds still result in exactly one `TimeUtils.getFormattedDate` call.
4. Run the whole default-debug suite to verify the test is isolated in the shared sandbox.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.ui.personals.PersonalsAdapterTest"` and `./gradlew testDefaultDebugUnitTest` pass. User-visible behavior is unchanged: personal cards display the same formatted date, including after RecyclerView rebinds.

size budget: About 1 changed line in 1 file; no more than the existing imports should be touched.

out of scope: Do not modify the date cache, `TimeUtils`, locale handling, or production UI. Do not add timing-based assertions.

---

### 4. Run the voices actions tests in the shared default Robolectric sandbox (roadmap 7+8)

context: `app/src/test/java/org/ole/planet/myplanet/ui/voices/VoicesActionsTest.kt:28-30` pins SDK 34 even though the suite checks dialog binding, repository call counts, and deterministic date presentation. Its explicit locale and time-zone reset already protects shared state, so the pin buys no documented API coverage while creating another sandbox; this improves feedback performance without directly moving north stars 9 or 10.

files: Touch only `app/src/test/java/org/ole/planet/myplanet/ui/voices/VoicesActionsTest.kt`, specifically the class-level configuration for `VoicesActionsTest`; leave `setUp`, `tearDown`, and all `showMemberDetails` tests intact. Leave voices production code and repositories alone.

steps:
1. Remove only `sdk = [34]` from `@Config` and preserve `application = Application::class`.
2. Retain the UTC and US-locale setup plus restoration because the default sandbox is shared.
3. Run the focused class and confirm dialog wiring, aggregate visit-stat calls, and date formatting remain identical.
4. Run the complete default-debug suite to verify locale, time-zone, and application state do not leak.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.ui.voices.VoicesActionsTest"` and `./gradlew testDefaultDebugUnitTest` pass. User-visible behavior remains unchanged: edit/reply dialogs are wired and member details show the same visit count and formatted last-login value.

size budget: About 1 changed line in 1 file, with zero production or dependency changes.

out of scope: Do not alter dialog construction, member-stat queries, displayed strings, locales, or time zones. Do not refactor coroutine tests.

---

### 5. Run the chat adapter clipboard tests in the shared default Robolectric sandbox (roadmap 7+8)

context: `app/src/test/java/org/ole/planet/myplanet/ui/chat/ChatAdapterTest.kt:19-21` creates an SDK-33 sandbox for clipboard tests that assert service caching and copied text, not SDK-33 behavior. Moving the class to the default sandbox reduces one unique SDK environment while preserving a regression test for a production micro-optimization; this does not directly change north stars 9 or 10.

files: Touch only `app/src/test/java/org/ole/planet/myplanet/ui/chat/ChatAdapterTest.kt`, specifically `ChatAdapterTest`'s class-level `@Config`; leave `invokeCopyToClipboard`, `CountingClipboardContext`, and both test methods untouched. Leave the production chat adapter and RecyclerView setup alone.

steps:
1. Remove `sdk = [33]` from the class annotation and retain `application = Application::class`.
2. Keep the stable clipboard-manager wrapper and its lookup counter unchanged.
3. Run the focused class and confirm copied content and the single-service-lookup assertion pass at the default SDK.
4. Run the full default-debug unit suite to detect clipboard state leakage between shared-sandbox tests.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.ui.chat.ChatAdapterTest"` and `./gradlew testDefaultDebugUnitTest` pass. User-visible behavior stays the same: repeated copy actions put the latest message on the clipboard without repeatedly resolving the system service.

size budget: About 1 changed line in 1 file; no new tests, libraries, or implementation changes.

out of scope: Do not alter clipboard behavior, reflection helpers, chat rendering, or RecyclerView logic. Do not broaden this task into adapter refactoring.

---

### 6. Run the resources filter dialog smoke test in the shared default Robolectric sandbox (roadmap 7+8)

context: `app/src/test/java/org/ole/planet/myplanet/ui/resources/ResourcesFilterFragmentTest.kt:14-16` pins SDK 32 for a smoke test that only opens the dialog and finds `iv_close`. The default SDK exercises the same contract and permits sandbox reuse, shortening feedback for incremental UI work; this supports roadmap 8 and future portable-screen work but makes no direct north-star 10 conversion.

files: Touch only `app/src/test/java/org/ole/planet/myplanet/ui/resources/ResourcesFilterFragmentTest.kt`, specifically the `ResourcesFilterFragmentTest` class annotation; leave `test fragment inflates and iv_close is present` unchanged. Leave the fragment, layout resources, and filtering implementation alone.

steps:
1. Remove only the `sdk = [32]` parameter from `@Config`, keeping `application = Application::class`.
2. Preserve the AppCompat host activity and Material Components theme setup.
3. Run the focused test and verify the dialog view and close image remain present under the default SDK.
4. Run the entire default-debug suite to check fragment-manager and theme isolation in the shared sandbox.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.ui.resources.ResourcesFilterFragmentTest"` and `./gradlew testDefaultDebugUnitTest` pass. User-visible behavior is unchanged: the resource filter dialog still opens and exposes its close control.

size budget: About 1 changed line in 1 file; no resource, dependency, or production changes.

out of scope: Do not modify filter behavior, dialog styling, view IDs, or migrate this screen to Compose. Do not remove the custom application setting.

---

### 7. Run the resources adapter tests in the shared default Robolectric sandbox (roadmap 7+8)

context: `app/src/test/java/org/ole/planet/myplanet/ui/resources/ResourcesAdapterTest.kt:23-25` pins SDK 32 while testing RecyclerView payload notifications, identity binding, and null-item filtering. No assertion targets an API-32 branch, so default-sandbox reuse removes avoidable setup from a frequently relevant adapter suite; it improves performance safeguards but does not directly advance north stars 9 or 10.

files: Touch only `app/src/test/java/org/ole/planet/myplanet/ui/resources/ResourcesAdapterTest.kt`, specifically the class-level annotation on `ResourcesAdapterTest`; leave its dispatcher provider, Mockito setup, and test methods unchanged. Leave all production resource classes and build configuration alone.

steps:
1. Remove `sdk = [32]` from `@Config` while retaining the plain `Application` override.
2. Keep existing Mockito and unconfined-dispatcher behavior exactly as-is to isolate the SDK change.
3. Run the class and confirm view-mode and identity payloads, partial binding, and null filtering still pass.
4. Run the complete default-debug unit suite to validate shared-sandbox compatibility.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.ui.resources.ResourcesAdapterTest"` and `./gradlew testDefaultDebugUnitTest` pass. User-visible behavior remains unchanged: resource cards switch layouts, respect guest identity, and display valid entries exactly as before.

size budget: About 1 changed line in 1 file; keep the task below 5 changed lines and add no dependencies.

out of scope: Do not replace Mockito, change dispatchers, alter payload behavior, or touch resource filtering. Do not modify files owned by open resource PRs.

---

### 8. Run the enterprise report fragment tests in the shared default Robolectric sandbox (roadmap 7+8)

context: `app/src/test/java/org/ole/planet/myplanet/ui/enterprises/EnterprisesReportsFragmentTest.kt:25-27` pins SDK 32 for tests of team-name fallback, adapter title binding, and CSV arguments. Those contracts are SDK-independent, making the extra sandbox pure overhead; reusing the default improves test throughput while leaving platform separation north stars 9 and 10 unchanged.

files: Touch only `app/src/test/java/org/ole/planet/myplanet/ui/enterprises/EnterprisesReportsFragmentTest.kt`, specifically the class-level `@Config` for `EnterprisesReportsFragmentTest`; leave `callGetEffectiveTeamName` and all four tests unchanged. Leave enterprise production code, CSV generation, and layouts alone.

steps:
1. Delete `sdk = [32]` from the annotation and retain `application = android.app.Application::class`.
2. Preserve the AppCompat theme, reflection helper, MockK call verification, and coroutine test structure.
3. Run the focused class and confirm argument precedence, fallback naming, title binding, and export assertions pass.
4. Run the full default-debug suite to catch shared classloader or fragment-state problems.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.ui.enterprises.EnterprisesReportsFragmentTest"` and `./gradlew testDefaultDebugUnitTest` pass. User-visible behavior stays the same: enterprise report titles and exported CSV names continue using the effective enterprise name.

size budget: About 1 changed line in 1 file, with no production, resource, or dependency changes.

out of scope: Do not refactor reflection, change report naming, alter CSV content, or touch enterprise ViewModels. Do not add new SDK-specific coverage.

---

### 9. Run the exam DAO tests in the shared default Robolectric sandbox (roadmap 1+7+8, north star 9)

context: `app/src/test/java/org/ole/planet/myplanet/data/room/dao/ExamDaoTest.kt:17-19` pins SDK 32 even though `ExamDaoTest` validates Room queries against an in-memory database and contains no Android-version condition. Removing the pin makes data-layer checks cheaper and keeps query contracts easier to run while the core is separated from Android, directly supporting roadmap 1 and the testing path toward north star 9.

files: Touch only `app/src/test/java/org/ole/planet/myplanet/data/room/dao/ExamDaoTest.kt`, specifically the class-level `@Config` and its now-unused import; leave `setup`, `teardown`, and every DAO query test unchanged. Leave the Room database, entities, and DAO declarations alone.

steps:
1. Remove the class-level `@Config(sdk = [32])` because no application override remains necessary.
2. Remove the now-unused `org.robolectric.annotation.Config` import and keep imports sorted.
3. Run the focused DAO class against the default Robolectric SDK, preserving the real in-memory Room queries.
4. Run the full default-debug suite to ensure database creation and teardown remain isolated in the shared sandbox.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.data.room.dao.ExamDaoTest"` and `./gradlew testDefaultDebugUnitTest` pass. User-visible behavior is unchanged: survey lookup, team ownership, adoptability exclusions, and individual-survey filtering return the same records.

size budget: About 2 changed lines in 1 file; no schema, query, migration, or dependency changes.

out of scope: Do not change SQL, Room entities, database versioning, test fixtures, or `runBlocking`. Do not turn this into a DAO redesign.

---

### 10. Run the course DAO tests in the shared default Robolectric sandbox (roadmap 1+7+8, north star 9)

context: `app/src/test/java/org/ole/planet/myplanet/data/room/dao/CourseDaoTest.kt:19-21` pins SDK 32 although the suite exercises in-memory Room matching, escaping, chunking, and title filtering rather than an Android API level. Letting this comparatively broad DAO class reuse the default sandbox removes redundant initialization and makes repository-core regressions cheaper to detect, moving the data layer and north star 9 forward through faster platform-bound contract tests.

files: Touch only `app/src/test/java/org/ole/planet/myplanet/data/room/dao/CourseDaoTest.kt`, specifically the class-level `@Config` and unused `Config` import; leave `userIdPattern`, lifecycle methods, and all query tests unchanged. Leave database schema, DAO code, models, and repositories alone.

steps:
1. Remove the class-level `@Config(sdk = [32])` annotation.
2. Delete the unused Robolectric `Config` import and preserve alphabetical import ordering.
3. Run the focused class and verify user-pattern escaping, 1,200-ID chunking, deduplication, empty input, cross-column matching, and title filtering.
4. Run the complete default-debug suite to validate in-memory database isolation on the shared default sandbox.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.data.room.dao.CourseDaoTest"` and `./gradlew testDefaultDebugUnitTest` pass. User-visible behavior remains unchanged: course membership and search queries still return complete, deduplicated, correctly escaped results.

size budget: About 2 changed lines in 1 file; remain below 5 files and introduce no new dependency or helper.

out of scope: Do not optimize or rewrite DAO SQL, change the 1,200-row fixture, alter schema configuration, or refactor coroutine style. Do not touch any repository implementation.
