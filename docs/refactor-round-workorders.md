# myPlanet refactor round — work orders

date: 2026-08-27· base commit: 45bac8d (master, v0.67.59)· open PRs checked: 16274 16270 16258 16257 16192 16101 16096 15951 15825 15824 15820 15808 15699 15559 15519 15412 15267 15266 15226 15198 15158 15108 14960 14893 14883 14650 14427 13928 13848 13657 13604 13415 13355 13287 10993 8175 4075

focus this round: GitHub workflows quick wins· micro-optimizations that unblock bigger refactors· obvious inefficiencies removable without rewrites.

Every file named below was opened and confirmed to exist on `master` at `45bac8d`, and was checked against the open-PR list above — none is touched by an open PR. No file appears in more than one task.

---

### 1. remove the dead `files ?: emptyList()` in resource download routing (roadmap 1+7)

context: `ResourcesRepositoryImpl.downloadFiles` loads `var files = libraryList`, reassigns it to `getAllLibrariesToSync()` when null, then guards again with `val safeFiles = files ?: emptyList()`. After the null branch the value is never null, so the elvis is dead weight and `safeFiles` is just `files`. It obscures the real flow (null => fetch the syncable set) that the data-layer cleanup roadmap wants to make obvious. Evidence: `app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImpl.kt:413-417` — `var files = libraryList` / `if (files == null) files = getAllLibrariesToSync()` / `val safeFiles = files ?: emptyList()`.
files: `app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImpl.kt` — the `downloadFiles` override (lines 412-428). Leave `getAllLibrariesToSync` (line 430), `downloadResources` (line 395), `MyLibraryDao.getSyncable()` (`app/src/main/java/org/ole/planet/myplanet/data/room/dao/MyLibraryDao.kt:83`), and `MyLibrary.needToUpdate()` (`app/src/main/java/org/ole/planet/myplanet/model/MyLibrary.kt:148`) untouched.
steps:
1. Replace the `var files` / `if (files == null)` / `val safeFiles = files ?: emptyList()` block with `val files = libraryList ?: getAllLibrariesToSync()`.
2. Use `files` directly where `safeFiles` was read (the `DownloadUtils.downloadAllFiles(files)` call at line 418 and the `return files` at line 427).
3. Remove the now-unused `safeFiles` binding.
4. Run `./gradlew testDefaultDebugUnitTest --tests "*ResourcesRepositoryImplTest"` to confirm the download path tests stay green.
acceptance: `./gradlew testDefaultDebugUnitTest` green; the resource download path still resolves to the syncable library set when no list is passed, and returns the empty list when `getAllLibrariesToSync()` is empty. Confirm `ResourcesRepositoryImpl.downloadFiles(null)` returns the same library list it did before (no `emptyList()` short-circuit).
size budget: ~4 changed lines, 1 file
out of scope: no DAO changes, no changes to `DownloadUtils`

---

### 2. replace the per-candidate grep dedup in coauthor crediting with an associative-array lookup (roadmap 8)

context: `coauthors.sh` credits collaborators on squash commits. Its dedup loop (`while IFS= read -r login`) calls `printf '%s' "$seen" | grep -qxF` and `printf '%s' "$collaborators" | grep -qxF` once per candidate, spawning two `grep` processes per candidate — O(n^2) in collaborators and the slowest part of every `automerge.sh` squash. A bash 4 associative array makes both lookups O(1). Evidence: `.github/scripts/coauthors.sh:83-88` — the two `grep -qxF` checks per candidate.
files: `.github/scripts/coauthors.sh` — the dedup loop (lines 65-92). Leave the collaborator fetch (lines 21-24), the trailer parsing (lines 40-60), the `[bot]`/author/owner skips (lines 74-82), and the final owner append (lines 94-96) untouched.
steps:
1. Before the loop, build `declare -A collab_set=()` and populate it from the `collaborators` newline list (one entry per lowercased login) so membership is O(1).
2. In the loop, replace the `printf '%s' "$seen" | grep -qxF "$l"` check with a lookup against a `declare -A seen_set=()` that you add `$l` to on emit.
3. Replace the `printf '%s' "$collaborators" | grep -qxF "$l"` collaborator check with `[[ -n "${collab_set[$l]:-}" ]]`.
4. Keep the existing `[bot]` skip, author/owner skips, and the exact `Co-authored-by:` line format.
5. Verify on a PR with duplicate commenter logins that each still appears exactly once in the commit trailer.
acceptance: a repo with many collaborators still credits the same set; `shellcheck -x .github/scripts/coauthors.sh` clean (the `automerge.yml` runner has bash 4+). Behaviour is unchanged: every User-type collaborator who touched the PR, plus the owner, gets exactly one trailer. The squash commit body for a many-collaborator PR credits the same set as before, in the same order.
size budget: ~25 changed lines, 1 file
out of scope: no changes to `automerge.sh` or the trailer regex

