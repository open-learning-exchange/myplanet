# myPlanet refactor work orders — performance quick wins round

- **date**: 2026-09-17
- **base commit**: fcb4c53b2d1c4134789f1c07bd785d97d7f4215b (master)
- **open PRs checked**: 17255, 17254, 17222, 17187, 16624, 16623, 16594, 16270, 15951, 15825, 15824, 15820, 15808, 15559, 15519, 15412, 15267, 15266, 15226, 15198, 15108, 14960, 14893, 14883, 14650, 14427, 13928, 13848, 13657, 13604, 13415, 13355, 13287, 10993, 8175, 4075 — every file touched by any of these PRs is off-limits; every file cited below was confirmed absent from all of those PR file lists.
- **focus**: performance quick wins — micro-optimizations removable without rewrites. The codebase is already heavily optimized (cached formatters, lazy Gson, FileExistenceCache, chunked bulk queries, ListAdapter+DiffUtil with payloads), so the remaining wins are subtle leftovers: loop-invariant lookups inside loops, JSON re-parse getters, per-event accessibility assignments, duplicate accumulation on re-sync, non-atomic map-of-list mutation under parallel coroutines, and redundant filesystem stats on bind paths.

Each task is independently mergeable in any order. No file appears in more than one task. Every task states which roadmap number(s) it serves and, where true, how it also moves 9/10 forward.

---

### 1. hoist the per-attachment SharedPreferences read and dedupe attachments in MyLibrary.insertMyLibrary (roadmap 1+7)

context: `insertMyLibrary` runs once per library row during sync. Inside the `attachmentsObj.entrySet().forEach` loop it re-reads the CouchDB base URL from SharedPreferences for every attachment — `resourceRemoteAddress = "${params.spm.getCouchdbUrl().ifEmpty { "http://" }}/resources/$resourceId/$key"` (model/MyLibrary.kt:226) — and stats the filesystem per attachment via `FileUtils.checkFileExist` (model/MyLibrary.kt:228). Worse, `attachmentList.add(realmAttachment)` (model/MyLibrary.kt:223) appends unconditionally, so the attachments array grows duplicates on every re-sync of the same document.

files: app/src/main/java/org/ole/planet/myplanet/model/MyLibrary.kt — `MyLibrary.insertMyLibrary` (companion, ~lines 184-270). Leave `isResourceOffline` (lines 137-140), `serializeLibrary`, and the rest of the companion alone.

steps:
1. Resolve `params.spm.getCouchdbUrl().ifEmpty { "http://" }` once before the `entrySet().forEach` loop into a local `baseUrl` val and use it inside the loop.
2. Keep the `FileUtils.checkFileExist` call inside the loop but only for keys without `/` (the existing `if (key.indexOf("/") < 0)` guard stays).
3. Before building `attachmentList`, capture the set of existing attachment names (`this.attachments?.map { it.name }?.toSet()`); inside the loop, skip `add` when `key` is already in that set.
4. Run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest` green; re-syncing a library document does not grow its attachment list (verify by syncing the same library twice and checking `attachments.size` is stable); offline flag still flips correctly when a resource file is present on disk.

size budget: ~15 changed lines, 1 file

out of scope: do not change the Room entity shape, the Attachment class, or any sync-manager code; do not move the fs stat off the sync thread — the callers already run it off-main.

---

### 2. hoist the loop-invariant JSON read in ExamQuestion.insertCorrectChoice (roadmap 1+7)

context: In the else-branch of `insertCorrectChoice`, the loop `for (a in 0 until array.size())` re-evaluates `JsonUtils.getString("correctChoice", question)` on every element of the answer array (model/ExamQuestion.kt:103) even though `question` never changes during the loop. `getString` does a `has`/`get`/null-check chain each call, so a 5-choice question pays 5 redundant JSON lookups; the same getter pattern also means `correctChoiceList` is reassigned on every matching element instead of short-circuiting after the first match.

files: app/src/main/java/org/ole/planet/myplanet/model/ExamQuestion.kt — companion `insertCorrectChoice` (~lines 96-108). Leave `correctChoiceArray` getter, `serializeQuestions`, `insertSubjects`, and all other functions alone.

steps:
1. In the else branch, read `JsonUtils.getString("correctChoice", question)` once into a local val before the `for` loop.
2. Inside the loop, compare that val to `JsonUtils.getString("id", res)`; on a match assign `correctChoiceList` and `break` (only the first matching id should win — preserve that).
3. Run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest` green; a question with a scalar `correctChoice` value still gets the right `correctChoiceList` after insert (check via the existing exam-parsing tests).

