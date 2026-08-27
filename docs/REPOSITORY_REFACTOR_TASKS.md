# Repository Refactor Tasks — Reinforcing Layer Boundaries

**date:** 2025-08-27  
**base commit:** 89fd72c (grafted, HEAD → qwen-code-a49913af-2a93-49f6-9f88-b80ce7c09cbf, tag: v0.67.58, master)  
**open PRs checked:** could not check open PRs (no GitHub API access in this environment)

---

### 1. Move team member-count query into TeamsMembersRepository interface (roadmap 1+4)

context: `RequestsViewModel.kt:37` calls `teamsRepository.getJoinedMemberCount(teamId)` which is declared in `TeamsMembersRepository.kt:17` but implemented in `TeamsRepositoryImpl.kt:1047`. The method lives in the monolithic impl but is only exposed via the members sub-interface. This creates a cross-feature data leak where membership counting logic is buried in the main repo impl rather than being explicitly part of the members contract.

files: 
- `app/src/main/java/org/ole/planet/myplanet/repository/TeamsMembersRepository.kt` (line 17 — already declared)
- `app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt` (lines 1047-1049 — implementation exists)
- do NOT touch `TeamsRepository.kt` or any DAO files

steps: 
1. Confirm `getJoinedMemberCount` is already declared in `TeamsMembersRepository` interface (it is at line 17)
2. Verify `TeamsRepositoryImpl` implements it correctly using `teamDao.countByTeamIdAndDocType` (it does at lines 1047-1049)
3. No code changes needed — this task documents that the boundary is already correct
4. Run unit tests to confirm no regression

acceptance: `./gradlew testDefaultDebugUnitTest` green; requests screen still shows correct joined-member count; no file modifications required as boundary already exists

size budget: 0 changed lines, 0 files (verification task)

out of scope: no DAO changes, no new repository methods, no ViewModel changes

---

### 2. Extract hasPendingRequest from TeamsRepositoryImpl into dedicated helper (roadmap 1+4)

context: `TeamsRepositoryImpl.kt:559-562` implements `hasPendingRequest` by directly querying `teamDao.getByTeamIdUserIdAndDocType`. This logic is duplicated conceptually wherever request status is checked. The method is already part of `TeamsMembersRepository.kt:9` but the implementation uses inline DAO calls instead of a reusable pattern.

files: 
- `app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt` (lines 559-562)
- `app/src/main/java/org/ole/planet/myplanet/data/room/dao/TeamDao.kt` (line 24 — existing query)
- do NOT touch `TeamsMembersRepository.kt` interface

steps: 
1. Keep existing `hasPendingRequest` implementation at lines 559-562 unchanged
2. Add KDoc to clarify the method handles null-safety for teamId and userId
3. Ensure `teamDao.getByTeamIdUserIdAndDocType` at line 24 has proper null handling
4. Run tests to verify pending request detection works

acceptance: `./gradlew testDefaultDebugUnitTest` green; TeamDetailFragment shows correct pending request state; no behavioral changes

size budget: ~5 changed lines (KDoc only), 1 file

out of scope: no new DAO queries, no interface changes, no ViewModel modifications

---

### 3. Consolidate isMember and isTeamLeader into single membership status call (roadmap 1+7)

context: `TeamsRepositoryImpl.kt:547-557` has two separate methods `isMember` and `isTeamLeader` that each query `teamDao.getByTeamIdAndDocType` and iterate results. Callers like `TeamDetailFragment.kt:147` and various UI fragments make multiple calls, causing duplicate database reads. The interface `TeamsMembersRepository.kt:7-8` declares them separately but they can be combined internally.

files: 
- `app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt` (lines 547-557)
- `app/src/main/java/org/ole/planet/myplanet/repository/TeamsMembersRepository.kt` (lines 7-8)
- do NOT touch `TeamDao.kt` or any Fragment files

steps: 
1. Add private helper `getMembershipStatus(teamId, userId)` returning a data class with `isMember` and `isLeader` flags
2. Refactor `isMember` to call the helper and return `status.isMember`
3. Refactor `isTeamLeader` to call the helper and return `status.isLeader`
4. Ensure single DAO call per team/user pair instead of two

acceptance: `./gradlew testDefaultDebugUnitTest` green; team detail screen loads with same membership state; performance improved by 50% fewer DAO calls

size budget: ~20 changed lines, 1 file

out of scope: no interface signature changes, no Fragment refactoring, no new data classes exposed publicly

---

### 4. Move getRequestedMembers logic from TeamsRepositoryImpl to use dedicated DAO query (roadmap 1+7)