---

### 3. deduplicate the shared display logic in the two `CourseRatingUtils.showRating` overloads (roadmap 8)

context: `CourseRatingUtils.showRating(context, ratingSummary, …)` and `showRating(context, obj: JsonObject?, …)` both end by formatting the average to a TextView, setting the rating-count string, and setting the ratingBar — the last three lines of each overload are identical except for how `averageRating`/`totalRatings`/`userRating` were sourced. The duplication means a rating-display tweak must be made in two places, which the code-health roadmap wants to collapse. Evidence: `app/src/main/java/org/ole/planet/myplanet/utils/CourseRatingUtils.kt:22-24` and `46-48` are the duplicated display triples (`average?.text`, `ratingCount?.text`, `ratingBar?.rating`).
files: `app/src/main/java/org/ole/planet/myplanet/utils/CourseRatingUtils.kt` — both `showRating` overloads (lines 11-25 and 27-49). Leave the `RatingSummary` import/reference and the `R.string.rating_count_format` usage in place; do not touch `RatingSummary` (`app/src/main/java/org/ole/planet/myplanet/repository/RatingSummary.kt`).
steps:
1. Add a `private fun applyRating(context, averageRating: Float?, totalRatings: Int?, userRating: Float?, average: TextView?, ratingCount: TextView?, ratingBar: AppCompatRatingBar?)` holding the three display lines (`average?.text = String.format(...)`, `ratingCount?.text = context.getString(R.string.rating_count_format, totalRatings ?: 0)`, `ratingBar?.rating = userRating ?: averageRating ?: 0f`).
2. Have the `RatingSummary` overload compute `averageRating`/`totalRatings`/`userRating` and call `applyRating(...)`.
3. Have the `JsonObject` overload compute the same three and call `applyRating(...)`; keep the existing null/number guards on the JSON fields unchanged.
4. Run `./gradlew testDefaultDebugUnitTest --tests "*CourseRatingUtilsTest"` and confirm both overload tests pass without a behaviour change.
acceptance: `./gradlew testDefaultDebugUnitTest` green (`CourseRatingUtilsTest` covers both overloads); the rating bar, average, and count render identically for a `RatingSummary` and for an equivalent `JsonObject`. A `RatingSummary` and a `JsonObject` carrying the same average/count/userRating produce identical rendered average text, count text, and rating bar value.
size budget: ~15 changed lines, 1 file
out of scope: no new public API, no changes to `RatingSummary`

---

### 4. hoist the per-resource base-URL resolution out of the download URL list build (roadmap 1+7)

context: `DownloadUtils.downloadAllFiles` builds the resource URL list with `dbMyLibrary.map { UrlUtils.getUrl(it) }`. Each call resolves `UrlUtils.getUrl(library)` => `getUrl(id, file)` => `"${getUrl()}/resources/$id/$file"`, where `getUrl()` (`dbUrl(spm())`) recomputes the CouchDB base and runs `spm()` + `baseUrl()` string ops once per resource. The base is constant for the whole list; resolving it once removes N redundant `baseUrl` calls from the sync-time hot path that roadmap 1 is cleaning up. Evidence: `app/src/main/java/org/ole/planet/myplanet/utils/DownloadUtils.kt:127` — `dbMyLibrary.map { UrlUtils.getUrl(it) }`; each `getUrl(it)` (`UrlUtils.kt:92`) delegates to `getUrl(id, file)` (`UrlUtils.kt:96-97`) which calls `getUrl()` (`UrlUtils.kt:118` => `dbUrl(spm())` at `:79`).
files: `app/src/main/java/org/ole/planet/myplanet/utils/DownloadUtils.kt` — `downloadAllFiles` (line 126). `app/src/main/java/org/ole/planet/myplanet/utils/UrlUtils.kt` — add one helper and have `getUrl(MyLibrary?)` / `getUrl(id, file)` delegate to it; do not touch `hostUrl`, `header`, or the `*Url(spm)` builders.
steps:
1. In `UrlUtils`, extract the shared `"/resources/$id/$file"` format into a `fun resourceUrl(base: String, id: String?, file: String?): String` so the format lives in one place.
2. Resolve `val base = getUrl()` once at the top of `downloadAllFiles`, then `map` each library to `UrlUtils.resourceUrl(base, it?.resourceId, it?.resourceLocalAddress)`.
3. Keep the return type `ArrayList<String>` and the element ordering identical.
4. Run `./gradlew testDefaultDebugUnitTest --tests "*DownloadUtilsTest" --tests "*ResourcesPreviewLoaderTest"` to confirm URL-list parity.
acceptance: `./gradlew testDefaultDebugUnitTest` green (the `DownloadUtilsTest`/`ResourcesPreviewLoaderTest` paths still pass); the download URL list is unchanged for a given library set. For a fixed library set, `downloadAllFiles(...)` returns a list equal element-by-element to the previous per-item resolution.
size budget: ~12 changed lines, 2 files
out of scope: no DAO changes, no `ResourcesRepositoryImpl` changes