size budget: ~8 changed lines, 1 file

out of scope: do not restructure the questions-insert flow, do not touch the `if` branch (JsonArray path), and do not add caching to `correctChoiceArray` — that getter stays as-is.

---

### 3. convert the diagnosis lookup to a set and hoist per-checkbox styling in HealthExaminationActivity (roadmap 7)

context: `preloadCustomDiagnosis` builds `mainList = listOf(*arr)` (ui/health/HealthExaminationActivity.kt:201) and then calls `mainList.contains(s)` inside the `conditionsMap` loop — an O(n·m) linear scan where a `Set` gives O(1) membership. Separately, `showCheckbox` re-resolves `ContextCompat.getColorStateList(this, R.color.daynight_textColor)` (line 216), `ContextCompat.getColor` (line 217), and `dpToPx(8)` four times (line 222) for every checkbox in the diagnosis list — all loop-invariant.

files: app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationActivity.kt — `preloadCustomDiagnosis` (lines 199-209) and `showCheckbox` (lines 211-228). Leave `showOtherDiagnosis`, `otherConditions`, and the activity's other listeners alone.

steps:
1. In `preloadCustomDiagnosis`, change `listOf(*arr)` to `setOf(*arr)` so `mainList` is a `Set<String>`; keep the `!mainList.contains(s)` check.
2. In `showCheckbox`, before the `for (s in arr)` loop, hoist `ContextCompat.getColorStateList(this, R.color.daynight_textColor)` into a local `tintList` val, `ContextCompat.getColor(this, R.color.daynight_textColor)` into a `textColor` val, and `dpToPx(8)` into a `padPx` val.
3. Inside the loop, assign `c.buttonTintList = tintList`, `c.setTextColor(textColor)`, `c.setPadding(padPx, padPx, padPx, padPx)`.
4. Run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest` green; opening an examination still renders the full diagnosis checkbox list with correct checked state and custom-diagnosis prefill.

size budget: ~12 changed lines, 1 file

out of scope: do not restructure the conditions map or move the view creation off the calling thread; do not add new fields to the activity.

---

### 4. memoize the re-parsing valueChoicesArray getter in Answer (roadmap 1+7)

context: `Answer.valueChoicesArray` is a `@get:Ignore` derived getter that rebuilds a `JsonArray` and calls `JsonUtils.gson.fromJson(choice, JsonObject::class.java)` for every element of `valueChoices` on every access (model/Answer.kt:24-34). It is hit once per answer inside `createObject` on the upload path via `serializeAnswer`, so uploading a submission re-parses every choice string of every answer every time — pure CPU waste with no memoization.

files: app/src/main/java/org/ole/planet/myplanet/model/Answer.kt — the `valueChoicesArray` getter (lines 23-34). Leave `serializeAnswer`, `createObject`, and the entity fields alone.

steps:
1. Add a private `@Ignore` var (e.g. `cachedChoicesArray`) alongside a marker of what `valueChoices` list it was built from (an identity or content check is fine — the list is only set once before serialization).
2. In the getter, return the cached array when `valueChoices` is unchanged; otherwise rebuild it and store it.
3. Keep the null/empty `valueChoices` behavior identical — empty `JsonArray`.
4. Run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest` green; serialization output for an answer with choices is byte-identical to before (verify via existing submission-upload serialization tests or a quick manual JsonAssert).

size budget: ~15 changed lines, 1 file

out of scope: do not change the entity schema, DAO, or any repository; do not make the cache observable or share it across instances.

---

### 5. resolve the local resource directory once in WebViewActivity instead of per navigation (roadmap 7)

context: `getLocalResourceDirectory(resourceId)` runs `getExternalFilesDir` plus two `canonicalFile` resolutions (ui/viewer/WebViewActivity.kt:294-312). Today it is re-computed in `onCreate` (line 67), `setupWebView` (line 107), `setupAssetLoader` (line 174), and inside `checkUrlSafety` (line 289) which fires on every `onPageStarted`/`shouldOverrideUrlLoading` — so every link the user taps re-resolves the same directory and re-calls `getExternalFilesDir` (line 290) per URL check.

files: app/src/main/java/org/ole/planet/myplanet/ui/viewer/WebViewActivity.kt — `onCreate`, `setupWebView`, `setupAssetLoader`, `checkUrlSafety`, `getLocalResourceDirectory`. Leave `WebViewClient` callbacks, `ResourceViewerFragment`, and other viewer files alone.

