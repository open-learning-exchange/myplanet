# myPlanet Task Generation — perf quick wins round

date: 2026-08-27
base commit: 89fd72c251df68ed01094091d4de7ba7a2571ebe
open PRs checked: #16293, #16292, #16274, #16270, #16258, #16257, #16192, #16101, #16096, #15951, #15825, #15824, #15820, #15808, #15699, #15559, #15519, #15412, #15267, #15266, #15226, #15198, #15158, #15108, #14960, #14893, #14883, #14650, #14427, #13928, #13848, #13657, #13604, #13415, #13355, #13287, #10993, #8175, #4075

Roadmap numbers referenced: 1 data layer cleanup · 5 sync/upload · 7 performance · 8 code health/tests · 9 Kotlin-multiplatform readiness · 10 Compose-multiplatform readiness

This generated-prompt was produced by an AI agent (OpenHands) on behalf of the user.

---

### 1. debounce the patient-search keystrokes hitting the DAO (roadmap 7)

context: `ui/health/MyHealthFragment.kt:setTextWatcher` (lines 304–308) wires `etSearch.doAfterTextChanged { viewModel.searchPatients(...) }`, so every character typed on the health-users screen immediately fires a `healthRepository.searchPatients` DAO round-trip on the main coroutine scope. A user typing a six-letter name triggers six database queries.
files: `app/src/main/java/org/ole/planet/myplanet/ui/health/MyHealthFragment.kt` — only `setTextWatcher` (lines 304–308). Do NOT touch `HealthViewModel.kt`, `HealthRepository`, or `HealthExaminationActivity` — open PRs cover the health layer elsewhere.
steps:
1. Replace the direct `viewModel.searchPatients(editable?.toString() ?: "", "joinDate", true)` call with a cancel-and-post debounce (e.g. keep a `var debounceJob: Job?` or use `Handler.postDelayed`, whichever fits the fragment) keyed on `viewLifecycleOwner.lifecycleScope`.
2. Debounce interval ~300 ms; invoke the same `viewModel.searchPatients(query, "joinDate", true)` signature so sorting stays identical.
3. Null out the debounce handle inside `onDestroyView` next to the existing `removeTextChangedListener(textWatcher)` cleanup.
4. Run `./gradlew testDefaultDebugUnitTest -PtestShardTotal=2 -PtestShardIndex=1` (or the full two-shard set) once to confirm the suite stays green.
acceptance: `./gradlew testDefaultDebugUnitTest` passes; health-users search still returns filtered patients after typing; no `searchPatients` call fires sooner than ~300 ms after the last typed character.
size budget: ~10 changed lines, 1 file.
out of scope: no HealthViewModel/HealthRepository changes, no query-shape changes.

---

### 2. hoist achievement-resource dup-check to a Set (roadmap 7+1)

context: `ui/user/EditAchievementFragment:editResourceItem` builds `prevList` and, inside `showResourceListDialog`, loops `for (i in 0 until list.size) { if (prevList.contains(list[i].title)) selected.add(i) }` (lines 477–482). `prevList.contains` is an O(n) linear scan inside an O(n) loop — worst-case O(n²) when reopening a large achievement resource list.
files: `app/src/main/java/org/ole/planet/myplanet/ui/user/EditAchievementFragment.kt` — only the `editResourceItem` block (line 478) and the `prevList` parameter handed to `showResourceListDialog` (line 394). Leave the rest of the fragment alone.
steps:
1. In `editResourceItem`, wrap `prevList` in `prevTitles: Set<String?>` built once via `prevList.toSet()` (or `toHashSet()`) before the loop.
2. Change `showResourceListDialog(prevList: List<String?>)` signature to accept the `Set`, or keep signature and compute locally — pick whichever keeps the diff minimal.
3. Replace `prevList.contains(list[i].title)` with `prevTitles.contains(list[i].title)`.
4. Run any existing `EditAchievementFragment` unit tests.
acceptance: `./gradlew testDefaultDebugUnitTest` passes; achievement resource dialog still pre-checks the previously linked resources; duplicate-title marking works the same.
size budget: ~4 changed lines, 1 file.
out of scope: no dialog refactoring, no achievement model changes.

---

### 3. collapse the double StorageStatsManager call (roadmap 7+1)