---

### 5. drop the deprecated `StringBuffer` regex replacement in markdown image rewriting (roadmap 8+10)

context: `MarkdownUtils.prependBaseUrlToImages` builds the rewritten markdown with `StringBuffer result` and `matcher.appendReplacement(result, ...)` / `matcher.appendTail(result)`. `Matcher.appendReplacement(StringBuffer, String)` and `appendTail(StringBuffer)` are the deprecated JDK overloads; the `StringBuilder` overloads are the supported replacement. This is the kind of platform-adjacent call the KMP north star (roadmap 9) wants identified and isolated, and it currently relies on a deprecated JDK API that a future toolchain bump will warn on. Evidence: `app/src/main/java/org/ole/planet/myplanet/utils/MarkdownUtils.kt:89-101` — `val result = StringBuffer()` then `matcher.appendReplacement(result, ...)` / `matcher.appendTail(result)`.
files: `app/src/main/java/org/ole/planet/myplanet/utils/MarkdownUtils.kt` — `prependBaseUrlToImages` (lines 81-102). Leave the Markwon plugin builder (lines 47-63), `setMarkdownText`, and the `AlignTagHandler` untouched.
steps:
1. Change `val result = StringBuffer()` to `val result = StringBuilder()`.
2. Swap `matcher.appendReplacement(result, ...)` to the `StringBuilder` overload (same arguments) and `matcher.appendTail(result)` likewise.
3. Keep the `<img src=$fullUrl width=$width height=$height/>` replacement string and the `resources/` prefix-strip behavior exactly as-is.
4. Run `./gradlew testDefaultDebugUnitTest --tests "*MarkdownUtilsTest"` to confirm the rewrite tests still pass.
acceptance: `./gradlew testDefaultDebugUnitTest` green (`MarkdownUtilsTest` covers the rewrite); rendered markdown with relative `![]()` image links still gets the base URL prepended identically. Markdown whose `![]()` links point at `resources/...` relative paths still resolves to `<img src=baseUrl/... width height/>` with the same dimensions.
size budget: ~3 changed lines, 1 file
out of scope: no changes to the Markwon instance or image span handling

---

### 6. fix the mislabeled network-id variable in wifi network detection (roadmap 8)

context: `NetworkUtils.getCurrentNetworkId` declares `var ssid = -1`, then on a wifi connection assigns `ssid = connectionInfo.networkId` and returns it. The value is the WiFi `networkId` (an Int), not an SSID (a String), so the name lies about both type and meaning and misleads anyone touching the connectivity path that roadmap 7 is scrutinizing. Evidence: `app/src/main/java/org/ole/planet/myplanet/utils/NetworkUtils.kt:151` — `var ssid = -1`; `:166` — `ssid = connectionInfo.networkId`; `:169` — `return ssid`.
files: `app/src/main/java/org/ole/planet/myplanet/utils/NetworkUtils.kt` — `getCurrentNetworkId` (lines 150-170). Leave the `NetworkCallback`, `isNetworkConnectedFlow`, and `isWifiBluetoothEnabled` family untouched; keep the `@Suppress("DEPRECATION")` on the `connectionInfo`/`networkId` access.
steps:
1. Rename the local `ssid` to `networkId` (declaration at line 151, the assignment at line 166, and the `return` at line 169).
2. Leave the SDK-branched `WifiInfo` retrieval and the `@Suppress("DEPRECATION")` exactly as they are.
3. Run `./gradlew testDefaultDebugUnitTest --tests "*NetworkUtilsTest" --tests "*NetworkUtilsStateTest"` to confirm no reference to the old name remains.
5. Confirm with `grep -rn "var ssid" app/src/main/java/` that no stale `ssid` local remains in `NetworkUtils.kt`.
acceptance: `./gradlew testDefaultDebugUnitTest` green (`NetworkUtilsTest`/`NetworkUtilsStateTest` exercise the connectivity paths); the method still returns the connected wifi network id or -1. On a connected wifi network the method returns the OS `networkId`; when not connected wifi it returns -1.
size budget: ~3 changed lines, 1 file
out of scope: no behaviour change, no new network capability checks

