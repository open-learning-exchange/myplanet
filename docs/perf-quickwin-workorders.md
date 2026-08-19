# myPlanet refactor round — performance quick wins

date · 2026-08-19
base commit · 9c54a03
open PRs checked · 44 (all open PRs listed via `gh pr list`; each candidate file verified CLEAR against every open PR's changed-file set using a block-check that returns BLOCKED/CLEAR)

---

### 1. Partition the notification payload once instead of filtering it four times (roadmap 7; also 6 + 8)

context: `NotificationsViewModel.loadNotifications` fetches `payloadNotifications` (line 60), then scans the whole list four separate times — lines 62–66, 68–71, 77–80, 83–84 — each pass calling `it.type.lowercase()` on every element to split out `task` vs `join_request` rows. The type is lowercased up to four times per notification on every load, and each pass re-iterates the full list. The resolved type (the `team`→`join_request`/`chat` classification) is computed later in `formatNotification`→`resolveType` (line ~248/329), so these four filters only ever compare the raw `type` against the literals `"task"` and `"join_request"`.

files: `app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModel.kt` — `loadNotifications` (lines 57–95). Leave `formatNotification`, `resolveType`, `parseTaskDate`, and the `_notifications`/`_unreadCount` assignments untouched; do not change `NotificationsRepository` or any DAO.

steps:
1. In `loadNotifications`, after obtaining `payloadNotifications` (line 60), make a single pass that buckets items by `it.type.lowercase()` into three local lists: one for `== "task"`, one for `== "join_request"`, and one bucket for everything else (so raw `"team"`/`"newTask"`/`"newResource"` items land there and, as today, contribute nothing to the task/join-request enrichment).
2. Derive `taskIds` (currently lines 62–66) and `taskTitles` (68–71) from the `task` bucket; derive `joinRequestIds` (77–80) and `joinRequestsWithoutRelatedId` (83–84) from the `join_request` bucket. Preserve the exact predicates — `taskIds`/`joinRequestIds` map `relatedId` then `distinct()`; `taskTitles` keeps the `relatedId.isNullOrEmpty() || !taskTeamNames.containsKey(it.relatedId)` condition; `joinRequestsWithoutRelatedId` keeps `it.relatedId.isNullOrEmpty()`.
3. Keep the `taskTeamNames` merge (66, 72–75), the `joinRequestDetails` fetch + `""` fallback (81, 85–88), and the final `.map { formatNotification(...) }` (90–92) and unread count (93) exactly as-is.
4. Remove the now-unused imports if the compiler flags any.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.ui.notifications.NotificationsViewModelTest"` is green — in particular `testResolveTypeClassifiesRawTeamTypeAsJoinRequest`, `...AsChatForPostedMessage`, `...RawNewTaskTypeAsTask`, and `...RawNewResourceTypeAsResource` still pass (they exercise `resolveType`, which this task does not touch). `./gradlew testDefaultDebugUnitTest` stays green. Notifications screen still groups task and join-request items with their team names exactly as before.

size budget: ~35 changed lines, 1 file
out of scope: no changes to `resolveType`/`formatNotification`, no repository or DAO changes, no UI/layout changes.

---

### 2. Drop the redundant second set copy in `MyCourse.saveConcatenatedLinksToPrefs` (roadmap 1 + 7)

context: `MyCourse.saveConcatenatedLinksToPrefs` (lines 94–111) already builds a mutable set — `existingConcatenatedLinks` (line 96–100, `toMutableSet()` / `mutableSetOf()`) — then at line 105 copies it a second time via `val existingSet = existingConcatenatedLinks.toHashSet()`, and only ever uses `existingSet` (adds links at 107, serializes at 109). `existingConcatenatedLinks` is never read after line 105. The `toHashSet()` is a full defensive copy of an already-mutable set, allocated on every save.

files: `app/src/main/java/org/ole/planet/myplanet/model/MyCourse.kt` — `saveConcatenatedLinksToPrefs` (lines 94–111) only. Do not touch `addConcatenatedLink`, the `concatenatedLinks` companion set, `getCoverImageFile`, or `serialize`.

steps:
1. Change the construction of `existingConcatenatedLinks` (lines 96–100) so both branches yield a `MutableSet<String>` with the same element set as today but without the later copy: build it as a `HashSet` directly — `JsonUtils.gson.fromJson(...).toHashSet()` for the non-null branch and `hashSetOf()` for the null branch. (Use `HashSet`, not `LinkedHashSet`, to preserve the current unspecified-order output semantics exactly.)
2. Delete line 105 (`val existingSet = existingConcatenatedLinks.toHashSet()`).
3. Use `existingConcatenatedLinks` in place of `existingSet` at lines 107 and 109 (the `for` loop's `add` and the `JsonUtils.gson.toJson(existingConcatenatedLinks.toList())`).
4. Keep the `synchronized(concatenatedLinks)` block (102–104) and the `spm.setConcatenatedLinks(...)` call unchanged.

acceptance: `./gradlew testDefaultDebugUnitTest` green. `VoicesRepositoryNewsSyncTest` (which mocks `getConcatenatedLinks()` to null, exercising the empty-set branch) still passes. Concatenated links still persist and accumulate across saves exactly as before.

size budget: ~6 changed lines, 1 file
out of scope: no change to the `concatenatedLinks` in-memory set or its synchronization, no SharedPrefManager changes.

---

### 3. Remove the serialize→parse round-trip in `UserEntity.addImageUrl` (roadmap 1; also 9)

context: `UserEntity.addImageUrl` (lines 160–170) does `val element = JsonParser.parseString(jsonDoc["_attachments"].asJsonObject.toString())` then `val obj = element.asJsonObject` and iterates `obj.entrySet()`. The input `jsonDoc["_attachments"].asJsonObject` is already a `JsonObject`; the code serializes it to a String and immediately parses it back into an identical `JsonObject`, then iterates that. This runs once per user document during user sync (many users per sync), allocating a full string + re-parse for nothing. `JsonParser` is imported only for this line (line 10).

files: `app/src/main/java/org/ole/planet/myplanet/model/UserEntity.kt` — `addImageUrl` (lines 160–170) only. Leave `parseLeadersJson`, `getFullName*`, `isManager`/`isLeader`/`isGuest`, and all other fields alone. Do not touch `UrlUtils` or any caller (one caller, `UserRepositoryImpl`, is owned by an open PR — change only the method body, not its signature).

steps:
1. Replace the `JsonParser.parseString(...)`/`element`/`obj` dance (lines 162–164) with a direct reference to the attachments object: `val attachments = jsonDoc["_attachments"].asJsonObject` and iterate `attachments.entrySet()`.
2. Keep the rest of the method identical: the `for ((key1) in entries) { userImage = UrlUtils.getUserImageUrl(id, key1); break }` loop and the outer `if (jsonDoc?.has("_attachments") == true)` guard.
3. Remove the now-unused `import com.google.gson.JsonParser` (line 10) — it is referenced only by this method (confirmed: no other `JsonParser` use in the file).

acceptance: `./gradlew testDefaultDebugUnitTest` green. The method signature is unchanged, so `UserRepositoryImpl` (an open PR's file) compiles unmodified. After a user sync, profile images still resolve to the first attachment's URL.

size budget: ~6 changed lines (net), 1 file
out of scope: no `UrlUtils.getUserImageUrl` changes, no caller changes, no new tests required.

---

### 4. Cache `DateFormatSymbols().months` in `ActivitiesFragment.getMonth` (roadmap 7 + 8)

context: `ActivitiesFragment.getMonth` (lines 125–127) returns `DateFormatSymbols().months[month]`, constructing a fresh `DateFormatSymbols` (which loads the locale month-name table) on every call. It is invoked from the chart's `ValueFormatter.getFormattedValue` (lines 103–107), i.e. once per visible x-axis label — up to 12 allocations per chart render, repeated on every `invalidate()`. The month array is a locale constant for the fragment's lifetime.

files: `app/src/main/java/org/ole/planet/myplanet/ui/dashboard/ActivitiesFragment.kt` — `getMonth` (125–127) and the companion object. Leave `computeMonthlyCounts` (58–74) and `renderChart` (76–123) untouched; do not change the chart import set.

steps:
1. In the `companion object` (none currently exists with this data — add a `private val` to the existing or a new companion), store `private val monthNames = DateFormatSymbols().months`.
2. Change `getMonth` to `return monthNames[month]`.
3. Keep `getMonth` `internal` and its single-`month: Int` signature (the test calls `fragment.getMonth(Calendar.JANUARY)`).

acceptance: `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.ui.dashboard.ActivitiesFragmentTest"` green — `getMonth_returnsValidMonthName` (asserts January/December are non-blank) still passes. `./gradlew testDefaultDebugUnitTest` green. The activities chart still labels months with full localized names.

size budget: ~5 changed lines, 1 file
out of scope: no charting-library changes, no change to `computeMonthlyCounts` or data fetching.

---

### 5. Remove the no-op `.map { it }` and `.mapNotNull { it }` copies in `UploadRepositoryImpl` (roadmap 1 + 8)

context: In `queryPending` (lines 32–41), the `AdoptedSurveys` branch is `examDao.getPendingAdoptedSurveys().map { it } as List<T>` (line 35) — `.map { it }` allocates a new list of the identical elements purely to feed an unchecked cast that works on the DAO list directly. In `markExamsUploaded` (lines 86–108), `examDao.upsertAll(updated.mapNotNull { it })` (line 105) filters nulls out of `updated`, but `updated` is `mutableListOf<StepExam>()` (line 91) and only non-null `exam`s are ever added to it (line 100, inside the `exam == null`→`failed` else-branch), so `mapNotNull { it }` is a no-op copy on the per-batch mark-uploaded path.

files: `app/src/main/java/org/ole/planet/myplanet/repository/UploadRepositoryImpl.kt` — `queryPending` (32–41) and `markExamsUploaded` (86–108) only. Leave `hydrateSubmissions`, `fetchExistingDoc`, `uploadResource`, and the submission-update branch of `markUploaded` untouched.

steps:
1. Line 35: replace `examDao.getPendingAdoptedSurveys().map { it } as List<T>` with `examDao.getPendingAdoptedSurveys() as List<T>` (the DAO returns `List<StepExam>`; the unchecked cast is unchanged — keep the `@Suppress("UNCHECKED_CAST")` on the function at line 31).
2. Line 105: replace `examDao.upsertAll(updated.mapNotNull { it })` with `examDao.upsertAll(updated)` (`updated` is `MutableList<StepExam>` and `ExamDao.upsertAll(items: List<StepExam>)` accepts it).
3. Confirm no import becomes unused (none are introduced or removed by these edits).

acceptance: `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.repository.UploadRepositoryImplTest"` green — `queryPending returns adopted surveys from exam dao` (64–75), `markUploaded updates exams and returns missing exams as failures` (144–162, which verifies `examDao.upsertAll(listOf(existingExam))`) both pass. `./gradlew testDefaultDebugUnitTest` green. Upload pending/marked flows unchanged in behavior.

size budget: ~2 changed lines, 1 file
out of scope: no DAO changes, no `UploadQueryContract`/`UploadedItemResult` changes, no submission-branch changes.

---

### 6. Split blood-pressure input with a literal instead of a per-keystroke regex (roadmap 7 + 8)

context: `HealthExaminationActivity.validateFields` (157–187) registers a `doOnTextChanged` callback that, on every keystroke in the blood-pressure field, runs `"${...}".split("/".toRegex())` (line 166). `"/"` has no regex metacharacters, so the `toRegex()` compiles a `Pattern` and discards it on every character typed — pure overhead in a text-watcher. The sibling `HealthExaminationAdapter` already uses the project's idiom for this — `private val colonRegex by lazy { ":".toRegex() }` (line 188) — when a regex is actually needed; here the literal `String.split` overload is the exact equivalent.

files: `app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationActivity.kt` — `validateFields` (157–187), specifically line 166. Leave `showCheckbox`, `showOtherDiagnosis`, `setData`, and the encrypted-data handling untouched.

steps:
1. On line 166, change `.split("/".toRegex())` to `.split("/")` (the `String.split(String)` overload, which splits on a literal delimiter). Keep the trailing `.dropLastWhile { it.isEmpty() }.toTypedArray()` (166) and the `sysDia.size > 2 || sysDia.isEmpty()` check (167) exactly as-is — the resulting array has the same contents for any input containing zero or more `/`.
2. No imports change (no `Regex`/`Pattern` import is added or removed by this edit).

acceptance: `./gradlew testDefaultDebugUnitTest` green. Typing `120/80` shows no error; `120/80/90` and `abc` still trigger the systolic/diastolic validation errors; a value with no `/` still shows the "should be numeric systolic/diastolic" error.

size budget: ~1 changed line, 1 file
out of scope: no changes to the BP range thresholds, no other field validations, no layout changes.

---

### 7. Drop the dead `batchDocuments` array and the `Pair` wrapper in the resource-sync parse loop (roadmap 5 + 7)

context: In `SyncManager`'s resource sync, the per-batch parse block (lines 326–354) allocates `val batchDocuments = JsonArray()` (328) and `val validDocuments = mutableListOf<Pair<JsonObject, String>>()` (329). `batchDocuments` is `add`ed to at line 338 but is never read anywhere (grep confirms no other reference) — dead allocation per batch. `validDocuments` stores `Pair(doc, id)` (339), but only `.first` is consumed (line 349 `validDocuments.map { it.first }`) and `validDocuments.size` (354) is just a count; the `id` (`.second`) is never read. So the `Pair` wrapper, the `id` computed at line 335, and the trailing `.map { it.first }` allocation are all dead weight in the resource-sync hot loop (one batch per page of every resource sync).

files: `app/src/main/java/org/ole/planet/myplanet/services/sync/SyncManager.kt` — the resource-sync parse block, lines 326–354 only. Leave the API call (305–308), the `rows`/`batchSizer` logic (319–324), the `batchInsertResources` call (352), the progress/cleanup block (363–405), and every other sync phase untouched. `TransactionSyncManager` is owned by an open PR — do not touch it.

steps:
1. Delete line 328 (`val batchDocuments = JsonArray()`) and its `add` at line 338.
2. Change line 329 to `val validDocuments = mutableListOf<JsonObject>()` and line 339 to `validDocuments.add(doc)` (drop the `Pair` and the `id`).
3. Since `id` (line 335) is no longer used, remove the `val id = getString("_id", doc)` line and fold its guard directly into the `if`: keep the existing `!id.startsWith("_design") && id.isNotBlank()` condition by computing `id` inline only for the guard (i.e. `val id = getString("_id", doc); if (!id.startsWith("_design") && id.isNotBlank()) { validDocuments.add(doc) }` — `id` is still needed for the guard, so retain that local; only the `Pair`'s stored `id` is removed).
4. At line 348–349, replace `val docs = validDocuments.map { it.first }` with `val docs = validDocuments` and pass it to `resourcesRepository.batchInsertResources(docs)` (352). `validDocuments.size` at 354 stays valid (it's now the doc count).

acceptance: `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.services.sync.SyncManagerTest"` green (`resourcesRepository` is a relaxed mock; the change is behavior-preserving — same docs reach `batchInsertResources`). `./gradlew testDefaultDebugUnitTest` green. A resource sync still inserts the same documents and the per-batch progress/`ResourceSyncPosition` checkpointing is unchanged.

size budget: ~10 changed lines (net), 1 file
out of scope: no `TransactionSyncManager` changes, no `ResourcesRepository`/DAO changes, no logging-schema changes (the `validDocuments.size` count passed to `logRealmOperation` stays the same value).

---

### 8. Cache the two tag-drawer row colors in `ResourcesTagsAdapter.ChildViewHolder` (roadmap 7; also 6)

context: `ResourcesTagsAdapter.ChildViewHolder.bind` (lines 92–98) calls `ContextCompat.getColor(itemView.context, R.color.multi_select_grey)` and `ContextCompat.getColor(itemView.context, R.color.daynight_textColor)` (lines 94–95) on every child row bind. Both are constant colors resolved from resources per bind in the scrolling tag/navigation drawer. The in-repo precedent is `ChatShareTargetAdapter.ChildViewHolder` (lines 61–62), which hoists exactly these two colors to `private val ... = ContextCompat.getColor(view.context, ...)` computed once per ViewHolder.

files: `app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesTagsAdapter.kt` — `ChildViewHolder` (lines 91–99). Leave `ParentViewHolder` (68–89), `setExpandedIcon`, `createCheckbox`, the `DiffUtils` callback, and `getItemViewType` untouched.

steps:
1. In `ChildViewHolder` (91), add two `private val`s mirroring `ChatShareTargetAdapter.ChildViewHolder`: `private val backgroundColor = ContextCompat.getColor(itemView.context, R.color.multi_select_grey)` and `private val textColor = ContextCompat.getColor(itemView.context, R.color.daynight_textColor)`.
2. In `bind` (92–98), replace the two inline `ContextCompat.getColor(...)` calls at lines 94–95 with `backgroundColor` and `textColor` respectively. Everything else in `bind` (the `tvDrawerTitle.text`, `setOnClickListener`, `createCheckbox`) stays identical.

acceptance: `./gradlew testDefaultDebugUnitTest` green (no test references `ResourcesTagsAdapter`). The tag drawer rows still render the grey background and day/night text color identically; scrolling is unchanged visually.

size budget: ~5 changed lines, 1 file
out of scope: no `ParentViewHolder` changes, no new constructor params, no layout/XML changes.

---

### 9. Cache the two selection colors in `UserArrayAdapter` (roadmap 7; also 6)

context: `UserArrayAdapter` resolves two constant colors per bind: `onBindViewHolder` (lines 52–81) calls `ContextCompat.getColor(context, R.color.md_grey_300)` and `ContextCompat.getColor(context, android.R.color.transparent)` at lines 66/68 (full bind) and again at 43/45 (the `PAYLOAD_SELECTION` partial bind at 38–50). This adapter backs user-selection lists (e.g. login/become-member), which can list many users, so the two lookups run per row and again on every selection toggle. `StatsAdapter` (lines 20–21) and `ProgressGridAdapter` (21–29) already cache exactly this pair as adapter-level `by lazy`/`val`s.

files: `app/src/main/java/org/ole/planet/myplanet/ui/user/UserArrayAdapter.kt` — the adapter class (16–86). Leave the `DiffUtils` callback, `avatarSize` logic, `ViewHolder`, and the click/`notifyItemChanged` selection plumbing untouched.

steps:
1. Add two `private val`s on the adapter (mirroring `StatsAdapter`): `private var selectedBgColor = 0` and `private var transparentColor = 0`, lazily resolved on first use — or, since the adapter gets a context on the first `onCreateViewHolder`/bind, resolve them once in `onCreateViewHolder` (where `avatarSize` is already cached at lines 31–33) using `parent.context`, storing into the two fields. Either formulation computes each color exactly once.
2. In `onBindViewHolder` (full, 52–81), replace lines 66/68's `ContextCompat.getColor(...)` with the cached `selectedBgColor`/`transparentColor`.
3. In the payload `onBindViewHolder` (38–50), replace lines 43/45's `ContextCompat.getColor(...)` with the same cached values (drop the now-redundant local `context` if it becomes unused in that branch).

acceptance: `./gradlew testDefaultDebugUnitTest` green (no `UserArrayAdapter` test exists). User-selection rows still highlight the selected user in `md_grey_300` and render unselected rows transparent; tapping a row still re-highlights correctly via the `PAYLOAD_SELECTION` path.

size budget: ~10 changed lines, 1 file
out of scope: no `ItemUserBinding`/layout changes, no selection-state model changes, no `DiffUtils` changes.

---

### 10. Hoist the `bg_primary` drawable to the ViewHolder and cache the two tint colors in `FeedbackAdapter` (roadmap 7; also 6)

context: `FeedbackAdapter.onBindViewHolder` (39–75) does, on every feedback-row bind: `ContextCompat.getColor(context, R.color.mainColor)` and `... R.color.md_amber_500` (53–54) plus `ContextCompat.getDrawable(context, R.drawable.bg_primary)` twice (56–57, once each for `tvPriority` and `tvStatus`). The two colors are constants; the drawable (`bg_primary`, a `GradientDrawable` shape) is the same resource for every row and only the per-view tint (`ViewCompat.setBackgroundTintList`, 59–66) differs per bind. Inflating the drawable and resolving the colors per bind is unnecessary — `HealthExaminationAdapter` (46–48) and `ChatShareTargetAdapter` (61–62) already cache constants in the ViewHolder, and `TintTest` covers the `bg_primary` tint mechanism.

files: `app/src/main/java/org/ole/planet/myplanet/ui/feedback/FeedbackAdapter.kt` — the adapter (18–79). Leave the `DiffUtils` callback, `onCreateViewHolder`, the contentDescription construction (48–51), and the click listener (68–74) untouched. Do not touch `FeedbackDetailActivity` or `bg_primary.xml`.

steps:
1. Cache the two colors as adapter-level `private val`s resolved once from the first bind context (or store them as fields filled in `onCreateViewHolder` from `parent.context`, the same lifecycle used elsewhere) — `primaryColor` (`R.color.mainColor`) and `greyColor` (`R.color.md_amber_500`).
2. In `FeedbackViewHolder` (77–78), set each view's background once at construction: `rowFeedbackBinding.tvPriority.background = ContextCompat.getDrawable(rowFeedbackBinding.root.context, R.drawable.bg_primary)` and likewise for `tvStatus` — two separate drawable instances per ViewHolder so per-view `setBackgroundTintList` stays isolated across recycled holders.
3. In `onBindViewHolder` (39–75), drop lines 56–57 (the per-bind `getDrawable` calls) and the `ContextCompat.getColor` calls at 53–54; use the cached colors in the two `ColorStateList.valueOf(...)` arguments at 61 and 65. The tint conditions (`"yes".equals(feedback.priority, ignoreCase = true)` at 61, `"open".equals(feedback.status, ignoreCase = true)` at 65) are unchanged.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.ui.feedback.TintTest"` green (standalone drawable-tint test, unaffected). `./gradlew testDefaultDebugUnitTest` green. The feedback list still shows priority/status badges tinted `mainColor` for `yes`/`open` and `md_amber_500` otherwise, with the rounded `bg_primary` shape behind each.

size budget: ~14 changed lines, 1 file
out of scope: no `bg_primary.xml` changes, no `FeedbackDetailActivity` changes, no new tint resources.

---

self-check (verified before output):
- exactly 10 tasks ✓
- no file in two tasks (10 distinct files) ✓
- every cited path opened and line-confirmed ✓
- every task has all 7 template sections ✓
- no task under 15 lines ✓
- no task touches a file from the open-PR (BLOCKED) set ✓