context: `utils/FileUtils.kt:totalAvailableMemoryRatio` (lines 347–351) and `availableOverTotalMemoryFormattedString` (lines 353–358) each call `getStorageStats(context)` twice — every storage screen paints two `StorageStatsManager` round trips where one is enough.
files: `app/src/main/java/org/ole/planet/myplanet/utils/FileUtils.kt` — functions `totalMemoryCapacity`, `totalAvailableMemory`, `totalAvailableMemoryRatio`, `availableOverTotalMemoryFormattedString` only. The private `getStorageStats(context): Pair<Long, Long>` stays.
steps:
1. In `totalAvailableMemoryRatio`, call `getStorageStats(context)` once into `val (total, available) = ...` and compute from that single Pair.
2. In `availableOverTotalMemoryFormattedString`, do the same — call `getStorageStats` once and read `.first`/`.second` from the same Pair.
3. Run `FileUtilsTest` if present, otherwise the full unit suite.
acceptance: `./gradlew testDefaultDebugUnitTest` passes; Settings → storage breakdown, Login storage note, and TaskNotificationWorker's percentage check still report identical values.
size budget: ~6 changed lines, 1 file.
out of scope: no StorageStatsManager API changes, no fragment changes.

---

### 4. stop regex-compilation per getUserInfo call (roadmap 1+9)

