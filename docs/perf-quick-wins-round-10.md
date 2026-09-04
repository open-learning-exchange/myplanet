# myPlanet — performance quick-win work orders (round 10)

date: 2026-09-04 · base commit: 9ff1273dc95f8cbd3590fca12ca821454b2e27bc (master, tag v0.69.36) ·
open PRs checked: 48 open PRs inspected via the GitHub API before writing; 7 review/merge-gated PRs
established the off-limits set (#15699, #15951, #16624, #16680, #16686, #16688, #15808, and the
Dependabot/jules/codex branches). No file touched below appears in any open PR's diff. Every path,
class and function below was opened and confirmed to exist at the base commit.

Each task serves one roadmap number and notes where it also nudges the KMP/compose-multiplatform
north stars (9/10). All tasks are independently mergeable in any order.

---

### 1. stop opening a network connection just to read a content type (roadmap 5+7, nudges 9)

context: `UploadRepositoryImpl.uploadAttachment` opens
`file.toURI().toURL().openConnection()` (cast to `URLConnection`) solely to read
`connection.contentType`, then never reads a single byte from that connection. For a remote URL
this fires a wasted HTTP request on every attachment/photo/CV upload; for a local `file:` URL it
is pointless work. The project already has the correct local-only pattern:
`URLConnection.guessContentTypeFromName(file.name)` (used at `WebViewActivity.kt:161`) and
`FileUtils.getMimeType(name)` (used in `UploadManager.kt:362`), so this is a consistency fix too.
evidence: `app/src/main/java/org/ole/planet/myplanet/repository/UploadRepositoryImpl.kt`
(`uploadAttachment`, around the `openConnection()` call).

files: `app/src/main/java/org/ole/planet/myplanet/repository/UploadRepositoryImpl.kt` —
`uploadAttachment(file, destinationFormat, id, rev, name)`. Do NOT touch `FileUploader.kt`,
`PhotoUploader.kt`, `AchievementUploader.kt` or `UploadManager.kt` (open PRs / callers stay).

steps:
1. Replace the `openConnection()` + `connection.contentType` lookup with
   `URLConnection.guessContentTypeFromName(file.name) ?: "application/octet-stream"`.
2. Remove the now-unused `java.net.URLConnection` import if it becomes unused (keep `java.net.URL`
   only if still referenced; otherwise drop it too).
3. Leave the rest of the method (header map via `FileUploader.getHeaderMap`, request body, response
   handling) untouched.