context: `TeamsRepositoryImpl.kt:1051-1056` implements `getRequestedMembers` by calling `teamDao.getByTeamIdAndDocType(teamId, "request")` then mapping userIds to UserEntities. This pattern duplicates the user-fetching logic found elsewhere. The DAO already has `getByTeamIdAndDocType` but we can add a specialized query for request-specific fields.

files: 
- `app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt` (lines 1051-1056)
- `app/src/main/java/org/ole/planet/myplanet/data/room/dao/TeamDao.kt` (add new query method)
- do NOT touch `TeamsMembersRepository.kt` interface

steps: 
1. Add `@Query("SELECT userId FROM teams WHERE teamId = :teamId AND docType = 'request' AND userId IS NOT NULL")` to TeamDao returning `List<String>`
2. Update `getRequestedMembers` to use the new query instead of full entity fetch
3. Batch-fetch UserEntities using existing `userRepository.getUserById` calls
4. Remove unused imports from TeamsRepositoryImpl

acceptance: `./gradlew testDefaultDebugUnitTest` green; RequestsFragment shows same list of pending members; reduced memory footprint by not loading full Team entities

size budget: ~10 changed lines, 2 files

out of scope: no interface changes, no Fragment logic changes, no new repository methods

---

### 5. Add Flow-based observation for requested members count (roadmap 1+6)

context: `RequestsViewModel.kt:36-40` fetches requested members and member count imperatively in `fetchMembers()`. This requires manual refresh when data changes. The DAO already has `observeByTeamIdAndDocType` at `TeamDao.kt:23` but the repository doesn't expose a Flow-based count for requests.

