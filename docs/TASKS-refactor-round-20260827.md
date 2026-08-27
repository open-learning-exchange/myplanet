# Task-Generation Brief — myPlanet refactor round

**date**: 2026-08-27 · **base commit**: 89fd72c · **open PRs checked**: #16274, #16270, #16258, #16257, #16192, #16101, #16096, #15951, #15825, #15824, #15820, #15808, #15699, #15559, #15519, #15412, #15267, #15266, #15226, #15198

---

## 1. Move finance totals from EnterprisesFinancesFragment to ViewModel (roadmap 1+3)

**context**: `EnterprisesFinancesFragment.kt:279-294` computes debit/credit/total in a private method called from the Fragment. The `HeaderState` data class at line 400 holds mutable state that should be in a ViewModel. This business logic belongs in the ViewModel where it can be tested, reused, and survives configuration changes.

**files**:
- `app/src/main/java/org/ole/planet/myplanet/ui/enterprises/EnterprisesFinancesFragment.kt` (lines 52, 128-130, 279-294, 352, 400-408). Remove `calculateTotal`, `HeaderState.debit/credit/total/isCautionVisible`, the call at line 352, and the `headerState` field at line 52.
- `app/src/main/java/org/ole/planet/myplanet/ui/enterprises/EnterprisesFinancesViewModel.kt`. Add `data class FinanceTotals(val debit: Int, val credit: Int, val total: Int, val isCautionVisible: Boolean)`. Add `private val _financeTotals = MutableStateFlow(FinanceTotals())` and expose `val financeTotals: StateFlow<FinanceTotals>`. Add `fun computeTotals(transactions: List<Transaction>)` that iterates the list and updates the StateFlow.

**steps**:
1. Add `FinanceTotals` data class and `_financeTotals` StateFlow to `EnterprisesFinancesViewModel`
2. Implement `computeTotals` in the ViewModel using the same logic from `calculateTotal`
3. In `EnterprisesFinancesFragment`, call `viewModel.computeTotals(results)` in `updatedFinanceList` after results are received
4. In `onViewCreated`, collect `_financeTotals` and bind to the header views (tvDebit, tvCredit, tvBalance, and caution visibility)
5. Remove the `calculateTotal` method, `HeaderState` class, and `headerState` field from the Fragment
6. Run `./gradlew testDefaultDebugUnitTest`

**acceptance**: `./gradlew testDefaultDebugUnitTest` green; finance header shows correct debit/credit/total and caution visibility in the enterprises finances screen after rotation and process death

**size budget**: ~60 changed lines, 2 files

**out of scope**: no DAO changes, no repository changes

---

## 2. Add Flow observe to CommunityDao (roadmap 1)

**context**: `CommunityDao.kt:14` only has `getAllSorted(): List<Community>` as a suspend function. Any UI watching community data must manually refresh by re-calling the suspend function. Adding a Flow version lets Room notify observers automatically when data changes, eliminating the need for manual refresh calls and reducing UI boilerplate.

**files**:
- `app/src/main/java/org/ole/planet/myplanet/data/room/dao/CommunityDao.kt` (after line 14). Add: `@Query("SELECT * FROM community ORDER BY weight ASC") abstract fun observeAllSorted(): Flow<List<Community>>`
- `app/src/main/java/org/ole/planet/myplanet/repository/CommunityRepository.kt` (after line 8). Add: `fun observeAllSorted(): Flow<List<Community>>`
- `app/src/main/java/org/ole/planet/myplanet/repository/CommunityRepositoryImpl.kt` (after line 28). Add: `override fun observeAllSorted(): Flow<List<Community>> = communityDao.observeAllSorted()`

**steps**:
1. Add `observeAllSorted` Flow query to `CommunityDao` with the same SQL as `getAllSorted`
2. Add `observeAllSorted` interface method to `CommunityRepository`
3. Implement in `CommunityRepositoryImpl` to delegate directly to the DAO Flow
4. Verify the existing `getAllSorted` suspend method is still used by any existing callers — keep it for backward compatibility
5. Run `./gradlew testDefaultDebugUnitTest`

