# myPlanet refactor round — generated work orders

- date: 2026-08-27 (UTC)
- base commit: 45bac8d0050286289dbb8fa9680864a16758be5f
- open PRs checked: 37 (#4075, #8175, #10993, #13287, #13355, #13415, #13604, #13657, #13848, #13928, #14427, #14650, #14883, #14893, #14960, #15108, #15158, #15226, #15266, #15267, #15412, #15519, #15559, #15699, #15808, #15820, #15824, #15825, #15951, #16096, #16101, #16192, #16257, #16258, #16270, #16274) — 803 distinct files claimed by those PRs were screened out
- focus evidence mined from: test run 33058620683 (master), release run 33058620684 (latest), build run 33058348877 (latest completed)

Roadmap key used below: 1 data-layer cleanup · 2 global navigation · 3 viewmodel/use-case · 4 DI cleanup · 5 sync/upload consolidation · 6 compose migration · 7 performance hotspots · 8 code health + tests · 9 KMP core · 10 CMP portability (9/10 never scheduled, only moved when inseparable).

---

### 1. silence the deprecated jetifier CI warning with app/libs justification (roadmap 7+8)

context: every CI stage (build, test, release) spams the AGP message `WARNING: The option setting 'android.enableJetifier=true' is deprecated.` — observed in test run 33058620683, release run 33058620684 and build run 33058348877 logs.

Jetifier itself is still required because `app/libs/` ships two legacy AAR bundles (`ChipCloud-3.0.5.aar`, `flexbox-1.0.0.aar`), so the flag must remain — but the warning should be suppressed per AGP's own suggestion.

files: `gradle.properties` — line 25 comment and line 26 `android.enableJetifier=true`.

Neighbors to leave alone: `android.useAndroidX`, `android.nonTransitiveRClass`, `android.nonFinalResIds` and every `PLANET_*` URL/PIN pair below.

steps:

1. Add a new line `android.sync.suppressAgpWarnings=UNSUPPORTED_PROJECT_OPTION_USE` immediately after the `enableJetifier=true` line.
2. Update the adjacent comment so it names the two remaining AARs in `app/libs` (ChipCloud, flexbox) as the justification.
3. Touch no other property in the file.
4. Run a clean assemble and read the log to confirm the warning no longer prints.

acceptance:

- `./gradlew assembleDefaultDebug` green.
- The literal substring `enableJetifier=true is deprecated` no longer appears in that build log.
- User-visible: app still builds and installs; the AAR-backed layouts (chip groups, flexbox) render unchanged on the resources screen.

size budget: ~2 changed lines (plus comment reword), 1 file.

out of scope: no attempt to delete the jetifier flag itself; no handling of the `PLANET_*` secrets; no Gradle version bumps; no AAR migration off Jetifier.

---

### 2. drop per-call Java reflection from the usage-stats permission check (roadmap 7+8)

context: `BasePermissionActivity.getUsagesPermission` resolves `AppOpsManager` methods via `Class.getMethod` + `Method.invoke` on every single call (`app/src/main/java/org/ole/planet/myplanet/base/BasePermissionActivity.kt`, lines 50-67).

Because `AppOpsManager.checkOpNoThrow(String, int, String)` is a public API since API 19 and the app targets minSdk 26, the reflective dance plus the Q-vs-legacy branch buys nothing but allocation and a catch-all exception surface — a verified micro-optimization (roadmap 7) and health fix (roadmap 8).

files: `app/src/main/java/org/ole/planet/myplanet/base/BasePermissionActivity.kt` — `getUsagesPermission(context)` at lines 50-67.

Neighbors to leave alone: `checkPermission`, `checkUsagesPermission`, `requestAllPermissions` and unrelated `Build`/`Process` usage elsewhere in the file.

steps:

1. Replace the `getMethod`/`invoke` block with a direct call `appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)`.
2. Keep the surrounding try/catch → `Log.e` fallback and the `mode == AppOpsManager.MODE_DEFAULT` branch logic unchanged.
3. Delete the `Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q` branch inside this method only.
4. Remove reflection-specific leftovers only if this file needed them solely here.

acceptance:

- `./gradlew assembleDefaultDebug` green.
- `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.base.BasePermissionActivityTest"` green; full `./gradlew testDefaultDebugUnitTest` stays green.
- User-visible: the usage-access prompt still opens the correct Settings screen, and the returned value still matches the system setting.

size budget: ~12 changed lines, 1 file.

out of scope: no migration of any other Activity; no behavior change in what is requested — only how AppOps is queried.

---

### 3. remove duplicated version-name parsing from the release flow (roadmap 7)

context: `.github/workflows/release.yml` parses `versionName` out of `app/build.gradle` twice — once in the `set release version` step (line 40) and again inside `build release APK and AAB` (line 55) with the identical sed pattern.

Both steps run in the same job, so the exported `ANDROID_VERSION` env var is already available; this is exactly the kind of workflow-file duplication worth deleting as a workflow quick win (verified in release run 33058620684).

files: `.github/workflows/release.yml` — `set release version` step (lines 39-40) and `build release APK and AAB` step (lines 54-59).

Neighbors to leave alone: sign, sha256, publish, tag and discord steps; the `-lite` suffix rule at lines 56-58.

steps:

1. In `build release APK and AAB`, replace the inline `VERSION=$(sed -n 's/.*versionName...' app/build.gradle)` with `VERSION=$ANDROID_VERSION`, reusing what `set release version` already exported.
2. Keep the `-lite` suffix logic untouched.
3. Validate the YAML still parses (e.g. `python3 -c "import yaml; yaml.safe_load(open('.github/workflows/release.yml'))"`).
4. Skim the diff to confirm no other step was rewritten.

acceptance:

- The workflow file parses as valid YAML.
- `./gradlew assembleDefaultDebug` remains green locally.
- User-visible: on the next master push, `ANDROID_VERSION_NAME`/`ANDROID_VERSION_CODE` still flow into the sign/publish steps exactly as before.

size budget: ~1 changed line, 1 file.

out of scope: no touching of tag/release-publish logic; no cleanup of the lite-suffix convention; no re-dispatching of any release.

---

### 4. convert the sync manager's silent catches into tagged Log.e (roadmap 5+8)

context: `SyncManager` already imports `android.util.Log` (line 11), yet five exception sites still use naked `err.printStackTrace()` / `e.printStackTrace()` (`app/src/main/java/org/ole/planet/myplanet/services/sync/SyncManager.kt`, lines 218, 383, 406, 417, 553).

Sync-failure evidence never reaches logcat, which undermines roadmap 5's sync/upload consolidation and roadmap 8's log-quality goal.

files: `app/src/main/java/org/ole/planet/myplanet/services/sync/SyncManager.kt`.

Neighbors to leave alone: `RealtimeSyncManager`/`TransactionSyncManager` wiring, `handleException(err.message)` propagation, and the existing `Log.d("SyncPerf", ...)` strings.

steps:

1. Add `private const val TAG = "SyncManager"` once (companion or top-level constant).
2. Convert each of the five printStackTrace sites to `Log.e(TAG, "<phase> failed", e/err)` where `<phase>` matches the local context (sync, progress update, table pull).
3. Preserve the existing failure propagation — no behavior change around catch handling.
4. Keep the existing Log import and the `SyncPerf` debug messages intact.

acceptance:

- `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.services.sync.SyncManagerTest"` green.
- Full `./gradlew testDefaultDebugUnitTest` stays green.
- User-visible: a forced sync failure (e.g. airplane mode mid-sync) produces a tagged logcat line per site instead of a raw stderr dump; sync UI state transitions behave exactly as before.

size budget: ~10 changed lines, 1 file.

out of scope: no restructure of the sync flow; no new TAG wiring into other sync classes; no Timber introduction (dependency-free task).

---

### 5. route login-sync exceptions through tagged logging (roadmap 1+8)

context: `LoginSyncManager` contains nine `e.printStackTrace()` plus two `t.printStackTrace()` (Throwable catch) sites (`app/src/main/java/org/ole/planet/myplanet/services/sync/LoginSyncManager.kt`, lines 47, 55, 95, 103, 112, 117, 138, 153, 158, 161).

Those are precisely the signals the login-sync UX reports back to the user when a server is unreachable — hiding them on stderr hardens nothing (roadmap 1 data-layer cleanup, roadmap 8 log quality).

files: `app/src/main/java/org/ole/planet/myplanet/services/sync/LoginSyncManager.kt`.

Neighbors to leave alone: `UrlUtils.basicAuthHeader`/`getUrl` call structure, `OnSyncListener` callbacks, `AndroidDecrypter` decryption path.

steps:

1. Add `private const val TAG = "LoginSyncManager"` to the class.
2. Add the missing `import android.util.Log`.
3. Replace every `e.printStackTrace()` / `t.printStackTrace()` with `Log.e(TAG, "<operation> failed", e/t)` — operation names should mirror the surrounding action (auth-header build, `_users` find, decryption).
4. Keep the `UnknownHostException`-style error-message mapping intact — only the stderr write moves to logcat.

acceptance:

- `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.services.sync.LoginSyncManagerTest"` green.
- Full `./gradlew testDefaultDebugUnitTest` stays green.
- User-visible: a failed login against an unreachable server still shows the same error toast/dialog; logcat now carries `LoginSyncManager` tagged entries instead of raw stack traces.

size budget: ~22 changed lines, 1 file.

out of scope: no reordering or rewording of user-facing error messages; no refactoring of try/catch scope; no new listener interfaces.

---

### 6. legalize logging across configuration failure branches (roadmap 1+8)

context: `ConfigurationsRepositoryImpl` returns structured failures (`UrlCheckResult.Failure`, `ConfigurationResult.Failure`) but swallows eleven exceptions via `e.printStackTrace()`/`t.printStackTrace()` (`app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationsRepositoryImpl.kt`, lines 77, 87, 116, 156, 243, 263, 282, 310, 313, 339, 342).

Repository diagnostics are opaque, which blocks roadmap 1's data-layer cleanup from being observable without touching return shapes.

files: `app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationsRepositoryImpl.kt`.

Neighbors to leave alone: `RepositoryModule` binding, the `UrlCheckResult`/`ConfigurationResult` sealed types, and the `R.string.device_couldn_t_reach_local_server` lookup.

steps:

1. Add `private const val TAG = "ConfigurationsRepository"` to the impl class.
2. Add the missing `import android.util.Log`.
3. Convert all eleven printStackTrace sites to `Log.e(TAG, "<branch> failed", e/t)`.
4. Keep each `Failure(...)` return exactly as constructed today.

acceptance:

- `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.repository.ConfigurationsRepositoryImplTest"` green.
- Full `./gradlew testDefaultDebugUnitTest` stays green.
- User-visible: a failed URL check during server setup still returns the same `Failure` and the same message; logcat additionally shows the tagged line.

size budget: ~24 changed lines, 1 file.

out of scope: no API-shape change on failures; no logging-infra extraction; no KMP (roadmap 9) attempt — the impl remains android-linked, this move is neutral.

---

### 7. replace secure-store silent catches with tagged logging (roadmap 8)

context: `SecurePrefs` caches the Tink `Aead` and encrypted SharedPreferences in `@Volatile` fields, but eight exceptions (`app/src/main/java/org/ole/planet/myplanet/utils/SecurePrefs.kt`, lines 35, 187, 214, 243, 255, 282, 294, 313) are swallowed by `e.printStackTrace()`.

This includes `AeadConfig.register()` inside `init` (lines 31-37) — a security-relevant initialization failure that must never be silent.

files: `app/src/main/java/org/ole/planet/myplanet/utils/SecurePrefs.kt` (its `android.util.Log` import already exists at line 7).

Neighbors to leave alone: Tink registration, `warmUp`, legacy-file wipe behavior, EncryptedSharedPreferences construction.

steps:

1. Add `private const val TAG = "SecurePrefs"` to the object.
2. Replace the eight printStackTrace sites with `Log.e(TAG, "<store-op> failed", e)` (use `Log.w` on the clear-and-retry branch).
3. Preserve the "clear and retry" recovery path intact.
4. Confirm `SecurePrefsTest` expectations still hold unchanged.

acceptance:

- `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.utils.SecurePrefsTest"` green.
- Full `./gradlew testDefaultDebugUnitTest` stays green.
- User-visible: after an app restart the encrypted store still opens; on init failure the failure is logged with a tag rather than dumped to stderr.

size budget: ~18 changed lines, 1 file.

out of scope: no crypto/key-handling changes; no migration away from `getPrimitive(Class)`; neutral for roadmap 9.

---

### 8. tag file-utils failures instead of writing to stderr (roadmap 8)

context: `FileUtils` swallows nine exceptions through `e.printStackTrace()` (`app/src/main/java/org/ole/planet/myplanet/utils/FileUtils.kt`, lines 63, 75, 98, 122, 144, 156, 182, 308, 392) while its callers toast or return null.

Storage-stat and MIME-mapping failures vanish entirely, complicating roadmap 8 health work on a 396-line object that many features share.

files: `app/src/main/java/org/ole/planet/myplanet/utils/FileUtils.kt`.

Neighbors to leave alone: every public helper signature in `object FileUtils`, FileProvider/FileUploader boundaries, Toast call sites.

steps:

1. Add `private const val TAG = "FileUtils"` inside the object.
2. Add the missing `import android.util.Log`.
3. Convert the nine sites to `Log.e(TAG, "<helper> failed", e)`.
4. Keep null/empty return behavior identical at every site.

acceptance:

- `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.utils.FileUtilsTest"` green.
- Full `./gradlew testDefaultDebugUnitTest` stays green.
- User-visible: helpers still return null when storage or MIME lookups fail; no new toasts, no changed UI — only logcat output moves to a tag.

size budget: ~19 changed lines, 1 file.

out of scope: no helper restructuring; no roadmap-9 attempt (this util already depends on android APIs; the move is neutral).

---

### 9. consolidate camera failure logging onto a single tag (roadmap 8)

context: `CameraUtils` mixes raw `e.printStackTrace()`/`error.printStackTrace()` (`app/src/main/java/org/ole/planet/myplanet/utils/CameraUtils.kt`, lines 116, 142, 163, 186, 199) with hard-coded string tags `Log.e("CameraUtils", ...)` at lines 168 and 191.

Consolidating onto one constant avoids tag drift and makes capture failures searchable (roadmap 8).

files: `app/src/main/java/org/ole/planet/myplanet/utils/CameraUtils.kt` (Log import already present at line 20).

Neighbors to leave alone: CameraX setup, `error.message` propagation, camera-permission rhetoric.

steps:

1. Introduce `private const val TAG = "CameraUtils"` in the class/object.
2. Replace the five printStackTrace sites and the two string-tagged `Log.e("CameraUtils", ...)` calls with `Log.e(TAG, "<camera-op> failed", e/error)`.
3. Leave the capture and permission flow otherwise unchanged.
4. Verify the build compiles without the old string literals.

acceptance:

- `./gradlew assembleDefaultDebug` green (no dedicated CameraUtils test exists).
- Full `./gradlew testDefaultDebugUnitTest` stays green.
- User-visible: camera capture still succeeds/fails identically; all failure logs now carry one searchable TAG.

size budget: ~9 changed lines, 1 file.

out of scope: no camera library changes; no permission-flow edits; no other utils touched.

---

### 10. redirect worker-level download exceptions to tagged logging (roadmap 8)

context: `DownloadWorker` performs background file downloads, yet five exception branches (`app/src/main/java/org/ole/planet/myplanet/services/DownloadWorker.kt`, lines 72, 81, 91, 107, 142) dump to `e.printStackTrace()`.

WorkManager never collects worker stderr, so the retry frontier logic depends on failures that are invisible (roadmap 8).

files: `app/src/main/java/org/ole/planet/myplanet/services/DownloadWorker.kt`.

Neighbors to leave alone: `CoreDependenciesEntryPoint` lookup, `Result.retry()` decisions, `DownloadService` interop.

steps:

1. Add `private const val TAG = "DownloadWorker"` to the class.
2. Add the missing `import android.util.Log`.
3. Convert the five sites to `Log.e(TAG, "download failed", e)`.
4. Keep the retry/Result semantics untouched.

acceptance:

- `./gradlew assembleDefaultDebug` green (no dedicated DownloadWorker test exists).
- Full `./gradlew testDefaultDebugUnitTest` stays green.
- User-visible: a failed background download still schedules its retry the same way; logcat now shows a tagged line per failure instead of nothing.

size budget: ~10 changed lines, 1 file.

out of scope: no retry-policy changes; no WorkManager scheduling changes; no behavior change to download selection.
