# Task Generation Brief — myPlanet refactor round 2026-08-27

**date**: 2026-08-27
**base commit**: 89fd72c (v0.67.58)
**open PRs checked**: 16274, 16258, 16257, 16192 (files from these PRs are avoided below)

---

## 1. Use count query instead of loading full news list for team notification count (roadmap 1+7)

context: `TeamsVoicesViewModel.kt:58-61` calls `voicesRepository.getFilteredNews(teamId)` to get a `List<News?>` then reads `newsList.size` just to pass it to `updateTeamNotification`. This loads every news row plus all column data only to extract an integer count. `VoicesRepository` already exposes `countTeamChats(teamId): Long` backed by SQL `COUNT(*)`.

files:
- `app/src/main/java/org/ole/planet/myplanet/ui/teams/voices/TeamsVoicesViewModel.kt` — `getFilteredNews` function at line 58
- do NOT touch `VoicesRepositoryImpl.kt` or `VoicesRepository.kt`

steps:
1. In `getFilteredNews`, split into two calls: first `countTeamChats(teamId).toInt()` for the notification update, then `getFilteredNews(teamId)` for the return value
2. Call `notificationsRepository.updateTeamNotification(teamId, count)` with the count
3. Return `newsList` from the second call
4. Remove the unused count extracted from `newsList.size`
5. Run `./gradlew testDefaultDebugUnitTest`

acceptance: `./gradlew testDefaultDebugUnitTest` green; team voices screen still shows correct news count and notification badge updates correctly

size budget: ~4 changed lines, 1 file

out of scope: no repository changes, no DAO changes

---

## 2. Batch user lookups in TeamsRepositoryImpl to eliminate N+1 queries (roadmap 1+7)

context: `TeamsRepositoryImpl.kt:961-967` in `getJoinedMembers` and `TeamsRepositoryImpl.kt:1051-1056` in `getRequestedMembers` both call `userRepository.getUserById(it)` inside a `mapNotNull`. Each call hits the database separately, producing one query per member. `UserRepository` already exposes `getUsersByIds(userIds: List<String>)` which batches into chunks of 400 and makes a single `IN` query.

files:
- `app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt` — `getJoinedMembers` at line 961 and `getRequestedMembers` at line 1051
- do NOT touch `UserRepositoryImpl.kt` or `UserRepository.kt`

steps:
1. In `getJoinedMembers`, replace `teamMembers.mapNotNull { userRepository.getUserById(it) }` with `userRepository.getUsersByIds(teamMembers)`
2. In `getRequestedMembers`, replace `requestedMemberIds.mapNotNull { userRepository.getUserById(it) }` with `userRepository.getUsersByIds(requestedMemberIds)`
3. Run `./gradlew testDefaultDebugUnitTest`

acceptance: `./gradlew testDefaultDebugUnitTest` green; team member list and team requests screen still show correct members

size budget: ~4 changed lines, 1 file

out of scope: no DAO changes, no interface changes

---

## 3. Cache imagesArray.size() and use isEmpty in VoicesAdapter (roadmap 7)

context: `VoicesAdapter.kt:876-889` calls `imagesArray.size()` three times — once in the `if` condition, once in the `else-if`, and once inside the `for` loop at line 883. The idiomatic Kotlin form `!imagesArray.isEmpty` is also more readable.

files:
- `app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesAdapter.kt` — `bind` method in `NewsViewHolder`, lines 876-889
- do NOT touch `VoicesFragment.kt` or `VoicesViewModel.kt`

steps:
1. Extract `val imageCount = imagesArray.size()` before the `if` at line 876
2. Replace `imagesArray.size() > 0` with `!imagesArray.isEmpty` (this replaces both the `if` and `else-if` size checks)
3. Replace `for (i in 0 until imagesArray.size())` with `for (i in 0 until imageCount)`
4. Run `./gradlew testDefaultDebugUnitTest`

acceptance: `./gradlew testDefaultDebugUnitTest` green; voices feed still renders all images correctly for single-image and multi-image posts

size budget: ~4 changed lines, 1 file

out of scope: no changes to image loading logic, no changes to Glide calls

---

## 4. Optimize VoicesViewModel: filterNews caching and isNotEmpty in downloadResources (roadmap 7)

context: `VoicesViewModel.kt:104-107` rebuilds the `labelDisplayToValue` map from `Constants.LABELS` on every call to `filterNews`. Since `Constants.LABELS` is a static constant, it can be cached. Additionally, `VoicesViewModel.kt:121-123` does a map lookup when `Constants.LABELS` already maps display name to value. Separately, `VoicesViewModel.kt:218` uses `(news?.imagesArray?.size() ?: 0) > 0` which can use idiomatic Kotlin `!news?.imagesArray.isNullOrEmpty()`.

files:
- `app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesViewModel.kt` — `filterNews` function at line 96 and `downloadReferencedResources` function at line 215
- do NOT touch `VoicesFragment.kt` or `VoicesAdapter.kt`

steps:
1. Add a private lazy val at class level: `private val constantLabelsMap = Constants.LABELS.toMutableMap()`
2. In `filterNews`, replace the map-building loop with `val labelDisplayToValue = constantLabelsMap` plus the dynamic labels from news items
3. Add `val knownLabelValue = Constants.LABELS[selectedLabel]` before the filter check
4. Replace `labelDisplayToValue.containsKey(selectedLabel)` with `knownLabelValue != null || labelDisplayToValue.containsKey(selectedLabel)`
5. Use `knownLabelValue ?: labelDisplayToValue[selectedLabel]` for the value lookup
6. In `downloadReferencedResources`, replace `(news?.imagesArray?.size() ?: 0) > 0` with `!news?.imagesArray.isNullOrEmpty()`
7. Run `./gradlew testDefaultDebugUnitTest`

