Date: 2026-08-19
Base commit: 9c54a0341557a7e7ae4bdc313fd1c97c0cc23b32
Open PRs checked: could not check open PRs

### 1. replace member-count list load with the existing count query (roadmap 1+7)
context:
RequestsViewModel.kt:39 calls getJoinedMembers(teamId).size, which
loads every membership row plus one user query per member, to produce
an Int. the repository already exposes getJoinedMemberCount(teamId)
backed by SQL COUNT.
files:
app/src/main/java/org/ole/planet/myplanet/ui/teams/members/RequestsViewModel.kt. do NOT touch TeamsRepositoryImpl.
steps:
  1. Swap the call to `getJoinedMemberCount`
  2. Remove unused imports
  3. Run the unit tests
acceptance:
./gradlew testDefaultDebugUnitTest green; requests screen still shows
the correct joined-member count
size budget:
~2 changed lines, 1 file
out of scope:
no DAO changes, no repository changes

---
### 2. replace slow String.format hex encoding with bitwise loop (roadmap 7)
context:
Sha256Utils.kt:19 and AndroidDecrypter.kt:49 use
`String.format("%02x", ...)` in high-frequency hot paths for hex
encoding. Parsing the format string at runtime is extremely slow and
causes significant allocation overhead. Memory explicitly forbids
this.
files:
app/src/main/java/org/ole/planet/myplanet/utils/Sha256Utils.kt,
app/src/main/java/org/ole/planet/myplanet/utils/AndroidDecrypter.kt.
steps:
  1. Replace `"%02x".format(...)` in `Sha256Utils.kt` with a manual bitwise append (or `joinToString("") { it.toUByte().toString(16).padStart(2, '0') }`)
  2. Replace `String.format("%02x", b)` in `AndroidDecrypter.kt` similarly
  3. Run unit tests
acceptance:
./gradlew testDefaultDebugUnitTest green; authentication and
decryption still succeed
size budget:
~5 changed lines, 2 files
out of scope:
do not refactor the overall crypto strategy, only the hex string
generation

---
### 3. use async apply() instead of blocking commit() for SharedPreferences (roadmap 7)
context:
SharedPrefManager.kt:284 and TransactionSyncManager.kt:326 use
`.commit()` which synchronously blocks on I/O. Memory explicitly
advises using `.apply()` in high-frequency loops to avoid blocking.
files:
app/src/main/java/org/ole/planet/myplanet/services/SharedPrefManager.kt, app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt.
steps:
  1. Change `.commit()` to `.apply()` in both files
  2. Run unit tests
acceptance:
./gradlew testDefaultDebugUnitTest green; shared preferences save
successfully without blocking
size budget:
~2 changed lines, 2 files
out of scope:
do not touch other shared pref operations

---
### 4. add explicit comparator to distinctUntilChanged for Room entities (roadmap 7+8)
context:
TeamsRepositoryImpl.kt:297 uses `.distinctUntilChanged()` without a
comparator on a Flow of Room entities (TeamDetails). Room constructs
fresh instances on every invalidation, so parameterless reference
equality checks will always fail and suppress nothing, causing
redundant UI updates.
files:
app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt.
steps:
  1. Add a lambda to `.distinctUntilChanged { old, new -> old.teamId == new.teamId && old.teamRev == new.teamRev }` (using id and rev/relevant fields)
  2. Run unit tests
acceptance:
./gradlew testDefaultDebugUnitTest green; team list only updates when
data actually changes
size budget:
~5 changed lines, 1 file
out of scope:
do not modify the DAOs or other repositories

---
### 5. replace array combine with typesafe overload in ChatViewModel (roadmap 8+9)
context:
ChatViewModel.kt:116 uses an untyped combine array overload because it
has 5 flows, but Kotlin natively supports 5 flows typesafe. Combining
more than 5 flows risks runtime `ClassCastException` and losing type
safety.
files:
app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatViewModel.kt.
steps:
  1. Remove the array based `combine` and use the 5-flow overload `combine(flow1, flow2, flow3, flow4, flow5) { a, b, c, d, e -> ... }`
  2. Ensure types match properly
  3. Run unit tests
acceptance:
./gradlew testDefaultDebugUnitTest green; chat ui state updates
correctly
size budget:
~10 changed lines, 1 file
out of scope:
do not change the flows being combined

