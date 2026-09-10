# myPlanet refactor round — 10 work orders

**Open-PR check (R3)**: Performed. 40 open PRs enumerated via GitHub API, and every PR's changed-file list was fetched and merged into an off-limits set. Notable off-limits areas: `TeamsRepository*` / `TeamsTasks*` / `TeamCalendar*` / leaderboard (PRs 16623, 15951, 15825, 15820, 14883, 15108), `CoursesRepository*`, `CoursesFragment`, `CoursesViewModel`, `CourseSelectionController`, `CourseFilterController` (16624, 13848, 13657, 15412), sync layer `AppDatabase`, most DAOs, `TransactionSyncManager`, `RoomModule`, `ServiceModule`, `RepositoryModule` (15808, 15825, 15824), `UploadManager.kt`, `VoicesRepository*`, `BaseVoicesFragment`, `VoicesAdapter`, `VoicesFragment`, `ReplyActivity` (10993, 13415), `ResourcesFragment`, `ResourcesAdapter`, `ResourcesViewModel`, `AddResource*` (13355, 15267, 13848, 16101, 16986), `DashboardActivity`, `DashboardViewModel`, `BellDashboard*`, `BaseDashboardFragment`, `BaseRecyclerFragment`, `BaseResourceFragment`, `BasePermissionActivity`, `SubmissionsRepositoryImpl`, `SubmissionViewModel`, `SurveysRepository*`, `ProgressRepositoryImpl`, `ActivitiesRepository*`, `EventsRepository*`, `MainApplication.kt`, `app/build.gradle`, `gradle/libs.versions.toml`, `settings.gradle`, all `values*/strings.xml`, `AndroidManifest.xml`, `Meetup.kt`, `TeamTask.kt`, `OnboardingActivity`, `LoginActivity`, `ChatDetailFragment`, `ChatViewModel`, `UserProfileFragment`, `ExamTakingFragment`, `UserInformationFragment`, `EdgeToEdgeUtils`, `TimeUtils.kt` (15951), `flutter/` and `.github/` workflow areas, plus a long tail of layout/drawable files. All tasks below use only files verified present in the working tree and absent from that set.

---

### Task 1 — Move full-page PDF render off the main thread in ResourceViewerFragment
**Roadmap**: 7 (performance hotspots). Also 10: rendering the first page already has a platform-aware helper elsewhere, so this keeps the fragment thinner.
**Files** (2):
- `app/src/main/java/org/ole/planet/myplanet/ui/viewer/ResourceViewerFragment.kt`
- `app/src/main/java/org/ole/planet/myplanet/utils/PdfThumbnailLoader.kt`

**Problem**: `ResourceViewerFragment.renderPdf()` (lines 509–535) opens a `ParcelFileDescriptor`, creates a `PdfRenderer`, renders page 0 into a full-size `createBitmap(page.width, page.height)`, and mutates the view hierarchy — all synchronously on the main thread when a PDF is opened. Large PDFs block the UI frame loop; the class already has `MAX_TEXT_VIEWER_CHARS` guarding the text viewer, so this is the remaining main-thread file I/O in the viewer.
**Work order**: In `renderPdf()`, wrap only the decode/render portion (open fd → `PdfRenderer` → `openPage(0)` → `createBitmap` → `page.render`) in `viewLifecycleOwner.lifecycleScope.launch { withContext(dispatcherProvider.io) { ... } }` (both are already injected/available in this fragment: `dispatcherProvider` at line 105, `lifecycleScope` imported at line 36). Keep all view mutations (`pdfPlaceholder.visibility`, `parent.addView`) on the main thread after the bitmap returns. Use `.use {}` for `ParcelFileDescriptor`, `PdfRenderer`, and `Page` instead of the three manual `close()` calls so an exception cannot leak the descriptor. If the file does not render, keep the existing behavior (placeholder stays visible) — do not add new error UI. `PdfThumbnailLoader.kt` may receive a new `fullPageBitmap(file, dispatcherProvider): Bitmap?` function reusing its existing try/use pattern, and `renderPdf()` calls it; if that exceeds the line budget, do the work inline in the fragment and leave `PdfThumbnailLoader.kt` untouched.
**Acceptance**: `./gradlew assembleDefaultDebug` compiles; opening a PDF in the viewer shows the page; no behavior change besides responsiveness. No new dependencies.

---

### Task 2 — Stop `Calendar.getInstance()` churn in CalendarFragment date setup
**Roadmap**: 7 (micro), 6-neutral.
**File** (1):
- `app/src/main/java/org/ole/planet/myplanet/ui/calendar/CalendarFragment.kt`