---

### 7. collapse the redundant null-guard in team-id and team-name preference getters (roadmap 8)

context: `SharedPrefManager.getSelectedTeamId` and `getTeamName` read `pref.getString(KEY, "")` (which is already non-null because of the `""` default) then apply `.takeIf { !it.isNullOrEmpty() } ?: ""` — the `takeIf` always returns the value (it is never null or empty after the default), so the elvis is dead. Two more getters (`getServerUrl`, `getCouchdbUrl`, `getUrlUser`, …) follow the cleaner `pref.getString(KEY, "") ?: ""` shape; these two should match. Evidence: `app/src/main/java/org/ole/planet/myplanet/services/SharedPrefManager.kt:102` — `pref.getString(SELECTED_TEAM_ID, "").takeIf { !it.isNullOrEmpty() } ?: ""`; `:118` — the same for `TEAM_NAME`; compare the plain `?: ""` form at `:149` (`getServerUrl`).
files: `app/src/main/java/org/ole/planet/myplanet/services/SharedPrefManager.kt` — `getSelectedTeamId` (line 101) and `getTeamName` (line 117). Leave the setters, the `clearPreferences` logic, and every other getter untouched.
steps:
1. In `getSelectedTeamId`, replace `pref.getString(SELECTED_TEAM_ID, "").takeIf { !it.isNullOrEmpty() } ?: ""` with `pref.getString(SELECTED_TEAM_ID, "") ?: ""` (`takeIf` is a stdlib extension, so no import is added or removed).
2. In `getTeamName`, do the same against the `TEAM_NAME` key.
3. Run `./gradlew testDefaultDebugUnitTest` to confirm no getter test depended on the `takeIf` returning a distinct value.
4. Confirm with `grep -n "takeIf" app/src/main/java/org/ole/planet/myplanet/services/SharedPrefManager.kt` that the two getters no longer chain `takeIf`.
acceptance: `./gradlew testDefaultDebugUnitTest` green; an unset selected-team-id / team-name still returns `""` (not null) for every caller. An unset key still yields `""` (never null) so `getSelectedTeamId().isEmpty()` checks at call sites behave the same.
size budget: ~4 changed lines, 1 file
out of scope: no changes to the constants, no new getters

---

### 8. make the `addJson` non-empty check read as a predicate (roadmap 8)

context: `JsonUtils.addJson` guards with `value != null && value.keySet().size > 0` before adding a nested object. `keySet().size > 0` is the long-form of `isNotEmpty()` and is the only place in the file that reaches into the key set's size; the rest of `JsonUtils` already uses idiomatic predicates. It is exactly the kind of low-clarity spot the code-health roadmap wants tidied before the data layer is reused under KMP (roadmap 9). Evidence: `app/src/main/java/org/ole/planet/myplanet/utils/JsonUtils.kt:93` — `if (value != null && value.keySet().size > 0) `object`.add(fieldName, value)`.
files: `app/src/main/java/org/ole/planet/myplanet/utils/JsonUtils.kt` — `addJson` (lines 92-93). Leave every `get*`/`add*` helper and the `safeGet` wrapper untouched.
steps:
1. Replace `value.keySet().size > 0` with `value.keySet().isNotEmpty()`.
2. Keep the `value != null` guard and the `object.add(fieldName, value)` call as-is.
3. Run `./gradlew testDefaultDebugUnitTest --tests "*JsonUtilsTest"` to confirm `addJson` still skips empty objects and adds non-empty ones.
4. Confirm with `grep -n "keySet().size" app/src/main/java/org/ole/planet/myplanet/utils/JsonUtils.kt` that no `.size > 0` form remains in the file.
acceptance: `./gradlew testDefaultDebugUnitTest` green (`JsonUtilsTest` covers `addJson`); an empty nested object is still not added, a non-empty one still is. An empty `JsonObject` is still not added; a `JsonObject` with at least one key is added under `fieldName`.
size budget: ~1 changed line, 1 file
out of scope: no new helpers, no signature changes