context: `ui/sync/ProcessUserDataActivity.getUserInfo` (lines 239–248) calls `uri.userInfo?.split(":".toRegex())`, which compiles a `Regex` object every call. The sibling `ServerUrlMapper.getUserInfo` (lines 111–121 of `services/sync/ServerUrlMapper.kt`) already proves a plain `split(":")` suffices. One of the sync helpers allocates for nothing — removing it also untangles the data layer a step on the platform-core road (9).
files: `app/src/main/java/org/ole/planet/myplanet/ui/sync/ProcessUserDataActivity.kt` — companion `getUserInfo` only. Do NOT touch `SyncActivity.kt` or `LoginActivity.kt` (open PR #16274 owns them).
steps:
1. Replace `split(":".toRegex())` with `split(":")`.
2. Simplify the string-template callsariable (`"${info?.get(0)}"` → `info?.get(0) ?: ""` style is acceptable but keep the current `ar` shape so behaviour is identical).
3. Run the activity's unit tests if any exist; otherwise the whole suite.
acceptance: `./gradlew testDefaultDebugUnitTest` passes; manual-server-login flow that parses `user:pass@host` URIs still extracts user/password correctly (e.g. server URL with embedded credentials).
size budget: ~3 changed lines, 1 file.
out of scope: no refactor into a shared parser — ServerUrlMapper is handled in a separate task.

---

### 5. align ServerUrlMapper.getUserInfo with the plain-split style (roadmap 5+9)

context: `services/sync/ServerUrlMapper.getUserInfo` (lines 111–121) and `ui/sync/ProcessUserDataActivity.getUserInfo` are two bodies for the same parse. Normalizing the mapper version while the other task cleans the activity version shrinks one more sync-side duplication and keeps trying eventual KMP extraction (9) honest. Even though the mapper already uses `split(":")`, the three-element `defaultInfo`/`info` juggling is needlessly allocation-heavy.
files: `app/src/main/java/org/ole/planet/myplanet/services/sync/ServerUrlMapper.kt` — `getUserInfo` only; the rest of the mapper (URL rewriting, preference edits, `isUrlDirectlyReachable`) stays untouched.
steps:
1. Reimplement `getUserInfo` as a direct `userInfo?.split(":")` that fills a `Array<String>` of two entries — keep the same "empty when malformed" contract.
2. Remove now-unneeded `defaultInfo`/`info` temporaries and the `dropLastWhile` chain (split already drops trailing empties for single-colon case matches the activity variant).
3. Run any `ServerUrlMapper` unit tests (`app/src/test/...`).
acceptance: `./gradlew testDefaultDebugUnitTest` passes; `updateUrlPreferences` still extracts user/password for URLs like `https://user:pwd@example-clone:port`.
size budget: ~8 changed lines, 1 file.
out of scope: no shared-parser extraction yet (kept in per-file scope to stay under the file-per-task budget).

---

### 6. cache the ClipboardManager lookup in the chat adapter (roadmap 7)

context: `ui/chat/ChatAdapter.copyToClipboard` (lines 96–105) performs `context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager` on every long-press of a chat message. Service lookups aren't free when scrolling history with several queries/responses.
files: `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatAdapter.kt` — `copyToClipboard` body only.
steps:
1. Hoist the lookup once per adapter invocation into `private val clipboard = lazy { context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }` (or fetch inside `QueryViewHolder`/`ResponseViewHolder` construction).
2. Read `clipboard.value` inside `copyToClipboard`.
3. Run the ChatAdapter tests if any; otherwise the whole suite.
acceptance: `./gradlew testDefaultDebugUnitTest` passes; "copied to clipboard" toast appears on long-press and clip contains message text.
size budget: ~4 changed lines, 1 file.
out of scope: no clipboard-format handling, no ChatMessage model changes.

---

### 7. hoist the mistake-row color lookup in course progress (roadmap 7)

context: `ui/courses/CoursesProgressAdapter.showStepMistakes` (line 46) does `ContextCompat.getColor(context, R.color.daynight_textColor)` per bind before any row creation. Per-bind `getColor` costs a resource-theme resolution each time a course row inflates steps.
files: `app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesProgressAdapter.kt` — `showStepMistakes` only. Leave `CourseProgressActivity` untouched (open PR activity covers progress UI).
steps:
1. Add a lazily-evaluated `private val textColor by lazy { ContextCompat.getColor(context, R.color.daynight_textColor) }` on the adapter.
2. Replace the inline lookup at line 46 with `textColor`.
3. Run the Progress adapters' unit tests if present; otherwise the whole suite.
acceptance: `./gradlew testDefaultDebugUnitTest` passes; progress rows still render step/mistake rows with the same theme-aware text color in light and dark mode.
size budget: ~3 changed lines, 1 file.
out of scope: no UI overhaul, no changes to `ProgressGridAdapter`.

---

### 8. hoist the members-list date formatter to the companion (roadmap 7)

context: `ui/teams/members/MembersAdapter.kt:34` allocates a `DateTimeFormatter.ofPattern(...).withZone(ZoneId.systemDefault())` on every adapter instantiation — each time TeamDetail recreates the members list, a fresh formatter is built.
files: `app/src/main/java/org/ole/planet/myplanet/ui/teams/members/MembersAdapter.kt` — the `dateFormatter` field only. Do NOT touch `MembersViewModel`, `MembersDetailFragment`, or `RequestsAdapter` (open PRs own them).
steps:
1. Move `DateTimeFormatter.ofPattern(TimeUtils.DATE_FORMAT).withZone(ZoneId.systemDefault())` into a `companion object` (it is thread-safe and immutable).
2. Refer to the companion constant from the bind path.
3. Run the Members adapter tests if present; otherwise the whole suite.
acceptance: `./gradlew testDefaultDebugUnitTest` passes; member rows still show "last visit" dates formatted the same.
size budget: ~3 changed lines, 1 file.
out of scope: no date-format changes, no repository/DAO changes.

---

### 9. lazy-hoist the dashboard survey list text color (roadmap 7)

context: `ui/dashboard/DashboardSurveysAdapter.onBindViewHolder` (lines 41–48) calls `ContextCompat.getColor(holder.itemView.context, R.color.daynight_textColor)` on every row bind of the survey-picker dialog.
files: `app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardSurveysAdapter.kt` — the `onBindViewHolder` color only.
steps:
1. Add `private val textColor by lazy { ContextCompat.getColor(..., R.color.daynight_textColor) }` resolved on the first created holder (or on the adapter instantiation).
2. Use the cached `Int` in `onBindViewHolder`.
3. Run any ready dashboard tests; otherwise the whole suite.
acceptance: `./gradlew testDefaultDebugUnitTest` passes; survey list colors match the app theme in light/dark.
size budget: ~3 changed lines, 1 file.
out of scope: no dialog/layout changes, no toggle to ViewBinding.

---

### 10. lift the handled-payload set to companion scope (roadmap 7)

context: `ui/user/AchievementsAdapter.onBindViewHolder(holder, position, payloads)` (lines 59-67) builds `setOf(PAYLOAD_PHONE, PAYLOAD_EMAIL)` per partial-bind call. It's an allocation on every diff callback from the achievements list.
files: `app/src/main/java/org/ole/planet/myplanet/ui/user/AchievementsAdapter.kt` — the `handledPayloads` constant only.
steps:
1. Move `setOf(PAYLOAD_PHONE, PAYLOAD_EMAIL)` into the `companion object` (the file already has one).
2. Keep the same partial-bind gating logic unchanged (`flatPayloads` / `payloadSet` internals stay).
3. Run the AchievementsAdapter tests if present; otherwise the whole suite.
acceptance: `./gradlew testDefaultDebugUnitTest` passes; achievement rows still update phone/email in place without a full re-bind when the diff payload arrives.
size budget: ~2 changed lines, 1 file.
out of scope: no diff-payload expansion (name/relationship untamed), no model changes.

---
</parameter>
<parameter name="summary">Write 10 perf work orders to docs file</parameter>
<parameter name="security_risk">MEDIUM</parameter>
</invoke>