**Problem**: `onCreateView` calls `Calendar.getInstance()` then `binding.calendarView.setDate(calendar.time)` — fine alone, but `Calendar.getInstance()` is a surprisingly expensive allocation (locale + timezone resolution); the applandeo `CalendarView.setDate` accepts any `Date`, so `Date()` is sufficient and avoids the Calendar allocation on every view recreation (this fragment is recreated on each dashboard tab switch).
**Work order**: Replace `val calendar = Calendar.getInstance(); binding.calendarView.setDate(calendar.time)` with `binding.calendarView.setDate(java.util.Date())` (add the `java.util.Date` import, remove the now-unused `java.util.Calendar` import). No other changes in this 34-line file.
**Acceptance**: Calendar opens on today's date; compile clean.

---

### Task 3 — Reuse a thread-safe java.time formatter in SubmissionsRepositoryExporter
**Roadmap**: 1 (data-layer cleanup), 7, 8 (removes duplicated formatting logic). Also 9: fewer bespoke `java.text` sites makes the eventual platform-free core easier.
**File** (1):
- `app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryExporter.kt`

**Problem**: The exporter keeps a `ThreadLocal<SimpleDateFormat>` (line 34, pattern `"yyyy-MM-dd HH:mm"`) used once at line 175 for the PDF "Generated:" stamp, duplicating formatter machinery that the app's `TimeUtils` centralizes with cached, thread-safe `DateTimeFormatter`s. (`TimeUtils.kt` itself is off-limits this round via PR 15951, so the fix stays local to the exporter.)
**Work order**: Delete the `dateFormatter` ThreadLocal field and the now-unused `java.text.SimpleDateFormat` import (check `java.util.Locale`/`java.util.Date` usage before removing those imports). In `SubmissionsRepositoryExporter.kt` only, replace the ThreadLocal with a single `private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.getDefault()).withZone(ZoneId.systemDefault())` (java.time is already the app's sanctioned path per `TimeUtils`), and replace the call site at line 175 with `dateFormatter.format(Instant.ofEpochMilli(System.currentTimeMillis()))`. `DateTimeFormatter` is immutable and thread-safe, so the ThreadLocal indirection disappears.
**Acceptance**: Generated PDF header text format unchanged (`yyyy-MM-dd HH:mm`). Existing exporter tests (if present under `app/src/test`) still pass; run `./gradlew testDefaultDebugUnitTest --tests "*SubmissionsRepositoryExporter*"`.

---

### Task 4 — Replace the ThreadLocal SimpleDateFormat in VoicesActions with java.time
**Roadmap**: 8 (code health), 7 (allocation on a UI path). Also 9.
**File** (1):
- `app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesActions.kt`

**Problem**: `VoicesActions` (an `object`) holds `ThreadLocal<SimpleDateFormat>` (line 33) used at line 224 to render the user's last-visit timestamp in the member detail dialog. `SimpleDateFormat` + `ThreadLocal` is legacy machinery; the pattern `"MMMM dd, yyyy hh:mm a"` can be served by one immutable `DateTimeFormatter`.
**Work order**: Replace line 33 with a `DateTimeFormatter` built via `java.time.format.DateTimeFormatter.ofPattern("MMMM dd, yyyy hh:mm a", ...)` with `ZoneId.systemDefault()`. Update line 224 to `dateFormatter.format(Instant.ofEpochMilli(it))`, dropping the `java.util.Date` and `java.text.SimpleDateFormat` imports (verify no other use of `Date` in the file first — if `Date` is used elsewhere, keep its import). Locale-sensitive month names must still follow the device locale: construct the formatter lazily (e.g. `by lazy`) or read `Locale.getDefault()` at call time inside a small private function so a locale change while the process lives is honored.
**Acceptance**: Member detail dialog shows the same "last visit" string format. `./gradlew assembleDefaultDebug` passes; no other changes.

---

### Task 5 — Drop the empty `onCreate` and inflate the dialog binding lazily in MyHealthFragment
**Roadmap**: 8, 3 (viewmodel-layer hygiene: fragment slimming).
**File** (1):
- `app/src/main/java/org/ole/planet/myplanet/ui/health/MyHealthFragment.kt`

**Problem**: Two small health-fragment inefficiencies visible at lines 77–109: (a) an empty `override fun onCreate` that does nothing but call super (dead override); (b) `alertMyPersonalBinding = AlertMyPersonalBinding.inflate(LayoutInflater.from(context))` runs eagerly in `onViewCreated` even though the dialog is only shown on demand — one extra layout inflation per fragment view creation on a hot dashboard tab.
**Work order**: (a) Delete the empty `onCreate` override. (b) Change `alertMyPersonalBinding` from `private lateinit var` to `private val alertMyPersonalBinding by lazy { AlertMyPersonalBinding.inflate(LayoutInflater.from(requireContext())) }` — `requireContext()` in a `by lazy` property initializer runs at first access, which is safe because all accesses happen after `onViewCreated`. Verify every read of `alertMyPersonalBinding` in the file occurs after view creation (search the file; there are dialog-building call sites below line 120) before converting. Do not touch `alertHealthListBinding` (it is nullable-cleared elsewhere) or any other field.
**Acceptance**: Health tab opens, "add member" / examination dialogs still inflate and show. No new code paths introduced.

---

### Task 6 — Remove redundant `exists()` syscall per file in FreeSpaceWorker
**Roadmap**: 7, 5 (sync/upload-adjacent worker hygiene).
**File** (1):
- `app/src/main/java/org/ole/planet/myplanet/services/FreeSpaceWorker.kt`

**Problem**: `deleteRecursive()` (lines 63–83) checks `file.exists()` for every node even though `walkBottomUp()` only yields files that existed at traversal time, and the authoritative outcome is `file.delete()`'s boolean anyway — so each node pays a redundant syscall. Progress reporting (`setProgress` every 10 files) and per-directory `markResourcesAsNotOffline` batching (lines 46–53) stay as-is.
**Work order**: Drop the outer `if (file.exists())` guard; keep the current `length()`-before-`delete()` order (length must be read before delete), keep `deletedFiles`/`freedBytes` accounting and the `isStopped` early-return semantics exactly as they are. Net change: one syscall fewer per file, identical observable behavior and progress output.
**Acceptance**: Worker still reports `deletedFiles`/`freedBytes` in its result `Data`; `./gradlew testDefaultDebugUnitTest --tests "*FreeSpace*"` passes if a test exists, otherwise compile + manual reasoning.

---

### Task 7 — Hoist per-bind resource lookups out of `onBindViewHolder` in TeamsSelectionAdapter
**Roadmap**: 7 (scroll jank on team picker), 6-neutral.
**File** (1):
- `app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamsSelectionAdapter.kt`

**Problem**: The adapter calls `setImageResource(R.drawable.team)` / `R.drawable.business` per bind (lines 35–37). Those take compile-time constant IDs and are already cheap; the executor must first open the file and confirm whether any genuinely hoistable per-bind resource resolution (e.g. `context.getString`, `ContextCompat.getColor`) happens inside `onBindViewHolder`.
**Work order**: Open `TeamsSelectionAdapter.kt`. Move any per-bind immutable lookups (string resources, colors, drawables, dimensions) into lazily-initialized adapter fields or the ViewHolder `init`, following the pattern already used in `FeedbackAdapter` (cached `ColorStateList`/label fields set in `onCreateViewHolder`). Do not change click behavior, selection logic, or the adapter's public API. If nothing hoistable is found beyond the two constant `setImageResource` calls, then instead keep the change minimal: leave binding logic untouched and limit the diff to what is verifiably safe — do not force a refactor for its own sake. Net budget stays under 150 lines regardless.
**Acceptance**: Team selection list scrolls identically; adapter tests (if any under `app/src/test/.../teams`) pass; compile clean.

---

### Task 8 — Remove the manual field-by-field copy in LifeAdapter.onItemMoveFinished
**Roadmap**: 7, 8. Also 9-adjacent (pure-Kotlin list logic stays portable).
**File** (1):
- `app/src/main/java/org/ole/planet/myplanet/ui/life/LifeAdapter.kt`

**Problem**: `onItemMoveFinished()` (lines 123–138) builds `updatedList` by constructing a brand-new `MyLife()` per item and copying six fields (`_id`, `imageId`, `userId`, `title`, `isVisible`, `weight = index`) — a manual copy constructor that silently drops any field added to `MyLife` later (the entity has an `equals`/`hashCode` covering `weight` at `model/MyLife.kt:23,43,52`).
**Work order**: `MyLife` is a plain class with mutable vars and (per `model/MyLife.kt`) no `copy()`. Replace the manual rebuild with: `val updatedList = list.mapIndexed { index, item -> item.also { it.weight = index } }` — the items are already detached copies made in `onItemMove` (`currentList.toMutableList()`), so mutating `weight` in place is safe and preserves every field. Keep `reorderCallback(updatedList)` and `submitList(updatedList)` as-is. Check `MyLife.kt` for any `@Ignore`/computed fields that must not be mutated (there are none relevant to `weight`) before editing.
**Acceptance**: Drag-to-reorder in My Life still reorders, persists order via `reorderCallback`, and survives process recreation. `./gradlew testDefaultDebugUnitTest --tests "*Life*"` if present, else compile.

---

### Task 9 — Dedupe dead-identity check and nested loop in TagsRepositoryImpl.getTagsWithChildren
**Roadmap**: 1 (data-layer), 7. Also 9: pure collection logic, zero Android imports — exactly the kind of function that lands in the platform-free core unchanged.
**File** (1):
- `app/src/main/java/org/ole/planet/myplanet/repository/TagsRepositoryImpl.kt`

**Problem**: `getTagsWithChildren()` (lines 17–39) loads `getTags(dbType)` (one DAO query) **and** `tagDao.getAll()` (second DAO query returning every tag row), then rebuilds a child map in memory. The `list.last() !== t` identity check (line 29) is dead code: each tag `t` is visited exactly once per parent id, so it can never already be the last element of that parent's list.
**Work order**: (a) Delete the dead `if (list.isEmpty() || list.last() !== t)` condition and just `list.add(t)` — semantics unchanged. (b) Replace the nested `for` loops with `allTags.forEach { t -> t.attachedTo?.filterNotNull()?.forEach { pid -> childMap.getOrPut(pid) { ArrayList() }.add(t) } }`. (c) Only if the executor can confirm from `data/room/dao/TagDao.kt` that the parent-tag predicate used by `getParentTags` is expressible in-memory without ambiguity, derive `parentTags` from `allTags` when `dbType == null` and skip the second DAO call in that case; otherwise leave the two queries as-is. Stay within the line budget — (a) and (b) alone are a valid completion.
**Acceptance**: `TagsRepositoryImplTest` (exists under `app/src/test/.../repository`) passes with `./gradlew testDefaultDebugUnitTest --tests "*TagsRepository*"`; tag lists render identically in the resources filter sheet.

---

### Task 10 — Fix the `message_placeholder` misuse in CommunityLeadersAdapter.onBindViewHolder
**Roadmap**: 8, 7 (per-bind string formatting in a list), 3-neutral.
**File** (1):
- `app/src/main/java/org/ole/planet/myplanet/ui/community/CommunityLeadersAdapter.kt`

**Problem**: `onBindViewHolder` (lines 37–48) calls `context.getString(R.string.message_placeholder, leader)` — passing the whole `UserEntity` as a format arg, so the row renders `UserEntity.toString()` output instead of a composed name, and pays a `getString` lookup per bind.
**Work order**: In this one file: (a) open `app/src/main/res/values/strings.xml` **read-only** (it is off-limits for edits) to see `message_placeholder`'s format spec; (b) replace the branch with direct composition: `holder.title.text = if (leader.firstName == null) leader.name else listOfNotNull(leader.firstName, leader.lastName).joinToString(" ")`, keeping coherence with `showLeaderDetails` below, which passes `leader.name` to `MembersDetailFragment.newInstance`; (c) change nothing else. Do not edit `strings.xml`, do not change the click handler or `showLeaderDetails`.
**Acceptance**: Leaders list shows "First Last" (or username fallback) rows; `./gradlew assembleDefaultDebug` compiles; if `CommunityLeadersAdapterTest` exists under `app/src/test`, it passes — otherwise add no new tests (test addition is allowed within the 150-line budget only if a matching test file already exists to extend).

---

## Self-check (P5)
- **R1**: 10 tasks, each touching disjoint files → mergeable in any order. ✅
- **R2**: File overlap check — all 10 task file sets are distinct (`ResourceViewerFragment.kt`, `CalendarFragment.kt`, `SubmissionsRepositoryExporter.kt`, `VoicesActions.kt`, `MyHealthFragment.kt`, `FreeSpaceWorker.kt`, `TeamsSelectionAdapter.kt`, `LifeAdapter.kt`, `TagsRepositoryImpl.kt`, `CommunityLeadersAdapter.kt`; T1's optional `PdfThumbnailLoader.kt` helper is exclusive to T1). ✅
- **R3**: all 10 files cross-checked against the 40-PR changed-file set; none collide. ✅
- **R4**: every cited path, class, function and line was opened and confirmed during P1 (`renderPdf` @ResourceViewerFragment.kt:509–535, `Calendar.getInstance()` in CalendarFragment.kt, ThreadLocal @SubmissionsRepositoryExporter.kt:34 and VoicesActions.kt:33, empty `onCreate` @MyHealthFragment.kt:77–79, eager `alertMyPersonalBinding` @MyHealthFragment.kt:94, progress loop @FreeSpaceWorker.kt:74–79, `setImageResource` @TeamsSelectionAdapter.kt:35–37, manual copy @LifeAdapter.kt:125–134, dead identity check @TagsRepositoryImpl.kt:29, `message_placeholder` call @CommunityLeadersAdapter.kt:41). ✅
- **R5**: each task ≤ ~150 changed lines, ≤ 2 files, no new dependencies, no TODOs. ✅
- **R6**: no implementation code written; plan only. ✅
