# myPlanet — Performance Quick-Win Work Orders

date: 2026-08-27
base commit: 89fd72c251df68ed01094091d4de7ba7a2571ebe (master)
open PRs checked (38): 16293, 16274, 16270, 16258, 16257, 16192, 16101, 16096, 15951, 15825, 15824, 15820, 15808, 15699, 15559, 15519, 15412, 15267, 15266, 15226, 15198, 15158, 15108, 14960, 14893, 14883, 14650, 14427, 13928, 13848, 13657, 13604, 13415, 13355, 13287, 10993, 8175, 4075

Every file below was opened and confirmed to exist, and confirmed NOT to appear in
any of the 38 open PRs (off-limits list built from `gh pr list --state open`).
No file appears in more than one task. No task adds a dependency or writes
implementation code — each is a verbatim work order for an executing agent.

roadmap key: 1 = data layer · 3 = viewmodel/use-case · 6 = compose migration ·
7 = performance · 8 = code health/tests · 9 = KMP core · 10 = compose multiplatform.

---

### 1. replace O(n*m) membership scan in achievement resource dialog with a Set lookup (roadmap 1+7+8)

context: `EditAchievementFragment.kt:480` builds the "add resources" checkbox
list. Inside `createResourceList`, the loop `for (i in list.indices)` calls
`prevList.contains(list[i].title)` where `prevList: List<String?>`. `List.contains`
is a linear scan, so the whole block is O(n*m) — n resources × m previously
selected titles. On a member with many achievements this dialog noticeably
stutters when opened.

files: `app/src/main/java/org/ole/planet/myplanet/ui/user/EditAchievementFragment.kt`
— method `createResourceList` (line 479). Leave `CheckboxAdapter` and the call
sites of `createResourceList` untouched.

steps:
1. At the top of `createResourceList`, build a lookup set:
   `val prevSet = prevList.filterNotNull().toHashSet()`.
2. Replace the predicate `prevList.contains(list[i].title)` with
   `list[i].title in prevSet`.
3. Remove now-unused imports if any were introduced (none expected).
4. Build and run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest` green; the "Add Resources"
dialog in Edit Achievements still pre-checks exactly the previously selected
resources and opens without the per-row lag.

size budget: ~3 changed lines, 1 file

out of scope: do not change `CheckboxAdapter`, do not change how `prevList` is
produced by callers, do not touch `MyLibrary.title`.

---

### 2. parse the resource URL once in getSDPathFromUrl instead of twice (roadmap 1+7)

context: `FileUtils.kt:82` `getSDPathFromUrl` calls both `getIdFromUrl(url)`
(line 149, which does `url.toUri().pathSegments` + `indexOf("resources")`) and
`getResourceRelativePathFromUrl(url)` (line 86, which repeats
`url.toUri()?.pathSegments` + `indexOf("resources")`). The same URI is parsed
and the same `pathSegments`/`indexOf` work is computed twice per call.
`getSDPathFromUrl` is reached from `checkFileExist` (line 127), which
`InlineResourceAdapter` calls per visible resource row during bind — a hot path.

files: `app/src/main/java/org/ole/planet/myplanet/utils/FileUtils.kt`
— `getSDPathFromUrl` (line 82), and you may read but must NOT change the public
signatures of `getIdFromUrl` (line 149) or `getResourceRelativePathFromUrl`
(line 86); they are called from elsewhere. Do not touch `getFileNameFromUrl`.

steps:
1. In `getSDPathFromUrl`, parse the URI once and derive both the resource id
   (the segment after `"resources"`) and the relative path (the segments after
   the id) from the single `pathSegments` list, preserving the exact fallback
   to `getFileNameFromUrl(url)` that `getResourceRelativePathFromUrl` uses on
   every error/missing-segment branch.
2. Keep `getIdFromUrl` and `getResourceRelativePathFromUrl` as-is for their
   other callers; only `getSDPathFromUrl` stops calling both.
3. Verify behavior parity: same `File` returned for a `resources/<id>/<rel>`
   URL and for a malformed URL (falls back to filename).
4. Build and run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest` green (watch
`FileUtilsTest` if present); resource rows still correctly show downloaded/
offline state via `checkFileExist`.