steps:
1. Add a private field initialized once in `onCreate` (or as a `lazy` val) holding the resolved `File?` from `getLocalResourceDirectory(intent.getStringExtra("RESOURCE_ID"))`.
2. Replace the three re-resolutions in `setupWebView`/`setupAssetLoader`/`checkUrlSafety` with the cached field.
3. In `checkUrlSafety`, also hoist the per-call `getExternalFilesDir` (line 290) into the same cached value or a second cached field — keep the `canonicalFile` safety checks that guard against path traversal.
4. Run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest` green; opening a downloaded resource in the WebView still loads it, and navigating internal links still enforces the URL-safety check (a `file://` URL outside the allowed dir is still blocked).

size budget: ~20 changed lines, 1 file

out of scope: do not change the URL-allowlist semantics or the `canonicalFile` containment logic itself; do not touch WebViewAssetLoader registration order.

---

### 6. collapse the double file resolution in InlineResourceAdapter.updateStatusAndPreview (roadmap 7)

context: `updateStatusAndPreview` first calls `FileUtils.checkFileExist(context, UrlUtils.getUrl(resource))` (ui/courses/InlineResourceAdapter.kt:177-179), which parses the URL and stats the resolved file. It then calls `getFileCacheKeyIfExist` / the `show*Preview` helpers that run `file.exists()` — and `file.lastModified()`/`file.length()` — on the same resolved file again (lines 206, 223, 236, 312-318). One `File` resolution and one `exists`/`lastModified`/`length` stat per row is all that is needed.

files: app/src/main/java/org/ole/planet/myplanet/ui/courses/InlineResourceAdapter.kt — `updateStatusAndPreview` (lines 162-203) and `getFileCacheKeyIfExist` (lines 312-318). Leave `previewJob` cancellation, `textCache`, `htmlCoverCache`, and the payload-based `onBindViewHolder` overload alone.

steps:
1. In `updateStatusAndPreview`, resolve the file once — `val file = FileUtils.getSDPathFromUrl(context, UrlUtils.getUrl(resource))` (or the same helper `checkFileExist` uses internally) — then do a single `file.exists()` check and reuse that `File` for the rest of the method.
2. Pass the already-resolved `File` down into the preview helpers instead of letting them re-resolve; if a helper signature must stay, only re-stat on it once.
3. In `getFileCacheKeyIfExist`, reuse the single `exists()`/`lastModified`/`length` results already gathered rather than re-statting.
4. Run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest` green; the inline-resource preview in a course step still shows the correct downloaded/not-downloaded state and the same preview image/text as before.

size budget: ~25 changed lines, 1 file

out of scope: do not change the preview-loading coroutine structure or the cache keys' format; do not touch the adapter's diff/payload logic.

---

### 7. move per-event accessibility strings in LifeAdapter to bind time (roadmap 7)

context: `onBindViewHolder` installs a `setOnTouchListener` that sets `contentDescription = context.getString(R.string.drag, myLife.title)` inside the touch callback (ui/life/LifeAdapter.kt:67) — which fires on every MotionEvent (down, move, up), not just once. The same pattern repeats in `visibility.setOnClickListener` (line 74). Both strings only depend on `myLife.title`, which is fixed at bind time, so they can be assigned once per bind instead of per event.

files: app/src/main/java/org/ole/planet/myplanet/ui/life/LifeAdapter.kt — `onBindViewHolder` (lines 48-83). Leave `updateVisibility`, `changeVisibility`, `ItemReorderHelper`, `LifeViewHolder`, and the companion `fragmentCache`/`DIFF_CALLBACK` alone.

steps:
1. In `onBindViewHolder`, directly assign `holder.dragImageButton.contentDescription = context.getString(R.string.drag, myLife.title)` right after the other binds (near line 58's `imageView.contentDescription`).
2. Assign `holder.visibility.contentDescription = context.getString(R.string.visibility_of, myLife.title)` the same way.
3. Remove the `contentDescription` assignments from inside the touch listener (line 67) and the click listener (line 74); keep the listeners' drag/visibility behavior intact.
4. Run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest` green; TalkBack still announces the correct drag and visibility labels on the life screen, and drag-reorder and show/hide still work.

size budget: ~8 changed lines, 1 file

out of scope: do not change drag behavior, `ItemReorderHelper`, or the reorder callback; do not alter the visibility toggle logic.

---

### 8. make SyncTimeLogger's per-key lists thread-safe and stop splitting per call (roadmap 8+7)