acceptance: `./gradlew testDefaultDebugUnitTest` green; voices feed filter dropdown and downloading referenced resources both work correctly

size budget: ~12 changed lines, 1 file

out of scope: no changes to the dynamic label handling logic or resource download logic

---

## 5. Use isNotEmpty() instead of size() > 0 in JsonUtils (roadmap 7)

context: `JsonUtils.kt:93` uses `value.keySet().size > 0` to check if a JsonObject is non-empty. Kotlin's standard idiom is `value.isNotEmpty()` which is more readable and avoids the extra method call.

files:
- `app/src/main/java/org/ole/planet/myplanet/utils/JsonUtils.kt` — `addJson` function at line 92
- do NOT touch other functions in this file

steps:
1. Replace `value.keySet().size > 0` with `value.isNotEmpty()`
2. Run `./gradlew testDefaultDebugUnitTest`

acceptance: `./gradlew testDefaultDebugUnitTest` green; JSON serialization/deserialization behavior unchanged

size budget: ~1 changed line, 1 file

out of scope: no other changes to JsonUtils

---

## 6. Use isNotEmpty() instead of size > 0 in TagsRepositoryImpl (roadmap 7)

context: `TagsRepositoryImpl.kt:150` uses `attachedTo.size > 0` to set `isAttached`. Kotlin idiom is `attachedTo.isNotEmpty()`.

files:
- `app/src/main/java/org/ole/planet/myplanet/repository/TagsRepositoryImpl.kt` — `createTag` function at line 150
- do NOT touch other functions in this file

steps:
1. Replace `attachedTo.size > 0` with `attachedTo.isNotEmpty()`
2. Run `./gradlew testDefaultDebugUnitTest`

acceptance: `./gradlew testDefaultDebugUnitTest` green; tag attachment behavior unchanged

size budget: ~1 changed line, 1 file

out of scope: no other changes to TagsRepositoryImpl

---

## 7. Remove redundant toList() in LifeAdapter onItemMoveFinished (roadmap 7)

context: `LifeAdapter.kt:128-130` creates `val finalList = list.toList()` and then passes it to both `reorderCallback` and `submitList`. Since `submitList` makes its own internal copy, the intermediate `finalList` variable is redundant.

files:
- `app/src/main/java/org/ole/planet/myplanet/ui/life/LifeAdapter.kt` — `onItemMoveFinished` at line 126
- do NOT touch `LifeFragment.kt` or other files

steps:
1. Remove the `val finalList = list.toList()` line
2. Replace both usages of `finalList` with `list` directly
3. Run `./gradlew testDefaultDebugUnitTest`

acceptance: `./gradlew testDefaultDebugUnitTest` green; life screen drag-to-reorder still works correctly

size budget: ~3 changed lines, 1 file

out of scope: no changes to `dragList` management

---

## 8. Use isNotEmpty() instead of size() > 0 in ConfigurationsRepositoryImpl (roadmap 7)

context: `ConfigurationsRepositoryImpl.kt:328` uses `rows.size() > 0` to check if the array is non-empty. Kotlin idiom is `rows.isNotEmpty()`.

files:
- `app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationsRepositoryImpl.kt` — relevant function at line 325
- do NOT touch other functions in this file

steps:
1. Replace `rows.size() > 0` with `rows.isNotEmpty()`
2. Run `./gradlew testDefaultDebugUnitTest`

acceptance: `./gradlew testDefaultDebugUnitTest` green; configuration loading behavior unchanged

size budget: ~1 changed line, 1 file

out of scope: no other changes to ConfigurationsRepositoryImpl

---

## 9. Use isEmpty() instead of size() == 0 in TransactionSyncManager (roadmap 7)

context: `TransactionSyncManager.kt:210` uses `arr.size() == 0` to check if the JsonArray is empty. Kotlin idiom is `arr.isEmpty()` which is more readable.

files:
- `app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt` — relevant code at line 210
- do NOT touch other functions in this file

steps:
1. Replace `arr.size() == 0` with `arr.isEmpty()`
2. Run `./gradlew testDefaultDebugUnitTest`

acceptance: `./gradlew testDefaultDebugUnitTest` green; sync behavior unchanged

size budget: ~1 changed line, 1 file

out of scope: no other changes to TransactionSyncManager

---

## 10. Use isNotEmpty() instead of size() > 0 in Feedback model (roadmap 7)

context: `Feedback.kt:51` and `Feedback.kt:72` both use `ar.size() > 0` to check if the JsonArray is non-empty. Kotlin idiom is `ar.isNotEmpty()`.

files:
- `app/src/main/java/org/ole/planet/myplanet/model/Feedback.kt` — `lastReply` getter at line 51 and `lastMessage` getter at line 72
- do NOT touch other files

steps:
1. Replace `ar.size() > 0` with `ar.isNotEmpty()` at line 51
2. Replace `ar.size() > 0` with `ar.isNotEmpty()` at line 72
3. Run `./gradlew testDefaultDebugUnitTest`

acceptance: `./gradlew testDefaultDebugUnitTest` green; feedback message display behavior unchanged

size budget: ~2 changed lines, 1 file

out of scope: no other changes to Feedback.kt

---