**acceptance**: `./gradlew testDefaultDebugUnitTest` green; existing `getAllSorted` suspend method continues to work for all callers; the new Flow version can be used by UI code

**size budget**: ~8 changed lines, 3 files

**out of scope**: no UI changes required; existing suspend method stays

---

## 3. Add Flow observe to MyLifeDao (roadmap 1)

**context**: `MyLifeDao.kt:9` has `getByUserId(userId: String?): List<MyLife>` as a suspend function. Any UI watching life items must manually refresh by re-calling the suspend function. Adding a Flow version lets Room notify observers automatically when data changes, eliminating the need for manual refresh calls and reducing UI boilerplate.

**files**:
- `app/src/main/java/org/ole/planet/myplanet/data/room/dao/MyLifeDao.kt` (after line 9). Add: `@Query("SELECT * FROM my_life WHERE (:userId IS NULL OR userId IS NULL OR userId = :userId) ORDER BY weight") fun observeByUserId(userId: String?): Flow<List<MyLife>>`
- `app/src/main/java/org/ole/planet/myplanet/repository/LifeRepository.kt`. Check if the existing `getMyLife` is a Flow — if not, add `fun observeMyLife(userId: String?): Flow<List<MyLife>>`
- `app/src/main/java/org/ole/planet/myplanet/repository/LifeRepositoryImpl.kt`. Add `override fun observeMyLife(userId: String?): Flow<List<MyLife>> = myLifeDao.observeByUserId(userId)` if the interface method was added.

**steps**:
1. Add `observeByUserId` Flow query to `MyLifeDao` with the same SQL as `getByUserId`
2. Check `LifeRepository` interface — if `getMyLife` is a suspend function, add `observeMyLife` Flow method
3. Implement in `LifeRepositoryImpl` to delegate to the DAO Flow if the interface method was added
4. Verify the existing `getByUserId` suspend method is still used by any existing callers — keep it for backward compatibility
5. Run `./gradlew testDefaultDebugUnitTest`

**acceptance**: `./gradlew testDefaultDebugUnitTest` green; existing `getByUserId` suspend method continues to work for all callers; the new Flow version can be used by UI code

**size budget**: ~8 changed lines, 3 files

**out of scope**: no UI changes required; existing suspend method stays

---

## 4. Move notification type resolution from NotificationsViewModel to NotificationsRepository (roadmap 1+3)

**context**: `NotificationsViewModel.kt:258-288` contains `resolveType` and `typeLabelFor` — pure business logic for classifying and labeling notification types. This belongs in the repository layer, not the ViewModel, to enable reuse and testing in isolation. Additionally, the DAO's `markSynced` uses a forEach loop and `getUnreadCount` lacks a Flow version.

**files**:
- `app/src/main/java/org/ole/planet/myplanet/data/room/dao/NotificationDao.kt` (after line 19). Add: `@Query("SELECT COUNT(*) FROM notifications WHERE (userId = :userId OR (:isAdmin = 1 AND userId = 'SYSTEM')) AND isRead = 0") fun observeUnreadCount(userId: String, isAdmin: Boolean): Flow<Int>`. Also (lines 52-55) replace the forEach-based `@Transaction markSynced` with: `@Query("UPDATE notifications SET needsSync = 0 WHERE id IN (:ids)") suspend fun markSyncedBulk(ids: List<String>)` and update the transaction to call it for ids in chunks of 900 before updating rev values individually.
- `app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepository.kt` (add before closing brace). Add: `data class NotificationTypeInfo(val type: String, val label: String); fun resolveNotificationType(type: String, message: String, subType: String?): NotificationTypeInfo; fun observeUnreadCount(userId: String?, isAdmin: Boolean = false): Flow<Int>`
- `app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt` (add after existing imports). Move the `resolveType` logic from `NotificationsViewModel.kt:258-288` and `typeLabelFor` into `resolveNotificationType`. Also implement: `override fun observeUnreadCount(userId: String?, isAdmin: Boolean): Flow<Int> = notificationDao.observeUnreadCount(userId ?: "", isAdmin)`
- `app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModel.kt`. Replace the inline `resolveType` and `typeLabelFor` calls in `buildGroupedList` with calls to `notificationsRepository.resolveNotificationType`