---
### 6. wrap ResponseBody.string() calls in withContext(IO) to prevent blocking main thread (roadmap 7+8)
context:
ChatApiService.kt:33 and ConfigurationsRepositoryImpl.kt:214/230 call
response.body()?.string() directly. Memory states 'calling
response.body()?.string() on a returned ResponseBody performs a
synchronous, blocking network stream read. Always wrap these .string()
calls in withContext(dispatcherProvider.io) to prevent
NetworkOnMainThreadException or ANRs when accessed from main-
dispatched coroutines.'
files:
app/src/main/java/org/ole/planet/myplanet/data/api/ChatApiService.kt, app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationsRepositoryImpl.kt.
steps:
  1. Wrap `response.body()?.string()` in `withContext(Dispatchers.IO)` (or inject DispatcherProvider)
  2. Update the tests
acceptance:
./gradlew testDefaultDebugUnitTest green
size budget:
~5 changed lines, 2 files
out of scope:
do not change the Retrofit return type

---
### 7. add Locale.US to String.format for numerical data (roadmap 8)
context:
TimeUtils.kt:181 and EventsDetailFragment.kt:190 use `String.format`
with `%02d` for numerical data. Memory states: 'When using
String.format for numerical data (e.g., duration formatting), always
specify a Locale (like Locale.US) to prevent formatting bugs in
locales with non-ASCII digits'.
files:
app/src/main/java/org/ole/planet/myplanet/utils/TimeUtils.kt, app/src/main/java/org/ole/planet/myplanet/ui/events/EventsDetailFragment.kt.
steps:
  1. Add `Locale.US` as the first argument to `String.format` in both files
  2. Run unit tests
acceptance:
./gradlew testDefaultDebugUnitTest green; times format correctly
without crashing on non-ASCII locales
size budget:
~2 changed lines, 2 files
out of scope:
do not refactor other String.format usages

---
### 8. chunk IN clause parameters for DAO queries to prevent SQLiteException (roadmap 7+8)
context:
TagsRepositoryImpl.kt passes unbounded lists to
`tagDao.getByIds(allTagIds)` and `tagDao.getByIds(tagIds)`. Memory
warns: 'When querying Room DAOs using IN clauses for batched reads,
chunk the parameter list (e.g., .chunked(300).flatMap {
dao.getByIds(it) }) at the repository layer to prevent
SQLiteException: too many SQL variables.'
files:
app/src/main/java/org/ole/planet/myplanet/repository/TagsRepositoryImpl.kt.
steps:
  1. Replace `tagDao.getByIds(...)` with `.chunked(900).flatMap { tagDao.getByIds(it) }` at lines 80 and 110
  2. Update the tests
acceptance:
./gradlew testDefaultDebugUnitTest green; queries no longer crash on
large datasets
size budget:
~4 changed lines, 1 file
out of scope:
do not modify the DAO layer itself

---
### 9. avoid O(N*M) scaling when parsing hierarchical IDs in ProgressRepositoryImpl (roadmap 7+8)
context:
ProgressRepositoryImpl.kt:80 uses `courseIds.firstOrNull {
parentId.contains(it) }` inside a `groupBy` loop over all submissions.
Memory warns: 'When parsing hierarchical or composite IDs (e.g.,
examId@courseId) to group or filter data, avoid using .contains
against a list of keys inside iterative operations to prevent O(N*M)
performance bottlenecks and substring collision bugs. Instead, split
by the known delimiter and use an O(1) HashSet lookup.'
files:
app/src/main/java/org/ole/planet/myplanet/repository/ProgressRepositoryImpl.kt.
steps:
  1. Remove the fallback substring check `courseIds.firstOrNull { parentId.contains(it) }`
  2. Correctly map the split parts to the `courseIdsSet` using `.find { courseIdsSet.contains(it) }` on the split list.
  3. Update tests
acceptance:
./gradlew testDefaultDebugUnitTest green; course tracking correctly
resolves parent course IDs
size budget:
~10 changed lines, 1 file
out of scope:
do not modify the return type or structure

---
### 10. use viewLifecycleOwner.lifecycleScope in Fragments instead of lifecycleScope (roadmap 7)
context:
Memory states: 'In Android Fragments, always use
viewLifecycleOwner.lifecycleScope rather than lifecycleScope for
launching coroutines to prevent scope leaks and ensure cancellation
when the view is destroyed.' Several fragments launch coroutines
directly on `lifecycleScope`.
files:
app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesFragment.kt, app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt, app/src/main/java/org/ole/planet/myplanet/base/BaseTeamFragment.kt, app/src/main/java/org/ole/planet/myplanet/ui/courses/CourseStepFragment.kt.
steps:
  1. Find instances of `lifecycleScope.launch` in the named Fragments and replace with `viewLifecycleOwner.lifecycleScope.launch`
  2. Run unit tests
acceptance:
./gradlew testDefaultDebugUnitTest green; no coroutines leak after
view destruction
size budget:
~10 changed lines, 4 files
out of scope:
do not modify Activities since they do not have viewLifecycleOwner