files: 
- `app/src/main/java/org/ole/planet/myplanet/repository/TeamsMembersRepository.kt` (add new method)
- `app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt` (implement new method)
- do NOT touch `RequestsViewModel.kt` yet (that's a future migration task)

steps: 
1. Add `fun observeRequestedMemberCountFlow(teamId: String): Flow<Int>` to `TeamsMembersRepository`
2. Implement in `TeamsRepositoryImpl` using `teamDao.observeByTeamIdAndDocType(teamId, "request").map { it.size }`
3. Add KDoc explaining this is for reactive UI updates
4. Ensure dispatcher is set via `flowOn(dispatcherProvider.default)`

acceptance: `./gradlew testDefaultDebugUnitTest` green; new Flow method compiles and type-checks; no existing functionality broken

size budget: ~8 changed lines, 2 files

out of scope: no ViewModel migration yet, no Fragment changes, no test additions beyond compilation

---

### 6. Extract getJoinRequestInfo from TeamsRepositoryImpl into TeamsMembersRepository (roadmap 1+4)

context: `TeamsRepositoryImpl.kt:402-410` has `getJoinRequestInfo` returning `JoinRequestInfo` data class, but this method is NOT declared in `TeamsMembersRepository.kt` interface. It's called from UI code dealing with join requests, making it a members-repository concern that bypasses the interface boundary.

files: 
- `app/src/main/java/org/ole/planet/myplanet/repository/TeamsMembersRepository.kt` (add method declaration)
- `app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt` (lines 402-410 — implementation exists)
- do NOT touch `TeamsRepository.kt` parent interface

steps: 
1. Define `data class JoinRequestInfo(val id: String, val teamId: String, val requesterName: String?)` in TeamsMembersRepository.kt if not already present
2. Add `suspend fun getJoinRequestInfo(requestId: String): JoinRequestInfo?` to `TeamsMembersRepository` interface
3. Verify `TeamsRepositoryImpl` already implements it (it does at lines 402-410)
4. Run tests to ensure interface binding works via Hilt

acceptance: `./gradlew testDefaultDebugUnitTest` green; join request dialogs function correctly; interface now fully declares all public methods used by UI

size budget: ~6 changed lines, 1 file

out of scope: no implementation changes, no DAO modifications, no ViewModel updates

---

### 7. Move respondToMemberRequest validation logic into repository layer (roadmap 1+4)

context: `RequestsViewModel.kt:44-64` contains business logic for accepting/rejecting member requests including optimistic UI updates and error rollback. The actual repository call at line 56 `teamsRepository.respondToMemberRequest(teamId, userId, isAccepted)` delegates to `TeamsRepositoryImpl` but the ViewModel handles too much orchestration.

files: 
- `app/src/main/java/org/ole/planet/myplanet/repository/TeamsMembersRepository.kt` (enhance method contract)
- `app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt` (find and update implementation)
- do NOT touch `RequestsViewModel.kt` orchestration logic yet

steps: 
1. Find `respondToMemberRequest` implementation in TeamsRepositoryImpl (search for the method)
2. Add KDoc specifying it handles all validation including null checks for teamId and userId
3. Ensure repository returns `Result<Unit>` with specific error types for validation failures
4. Verify method is declared in `TeamsMembersRepository` interface (it is at line 13)

acceptance: `./gradlew testDefaultDebugUnitTest` green; accept/reject member requests work identically; repository now owns validation logic

size budget: ~8 changed lines (KDoc and error handling), 1 file

out of scope: no ViewModel simplification yet, no new error types defined, no Fragment changes

---

### 8. Add batch member removal method to reduce DAO round-trips (roadmap 1+7)

context: `TeamsMembersRepository.kt:12` has `removeMember(teamId, userId)` for single removal. When removing multiple members (e.g., team cleanup scenarios), callers loop and cause N DAO transactions. The underlying `TeamDao.kt:29` has `deleteByTeamIdUserIdAndDocType` but no batch variant exists.

files: 
- `app/src/main/java/org/ole/planet/myplanet/repository/TeamsMembersRepository.kt` (add new method)
- `app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt` (implement new method)
- `app/src/main/java/org/ole/planet/myplanet/data/room/dao/TeamDao.kt` (add batch delete query)

steps: 
1. Add `@Query("DELETE FROM teams WHERE teamId = :teamId AND userId IN (:userIds) AND docType = :docType")` to TeamDao
2. Add `suspend fun removeMembers(teamId: String, userIds: List<String>): Int` to TeamsMembersRepository
3. Implement in TeamsRepositoryImpl calling the new DAO method with docType="membership"
4. Return count of removed members for logging purposes

acceptance: `./gradlew testDefaultDebugUnitTest` green; bulk member removal completes faster; no change to single-member removal behavior

size budget: ~12 changed lines, 3 files

out of scope: no Fragment batch operations added yet, no transaction wrapping (already handled by Room), no undo functionality

---

### 9. Create getNextLeaderCandidate with explicit exclusion logic (roadmap 1+4)

context: `TeamsMembersRepository.kt:20` declares `getNextLeaderCandidate(teamId, excludeUserId)` but the implementation in `TeamsRepositoryImpl` needs to use the existing DAO query `getEligibleNextLeaderCandidates` at `TeamDao.kt:22`. This ensures leadership transfer excludes current leader and handles edge cases consistently.

files: 
- `app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt` (implement or verify implementation)
- `app/src/main/java/org/ole/planet/myplanet/data/room/dao/TeamDao.kt` (line 22 — query exists)
- do NOT touch `TeamsMembersRepository.kt` interface

steps: 
1. Search for `getNextLeaderCandidate` implementation in TeamsRepositoryImpl
2. If missing, implement by calling `teamDao.getEligibleNextLeaderCandidates(teamId, excludeUserId).firstOrNull()`
3. Map the returned MyTeam entity to UserEntity via userId lookup
4. Add KDoc explaining exclusion parameter purpose

acceptance: `./gradlew testDefaultDebugUnitTest` green; leadership transfer flow finds correct next candidate; excluded leader not returned

size budget: ~10 changed lines, 1 file

out of scope: no UI flow changes, no additional candidate ranking logic, no tie-breaking rules

---

### 10. Document repository interface segregation pattern for future KMM migration (roadmap 4+9)

context: `TeamsRepository.kt:49` extends three sub-interfaces (`TeamsFinancesRepository`, `TeamsMembersRepository`, `TeamsNotificationsRepository`) demonstrating interface segregation. This pattern supports roadmap item 9 (KMM core) by allowing platform-specific implementations to mix only needed capabilities. Other repositories should follow this pattern but currently don't have consistent documentation.

files: 
- `app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepository.kt` (add KDoc to interface)
- `app/src/main/java/org/ole/planet/myplanet/repository/TeamsMembersRepository.kt` (add KDoc header)
- do NOT touch implementation files or other repositories yet

steps: 
1. Add KDoc to `TeamsRepository` explaining the segregation pattern and KMM rationale
2. Add KDoc header to `TeamsMembersRepository` stating it contains only membership-related operations
3. Reference roadmap item 9 in comments for future maintainers
4. Ensure no android.* imports in interface files (verify clean Kotlin)

acceptance: `./gradlew testDefaultDebugUnitTest` green; interfaces remain unchanged functionally; KDoc clearly explains the architectural pattern for KMM preparation

size budget: ~15 changed lines (comments only), 2 files

out of scope: no refactoring of other repositories yet, no actual KMM migration, no build configuration changes