size budget: ~25 changed lines, 1 file

out of scope: no changes to `DownloadService` or adapters; do not alter
`getFileNameFromUrl`.

---

### 3. collapse the two-pass filter+map in cached my-life loading into one pass (roadmap 1+7)

context: `LifeRepositoryImpl.kt:95` returns
`cached.filter { it.isVisible }.map { item -> MyLife(...) }`. This materializes
an intermediate list from `filter`, then iterates it again with `map`. The cache
is read on the my-life screen when the DB copy is empty, so it runs on the UI
path for the life list.

files: `app/src/main/java/org/ole/planet/myplanet/repository/LifeRepositoryImpl.kt`
— the `cached.filter { it.isVisible }.map { ... }` expression starting at line
95. Leave the surrounding cache-read logic and `getMyLifeByUserId` untouched.

steps:
1. Replace `cached.filter { it.isVisible }.map { item -> ... }` with a single
   `buildList { }` (or `mapNotNull`) that appends the `MyLife` only when
   `item.isVisible`, constructing the `MyLife` in the same `apply { }` block.
2. Preserve field assignments exactly: `isVisible`, `weight`, `imageId`,
   `userId`, `title`.
3. Build and run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest` green; the my-life list still
shows only visible items in the same order when the DB is empty and the cache
is used.

size budget: ~8 changed lines, 1 file

out of scope: no DAO changes, no changes to `CachedMyLifeItem`.

---

### 4. read the first conversation query once per row in ChatHistoryAdapter (roadmap 7+8)

context: `ChatHistoryAdapter.kt:104` evaluates
`item.conversations?.get(0)?.query` three times in one `onBindViewHolder` —
once for `chatTitle.text`, once for `contentDescription`, once for the `chatTitle`
field. Each access re-traverses the nullable list and indexes element 0. It also
guards with `item.conversations != null && item.conversations?.isNotEmpty() == true`,
which reads `conversations` twice for the emptiness check.

files: `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatHistoryAdapter.kt`
— `onBindViewHolder` (line 102). Do not touch the click-listener or
`onBindViewHolder(..., payloads)`.

steps:
1. At the top of the `if` branch, hoist:
   `val firstQuery = item.conversations?.firstOrNull()?.query`.
2. Use `firstQuery` for `chatTitle.text`, `contentDescription`, and the
   `chatTitle` field assignment.
3. Simplify the guard to a single read, e.g.
   `val conversations = item.conversations; if (!conversations.isNullOrEmpty()) { ... } else { ... }`.
4. Build and run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest` green; chat history rows still
show the first conversation's query as the title, falling back to `item.title`
when there are no conversations.

size budget: ~6 changed lines, 1 file

out of scope: no changes to the `ChatMessage`/conversation model or the diff
callback.

---

### 5. compute member display name once per row in MembersAdapter (roadmap 7+8)

context: `MembersAdapter.kt:104` binds the title with
`if (member.toString() == " ") member.name else member.toString()`, calling
`member.toString()` twice per row. `UserEntity.toString()` returns
`"$firstName $lastName"` (or similar), allocating a fresh String each call, and
this runs in every visible member row during scroll.

files: `app/src/main/java/org/ole/planet/myplanet/ui/teams/members/MembersAdapter.kt`
— `onBindViewHolder` (line 100), the `tvTitle` assignment at line 104. Leave
the leader/visit-date binding and overflow-menu logic untouched.

steps:
1. Hoist `val memberString = member.toString()` before the conditional.
2. Use it in the check and the assignment:
   `binding.tvTitle.text = if (memberString == " ") member.name else memberString`.