**steps**:
1. Add `observeUnreadCount` Flow query and `markSyncedBulk` batch method to `NotificationDao`
2. Refactor `@Transaction markSynced` to use the batch method
3. Add `NotificationTypeInfo` data class and `resolveNotificationType` to `NotificationsRepository` interface
4. Implement in `NotificationsRepositoryImpl` with the logic moved from ViewModel
5. Implement `observeUnreadCount` in `NotificationsRepositoryImpl`
6. Update `NotificationsViewModel.buildGroupedList` to use the repository method
7. Remove `resolveType` and `typeLabelFor` from ViewModel
8. Run tests

**acceptance**: `./gradlew testDefaultDebugUnitTest` green; notifications grouping and labeling behave identically; unread count updates reactively; sync marks notifications correctly

**size budget**: ~70 changed lines, 4 files

**out of scope**: no other DAO changes; keep `parseTaskDate` and `formatStorageNotification` in ViewModel

---

## 5. Add countPendingFeedback and Flow observeByOwner to FeedbackDao (roadmap 1+7)

**context**: `FeedbackDao.kt:21` has `getPending(): List<Feedback>` but no count variant — callers load the full list to check size. `FeedbackDao.kt:15` has `getByOwnerFlow` but `FeedbackRepositoryImpl` wraps it in `flow { emit(...) }` instead of delegating directly.

**files**:
- `app/src/main/java/org/ole/planet/myplanet/data/room/dao/FeedbackDao.kt` (after line 21). Add: `@Query("SELECT COUNT(*) FROM feedback WHERE isUploaded = 0") suspend fun countPending(): Int`
- `app/src/main/java/org/ole/planet/myplanet/repository/FeedbackRepository.kt` (after line 25). Add: `suspend fun countPendingFeedback(): Int`
- `app/src/main/java/org/ole/planet/myplanet/repository/FeedbackRepositoryImpl.kt` (line 72). Add: `override suspend fun countPendingFeedback(): Int = feedbackDao.countPending()` after the existing methods. Also update `getFeedback` to directly return the DAO flow without wrapping.
- `app/src/main/java/org/ole/planet/myplanet/services/upload/UploadConfigs.kt` (line 201). If `fetchPendingItems` lambda calls `.size` on the result, update to use the new count method instead.

**steps**:
1. Add `countPending` to `FeedbackDao`
2. Add interface method `countPendingFeedback` to `FeedbackRepository`
3. Implement in `FeedbackRepositoryImpl`; simplify `getFeedback` to delegate to DAO Flow
4. Update any caller checking list size to use count method
5. Run tests

**acceptance**: `./gradlew testDefaultDebugUnitTest` green; pending feedback count still correct in upload flow; feedback list updates reactively

**size budget**: ~15 changed lines, 4 files

**out of scope**: no UI changes, keep existing `getPendingFeedback` method

---

## 6. Move CSV export from EnterprisesReportsFragment to ViewModel (roadmap 1+3)

**context**: `EnterprisesReportsFragment.kt:78-86` builds a CSV file inline in the Fragment using `viewLifecycleOwner.lifecycleScope.launch`. The export logic should live in the ViewModel where it can be reused, tested, and survives configuration changes. Moving coroutine work out of the Fragment also improves testability.

**files**:
- `app/src/main/java/org/ole/planet/myplanet/ui/enterprises/EnterprisesViewModel.kt` (after line 65). Add: `private val _exportResult = MutableSharedFlow<Result<String>>(extraBufferCapacity = 1); suspend fun exportReportsCsv(reports: List<MyTeam>): String = enterprisesRepository.exportReportsAsCsv(reports, teamsRepository.getTeamNameFromPrefs() ?: "")`
- `app/src/main/java/org/ole/planet/myplanet/ui/enterprises/EnterprisesReportsFragment.kt` (lines 78-86). Replace the inline coroutine and CSV write with a call to `viewModel.exportReportsCsv(reports)` and handle the Result.

