# myPlanet refactor round — 10 work orders

**Generated:** 2026-08-27  
**Focus:** GitHub workflow quick wins · micro-optimizations · obvious inefficiencies  
**Branch checked:** master `@45bac8d` (session branch `copilot/myplanet-refactor`)  
**CI sources:** test run `33058620683`, release run `33058620684`

## Open pull requests (R3) — off-limits files

37 open PRs were listed; file lists were checked for active/ready work and large drafts. **Any file touched by an open PR is off-limits.** Notable collisions avoided:

| PR | Why it blocks |
|----|----------------|
| #16274 | `SyncActivity.kt` |
| #16270 | `.codex/setup.sh`, `AGENTS.md` |
| #16258 | `layout_search_pill.xml`, `values-night/colors.xml` |
| #16257 | Resources UI + library layouts + all `strings.xml` |
| #16192 | `CommunityServicesFragment.kt` |
| #16101 | `EdgeToEdgeUtils.kt`, many activities/layouts, `app/build.gradle` |
| #16096 | Life UI |
| #15951 | Teams UI/repo, `TimeUtils.kt` |
| #15808 | AppDatabase/DAOs/RoomModule/ServiceModule + many repos (incl. Ratings/Surveys/Submissions/Chat/Health) |
| #15699 | ResourceViewer*, WebViewActivity |
| #15226 | **`test.yml`, `build.yml`**, flutter tree, dependabot |
| #13928 | baseline profile → `app/build.gradle`, `settings.gradle`, `libs.versions.toml` |
| #14650 / older | ActivitiesRepositoryImpl and related |

Kotlin warning cleanups from CI (`ActivitiesRepositoryImpl`, `ChatRepositoryImpl`, `RatingsRepositoryImpl`, `SubmissionsRepositoryImpl`, `SurveysRepositoryImpl`, `TeamsRepositoryImpl`) were **dropped** because every cited file is on an open PR.