3. Build and run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest` green; the members list still
shows the member's full name, falling back to `name` when the computed name is
a single space.

size budget: ~2 changed lines, 1 file

out of scope: do not change `UserEntity.toString()`.

---

### 6. convert team course availability filter from List to Set membership (roadmap 1+7+8)

context: `TeamCoursesFragment.kt:109` computes
`val availableCourses = allCourses.filter { it.courseId !in existingIds }`
where `existingIds` comes from `teamsRepository.getTeamCourseIds(teamId)`, which
returns `List<String>` (`TeamsRepository.kt:59`). `!in` on a `List` is a linear
scan, so the filter is O(n*m) — all courses × already-added course ids. The
"Add Course" dialog runs this on open.

files: `app/src/main/java/org/ole/planet/myplanet/ui/teams/courses/TeamCoursesFragment.kt`
— `showAddCourseDialog` (line 99), the `availableCourses` line at 109. Do NOT
modify `TeamsRepository`/`TeamsRepositoryImpl` (the impl is large and
PR-adjacent); only the call site changes.

steps:
1. Change the local to a set for O(1) membership:
   `val existingIds = teamsRepository.getTeamCourseIds(teamId).toHashSet()`.
2. Leave the `allCourses.filter { it.courseId !in existingIds }` line as-is —
   it now runs against a `Set`.
3. Build and run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest` green; the "Add Course" dialog
still lists only courses the team does not already have, in the same order.

size budget: ~1 changed line, 1 file

out of scope: no repository or DAO changes; do not alter `getTeamCourseIds`'s
return type.

---

### 7. hoist the search-string lowercase out of the collections filter lambda (roadmap 7+8)

context: `CollectionsFragment.kt:115` filters tags with
`it.name?.lowercase(Locale.ROOT)?.contains(charSequence.lowercase(Locale.ROOT))`.
`charSequence.lowercase(Locale.ROOT)` is evaluated inside the `filter` lambda,
so the search string is re-lowercased once per tag on every keystroke. The tag
list can be large, and this runs on the text-change flow.

files: `app/src/main/java/org/ole/planet/myplanet/ui/resources/CollectionsFragment.kt`
— `filterTags` (line 112). Leave `buildTagDataList`, `selectedItemsList`, and the
selection-toggle logic untouched.

steps:
1. Before the `list.filter { }`, hoist:
   `val query = charSequence.lowercase(Locale.ROOT)`.
2. Use `query` inside the lambda:
   `it.name?.lowercase(Locale.ROOT)?.contains(query) == true`.
3. Build and run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest` green; typing in the
collections search box still filters tags case-insensitively, matching the
prior behavior.

size budget: ~2 changed lines, 1 file

out of scope: no changes to `buildTagDataList` or the tag entity.

---

### 8. parse each task notification's date once instead of twice in NotificationsViewModel (roadmap 3+7)

context: `NotificationsViewModel.kt` runs a regex parse twice for task
notifications that have no team-name match. Line 80, inside the title-collection
pass `.mapNotNull { parseTaskDate(it.message)?.first }`, parses the date for the
subset of task notifications whose `relatedId` is empty or missing from
`taskTeamNames`. Then `formatNotification` (line 343) calls
`parseTaskDate(notification.message)` again for every task notification to
render it. `parseTaskDate` (line 305) compiles-matches `TASK_DATE_PATTERN`
against the message each time.

files: `app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModel.kt`
— `loadNotifications`/the classification block (lines 66-94) and
`formatNotification` (line 335). Leave the companion `parseTaskDate` (line 305)
and `TASK_DATE_PATTERN` (line 304) as-is.

steps:
1. After classifying task notifications, build a single cache keyed by message:
   `val parsedByMessage = taskNotifications.associateBy { it.message }
   .mapValues { parseTaskDate(it.key) }` (parse each task message exactly once).
2. In the title-collection pass (line 80), read
   `parsedByMessage[it.message]?.first` instead of re-calling `parseTaskDate`.
3. Pass `parsedByMessage` into `formatNotification` (add a parameter defaulting
   to `emptyMap()`), and in the `"task"` branch look up
   `parsedByMessage[notification.message]` instead of calling
   `parseTaskDate(notification.message)` again.
4. Update the single call site of `formatNotification` (the final `.map { }`) to
   pass `parsedByMessage`.
5. Build and run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest` green; task notifications still
render with the parsed title + date and the correct team name.