**steps**:
1. Add `exportReportsCsv` method to `EnterprisesViewModel` that delegates to `enterprisesRepository.exportReportsAsCsv`
2. Update `EnterprisesReportsFragment` to call `viewModel.exportReportsCsv(reports)` instead of the inline coroutine
3. Handle the returned CSV string — write to file and share using the same Intent as before
4. Remove the inline coroutine and CSV construction from the Fragment
5. Ensure the ViewModel method is `suspend` so it can call the repository's suspend method
6. Run `./gradlew testDefaultDebugUnitTest`

**acceptance**: `./gradlew testDefaultDebugUnitTest` green; CSV export still produces identical output and the share dialog appears correctly

**size budget**: ~25 changed lines, 2 files

**out of scope**: no DAO changes, no repository changes

---

## 7. Add member count query to TeamDao (roadmap 1+7)

**context**: `TeamDao.kt` has no `getMemberCount` query. `TeamsRepositoryImpl.kt:1047` calls `getJoinedMembers(teamId).size` to get a count — loading all member rows into memory just to compute an integer. The repository already has `getJoinedMemberCount` at the interface level, but the implementation loads unnecessary data.

**files**:
- `app/src/main/java/org/ole/planet/myplanet/data/room/dao/TeamDao.kt` (add before upsert methods). Check if `getMemberCount(teamId: String): Int` exists — if not, add `@Query("SELECT COUNT(*) FROM teams WHERE teamId = :teamId AND docType = 'membership'") suspend fun getMemberCount(teamId: String): Int`
- `app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt` (line 1047). If a `getMemberCount` DAO method exists, update `getJoinedMemberCount` to call `teamDao.getMemberCount(teamId)` instead of loading all members.

**steps**:
1. Inspect `TeamDao.kt` for an existing `getMemberCount` query — the DAO may have been updated since this task was written; check all methods before adding a duplicate
2. If missing, add `@Query("SELECT COUNT(*) FROM teams WHERE teamId = :teamId AND docType = 'membership'") suspend fun getMemberCount(teamId: String): Int` before the upsert methods
3. Update `TeamsRepositoryImpl.getJoinedMemberCount` to call `teamDao.getMemberCount(teamId)` instead of `getJoinedMembers(teamId).size`
4. Run `./gradlew testDefaultDebugUnitTest`
5. Verify the requests screen still displays the correct member count

**acceptance**: `./gradlew testDefaultDebugUnitTest` green; team member count displays correctly in the requests screen without loading all member rows into memory

**size budget**: ~10 changed lines, 2 files

**Note**: This task may become a no-op if PR #15951 or another open PR already added `getMemberCount` to `TeamDao`. Verify before implementing.

**out of scope**: no changes to repository interface (TeamsMembersRepository)

---

## 8. Add count query to RetryDao (roadmap 1+7)

**context**: `RetryDao.kt:12` has `getPending(): List<RetryOperation>` but no count variant. `RetryRepositoryImpl.kt:89` has `getPendingCount(): Long` but it currently uses a different implementation — adding a DAO-backed count method would be more efficient.

**files**:
- `app/src/main/java/org/ole/planet/myplanet/data/room/dao/RetryDao.kt` (after line 12). Add: `@Query("SELECT COUNT(*) FROM retry_queue WHERE runAt <= :now") suspend fun countPending(now: Long): Int`
- `app/src/main/java/org/ole/planet/myplanet/repository/RetryRepository.kt` (after line 26). Add: `suspend fun countPending(): Int`
- `app/src/main/java/org/ole/planet/myplanet/repository/RetryRepositoryImpl.kt` (line 89). Add: `override suspend fun countPending(): Int = retryDao.countPending(timeProvider.now().toLong())`. Update `getPendingCount()` to delegate to this method.