---

### 9. use the injected clock for sync-timestamp writes instead of `Date()` (roadmap 7+8, advances 9)

context: `AutoSyncWorker` injects `timeProvider` and already reads the clock through it (`val currentTime = timeProvider.now()`, line 55), but writes the sync timestamps back with `Date().time` — `sharedPrefManager.setLastSync(Date().time)` (line 139) and `sharedPrefManager.setLastUsageUploaded(Date().time)` (line 157). That defeats the injectable-clock abstraction the test layer relies on (`TestDispatcherProvider`/`TimeProvider` fakes), so any test asserting "last sync moved forward" cannot control the value. It also keeps a `java.util.Date` call in the sync path, which is exactly what the KMP north star (roadmap 9) wants removed from core logic. Evidence: the only three `Date` references in the file are `import java.util.Date` (`:12`), `setLastSync(Date().time)` (`:139`), and `setLastUsageUploaded(Date().time)` (`:157`).
files: `app/src/main/java/org/ole/planet/myplanet/services/AutoSyncWorker.kt` — the two `Date().time` call sites (lines 139 and 157) and the import at line 12. Leave `doWork`, the `suspendCancellableCoroutine` block, and the upload sequence untouched.
steps:
1. Replace `sharedPrefManager.setLastSync(Date().time)` with `sharedPrefManager.setLastSync(timeProvider.now())`.
2. Replace `sharedPrefManager.setLastUsageUploaded(Date().time)` with `sharedPrefManager.setLastUsageUploaded(timeProvider.now())`.
3. Remove the now-unused `import java.util.Date` (no other `Date` usage remains).
4. Run `./gradlew testDefaultDebugUnitTest --tests "*AutoSyncWorkerTest"` (or the nearest sync-worker test) to confirm the timestamp path compiles and tests pass.
acceptance: `./gradlew testDefaultDebugUnitTest` green; `lastSync`/`lastUsageUploaded` advance by exactly the faked clock step under a `TimeProvider` fake. A real auto-sync still records a monotonic timestamp. Under a `TimeProvider` fake returning a fixed `now()`, `getAutoSyncInterval()`-driven writes record that exact value, not `System.currentTimeMillis()`.
size budget: ~4 changed lines, 1 file
out of scope: no changes to the sync interval check, no changes to `SharedPrefManager`

---

### 10. unblock the `pick_pr` skip set from an O(n^2) membership scan (roadmap 8)

context: `automerge.sh`'s `pick_pr` jq pipeline filters already-drained PRs with `map(select(.number as $n | $done | index($n) | not))`, where `$done` is rebuilt each call and `index` is an O(n) scan, making the filter O(n^2) in queue size. As the `automerge` queue grows the PR-pick step slows quadratically on every loop iteration, which is the friction roadmap 8 wants removed from the merge drain. Evidence: `.github/scripts/automerge.sh` `pick_pr` function — the `map(select(.number as $n | $done | index($n) | not))` filter.
files: `.github/scripts/automerge.sh` — the `pick_pr` function (lines 61-78). Leave `check_mergeable`, `runs_for`, `wait_for_runs`, and the main drain loop untouched.
steps:
1. In `pick_pr`, change `$done` from `split(" ")[] | ... | tonumber` into a jq set built once as an object map `[$done[] as $d | {($d|tostring): 1}] | add` so each `.number` lookup is O(1) via `.["\(.number)"]`.
2. Keep the `--limit 1000`, the draft filter, the priority sort (`sort_by([ (if .priority then 0 else 1 end), .number ])`), and `first` exactly as they are.
3. Preserve the existing behaviour that an empty `skip_numbers` produces an empty `$done` set and selects the lowest-numbered priority PR.
4. Diff `pick_pr` output against the old `index`-based filter for a sample open-PR JSON to confirm identical selection before merging.
5. Confirm with a one-off `jq` invocation that for an empty `skip_numbers` the new filter returns the same selection as the old `index` filter.
acceptance: a dry-run automerge dispatch (`workflow_dispatch` with `dry_run=true`) still picks the same PR it picks today for a given label/skip set; `shellcheck` and a manual `pick_pr` against sample JSON return identical output. For a queue with all members already in `skip_numbers`, `pick_pr` selects nothing (empty) just as before.
size budget: ~12 changed lines, 1 file
out of scope: no changes to the merge retry, conflict handling, or version bump
do not change the `skip_numbers` parsing contract (the caller still passes space-separated numbers).