size budget: ~25 changed lines, 1 file

out of scope: do not change `parseTaskDate`'s regex or signature; do not alter
the repository batching.

---

### 9. read download-queue sets and the file name once in onDownloadComplete (roadmap 7+8)

context: `DownloadService.kt:502` reads
`preferences.getStringSet(PRIORITY_DOWNLOADS_KEY, ...)?.count { it !in processedUrls }`
to get `remainingPriority`, then line 503 calls `getRemainingCount()` which
reads BOTH `PRIORITY_DOWNLOADS_KEY` and `PENDING_DOWNLOADS_KEY` again (lines
177-178) and allocates a union `priorityUrls + pendingUrls`. So the priority set
is read twice per completion. Separately, `getFileNameFromUrl(url)` (a URI parse
+ `URLDecoder.decode`) is called twice with the same `url` — line 513 for
`fileName` and line 516 inside the notification `setContentText`. This runs once
per file in a batch download.

files: `app/src/main/java/org/ole/planet/myplanet/services/DownloadService.kt`
— `onDownloadComplete` (line 491). Leave `getRemainingCount` (line 176) and its
other callers (lines 152, 198, 339, 461) untouched. Do not touch `FileUtils`.

steps:
1. In `onDownloadComplete`, read both sets once:
   `val prioritySet = preferences.getStringSet(PRIORITY_DOWNLOADS_KEY, emptySet()) ?: emptySet()`
   and `val pendingSet = preferences.getStringSet(PENDING_DOWNLOADS_KEY, emptySet()) ?: emptySet()`.
2. Compute `remainingPriority = prioritySet.count { it !in processedUrls }` and
   `remaining = (prioritySet + pendingSet).count { it !in processedUrls }` from
   those locals, and delete the `getRemainingCount()` call on line 503 within
   this method only.
3. Hoist `val fileName = getFileNameFromUrl(url)` and use it for both the
   `Download().fileName` assignment (line 513) and the `setContentText` (line 516).
4. Build and run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest` green; batch downloads still
report correct "remaining" counts and the completion notification still shows
the downloaded file name.

size budget: ~10 changed lines, 1 file

out of scope: no changes to `getRemainingCount`'s body or its other callers; no
changes to `FileUtils.getFileNameFromUrl`.

---

### 10. stop double-fetching the row item and re-resolving the text color in CoursesProgressAdapter (roadmap 7+8)

context: `CoursesProgressAdapter.kt:30` `onBindViewHolder` fetches
`val item = getItem(position)`, then calls `showStepMistakes(position, binding)`
which at line 47 calls `getItem(position)` a second time for the same row.
`showStepMistakes` also calls `ContextCompat.getColor(context, R.color.daynight_textColor)`
(line 53) on every bind that has step mistakes, re-resolving the color per row
during scroll.

files: `app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesProgressAdapter.kt`
— `onBindViewHolder` (line 30) and `showStepMistakes` (line 46). Leave the
view-recycling/`removeViews` logic and `DIFF_CALLBACK` untouched.

steps:
1. Change `showStepMistakes` to accept the already-fetched item:
   `private fun showStepMistakes(item: CoursesProgressRow, binding: RowMyProgressBinding)`
   and drop its internal `val item = getItem(position)` (line 47).
2. Update the call site in `onBindViewHolder` to pass `item`.
3. Hoist the text color to a `private val textColor by lazy { ContextCompat.getColor(context, R.color.daynight_textColor) }` field, and use `textColor` instead of the per-bind `ContextCompat.getColor(...)` call at line 53.
4. Build and run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest` green; the "My Progress"
course rows still expand to show per-step mistake counts with the correct text
color.

size budget: ~8 changed lines, 1 file

out of scope: no changes to the row-inflation/recycling logic or the diff
callback; do not alter `CoursesProgressRow`.