**steps**:
1. Add `countPending` to `RetryDao`
2. Add interface method to `RetryRepository`
3. Implement in `RetryRepositoryImpl`
4. Update callers checking `getPending().size`
5. Run tests

**acceptance**: `./gradlew testDefaultDebugUnitTest` green; retry queue still processes correctly

**size budget**: ~12 changed lines, 3 files

**out of scope**: no UI changes, keep existing `getPending` method

---

## 9. Add Flow observe to UserChallengeActionsDao (roadmap 1)

**context**: `UserChallengeActionsDao.kt` has no Flow-based observe methods. Any UI watching challenge actions must manually refresh by re-calling suspend functions. Adding a Flow version lets Room notify observers automatically when data changes, eliminating the need for manual refresh calls and reducing UI boilerplate.

**files**:
- `app/src/main/java/org/ole/planet/myplanet/data/room/dao/UserChallengeActionsDao.kt` (check for existing `getByUserId` suspend and add a Flow variant). Add `@Query("SELECT * FROM user_challenge_actions WHERE userId = :userId") fun observeByUserId(userId: String): Flow<List<UserChallengeAction>>`
- `app/src/main/java/org/ole/planet/myplanet/repository/UserRepository.kt` (check interface). Add `fun observeChallengeActions(userId: String): Flow<List<UserChallengeAction>>` if a method is needed
- `app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt`. Implement the Flow method if added to interface.

**steps**:
1. Inspect `UserChallengeActionsDao.kt` — if `getByUserId` exists as a suspend function, add `observeByUserId` Flow query
2. Check `UserRepository` interface — add `observeChallengeActions` if the existing `getChallengeActions` is a suspend function
3. Implement in `UserRepositoryImpl` to delegate to the DAO Flow if the interface method was added
4. Verify the existing suspend method is still used by any existing callers — keep it for backward compatibility
5. Run `./gradlew testDefaultDebugUnitTest`

**acceptance**: `./gradlew testDefaultDebugUnitTest` green; existing suspend methods continue to work for all callers; the new Flow version can be used by UI code

**size budget**: ~8 changed lines, 3 files

**out of scope**: no UI changes required; existing suspend method stays

---

## 10. Add Flow observe to ApkLogDao (roadmap 1)

**context**: `ApkLogDao.kt` has 4 suspend methods but no Flow-based observe. Any code watching pending APK logs must manually refresh by re-calling `getPending`. Adding a Flow version lets Room notify observers automatically when pending log data changes, eliminating the need for manual refresh calls.

**files**:
- `app/src/main/java/org/ole/planet/myplanet/data/room/dao/ApkLogDao.kt` (after line 12). Add: `@Query("SELECT * FROM apk_logs WHERE isUploaded = 0") fun observePending(): Flow<List<ApkLog>>`
- `app/src/main/java/org/ole/planet/myplanet/repository/DiagnosticsRepository.kt`. Add: `fun observePendingApkLogs(): Flow<List<ApkLog>>`
- `app/src/main/java/org/ole/planet/myplanet/repository/DiagnosticsRepositoryImpl.kt`. Add: `override fun observePendingApkLogs(): Flow<List<ApkLog>> = apkLogDao.observePending()`

**steps**:
1. Add `observePending` Flow query to `ApkLogDao` with the same SQL as `getPending`
2. Add `observePendingApkLogs` interface method to `DiagnosticsRepository`
3. Implement in `DiagnosticsRepositoryImpl` to delegate directly to the DAO Flow
4. Verify the existing `getPending` suspend method is still used by any existing callers — keep it for backward compatibility
5. Run `./gradlew testDefaultDebugUnitTest`

**acceptance**: `./gradlew testDefaultDebugUnitTest` green; existing `getPending` suspend method continues to work for all callers; the new Flow version can be used by UI code

**size budget**: ~8 changed lines, 3 files

**out of scope**: no UI changes required; existing suspend method stays
