# myPlanet refactor round — faster, leaner unit-test workflow

date: 2026-09-25 · base commit: `354410d` (master, "teams: smoother tasks completing (fixes #17510) (#17535)")
open PRs checked (100): 17679 17674 17673 17672 17671 17670 17668 17667 17666 17665 17664 17663 17662 17661 17659 17655 17654 17653 17652 17648 17647 17644 17643 17638 17634 17633 17632 17631 17630 17629 17622 17621 17620 17619 17618 17616 17613 17612 17610 17609 17604 17603 17602 17596 17595 17594 17593 17592 17589 17587 17586 17580 17579 17578 17577 17576 17575 17571 17570 17569 17565 17564 17563 17562 17561 17560 17551 17550 17545 17544 17543 17542 17541 17540 17539 17538 17537 17435 17254 17187 16623 16594 15951 15825 15824 15820 15808 15559 15267 15266 15226 15108 14883 14650 14427 13928 13848 13657 13604 13415

Every file those PRs touch (diffed against their merge base with master) is left alone. The ones that matter here:
- `.github/workflows/test.yml` and `.github/workflows/build.yml` (#15226)
- `app/build.gradle` (#17622)
- `settings.gradle` and `build.gradle.kts` (#13928)
- `docs/TESTING.md` (#17589, #17602, #17604, #17622)
- about 90 test classes, including the slow `ActivitiesRepositoryImplTest`, `DownloadService*Test`, `PersonalsRepositoryImplTest`, `UserRepositoryImplTest`, `ConfigurationsRepositoryImplTest`, `LoginSyncManagerTest`, `ExamDaoTest`, `CourseDaoTest` and `ResourcesFilterFragmentTest`

So no task edits workflow YAML or Gradle files. Every task works on the test sources, one main-source seam, or the timing script that the test workflow runs.

## Evidence the tasks are built on

- Test run 36030352276 on `354410d` took 4m 13s (shard 1) and 3m 55s (shard 2). "run unit tests" alone took 3m 57s and 3m 22s. `BUILD SUCCESSFUL in 3m 56s` came from a warm cache.
- Summed test time was 578.8s for shard 1 (1262 tests, 147 classes) and 475.3s for shard 2 (1090 tests, 157 classes). Both are over the `--warn-over 400` threshold, so the warning shows on every run.
- The "slowest individual tests" (12–17s each) are mostly the first test of a class that starts a Robolectric sandbox or a Hilt test component. Examples: `EdgeToEdgeUtilsTest.setupEdgeToEdge…` 17.4s, `BaseRecyclerFragmentTest.showNoData…` 14.6s, `TeamsRepositoryBulkInsertTransactionTest…` 12.1s.
- CI stages 9 android-all jars (1.2G). Each extra SDK level a fork touches costs one more sandbox boot.
- So the biggest wins come from four things:
  1. Running pure-JVM tests without Robolectric.
  2. Dropping Hilt test components that inject nothing.
  3. Removing SDK pins that have no reason.
  4. Not reinstalling static mocks before every test.

## Ranking (user impact ÷ blast radius)

| # | Task | Expected CI saving | Files |
|---|------|-------------------|-------|
| 1 | Hoist per-test static mocks in UploadManagerTest + NetworkUtilsMockTest | ~25–35s summed | 2 |
| 2 | Run EdgeToEdgeUtilsTest on plain JUnit | ~15s summed | 1 |
| 3 | Drop Hilt from utils tests that inject nothing; fold NetworkUtilsStateTest | ~10–20s summed, −1 class, −1 duplicate test | 3 |
| 4 | Delete the three tautological DispatcherProvider tests | −3 classes, −7 tests, 2 Hilt components | 3 |
| 5 | Build ThemeManagerTest's activity only where needed | ~6–9s summed | 1 |
| 6 | Unpin SDK 33 and drop the activity in CoursesItemUtilsTest | ~8–10s summed, one fewer SDK-33 sandbox | 1 |
| 7 | Unpin SDK 34 in three adapter tests | fewer SDK-34 sandbox boots | 3 |
| 8 | Run AndroidDecrypterTest and LeadersViewModelTest on plain JUnit | ~3–6s summed | 2 |
| 9 | Extract vital-sign parsing from HealthExaminationActivity | one activity launch fewer, +platform-free code | 4 |
| 10 | Make the timing summary tell real slowness from sandbox warm-up | better signal in every run | 1 |

---

### 1. Hoist per-test static mocks to class scope in UploadManagerTest and NetworkUtilsMockTest (roadmap 8+7)

context: `UploadManagerTest` already runs on plain JUnit (no runner annotation), but it was the slowest class in shard 2 at 29.4s for 24 tests, about 1.2s per test. Its `@Before setup()` (services/UploadManagerTest.kt:83-123) calls `mockkStatic(Log::class)`, `mockkStatic(SystemClock::class)`, `mockkStatic(android.text.TextUtils::class)`, `mockkObject(NetworkUtils)` and `mockkObject(UrlUtils)` before every test. `@After tearDown()` (lines 125-130) then calls `unmockkAll()`, so the bytecode is re-transformed 24 times. `NetworkUtilsMockTest` (11.5s, 14 tests) has the same pattern: `mockkObject(MainApplication.Companion)` and `mockkStatic(EntryPointAccessors::class)` in `setUp()` (utils/NetworkUtilsMockTest.kt:34-54), undone by `unmockkAll()` in `tearDown()` (lines 56-60).

files:
- app/src/test/java/org/ole/planet/myplanet/services/UploadManagerTest.kt: `setup()`, `tearDown()`, and the test `uploadNews derives mimeType from filename and passes to header map` (line 323, which calls `mockkObject(FileUtils)` at line 325 and never unmocks it)
- app/src/test/java/org/ole/planet/myplanet/utils/NetworkUtilsMockTest.kt: `setUp()`, `tearDown()`

Leave alone: `UploadManager.kt`, `NetworkUtils.kt`, and every other test in `services/`. `UploadConfigsTest` and `UploadCoordinatorTest` are owned by #17667.

steps:
1. In each class, add a `companion object` with `@BeforeClass @JvmStatic fun installStaticMocks()`. Move only the `mockkStatic(...)` / `mockkObject(...)` calls into it, keeping their order. Add `@AfterClass @JvmStatic fun removeStaticMocks()` that calls `unmockkAll()`.
2. Keep every `every { ... }` default stub in the per-test `@Before`. Re-stubbing is cheap, and it keeps each test isolated from stubs changed by the test before it.
3. Replace `unmockkAll()` in the per-test `@After` with `clearAllMocks(answers = false, recordedCalls = true, childMocks = false, verificationMarks = true, exclusionRules = false)`. This resets recorded calls and verification marks, but keeps the static mocks installed. Keep the other `@After` lines (`MainApplication.testContext = null`, `NetworkUtils.resetForTesting()`).
4. In `UploadManagerTest`, delete the redundant `io.mockk.unmockkObject(UrlUtils)` in `tearDown()`. In the `uploadNews derives mimeType…` test, wrap the body in `try { … } finally { unmockkObject(FileUtils) }` so `FileUtils` stays mocked only for that one test.
5. Run each class alone 3 times. Then read the class `time=` attribute in `app/build/test-results/testDefaultDebugUnitTest/TEST-*.xml` before and after the change, and report both numbers in the PR description.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.services.UploadManagerTest" --tests "org.ole.planet.myplanet.utils.NetworkUtilsMockTest"` passes 3 runs in a row.
- `./gradlew testDefaultDebugUnitTest` stays green.
- The summed class time in the XML falls by at least 30% for `UploadManagerTest`.
- The app's behaviour does not change, because this touches tests only.

size budget: ~40 changed lines, 2 files

out of scope: don't touch `UploadManager` or `NetworkUtils` production code, and don't change what the tests assert. Leave `BasePermissionActivityTest` and `BaseExamFragmentTest` for a later round.

---

### 2. Run EdgeToEdgeUtilsTest on plain JUnit instead of Robolectric (roadmap 8+7, moves 10)

context: `EdgeToEdgeUtilsTest` held the single slowest test in shard 1: `setupEdgeToEdge should configure window and insets` took 17.4s, and the class took 18.3s. Almost all of that is Robolectric sandbox start-up. The test never uses the Android runtime. `Activity`, `Window`, `View` and `WindowInsetsControllerCompat` are all mockk mocks (utils/EdgeToEdgeUtilsTest.kt:33-45). `WindowCompat` and `ViewCompat` are statically mocked. `EdgeToEdgeUtils` (app/src/main/java/org/ole/planet/myplanet/utils/EdgeToEdgeUtils.kt) only calls those two helpers and the controller setters. `returnDefaultValues = true` is already set in `testOptions`, so stub calls to android.jar return defaults instead of throwing.

files:
- app/src/test/java/org/ole/planet/myplanet/utils/EdgeToEdgeUtilsTest.kt: the class annotations at lines 22-23 and the imports at lines 4 and 18-20

Leave alone: `EdgeToEdgeUtils.kt` itself, and any other test in `utils/`.

steps:
1. Delete `@RunWith(RobolectricTestRunner::class)` and `@Config(application = Application::class)` from the class.
2. Remove the now-unused imports `android.app.Application`, `org.junit.runner.RunWith`, `org.robolectric.RobolectricTestRunner` and `org.robolectric.annotation.Config`.
3. Keep `setup()`, `tearDown()` and both tests exactly as they are.
4. Run the class. If it throws `RuntimeException: Method ... not mocked` or a `Stub!` error, find the Android call that isn't mocked. Add an `every { … }` for it on the existing relaxed mocks rather than bringing back the runner, and name that call in the PR description.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.utils.EdgeToEdgeUtilsTest"` shows 2 tests passed.
- `./gradlew testDefaultDebugUnitTest` stays green.
- In the next CI "summarise slowest tests" summary, `EdgeToEdgeUtilsTest` is gone from both top-15 tables.
- Screens still draw edge-to-edge as before, because this touches tests only.

size budget: ~6 deleted lines, 1 file

out of scope: don't refactor `EdgeToEdgeUtils`, and don't add new test cases.

---

### 3. Drop the unused Hilt test component from three utils tests and fold NetworkUtilsStateTest into NetworkUtilsTest (roadmap 8+4)

context: Three utils test classes run under `HiltTestApplication` with a `HiltAndroidRule` and call `hiltRule.inject()`, but none of them has a single `@Inject` or `@BindValue` field:
- `NetworkUtilsTest` (utils/NetworkUtilsTest.kt:24-35, 22 tests)
- `NetworkUtilsStateTest` (utils/NetworkUtilsStateTest.kt:20-31, 1 test)
- `IntentUtilsTest` (utils/IntentUtilsTest.kt:26-38, 5 tests)

Building a Hilt test component for each test is pure overhead. The code under test doesn't need Hilt either. `NetworkUtils.getDeviceName`, `getUniqueIdentifier`, `startListenNetworkState` and `stopListenNetworkState` only use `MainApplication.context`, `Build` and system services, and `IntentUtils` has no entry-point lookups. Also, `NetworkUtilsTest.testIsWifiEnabled` (lines 41-51) duplicates `NetworkUtilsMockTest`'s `isWifiEnabled returns true/false…` tests (utils/NetworkUtilsMockTest.kt:63-73).

files:
- app/src/test/java/org/ole/planet/myplanet/utils/NetworkUtilsTest.kt: class annotations, `hiltRule`, `init()`, `testIsWifiEnabled`
- app/src/test/java/org/ole/planet/myplanet/utils/NetworkUtilsStateTest.kt: delete the file after moving its test
- app/src/test/java/org/ole/planet/myplanet/utils/IntentUtilsTest.kt: class annotations, `hiltRule`, `init()`

Leave alone: `NetworkUtilsMockTest.kt` (task 1), `MarkdownUtilsTest.kt` (owned by #17550), and `NetworkUtils.kt`.

steps:
1. In `NetworkUtilsTest` and `IntentUtilsTest`, delete `@HiltAndroidTest`, the `hiltRule` field and the `hiltRule.inject()` call. Change `@Config(application = HiltTestApplication::class)` to `@Config(application = Application::class)`, where `Application` is `android.app.Application`. Keep `@LooperMode(LooperMode.Mode.PAUSED)`. If `IntentUtilsTest.init()` is left empty, delete it along with its `@Before` import.
2. Delete `NetworkUtilsTest.testIsWifiEnabled`, because `NetworkUtilsMockTest` already covers both branches.
3. Move `startListenNetworkState_isIdempotent` from `NetworkUtilsStateTest` into `NetworkUtilsTest` unchanged. Add the `@After fun tearDown() { NetworkUtils.resetForTesting() }` it relies on, plus the imports for `ConnectivityManager` and `org.robolectric.Shadows.shadowOf`. Then delete `NetworkUtilsStateTest.kt`.
4. Remove every import the change leaves unused, including the `dagger.hilt.android.testing.*` and `WifiManager` imports. Check with `python3 .agents/skills/kotlin-importing/kotlin-importing.py --check app/src/test` if the submodule is present.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.utils.NetworkUtilsTest" --tests "org.ole.planet.myplanet.utils.IntentUtilsTest"` passes. `NetworkUtilsTest` reports 22 tests: 22 − 1 deleted + 1 moved in.
- `./gradlew testDefaultDebugUnitTest` stays green.
- `grep -rn HiltAndroidTest app/src/test/java/org/ole/planet/myplanet/utils/` lists only `DispatcherProviderDITest.kt` and `DispatcherProviderIntegrationTest.kt` (task 4 removes those) and `MarkdownUtilsTest.kt`.

size budget: ~45 changed lines (mostly deletions), 3 files (1 deleted)

out of scope: don't change any assertion in the tests you keep, and don't touch `NetworkUtils` production code.

---

### 4. Delete the three tautological DispatcherProvider tests (roadmap 8+4)

context: Three test classes check wiring that the compiler and Hilt's graph validation already guarantee:
- `DefaultDispatcherProviderTest` runs under Robolectric (utils/DefaultDispatcherProviderTest.kt:9) and only asserts that `DefaultDispatcherProvider().io == Dispatchers.IO` and so on for its five properties (lines 14-37). Those are one-line getters with no logic to break.
- `DispatcherProviderDITest` (utils/DispatcherProviderDITest.kt:16-38) starts a full Hilt test component to `assertNotNull` an injected field. A missing binding already fails `hiltJavaCompileDefaultDebugUnitTest` at build time.
- `DispatcherProviderIntegrationTest` (utils/DispatcherProviderIntegrationTest.kt:17-50) declares its own test-only `MyService` class and checks that `withContext(io)` runs.

Together they cost 3 Robolectric class setups and 2 Hilt components per run, and they protect nothing. Real dispatcher behaviour is already covered by every ViewModel and repository test that injects `TestDispatcherProvider`.

files:
- app/src/test/java/org/ole/planet/myplanet/utils/DefaultDispatcherProviderTest.kt: delete
- app/src/test/java/org/ole/planet/myplanet/utils/DispatcherProviderDITest.kt: delete
- app/src/test/java/org/ole/planet/myplanet/utils/DispatcherProviderIntegrationTest.kt: delete

Leave alone: `utils/DispatcherProvider.kt`, `di/DispatcherModule.kt`, `utils/TestDispatcherProvider.kt`, `utils/MainDispatcherRule.kt`, and `docs/TESTING.md` (owned by open PRs).

steps:
1. Delete the three files.
2. Run `grep -rn "MyService\|DefaultDispatcherProviderTest\|DispatcherProviderDITest\|DispatcherProviderIntegrationTest" app/src/test`. It must return nothing, because `MyService` was only declared in the deleted integration test.
3. Run the full unit-test task to confirm that no other test depended on those classes.

acceptance:
- `./gradlew testDefaultDebugUnitTest` stays green, with 7 fewer tests in total (5 + 1 + 1).
- `./gradlew assembleDefaultDebug` still succeeds, which proves the DI graph still resolves `DispatcherProvider`.
- The app's behaviour does not change.

size budget: ~130 deleted lines, 3 files deleted

out of scope: don't add replacement tests, and don't edit `DispatcherModule` or the test count in `CLAUDE.md`.

---

### 5. Build ThemeManagerTest's activity only in the dialog test and drop the stray HiltTestApplication (roadmap 8+7)

context: `ThemeManagerTest` took 12.0s in shard 2 for 5 tests. Its `setUp()` (services/ThemeManagerTest.kt:39-47) runs `Robolectric.buildActivity(AppCompatActivity::class.java).setup()` before every test, and `tearDown()` (line 51) walks it through `pause().stop().destroy()`. Only `testShowThemeDialog` uses `activity`. The other four tests only call `ThemeManager.getCurrentThemeMode()` / `setThemeMode()`, which touch the `SharedPrefManager` mock and the static `AppCompatDelegate` mock. The class also declares `application = HiltTestApplication::class` (line 30) with no `HiltAndroidRule` and nothing to inject.

files:
- app/src/test/java/org/ole/planet/myplanet/services/ThemeManagerTest.kt: class `@Config`, fields `activityController` and `activity`, `setUp()`, `tearDown()`, `testShowThemeDialog`

Leave alone: `ThemeManager.kt` and `SharedPrefManager`.

steps:
1. Change `@Config(manifest = Config.NONE, application = HiltTestApplication::class)` to `@Config(manifest = Config.NONE, application = Application::class)`, where `Application` is `android.app.Application`. Remove the Hilt import.
2. Remove the `activityController` and `activity` fields and their lines in `setUp()` and `tearDown()`.
3. Inside `testShowThemeDialog`, build the controller locally with `Robolectric.buildActivity(AppCompatActivity::class.java).setup()`. Pass `controller.get()` to `showThemeDialog`, and in a `finally` block call `controller.pause().stop().destroy()`.
4. Keep `mockkStatic(AppCompatDelegate::class)` and `unmockkAll()` as they are, and remove any imports left unused.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.services.ThemeManagerTest"` shows 5 tests passed.
- `./gradlew testDefaultDebugUnitTest` stays green.
- The class time in its XML result falls, and you report before and after in the PR.
- In the app, Settings → theme dialog still switches between Light, Dark and System.

size budget: ~20 changed lines, 1 file

out of scope: don't change `ThemeManager` and don't add tests.

---

### 6. Unpin SDK 33 in CoursesItemUtilsTest and give it a themed context instead of a full activity (roadmap 8+7)

context: `CoursesItemUtilsTest.testBindCover_cachesExistenceCheckWithinTtlAndRestatsAfterTtl` took 9.7s in shard 1. The class is pinned with `@Config(sdk = [33])` (utils/CoursesItemUtilsTest.kt:24) for no stated reason, and #17602 removes the only other SDK-33 pin (`ChatAdapterTest`). That leaves this class as the only reason a fork boots an SDK-33 sandbox. `setUp()` (lines 34-40) also builds and fully sets up an `AppCompatActivity` only to use it as a `Context`. `CoursesItemUtils.bindCover` only needs `context.resources`, colours and Glide, which a themed application context provides.

files:
- app/src/test/java/org/ole/planet/myplanet/utils/CoursesItemUtilsTest.kt: the class `@Config` (line 24), `setUp()` (lines 34-40), the `activity` field (line 31), and its uses in the single test

Leave alone: `utils/CoursesItemUtils.kt`, `model/MyCourse.kt`, and `ChatAdapterTest.kt` (owned by #17602).

steps:
1. Delete `@Config(sdk = [33])` so the class runs on the default SDK like the rest of the suite.
2. Replace `private lateinit var activity: AppCompatActivity` with `private lateinit var context: Context`. In `setUp()`, set it to `ContextThemeWrapper(ApplicationProvider.getApplicationContext(), com.google.android.material.R.style.Theme_MaterialComponents)`, using `android.view.ContextThemeWrapper`.
3. Replace every `activity` use in the test (`View(activity)`, `ImageView(activity)`, `MyCourse.getCoverImageFile(activity, …)`, `bindCover(context = activity, …)`) with `context`.
4. Remove the imports for `AppCompatActivity`, `Robolectric` and `Config`, and add `android.content.Context`, `android.view.ContextThemeWrapper` and `androidx.test.core.app.ApplicationProvider`.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.utils.CoursesItemUtilsTest"` shows 1 test passed, with all three `verify(exactly = …) { spyFile.exists() }` checks still asserting.
- `./gradlew testDefaultDebugUnitTest` stays green.
- Course covers still show in the courses list, because this touches tests only.

size budget: ~15 changed lines, 1 file

out of scope: don't remove the android-all SDK-33 jar from `app/build.gradle`, which #17622 owns. Don't change the TTL logic.

---

### 7. Unpin Robolectric SDK 34 in LifeAdapterTest, PersonalsAdapterTest and VoicesActionsTest (roadmap 8+7, moves 10)

context: Three UI tests pin `sdk = [34]` in their class `@Config`, with no comment and no SDK-specific assertion:
- ui/life/LifeAdapterTest.kt:22
- ui/personals/PersonalsAdapterTest.kt:22
- ui/voices/VoicesActionsTest.kt:29

The suite defaults to targetSdk 36. Every fork that runs one of these classes pays an extra SDK-34 sandbox boot on top of the SDK-36 one it already has. Open PRs #17589, #17602 and #17604 are removing the same kind of pin from other classes, which confirms these pins are accidental.

files:
- app/src/test/java/org/ole/planet/myplanet/ui/life/LifeAdapterTest.kt: class `@Config` only
- app/src/test/java/org/ole/planet/myplanet/ui/personals/PersonalsAdapterTest.kt: class `@Config` only
- app/src/test/java/org/ole/planet/myplanet/ui/voices/VoicesActionsTest.kt: class `@Config` only

Leave alone: `ServerAddressAdapterTest.kt` (#17544) and the `DownloadService*Test.kt` files (#17596, #17622), which also pin 34. Also leave `NotificationUtilsTest.kt` and `TeamsRepositoryBulkInsertTransactionTest.kt`, whose low-SDK pins are deliberate. The second one's KDoc explains why.

steps:
1. In each of the three files, change `@Config(application = Application::class, sdk = [34])` to `@Config(application = Application::class)`.
2. Run each class. If one fails only on SDK 36, restore the pin in that file alone. Add a one-line comment above it naming the API difference that forces it, and say so in the PR description.
3. Run the full suite.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.ui.life.LifeAdapterTest" --tests "org.ole.planet.myplanet.ui.personals.PersonalsAdapterTest" --tests "org.ole.planet.myplanet.ui.voices.VoicesActionsTest"` passes.
- `./gradlew testDefaultDebugUnitTest` stays green.
- `grep -rn "sdk = \[34\]" app/src/test` no longer lists these three files.

size budget: 3 changed lines, 3 files

out of scope: don't touch any other pinned class, and don't edit `robolectricSdkJars` in `app/build.gradle`.

---

### 8. Run AndroidDecrypterTest and LeadersViewModelTest on plain JUnit (roadmap 8+3, moves 9)

context: Both classes start a Robolectric sandbox but test platform-free code:
- `AndroidDecrypterTest` (utils/AndroidDecrypterTest.kt:13, 10 tests) tests `AndroidDecrypter`. Its only Android import is `android.util.Log` (app/src/main/java/org/ole/planet/myplanet/utils/AndroidDecrypter.kt:3), and `returnDefaultValues = true` makes that a silent no-op on the JVM. Everything else is `java.security` / `javax.crypto` plus the PBKDF2 library.
- `LeadersViewModelTest` (ui/community/LeadersViewModelTest.kt:23-24) tests `LeadersViewModel`. Its imports are only `androidx.lifecycle.ViewModel` / `viewModelScope`, coroutines and repository interfaces (LeadersViewModel.kt:3-13). The test already swaps `Dispatchers.Main` through `MainDispatcherRule` (import at line 18).

Moving these to plain JUnit also shows that these classes need no Android runtime, which is the property the KMP core (roadmap 9) needs.

files:
- app/src/test/java/org/ole/planet/myplanet/utils/AndroidDecrypterTest.kt: line 13 and the imports at lines 10-11
- app/src/test/java/org/ole/planet/myplanet/ui/community/LeadersViewModelTest.kt: lines 23-24 and the imports at lines 14, 19 and 20

Leave alone: `model/UserEntityParseLeadersTest.kt`. It looks similar, but `UserEntity.parseLeadersJson` uses `org.json.JSONObject`, which has no real implementation on the plain JVM test classpath.

steps:
1. In `AndroidDecrypterTest`, delete `@RunWith(RobolectricTestRunner::class)` and its two imports.
2. In `LeadersViewModelTest`, delete `@RunWith(RobolectricTestRunner::class)` and `@Config(application = android.app.Application::class)`, and remove the `RunWith`, `RobolectricTestRunner` and `Config` imports.
3. Run both classes. If the `@Test(expected = StringIndexOutOfBoundsException::class)` case in `AndroidDecrypterTest` (line 82) behaves differently, the change is wrong. Revert that file only and note it in the PR.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.utils.AndroidDecrypterTest" --tests "org.ole.planet.myplanet.ui.community.LeadersViewModelTest"` shows 12 tests passed.
- `./gradlew testDefaultDebugUnitTest` stays green.
- Login password checks and the community leaders screen behave as before, because this touches tests only.

size budget: ~8 deleted lines, 2 files

out of scope: don't modify `AndroidDecrypter` or `LeadersViewModel`, and don't convert other classes.

---

### 9. Extract vital-sign parsing from HealthExaminationActivity into a platform-free function (roadmap 8+3, moves 9)

context: `HealthExaminationActivity.getFloat(trim: String)` (ui/health/HealthExaminationActivity.kt:360-363) is pure string-to-float logic: comma-to-dot, finite check, round to 1 decimal. It is private, so `HealthExaminationActivityTest.getFloat_commaDecimalLocale_keepsDecimalVitals` (ui/health/HealthExaminationActivityTest.kt:67-85) has to launch the whole Hilt `@AndroidEntryPoint` activity under Robolectric and reach the method by reflection (line 73). Moving the logic into a top-level function makes it testable in milliseconds. It also adds one more piece of platform-free domain code for the KMP core.

files:
- app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationActivity.kt: `getFloat` (lines 360-363) and its three call sites (lines 255, 257 and 258)
- new: app/src/main/java/org/ole/planet/myplanet/ui/health/HealthVitals.kt
- new: app/src/test/java/org/ole/planet/myplanet/ui/health/HealthVitalsTest.kt
- app/src/test/java/org/ole/planet/myplanet/ui/health/HealthExaminationActivityTest.kt: delete `getFloat_commaDecimalLocale_keepsDecimalVitals` and the `java.util.Locale` import (line 6)

Leave alone: `HealthExaminationViewModel.kt`, `AddHealthActivity.kt`, and the other `testPreloadCustomDiagnosis_…` test.

steps:
1. Create `HealthVitals.kt` in package `org.ole.planet.myplanet.ui.health`. It holds one `internal fun parseVitalReading(text: String): Float` with exactly the body of `getFloat`, and imports only `kotlin.math.roundToInt`. It must have no `android.*` imports.
2. In `HealthExaminationActivity`, delete `getFloat` and replace its 3 call sites with `parseVitalReading(...)`. `roundToInt` is still used elsewhere in the file, so keep that import.
3. Create `HealthVitalsTest` as a plain JUnit class with no runner. It carries over the six assertions from the deleted activity test: "36.6", "36,6", "72.46"→72.5, "170", "", and "abc". Run it once under `Locale.FRANCE` with a `try/finally` that restores the default locale.
4. Delete the old test method and its now-unused import from `HealthExaminationActivityTest`.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.ui.health.*"` passes.
- `./gradlew testDefaultDebugUnitTest` stays green.
- `grep -n "android\." app/src/main/java/org/ole/planet/myplanet/ui/health/HealthVitals.kt` prints nothing.
- In the app, entering "36,6" for temperature in a health examination still saves 36.6.

size budget: ~45 changed lines, 4 files (2 new)

out of scope: don't move the other vital-sign validation, and don't touch `HealthExamination` model setters.

---

### 10. Make the test timing summary tell real slowness from sandbox warm-up (roadmap 8)

context: The "summarise slowest tests" step in test.yml runs `.github/scripts/test_timing_summary.py`. It ranks classes by raw summed seconds and tests by raw seconds (lines 84-97). So every run's "slowest" tables are dominated by whichever test happened to boot a Robolectric sandbox. `EdgeToEdgeUtilsTest` "took" 17.4s for a verify-only mock test, while the truly slow-per-test classes are buried. `UploadManagerTest` averages about 1.2s over 24 tests. That points agents at the wrong fixes. The JUnit XML the script already parses (lines 55-66) has everything needed to separate the two.

files:
- .github/scripts/test_timing_summary.py: `main()` (the per-file loop at lines 55-66, and the two tables at lines 84-97) plus the module docstring (lines 2-14)

Leave alone: `.github/workflows/test.yml`. #15226 touches it, so the `--warn-over 400` value and the step wiring stay as they are.

steps:
1. While parsing each result file, also collect that class's per-test times alongside the existing `(elapsed, name)` tuple.
2. Add two columns to the "slowest test classes" table: `Tests` (the count) and `Median s/test`. Rows must still be sorted by summed seconds, so existing readers see the same order.
3. Add a third table, `### slowest classes per test (median, ≥3 tests)`. It shows the top `TOP_N` classes ranked by median per-test time, excluding classes with fewer than 3 tests. This is the table that finds real per-test slowness such as repeated static mocking.
4. In the "slowest individual tests" table, add a `Likely warm-up` column. It shows `yes` when a test's time is more than 5× its own class's median and the class has at least 3 tests. That marks sandbox or Hilt boot rather than slow test logic.
5. Update the module docstring to describe the new columns and table. Keep the CLI (`results_dir`, `--shard`, `--warn-over`) unchanged, so test.yml needs no edit.

acceptance:
- `python3 .github/scripts/test_timing_summary.py <dir>` works against a local `app/build/test-results/testDefaultDebugUnitTest` directory produced by `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.utils.*"`. It prints three tables with the new columns and exits 0.
- It still prints `No test result XML found` and exits 0 for an empty directory.
- `python3 -m py_compile .github/scripts/test_timing_summary.py` passes.
- The next CI run's step summary shows the new per-test table on both shards.

size budget: ~50 changed lines, 1 file

out of scope: don't change test.yml, the warn threshold or the exit codes, and add no Python dependencies (stdlib only).