context: `apiCallTimes`, `dbOperationTimes`, and `detailedLogs` are `ConcurrentHashMap<String, MutableList<...>>` (utils/SyncTimeLogger.kt:36-38), but the appends use `getOrPut(key) { mutableListOf() }.add(...)` (lines 167, 184, 195). `getOrPut` on `ConcurrentHashMap` is not atomic, and the returned `ArrayList` is unsynchronized — parallel sync coroutines can lose entries or race. Separately, `extractProcessName` does `endpoint.split("/")` (lines 331-344) on every API call, allocating an array for a string where `substringAfterLast` suffices.

files: app/src/main/java/org/ole/planet/myplanet/utils/SyncTimeLogger.kt — the map declarations (lines 36-38), the three append sites (lines 167, 184, 195), and `extractProcessName` (lines 331-344). Leave the log-formatting/reporting code and callers (`SyncManager`, `ApiInterface` interceptors) alone.

steps:
1. Replace `getOrPut(key) { mutableListOf() }.add(x)` with `map.computeIfAbsent(key) { Collections.synchronizedList(mutableListOf()) }.add(x)` (or `getOrPut` + `synchronized(list) { list.add(x) }` — pick the synchronized-list version for simpler diff).
2. Do this for all three append sites so every mutation goes through the synchronized list.
3. In `extractProcessName`, replace `endpoint.split("/")` (and any `.split("/").last()`-style usage) with `endpoint.substringAfterLast('/')`, and adjust the trailing-segment handling to keep identical output.
4. Run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest` green; sync logging still records per-endpoint timing entries and the report output format is unchanged.

size budget: ~20 changed lines, 1 file

out of scope: do not change the log file format, the public API of `SyncTimeLogger`, or add new fields; do not remove entries' deduping behavior if present.

---

### 9. memoize the derived messageList getter and fix stale cache invalidation in Feedback (roadmap 1+7)

context: `Feedback.messageList` rebuilds `List<FeedbackReply>` objects on every access even though `parsedMessages` already caches the `JsonArray` (model/Feedback.kt — `messageList`/`message` getters ~lines 41-82). Also, `setMessages(JsonArray)` writes `this.messages` without clearing `cachedMessages`, so the cached parse can go stale — a correctness hazard next to the wasted allocation.

files: app/src/main/java/org/ole/planet/myplanet/model/Feedback.kt — the `messageList`, `message` getters and `setMessages`/`messages` field handling. Leave `FeedbackReply`, `FeedbackDao`, `FeedbackRepositoryImpl`, and the feedback UI alone.

steps:
1. Keep the existing `cachedMessages`/`parsedMessages` mechanism; extend it to also cache the derived `List<FeedbackReply>` so `messageList` doesn't rebuild it per call.
2. In `setMessages` (and anywhere `messages` is reassigned), invalidate both the raw `JsonArray` cache and the derived list cache so stale parses can't leak.
3. Ensure `message` getter uses the same memoized list rather than re-deriving.
4. Run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest` green; the feedback detail screen still lists replies correctly, and a feedback whose `messages` is updated shows the new content immediately (no stale cache).

size budget: ~20 changed lines, 1 file

out of scope: do not change `Feedback`'s entity shape, the repository, or the adapter; do not add a Room column or DAO change.

---

### 10. stop allocating a Typeface on every group-header bind in ChatShareTargetAdapter (roadmap 7)

context: `GroupViewHolder.bind` calls `listTitleTextView.setTypeface(null, Typeface.BOLD)` (ui/chat/ChatShareTargetAdapter.kt:51). `setTypeface(null, style)` goes through `Typeface.create(null, BOLD)` internally, allocating a new `Typeface` object every time the header rebinds — pure churn since the typeface never changes.

files: app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatShareTargetAdapter.kt — `GroupViewHolder` class (~line 45) and its `bind` (~line 51). Leave the other view holders and the adapter's bind dispatch alone.

steps:
1. In `GroupViewHolder`'s `init` block (or on first `bind`), set the bold typeface once on `listTitleTextView` — e.g. `listTitleTextView.setTypeface(listTitleTextView.typeface, Typeface.BOLD)` to reuse the existing typeface instead of `setTypeface(null, Typeface.BOLD)`.
2. Remove the per-bind `setTypeface(null, Typeface.BOLD)` call from `bind`.
3. Run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest` green; group headers in the share-target picker still render bold.

size budget: ~5 changed lines, 1 file

out of scope: do not change styling of other elements, do not refactor the adapter's diff logic, do not touch other viewers/fragments.