4. Run the existing `UploadRepositoryImpl` unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest --tests
"org.ole.planet.myplanet.repository.UploadRepositoryImplTest"` green, and the full
`./gradlew testDefaultDebugUnitTest` green. Behaviour: uploads still send the correct
`Content-Type` for pdf/jpg/png/etc. with no extra network round-trip before the real upload.

size budget: ~6 changed lines, 1 file
out of scope: no changes to `FileUtils.getMimeType`, no changes to the upload callers, no new
abstraction for mime detection.

---

### 2. collapse the retry read-modify-write into single UPDATEs (roadmap 5+7, nudges 9)

context: `RetryRepositoryImpl.updateAttempt` and `markFailed` each do
`retryDao.findById(id)` → mutate the loaded `RetryOperation` → `retryDao.update(op)`. That is two
queries (a SELECT then an UPDATE) on every single retry attempt, and the entity load is pure
overhead — the DAO already does the identical transition for the sibling methods
`markInProgress` / `markCompleted` as one-line `@Query UPDATE`s. Lifting these two paths to direct
`@Query UPDATE` keeps them consistent with the siblings and halves the DB work per attempt.
evidence: `app/src/main/java/org/ole/planet/myplanet/repository/RetryRepositoryImpl.kt`
(`updateAttempt` around `findById`, `markFailed` around `findById`); sibling pattern at
`app/src/main/java/org/ole/planet/myplanet/data/room/dao/RetryDao.kt` (`markInProgress`,
`markCompleted`).

files:
- `app/src/main/java/org/ole/planet/myplanet/repository/RetryRepositoryImpl.kt` —
  `updateAttempt`, `markFailed`.
- `app/src/main/java/org/ole/planet/myplanet/data/room/dao/RetryDao.kt` — add two `@Query UPDATE`
  methods.

Do NOT touch `RetryOperation` (the `calculateNextRetryTime` helper stays), and leave
`markInProgress`/`markCompleted`/`recoverStuck` as they are.

steps:
1. In `RetryDao`, add `@Query("UPDATE retry_operation SET attemptCount = attemptCount + 1,
   lastAttemptTime = :now, nextRetryTime = :nextRetry, errorMessage = :message,
   httpCode = :httpCode, status = :status WHERE id = :id") suspend fun
   updateAttemptState(id: String, now: Long, nextRetry: Long, message: String?, httpCode: Int?,
   status: String): Int`. Derive `nextRetry` and `status` in the repository from
   `RetryOperation.calculateNextRetryTime(currentAttempt)` and the existing `>= maxAttempts` →
   `STATUS_ABANDONED` rule (read `attemptCount` by reusing the just-updated value; if a count read
   is still needed for `calculateNextRetryTime`, keep exactly one `findById` only there — do not
   add a second).
2. Have `updateAttempt` and `markFailed` call the new DAO method instead of `findById`+`update`.
   Preserve the existing status transitions exactly (`markFailed` sets `STATUS_PENDING` unless the
   new count reaches `maxAttempts`, in which case `STATUS_ABANDONED`).
3. Run the existing retry unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest --tests
"org.ole.planet.myplanet.repository.RetryRepositoryImplTest"` green, and full
`./gradlew testDefaultDebugUnitTest` green. Behaviour: a failed retry still increments
`attemptCount`, sets `lastAttemptTime`/`nextRetryTime`, and flips to `STATUS_ABANDONED` at
`maxAttempts`.

size budget: ~40 changed lines, 2 files
out of scope: no schema change (no `version` bump in `AppDatabase`), no change to
`RetryQueueWorker`, no new indexes.

---

### 3. hoist the repeated `Gson()`/`TypeToken` out of `SharedPrefManager.getSavedUsers` (roadmap 4+7, nudges 9)

context: `SharedPrefManager.getSavedUsers` constructs
`object : TypeToken<List<User>>() {}.type` on every call to deserialize the saved-users JSON, and
the login screen calls it on every refresh of the user list. The `TypeToken` (and its reflective
`type`) is constant and should live in a companion `val` like `Converters` already does for its
list types. `gson` is already an injected field, so only the type allocation is the waste.
evidence: `app/src/main/java/org/ole/planet/myplanet/services/SharedPrefManager.kt:76` — the inline
`object : TypeToken<List<User>>() {}.type`; compare the cached companion pattern at
`app/src/main/java/org/ole/planet/myplanet/data/room/Converters.kt` (`stringListType` etc.).

files: `app/src/main/java/org/ole/planet/myplanet/services/SharedPrefManager.kt` —
`getSavedUsers` (and its `setSavedUsers` caller can reuse the same type). Do NOT touch
`UserRepository`/`LoginViewModel`.

steps:
1. Add `private val savedUsersType = object : TypeToken<List<User>>() {}.type` to the companion
   object (or a `private val` near the existing `gson` field if companion is impractical).
2. Use `savedUsersType` in `getSavedUsers` instead of the inline `object : TypeToken...`.
3. If `setSavedUsers` does its own `toJson` with the same list type, leave it (it needs no type)
   — only the `fromJson` path changes.
4. Run the login/SharedPrefManager tests.

acceptance: `./gradlew testDefaultDebugUnitTest` green. Behaviour: the saved-users list on the
login screen still loads and serializes exactly as before.

size budget: ~6 changed lines, 1 file
out of scope: no change to the `User` model, no migration, no new fields.

---

### 4. fold the per-link loop and the set↔list round-trip in `MyCourse.saveConcatenatedLinksToPrefs` (roadmap 1+7, nudges 9)

context: `saveConcatenatedLinksToPrefs` deserializes the stored links into a `HashSet`, then runs
an explicit `for (link in linksToProcess) existingConcatenatedLinks.add(link)` loop (one `add` per
element) and finally serializes `existingConcatenatedLinks.toList()` — a redundant set→list copy
since `Gson.toJson` accepts a `Collection`. The loop is `addAll`, and the `.toList()` is removable.
evidence: `app/src/main/java/org/ole/planet/myplanet/model/MyCourse.kt:103-106` —
`for (link in linksToProcess) { existingConcatenatedLinks.add(link) }` and
`JsonUtils.gson.toJson(existingConcatenatedLinks.toList())`.

files: `app/src/main/java/org/ole/planet/myplanet/model/MyCourse.kt` —
`saveConcatenatedLinksToPrefs` (companion). Do NOT touch `SyncManager.kt` (the caller) or
`VoicesRepositoryImpl.saveConcatenatedLinksToPrefs` (a separate method).

steps:
1. Replace the `for (link in linksToProcess) { ... add(link) }` block with
   `existingConcatenatedLinks.addAll(linksToProcess)`.
2. Change `JsonUtils.gson.toJson(existingConcatenatedLinks.toList())` to
   `JsonUtils.gson.toJson(existingConcatenatedLinks)` (a `Set` serializes to a JSON array the
   same way; consumers read it back as `Array<String>::class.java` → `toHashSet()` at line 95,
   so order-independence is already relied upon).
3. Run the course sync unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest` green. Behaviour: after a library sync the
`concatenated_links` preference still contains every accumulated resource link, de-duplicated.

size budget: ~4 changed lines, 1 file
out of scope: no change to `addConcatenatedLink`, no change to the preference key, no new model
fields.

---

### 5. build the step-label format string once, not per step navigation (roadmap 7, nudges 10)

context: `TakeCourseFragment.setStepText` rebuilds the format template every call:
`String.format(Locale.getDefault(), "${getString(R.string.step)} %d/%d", ...)`. Each navigation
re-resolves `R.string.step` and allocates a fresh template string + `String.format` varargs box.
The label and its locale are stable for the fragment's lifetime, so the resolved
`getString(R.string.step)` should be read once (lazily) and reused; the `String.format` call
itself can stay (it is the actual formatting). This also makes the screen trivially portable for
a future Compose rewrite (stateless formatting helper).
evidence: `app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseFragment.kt:188-189`
(`setStepText`).

files: `app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseFragment.kt` —
`setStepText`. Do NOT touch `TakeCourseViewModel`/`CoursesStepsAdapter`.

steps:
1. Add a `private val stepLabel by lazy { getString(R.string.step) }` field (or read it once in
   `onViewCreated` into a non-null `String`).
2. Change line 189 to use the cached `stepLabel`:
   `binding.tvStep.text = String.format(Locale.getDefault(), "$stepLabel %d/%d", currentStep, totalSteps)`.
3. Verify the "Course Details" branch at `updateStepDisplay` (line 193) is unaffected.
4. Run the course-taking unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest` green. Behaviour: the step counter still reads
e.g. "Step 2/5" and flips to "Course Details" at position 0 exactly as before.

size budget: ~4 changed lines, 1 file
out of scope: no Compose migration here, no change to `R.string.step`, no new string resources.

---

### 6. hoist the diagnostics lookups in the single-log path to match the batch path (roadmap 7+8, nudges 9)

context: `DiagnosticsRepositoryImpl.saveLogToRoom` re-resolves `getUserModel()`,
`resolveParentCode`, `resolvePlanetCode`, and `VersionUtils.getVersionName(context)` for every
single log row, while the sibling `saveLogsToRoom` already hoists those exact four calls once
above its loop. The single path is hit from `DownloadRepositoryImpl`'s 404 logging, so during a
large download batch it can fire once per missing file. Hoisting the same four lookups makes the
two paths consistent and removes per-row reflection/PackageManager work.
evidence: `app/src/main/java/org/ole/planet/myplanet/repository/DiagnosticsRepositoryImpl.kt:59-77`
(`saveLogToRoom`) vs `:79-92` (`saveLogsToRoom`, which hoists them).

files: `app/src/main/java/org/ole/planet/myplanet/repository/DiagnosticsRepositoryImpl.kt` —
`saveLogToRoom` only. Do NOT touch `DiagnosticsDao`, `CrashLogStore`, or `DownloadRepositoryImpl`.

steps:
1. In `saveLogToRoom`, compute `model`, `versionName`, `parentCode`, `planetCode` once at the top
   (exactly as `saveLogsToRoom` does) and pass them into the single `buildApkLog(...)` call.
2. Keep the `try/catch (e: Exception) { e.printStackTrace(); false }` shell identical.
3. Run the diagnostics unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest` green. Behaviour: a 404 during download still
writes one `ApkLog` row with the same `parentCode`/`planetCode`/`versionName`/`userId` as before.

size budget: ~6 changed lines, 1 file
out of scope: no change to `buildApkLog`, no batching of the single path, no DAO change.

---

### 7. remove the dead `also {}` and the throwaway `val result` in `ServerUrlMapper.processUrl` (roadmap 7, nudges 9)

context: `ServerUrlMapper.processUrl` runs
`serverMappings[baseUrl].also { }` — an `also` block with an empty body, a leftover that
allocates a lambda and reads the mapped value for nothing. It then binds the result to a local
`val result = UrlMapping(...)` and returns it on the next line. Both are pure overhead and obscure
the intent; `serverMappings[baseUrl]` can be read directly into the `UrlMapping` constructor and
returned. `ServerUrlMapper` is a `@Singleton` with no Android framework deps, so this also keeps
the class clean for the KMP core (roadmap 9).
evidence: `app/src/main/java/org/ole/planet/myplanet/services/sync/ServerUrlMapper.kt:47-52` —
`serverMappings[baseUrl].also { }`, then `val result = UrlMapping(...)` / `return result`.

files: `app/src/main/java/org/ole/planet/myplanet/services/sync/ServerUrlMapper.kt` —
`processUrl`. Do NOT touch `SyncManager.kt`, `TransactionSyncManager.kt` (open PR), or
`updateUrlPreferences`.

steps:
1. Replace the `val alternativeUrl = extractedUrl?.let { baseUrl ->
   serverMappings[baseUrl].also { } }` block with
   `val alternativeUrl = extractedUrl?.let { serverMappings[it] }`.
2. Replace the `val result = UrlMapping(url, alternativeUrl, extractedUrl)` / `return result`
   pair with a single `return UrlMapping(url, alternativeUrl, extractedUrl)`.
3. Run the server-url-mapper unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest` green. Behaviour: a primary URL whose base
matches a known mapping still resolves to its clone `alternativeUrl`; an unknown base still
yields `alternativeUrl = null` with `extractedBaseUrl` populated.

size budget: ~4 changed lines, 1 file
out of scope: no change to `serverMappings`, no change to `updateUrlPreferences`, no new mapping
entries.

---

### 8. use the already-known id in the notification delete branches (roadmap 1+7, nudges 9)

context: `updateResourceNotification` and `updateStorageNotification` each construct
`notificationId` at the top, call `notificationDao.getById(notificationId)`, and in the delete
branch (`resourceCount == 0` / above threshold) call
`existingNotification?.let { notificationDao.deleteById(it.id) }` — reading `it.id` off the
loaded entity when the value is already the local `notificationId` they passed to `getById`.
Using `notificationId` directly removes the property indirection and makes the delete branch
self-contained, which trims the methods for the KMP core (roadmap 9). Both methods are called on
every resource/storage notification refresh.
evidence: `app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt`
— `updateResourceNotification` and `updateStorageNotification`, the
`existingNotification?.let { notificationDao.deleteById(it.id) }` lines (around the `else` branch
of each method).

files: `app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt` —
`updateResourceNotification`, `updateStorageNotification`. Do NOT touch `NotificationDao` or
`NotificationsViewModel`.

steps:
1. In the delete branch of each method, change
   `existingNotification?.let { notificationDao.deleteById(it.id) }` to
   `existingNotification?.let { notificationDao.deleteById(notificationId) }` (the local id
   constructed at the top of each method, equal to `it.id`).
2. Leave the upsert branch untouched (it correctly reuses the loaded entity).
3. Run the notifications unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest` green. Behaviour: when the resource count drops
to 0 or storage rises above 10%, the corresponding notification row is still deleted; when the
count/percent changes, the row is still updated and re-marked unread.

size budget: ~4 changed lines, 1 file
out of scope: no DAO change, no merging of the two near-duplicate methods (a larger refactor), no
change to the `STORAGE_WARNING_AVAILABLE_PERCENT` constant.

---

### 9. reuse the Markwon instance instead of rebuilding it per `setMarkdownText` caller that bypasses `create` (roadmap 7, nudges 10)

context: `MarkdownUtils.create` already double-check-locks a single `markwonInstance`, and
`setMarkdownText` routes through it — good. But `prependBaseUrlToImages` builds a fresh
`StringBuilder` and re-runs the compiled `imagePattern` matcher per call; the matcher itself is
fine (compiled once), yet callers that pass `null` short-circuit correctly while the non-null path
rebuilds the img tag string with `"<img src=$fullUrl .../>"` (unquoted attribute) on every match.
The string concat is inherent, but the `<img>` literal can be lifted to a small helper or a
`const val` template so a future Compose port has one rendering seam. Verified spot:
`prependBaseUrlToImages`.
evidence: `app/src/main/java/org/ole/planet/myplanet/utils/MarkdownUtils.kt:82-104`
(`prependBaseUrlToImages`, the `result.append("<img src=$fullUrl width=$width height=$height/>")`
line).

files: `app/src/main/java/org/ole/planet/myplanet/utils/MarkdownUtils.kt` —
`prependBaseUrlToImages`. Do NOT touch `buildMarkwon`, `CustomImageSpan`, or
`CustomLinkMovementMethod`.

steps:
1. Extract the img-tag template into a private helper or a `private const val` such as
   `IMG_TAG_TEMPLATE = "<img src=%s width=%d height=%d/>"` (note the quoted attribute value, a
   tiny correctness improvement — the current `<img src=$fullUrl ...>` breaks if a URL contains
   a space).
2. Replace the inline `result.append("<img src=$fullUrl width=$width height=$height/>")` with
   `result.append(IMG_TAG_TEMPLATE.format(fullUrl, width, height))`.
3. Keep the matcher/SB loop structure identical.
4. Run any markdown utility tests (and the broader suite).

acceptance: `./gradlew testDefaultDebugUnitTest` green. Behaviour: markdown with relative
`![alt](resources/foo.png)` images still resolves to `<img src="<baseUrl>foo.png" .../>` and
renders; URLs containing spaces no longer truncate at the space.

size budget: ~5 changed lines, 1 file
out of scope: no change to the Markwon plugin chain, no change to `setMarkdownText`, no new
dependency on a markdown library.

---

### 10. stop re-resolving `getString(R.string.step)` (and friends) per `TeamsSelectionAdapter` bind — already cached? confirm and finalize (roadmap 7, nudges 10)

context: `TeamsSelectionAdapter.bind` calls
`itemView.context.getString(R.string.teams)` on every bind to pick between the `team` and
`business` icon. `section` is a constructor parameter that does not change, so the
`section == itemView.context.getString(R.string.teams)` comparison re-resolves the localized
string per visible row. The fix is to compare against a stable resolved value held once (e.g.
resolve `R.string.teams` once in `init` and store it, or compare `section` against the resolved
constant). This also removes a `context` dependency from the bind hot path, which is exactly what
a Compose port needs.
evidence: `app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamsSelectionAdapter.kt:43` —
`if (section == itemView.context.getString(R.string.teams))`.

files: `app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamsSelectionAdapter.kt` —
`TeamSelectionViewHolder.bind`. Do NOT touch `TeamsAdapter.kt` (open PR) or
`TeamPagerAdapter.kt`.

steps:
1. In `init` of the adapter (or the ViewHolder), resolve the section name once: since `section`
   is passed in as a `String` that the caller already resolved from `R.string.teams`, the
   simplest portable fix is to compare `section == teamsLabel` where `teamsLabel` is a
   `private val` resolved lazily from `parent.context.getString(R.string.teams)` — or, better,
   accept that the caller already passes the resolved string and just compare against the
   constructor `section` directly against a cached resolved `teams` string captured in `init`.
2. Replace the per-bind `itemView.context.getString(R.string.teams)` with the cached value.
3. Run the teams selection / share-flow tests.

acceptance: `./gradlew testDefaultDebugUnitTest` green. Behaviour: the team rows in the
share-selection dialog still show the `team` icon under the "Teams" section and the `business`
icon otherwise, exactly as before.

size budget: ~6 changed lines, 1 file
out of scope: no change to `TeamsAdapter`, no change to the `sharedIds` set semantics, no layout
change.