Workflow paths-ignore on `test.yml`/`build.yml` **dropped** (#15226).

---

## Task 1 — Harden release workflow: no global npm Discord install

**Roadmap:** 8 (code health / CI). Does not advance 9/10.

**Problem (verified):**  
`.github/workflows/release.yml` has no `timeout-minutes` and no `concurrency`. The “send success message to discord” step runs `sudo npm install -g @treehouses/cli` on every successful **default** matrix leg (release log ~6s install waste every master release). A hung Gradle/sign step can hold a runner indefinitely.

**Files (1):**
- `.github/workflows/release.yml`

**Instructions:**
1. Add job-level `timeout-minutes` (suggest 45–60; match build/release wall times seen on master ~3–10 min build + sign/upload).
2. Add workflow/job `concurrency` group for master releases (e.g. `myPlanet-release`, `cancel-in-progress: false`) so overlapping master pushes do not double-publish.
3. Replace `sudo npm install -g @treehouses/cli` + `treehouses feedback ...` with a non-root install that still uses existing `secrets.CHANNEL` the same way treehouses expects (e.g. `npm install` into `$RUNNER_TEMP` / `npm exec --yes`, **no** `sudo`, **no** global prefix on the runner image). Keep the same release URL message text.
4. Do **not** change signing, Play upload, matrix, or version extraction logic.
5. Do **not** add new GitHub secrets or dependencies in the Android app.

**Acceptance:**
- Default matrix leg still posts a Discord message on success without `sudo npm install -g`.
- Lite leg still skips Discord (existing `matrix.build != 'lite'` guard preserved).
- Job has an explicit timeout and concurrency group.
- Diff limited to `release.yml`; under ~150 lines.

**Out of scope:** Migrating away from `@treehouses/cli` to a raw Discord webhook (would need a different secret); fixing third-party Node 20 deprecation warnings on `dogi/*` actions.

---

## Task 2 — Labels workflow: stop full-repo checkout

**Roadmap:** 8. Does not advance 9/10.

**Problem (verified):**  
`.github/workflows/labels.yml` always `actions/checkout@v7` the whole repo, then only runs `.github/scripts/labels.sh`. That script is pure `gh` API (PR files, patches, labels). `GRADLE_FILE=app/build.gradle` is matched as a **path string** against API file paths and uses the API patch blob — it never reads the local tree. Full clone is pure waste on every PR open/push.

**Files (1):**
- `.github/workflows/labels.yml`

**Instructions:**
1. Replace the full checkout with the minimum needed to run the script. Prefer sparse checkout of `.github/scripts` only, **or** fetch/run `labels.sh` from the base ref without a full tree — whichever keeps `labels.sh` executable and path-stable.
2. Keep `pull_request_target` permissions, concurrency, env (`GRADLE_FILE`, `EXCLUDE_PATHS`), and the existing `run: .github/scripts/labels.sh` contract.
3. Do **not** edit `.github/scripts/labels.sh` (leave for other tasks; avoid double-ownership).
4. Do **not** check out PR head code (security: `pull_request_target`).

**Acceptance:**
- Label job still applies size / `less` labels on open + synchronize.
- Workflow no longer clones the full application tree.
- Only `labels.yml` changes.

**Out of scope:** Changing size thresholds or exclude-path rules.

---

## Task 3 — Make RetryInterceptor backoff testable (kill real sleep in CI)

**Roadmap:** 7 (performance), 8 (tests). Soft unlock for 9 (interceptor stays Android-Intent-coupled via `BroadcastService`, but delay becomes pure).

**Problem (verified):**  
`RetryInterceptor.backoff` uses `Thread.sleep` against real wall clock while `TimeProvider` only drives the deadline. `RetryInterceptorTest` sets `initialDelay = 10L` but still sleeps; master shard logs: class **12.3s**, `testSuccessAfterIOException` **10.2s**. Tests already inject `TimeProvider` but use `SystemTimeProvider()`.

**Files (2):**
- `app/src/main/java/org/ole/planet/myplanet/data/api/RetryInterceptor.kt`
- `app/src/test/java/org/ole/planet/myplanet/data/api/RetryInterceptorTest.kt`

**Instructions:**
1. Introduce a small injectable wait abstraction **inside** these files or as a constructor parameter already satisfiable by Hilt without new modules if possible (e.g. optional `BackoffSleeper` defaulting to `Thread.sleep` slices, or advance-only via controllable `TimeProvider` + zero-duration sleep hook). **No new Gradle dependencies.**
2. Production behavior: still slice waits (respect `MAX_BACKOFF_SLICE_MS`), still abort when `chain.call().isCanceled()`, still honor `InterruptedException` → `IOException`.
3. Tests: use a fake that does **not** sleep wall-clock seconds; keep all existing retry-count / non-retry POST semantics assertions.
4. Do not change maxRetries, factor, or document-creating POST skip rules.

**Acceptance:**
- `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.data.api.RetryInterceptorTest"` finishes in well under 2s wall for the class (no multi-second sleeps).
- Existing test cases still pass with same proceed/broadcast verify counts.
- ≤~150 LOC across the two files.

**Out of scope:** Replacing `Intent` / `BroadcastService` (larger DI/KMP task); editing `NetworkModule`.

---

## Task 4 — ServerReachabilityProvider: use TimeProvider for cache TTL

**Roadmap:** 4 (DI cleanup), 7 (correct caching). Helps **9** (stop raw `System.currentTimeMillis` in a injectable service used by sync).

**Problem (verified):**  
`ServerReachabilityProvider` already injects `OkHttpClient`, `ServerUrlMapper`, `DispatcherProvider`, but cache TTL uses `System.currentTimeMillis()` twice (read + write). Project convention is `TimeProvider` (`TimeModule` already provides `SystemTimeProvider`). No unit test exists for this provider. Note: `MainApplication.Companion.isServerReachable` is a **separate** HttpURLConnection cache — do **not** merge in this task (call-site blast radius / open PRs).

**Files (≤2):**
- `app/src/main/java/org/ole/planet/myplanet/utils/ServerReachabilityProvider.kt`
- `app/src/test/java/org/ole/planet/myplanet/utils/ServerReachabilityProviderTest.kt` (**create**)

**Instructions:**
1. Add `TimeProvider` to the `@Inject constructor`; replace both `System.currentTimeMillis()` call sites with `timeProvider.now()`.
2. Keep TTL `30_000L`, `ConcurrentHashMap` cache, HEAD requests, alternative URL try order, `CancellationException` rethrow.
3. Add a focused unit test with mocked `OkHttpClient`/`Call`/`TimeProvider` proving: cache hit within TTL skips network; after TTL expiry a new call is made. Prefer pure MockK (Robolectric only if unavoidable).
4. Do not edit `MainApplication`, `ServerReachabilityWorker` (open PR #15808), or `ServerUrlMapper`.

**Acceptance:**
- Production compile via Hilt (TimeProvider already bound).
- New test covers TTL hit/miss.
- No `System.currentTimeMillis` left in this class.

**Out of scope:** Deduplicating companion reachability in `MainApplication`.

---

## Task 5 — RealtimeSyncManager: stop dropping burst table updates

**Roadmap:** 5 (sync/upload consolidate), 7 (hotspot). Soft **9** (pure Kotlin already; behavior fix only).

**Problem (verified):**  
`RealtimeSyncManager` uses `MutableSharedFlow<TableDataUpdate>(extraBufferCapacity = 1)` and `tryEmit`. Sync can notify many tables in a burst; capacity 1 + no collectors / slow collectors silently drops updates. UI helpers collect this flow for live refresh. Tests only cover single-emit happy paths.

**Files (2):**
- `app/src/main/java/org/ole/planet/myplanet/services/sync/RealtimeSyncManager.kt`
- `app/src/test/java/org/ole/planet/myplanet/services/sync/RealtimeSyncManagerTest.kt`

**Instructions:**
1. Raise `extraBufferCapacity` to a burst-safe size (align with `BroadcastService`’s `extraBufferCapacity = 64`, or similar constant documented in-file).
2. Prefer `tryEmit` kept (non-suspending notify from sync threads) unless you switch overflow policy explicitly; if using `BufferOverflow`, document choice (DROP_OLDEST vs SUSPEND — **do not** block sync threads with SUSPEND without justification).
3. Extend tests: emit N>previous-capacity updates with a slow/unconfined collector and assert none (or the intended overflow policy) are lost for the chosen capacity.
4. Do not change `TableDataUpdate` or UI collectors.

**Acceptance:**
- Existing three tests still pass.
- New test demonstrates multi-table burst delivery under the new capacity.
- Still `@Singleton` + `tryEmit` from non-coroutine callers works.

**Out of scope:** Wiring more tables to notify; changing `RealtimeSyncHelper`.

---

## Task 6 — Sha256Utils: remove android.util.Log (KMP-ready checksum)

**Roadmap:** 1 (data-layer cleanliness), **9** (zero `android.*` in pure crypto util).

**Problem (verified):**  
`Sha256Utils.getCheckSumFromFile` is pure `MessageDigest` SHA-512 over a `File`, except failure path calls `Log.w`. That is the **only** Android import. Tests live in `Sha256UtilsTest.kt`.

**Files (2):**
- `app/src/main/java/org/ole/planet/myplanet/utils/Sha256Utils.kt`
- `app/src/test/java/org/ole/planet/myplanet/utils/Sha256UtilsTest.kt`

**Instructions:**
1. Remove `import android.util.Log` and the Log call; on failure still return `""` (preserve API). Optional: swallow quietly or rethrow only if tests require — keep return contract.
2. Confirm no other `android.*` remains.
3. Update/add a test that forces failure (missing file / unreadable) and expects `""` without requiring Log assertions.
4. Do not rename the class or change hash algorithm (SHA-512 despite the class name — preserve behavior).

**Acceptance:**
- `Sha256Utils.kt` has zero `android.*` imports.
- Existing checksum tests still pass.
- No new dependencies.

**Out of scope:** Renaming to Sha512Utils; multiplatform `expect`/`actual` File.

---

## Task 7 — RetryRepositoryImpl: remove android.util.Log

**Roadmap:** 1, **9** (repository free of Android logging).

**Problem (verified):**  
`RetryRepositoryImpl` only Android import is `android.util.Log`; used when clear is refused during active processing and on successful clear. Domain retry queue logic is otherwise pure Room + `TimeProvider`.

**Files (1):**
- `app/src/main/java/org/ole/planet/myplanet/repository/RetryRepositoryImpl.kt`

**Instructions:**
1. Remove all `Log.*` calls and the `android.util.Log` import.
2. Preserve control flow (`AtomicBoolean`, `Mutex`, status transitions, return values).
3. Do not edit `RetryQueue.kt`, DAOs, or `RetryRepositoryImplTest.kt` unless a test asserts on Log (it should not).
4. Do not introduce a logging facade dependency.

**Acceptance:**
- File has zero `android.*` imports.
- `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.repository.RetryRepositoryImplTest"` passes.
- Behavior of enqueue/update/clear unchanged.

**Out of scope:** `RetryQueue.kt` Log cleanup (separate file ownership).

---

## Task 8 — UrlUtils: replace android.util.Base64 and drop Log

**Roadmap:** 1, **9** (auth header helper portable).

**Problem (verified):**  
`UrlUtils.basicAuthHeader` uses `android.util.Base64.encodeToString(..., NO_WRAP)`. Failure parsing alternative URL uses `android.util.Log.w`. Both are the only Android imports (`androidx.core.net.toUri` is AndroidX — replace alternative URL parse with `java.net.URI` if needed so the file can shed Android imports, **or** at minimum remove `android.util.*` and leave `toUri` if removing androidx needs more call-site care). Prefer: `java.util.Base64.getEncoder().withoutPadding()` **only if** output matches existing header format; Android `NO_WRAP` still includes padding — use `java.util.Base64.getEncoder()` (with padding, no line wraps) to match.

**Files (2):**
- `app/src/main/java/org/ole/planet/myplanet/utils/UrlUtils.kt`
- `app/src/test/java/org/ole/planet/myplanet/utils/UrlUtilsTest.kt`

**Instructions:**
1. Replace `android.util.Base64` with `java.util.Base64` ensuring `basicAuthHeader` output stays identical for fixture username/password pairs (add/adjust unit assertions).
2. Remove `Log.w` on alternative URL parse failure; keep fallback to host/scheme.
3. If `toUri()` is the last Android dependency, switch host/scheme parse to `URI`/`URL` from the JDK for that branch only.
4. Do not change `SharedPrefManager` init pattern or public URL helpers’ signatures.

**Acceptance:**
- Unit tests prove Basic header byte-identical to prior behavior for at least one known pair.
- No `android.util.Base64` / `android.util.Log` imports remain.
- Existing UrlUtils tests pass.

**Out of scope:** Removing `SharedPrefManager` singleton init (larger DI task).

---

## Task 9 — Move HTML parse out of NotificationListItem model

**Roadmap:** 1 (data layer clean), **9** (model without `android.text.Html`), soft **10** (presentation formatting stays at UI edge).

**Problem (verified):**  
`NotificationListItem.Item.parsedText` lazily calls `Html.fromHtml(..., FROM_HTML_MODE_LEGACY)` inside `model/`. `NotificationsAdapter.ItemViewHolder.bind` sets `binding.title.text = item.parsedText`. Models must not depend on Android text widgets/HTML for KMP.

**Files (2):**
- `app/src/main/java/org/ole/planet/myplanet/model/NotificationListItem.kt`
- `app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsAdapter.kt`

**Instructions:**
1. Remove `parsedText` and `android.text.Html` from `NotificationListItem.kt` so the sealed class is pure data.
2. In `NotificationsAdapter` bind, parse `notification.formattedText` with the same `Html.fromHtml` mode (or `HtmlCompat` if already on classpath — **do not add dependencies**; prefer existing `android.text.Html` in the adapter only).
3. Grep callers of `parsedText`; only adapter uses it — keep ViewModel grouping API unchanged.
4. Do not edit `NotificationsViewModel.kt` unless compile forces a trivial import fix (prefer zero ViewModel diff).

**Acceptance:**
- `NotificationListItem.kt` has zero `android.*` imports.
- Notification list still shows HTML titles correctly.
- No behavior change to selection/mark-read.

**Out of scope:** Full Compose notifications screen; changing `Notification` entity.

---

## Task 10 — Split VersionUtilsTest: pure JVM tests off Robolectric

**Roadmap:** 7 (CI hotspot), 8 (test health). Soft **9** (documents that compare/parse APIs are pure).

**Problem (verified):**  
Master test logs: `VersionUtilsTest` **5.7s** for the class. File mixes pure string version compare/parse/isVersionAllowed tests with Context/`PackageManager` tests under `@RunWith(RobolectricTestRunner::class)` + `@Config(application = Application::class)`. Pure tests pay Robolectric sandbox cost unnecessarily. Production `VersionUtils.kt` already separates pure functions from Context APIs — **do not edit production** if tests can split alone.

**Files (2):**
- `app/src/test/java/org/ole/planet/myplanet/utils/VersionUtilsTest.kt`
- `app/src/test/java/org/ole/planet/myplanet/utils/VersionUtilsPureTest.kt` (**create** — name may be `VersionUtilsCompareTest.kt` if preferred)

**Instructions:**
1. Move all tests that only call `compareVersions`, `isVersionAllowed`, and `parseApkVersionString` into a **plain JUnit4** class (no Robolectric runner, no Android `@Config`).
2. Leave `getVersionCode` / `getVersionName` / `getAndroidId` tests in the Robolectric class (keep SDK-specific `@Config` methods).
3. Do not modify `app/src/main/.../VersionUtils.kt`.
4. Do not change assertion semantics.

**Acceptance:**
- Both classes discovered by `testDefaultDebugUnitTest`.
- Pure class runs without Robolectric.
- Full VersionUtils-related tests still pass; pure class wall time clearly lower than old combined class cost on a warm run.

**Out of scope:** Stripping Android from `VersionUtils` production Context methods; baseline profiles.

---

## Self-check (P5)

| Rule | Status |
|------|--------|
| **R1** Exactly 10 independent tasks | Yes — T1–T10 |
| **R2** No file in more than one task | Yes — each path unique across tasks |
| **R3** Open PRs listed; colliding files avoided | Yes — 37 PRs; especially avoided #15226 workflows, #15808 repos/DAOs, #15951 teams/TimeUtils, #16101 edge-to-edge, resources/strings PRs |
| **R4** Paths/classes verified to exist | Yes — each cited path opened/listed before inclusion; new test files marked create |
| **R5** ~≤150 LOC, ≤5 files, no new deps, no TODOs | Yes — max 2 files/task; no dependency adds |
| **R6** Plan only, no implementation in this run | Yes — plan document only |

**Roadmap coverage this round:** 1, 4, 5, 7, 8 heavily; **9** via T4/T6/T7/T8/T9; **10** lightly via T9 (UI-edge formatting). Items 2, 3, 6 not primary this focus (navigation / ViewModels / Compose) by design.
