2026-11-10 · 2093a6224b221079f1413e2ab1f22cebec8cf736 · open PRs checked (35: #4075, #8175, #10993, #13287, #13355, #13604, #13657, #13848, #13928, #14427, #14650, #14883, #15108, #15226, #15266, #15267, #15559, #15808, #15820, #15824, #15825, #15951, #16594, #17694, #17811, #17832, #18029, #18045, #18076, #18455, #18539, #18540, #18551, #18554, #18555)

No open PR is labeled review-ready or merge. Any file those 35 PRs touch is off-limits, including every `*RepositoryImpl` except Diagnostics, Dictionary, and Life, plus `RepositoryModule.kt`, `RoomModule.kt`, `AppDatabase.kt`, and schema migrations. These ten work orders do not edit those files. They are independently mergeable in any order. Do not add dependencies, TODOs, or unused code.

---

### 1. split user id lookups so each side can use its index (roadmap 1+7+9)

context: `UserDao.getById` is `id = :id OR _id = :id LIMIT 1`
(`UserDao.kt:11-12`). `id` is the primary key (`UserEntity.kt:28`) and `_id`
is indexed (`UserEntity.kt:26`), but the OR stops SQLite from using either
access path, and LIMIT 1 is nondeterministic when both keys hit different
rows. `getUsersByAnyIdsInternal` (`UserDao.kt:13-16`) repeats the OR and
chunks at 450 because the list is bound twice. Member screens and submission
detail resolve users through this path. The DAO already has no `android.*`
import; keep it that way so the lookup can move into the platform-free core
unchanged.

files: `app/src/main/java/org/ole/planet/myplanet/data/room/dao/UserDao.kt`
(`getById`, `getUsersByAnyIds`, `getUsersByAnyIdsInternal`).
`app/src/test/java/org/ole/planet/myplanet/data/room/dao/UserDaoTest.kt`
(`createUser` at line 37, the two `getUsersByAnyIds_*` tests). Do not touch
`UserEntity.kt`, `UserRepository.kt`, `UserRepositoryImpl.kt`, or
`AppDatabase.kt`.

steps: 1. Keep the public signature `suspend fun getById(id: String): UserEntity?`,
but implement it as a primary-key lookup `WHERE id = :id LIMIT 1` and, only
on a miss, `WHERE _id = :id LIMIT 1`. Primary key wins. That is the only
allowed behavior change. 2. Replace the OR in `getUsersByAnyIdsInternal` with
two queries, `id IN (:userIds)` and `_id IN (:userIds)`. Keep
`getUsersByAnyIds` public. Raise its chunk from 450 to 900, matching the
other DAOs, because each statement now binds the list once. Still `distinct`
the input and `distinctBy { it.id }` on the union. 3. In `UserDaoTest`, add a
case where one row's `id` and another row's `_id` equal the same argument and
assert `getById` returns the primary-key row. Add an `_id`-only hit. Extend
the existing 1201-id test so a chunk larger than 900 mixed `id`/`_id` values
still returns each user once. Do not weaken `getUsersByAnyIds_matchesIdOr_Id`.
4. Run the unit tests named below.

acceptance: `./gradlew :app:testDefaultDebugUnitTest --tests org.ole.planet.myplanet.data.room.dao.UserDaoTest`
green, and `./gradlew testDefaultDebugUnitTest` stays green. A user opened by
local id and a user opened by Couch `_id` still resolves. When both keys
match different rows, the primary-key row is shown.

size budget: ~45 changed lines, 2 files

out of scope: no new index, no migration, no repository or viewmodel edits

---

### 2. split team id lookup and member-count existence check (roadmap 1+7)

context: `TeamDao.getByTeamId` is `_id = :teamId OR teamId = :teamId LIMIT 1`
(`TeamDao.kt:21`). `_id` is the primary key and `teamId` is indexed
(`MyTeam.kt:20-22`), so the OR blocks both indexes and LIMIT 1 can return
either row. `getById` already does the primary-key lookup (`TeamDao.kt:22`).
`countByTeamIdAndDocType` (`TeamDao.kt:35`) counts joined members with
`EXISTS (... u.id = teams.userId OR u._id = teams.userId)`, the same
non-sargable user-key OR. That count is what member-limit checks use.
`TeamDaoTest` has no coverage of either method today.

files: `app/src/main/java/org/ole/planet/myplanet/data/room/dao/TeamDao.kt`
(`getByTeamId`, `countByTeamIdAndDocType`).
`app/src/test/java/org/ole/planet/myplanet/data/room/dao/TeamDaoTest.kt`.
Do not touch `TeamsRepository.kt`, `TeamsRepositoryImpl.kt`, `MyTeam.kt`, or
`UserDao.kt` (task 1 owns `UserDao`).

steps: 1. Keep `suspend fun getByTeamId(teamId: String): MyTeam?`. Implement
it as `getById(teamId)` and, only on a miss, a new query
`WHERE teamId = :teamId LIMIT 1`. If both keys match different rows, the
`_id` row wins. Leave `getAllByTeamId` and `getResourceIdsByTeamId`
unchanged. 2. Rewrite the EXISTS in `countByTeamIdAndDocType` as two EXISTS
clauses, one `u.id = teams.userId` and one `u._id = teams.userId`, OR-ed.
Keep the rest of the predicate, including `isDeletePending = 0` and
`userId IS NOT NULL`. Do not substitute `countActiveMembershipsByTeamId`;
that query does not require a matching user. 3. Add `TeamDaoTest` cases:
`_id` hit wins over a different row whose `teamId` column matches;
`teamId`-column hit works when `_id` misses; the count includes a membership
whose `userId` matches `users.id`, one whose `userId` matches `users._id`,
and excludes a dangling `userId` and an `isDeletePending` row. Follow the
`MyTeam().apply { ... }` style already in that test. 4. Run the unit tests
named below.

acceptance: `./gradlew :app:testDefaultDebugUnitTest --tests org.ole.planet.myplanet.data.room.dao.TeamDaoTest`
green, and `./gradlew testDefaultDebugUnitTest` stays green. Opening a team
by document id or by `teamId` still returns that team. The joined-member
count does not include users who are not in `users`.

size budget: ~40 changed lines, 2 files

out of scope: no schema change, no call-site changes in blocked repository implementations

---

### 3. make my-life user lookups use the userId index (roadmap 1+7+9)

context: `MyLifeDao.observeByUserId`, `getByUserId`, `getVisibleByUserId`, and
`countByUserId` (`MyLifeDao.kt:14-24`) all use
`(:userId IS NULL AND ...) OR (:userId IS NOT NULL AND userId = :userId)`.
`userId` is indexed (`MyLife.kt:14`), but the parameter OR is not sargable,
so every my-life load scans the table. `LifeRepositoryImpl.normalizeUserId`
(`LifeRepositoryImpl.kt:19-21`) already turns blank and `"--"` into null
before calling the DAO. The DAO itself must keep a null parameter meaning
"unscoped" and a non-null parameter meaning exact `userId` equality.
`MyLifeDaoTest.observeByUserId_withNullMatchesRowsWithNullEmptyOrPlaceholderUserId`
locks the null behavior. The DAO already has no `android.*` import; this
rewrite must not add one.

files: `app/src/main/java/org/ole/planet/myplanet/data/room/dao/MyLifeDao.kt`
only. Do not edit `LifeRepositoryImpl.kt`, `LifeRepository.kt`, `MyLife.kt`,
or `MyLifeDaoTest.kt`.

steps: 1. Keep the four public signatures, including nullable `userId` and the
existing `ORDER BY weight ASC` on the three list queries. 2. Replace each
parameterized OR with a Kotlin branch inside the DAO: if `userId == null`,
run a query whose predicate is `userId IS NULL OR userId = '' OR userId = '--'`;
otherwise run `userId = :userId`. Name the new `@Query` methods with an
`Internal` suffix, matching `getByIdsInternal`. 3. Do not treat a blank or
`"--"` argument as unscoped. Only a null argument matches null, empty, and
`"--"` rows. `getVisibleByUserId` keeps `AND isVisible = 1` on both branches.
4. Run `MyLifeDaoTest` without changing its expectations.
`observeByUserId(null)` must still return the three unscoped rows and exclude
`user123`.

acceptance: `./gradlew :app:testDefaultDebugUnitTest --tests org.ole.planet.myplanet.data.room.dao.MyLifeDaoTest`
green, and `./gradlew testDefaultDebugUnitTest` stays green. My Life still
lists the signed-in user's rows in weight order, and a null user still sees
the shared null/blank/`"--"` rows.

size budget: ~45 changed lines, 1 file

out of scope: no repository normalization change, no new index, no viewmodel change

---

### 4. make personal title-count and id updates index-friendly (roadmap 1+7)

context: `PersonalDao.countByTitle` (`PersonalDao.kt:14-18`) filters with
`(:userId IS NULL OR :userId = '' OR userId = :userId)`. `userId` is indexed
(`Personal.kt:7`), but the parameter OR prevents that index from being used
when a real user id is passed, which is the save-path duplicate-title check.
`deleteByIdOrDocId` (`PersonalDao.kt:32-33`) and `updateFields`
(`PersonalDao.kt:41-42`) use `_id = :id OR id = :id`. `id` is the primary key
(`Personal.kt:9-11`), so the OR blocks the primary-key lookup.
`updateFields_resolvesByIdAndDocId` in `PersonalDaoTest.kt:91` already
requires both keys to work.

files: `app/src/main/java/org/ole/planet/myplanet/data/room/dao/PersonalDao.kt`
(`countByTitle`, `deleteByIdOrDocId`, `updateFields`).
`app/src/test/java/org/ole/planet/myplanet/data/room/dao/PersonalDaoTest.kt`
(`createPersonal` at line 53). Do not touch `PersonalsRepositoryImpl.kt`,
`Personal.kt`, or `PersonalsViewModel.kt` (task 10 owns the viewmodel).

steps: 1. Keep `countByTitle(title, userId)` public. If `userId` is null or
blank, count `title = :title COLLATE NOCASE` with no user filter. Otherwise
count `title = :title COLLATE NOCASE AND userId = :userId`. Preserve the
case-insensitive title match and the "blank user means any user" rule from
the comment at lines 12-13. 2. Replace the OR delete with a `@Transaction`
method that deletes `WHERE id = :id` and then `WHERE _id = :id`. Same rows
removed. 3. Replace the OR update with a `@Transaction` method that runs the
same COALESCE assignments twice, once `WHERE id = :id` and once
`WHERE _id = :id`. A row matching both is updated twice with the same values.
Leave `updateUploadedStatus` and `updateRemoteDocRef` alone. 4. Add tests:
blank and null `userId` count every matching title; a non-blank `userId` does
not count another user's title; delete and update still hit a row by `id` and
a different row by `_id` only. `createPersonal` sets `id` and `_id` equal, so
a doc-id-only row must set those fields differently. Keep
`updateFields_resolvesByIdAndDocId` green.

acceptance: `./gradlew :app:testDefaultDebugUnitTest --tests org.ole.planet.myplanet.data.room.dao.PersonalDaoTest`
green, and `./gradlew testDefaultDebugUnitTest` stays green. Saving a personal
resource still rejects a duplicate title for that user and still allows the
same title for a different user. Editing or deleting by local id or document
id still updates the right row.

size budget: ~50 changed lines, 2 files

out of scope: no new index, no repository implementation change

---

### 5. make opened-resource observation return one stable row per resource (roadmap 1+7+8)

context: `ResourceActivityDao.observeByUserAndType` (`ResourceActivityDao.kt:29-30`)
is `SELECT * ... GROUP BY resourceId` with no aggregate. SQLite returns an
arbitrary row per group, so the opened-resource list can change title or time
between reads. `ResourceActivity.id` is the primary key
(`ResourceActivity.kt:12-14`). The existing test
(`ResourceActivityDaoTest.kt:117`) only asserts the set of resource ids and a
size of 2. `getMostOpenedResource` already picks `MIN(title)` and must not be
changed. Callers in the blocked resources repository map this Flow to resource
ids, so the public signature must stay a `Flow<List<ResourceActivity>>`.

files: `app/src/main/java/org/ole/planet/myplanet/data/room/dao/ResourceActivityDao.kt`
(`observeByUserAndType`).
`app/src/test/java/org/ole/planet/myplanet/data/room/dao/ResourceActivityDaoTest.kt`
(`createActivity` at line 39, the test at line 117). Do not touch
`ResourcesRepositoryImpl.kt` or `ResourceActivity.kt`.

steps: 1. Keep the Flow signature
`observeByUserAndType(userName, type): Flow<List<ResourceActivity>>`.
2. Replace the bare GROUP BY with a query that returns the row whose `id` is
`MIN(id)` inside each `resourceId` group, still filtered by `user`, `type`,
and `resourceId IS NOT NULL`. Apply those filters in the subquery and again
on the outer select so another user's row cannot leak in through a colliding
id. `ORDER BY resourceId` so the list order is stable. 3. Extend the test at
line 117: insert two rows with the same `resourceId` and different titles, set
`id` explicitly to `"a"` and `"b"` (`createActivity` assigns a random UUID, so
do not rely on insertion order), and assert the returned row is the `"a"` row.
Keep the existing assertions that jane, a null resource id, and `other_type`
are excluded. 4. Run the unit tests named below.

acceptance: `./gradlew :app:testDefaultDebugUnitTest --tests org.ole.planet.myplanet.data.room.dao.ResourceActivityDaoTest`
green, and `./gradlew testDefaultDebugUnitTest` stays green. The
opened-resources list still shows one entry per resource for that user and
type, and repeating the read returns the same row.

size budget: ~30 changed lines, 2 files

out of scope: no repository implementation change, no change to `getMostOpenedResource` or upload queries

---

### 6. wrap stale exam-question deletes in one transaction (roadmap 1+7)

context: `QuestionDao.deleteStaleQuestions` (`QuestionDao.kt:29-32`) groups
questions by `examId` and calls `deleteStaleInternal` once per exam, with no
`@Transaction`. A failure halfway through a multi-exam sync leaves some exams
cleaned and others still holding removed questions. `deleteStaleInternal`
(`QuestionDao.kt:27`) is the SQL that must stay.
`QuestionDaoTest.deleteStaleQuestions_removesQuestionsNoLongerInTheSyncedExam`
already expects exam1 to drop `q2` and exam2 to keep `other`. Other DAO
default methods in this codebase already use `@Transaction` for the same
reason (`UserDao.deleteByIds`, `CommunityDao.replaceAll`).

files: `app/src/main/java/org/ole/planet/myplanet/data/room/dao/QuestionDao.kt`
(`deleteStaleQuestions` only). Do not edit `QuestionDaoTest.kt`,
`ExamQuestion`, or any exam repository. Run the existing test; do not change
its expectations.

steps: 1. Annotate `deleteStaleQuestions` with `@Transaction` and import
`androidx.room.Transaction`. 2. Keep the body: group by `examId`, skip a null
exam id, and call `deleteStaleInternal` with the distinct ids. Do not rewrite
the DELETE SQL and do not change `getByExamIds` chunking. 3. Do not add a new
public method. Callers of `deleteStaleQuestions` keep the same signature and
the same per-exam delete semantics, now inside one transaction. 4. Run
`QuestionDaoTest`. The existing two-exam case must still remove only the stale
question in exam1 and leave exam2's `other` row.

acceptance: `./gradlew :app:testDefaultDebugUnitTest --tests org.ole.planet.myplanet.data.room.dao.QuestionDaoTest`
green, and `./gradlew testDefaultDebugUnitTest` stays green. After a
course/exam sync, questions removed from the exam are gone and questions from
an exam not in the batch are still there.

size budget: ~5 changed lines, 1 file

out of scope: no query rewrite, no repository change, no new test file

---

### 7. stop community services depending on the fat teams repository (roadmap 1+4+9)

context: `CommunityServicesViewModel` (`CommunityServicesViewModel.kt:15-16`)
injects `TeamsRepository`, the write/sync aggregate. It only calls
`getTeamLinks()` (`line 28`, declared at `TeamsRepository.kt:54`) and
`isMember` (`line 32`). `isMember` is already on `TeamsMembersRepository`
(`TeamsMembersRepository.kt:7`). `getTeamLinks` is not on
`TeamsShareRepository` (`TeamsShareRepository.kt:5-8`), even though community
links are share data. Both narrow interfaces are already bound to
`TeamsRepositoryImpl` (`RepositoryModule.kt:201` and `:213`). The viewmodel
therefore sees leave-team, finance, and notification methods it must not call.
Moving the read onto the narrow ports is what a platform-free core can
implement without dragging the fat Android repository along.

files: `app/src/main/java/org/ole/planet/myplanet/repository/TeamsShareRepository.kt`.
`app/src/main/java/org/ole/planet/myplanet/ui/community/CommunityServicesViewModel.kt`.
`app/src/test/java/org/ole/planet/myplanet/ui/community/CommunityServicesViewModelTest.kt`.
Do not touch `TeamsRepository.kt`, `TeamsRepositoryImpl.kt`,
`RepositoryModule.kt`, `TeamsMembersRepository.kt`, or `ChatViewModel.kt`
(it already depends on `TeamsShareRepository` and does not call `getTeamLinks`).

steps: 1. Add `suspend fun getTeamLinks(): List<MyTeam>` to
`TeamsShareRepository`. Import `MyTeam`. Do not give it a body.
`TeamsRepository` already extends `TeamsShareRepository` and already declares
the same method, so the existing `override` in `TeamsRepositoryImpl` satisfies
both. Do not edit the impl. 2. Change `CommunityServicesViewModel`'s
constructor to take `TeamsShareRepository` and `TeamsMembersRepository`.
`loadTeamLinks` calls `getTeamLinks` on the share port. `isMember` calls
`isMember` on the members port. No other behavior change. 3. Update
`CommunityServicesViewModelTest` to pass two mocks. Keep the three existing
tests: init populates `teamLinks`, `isMember` forwards user id and team id,
and a null user id is forwarded unchanged. 4. Run the viewmodel test.
`ChatViewModelTest` must still compile without edits because it never calls
the new method.

acceptance: `./gradlew :app:testDefaultDebugUnitTest --tests org.ole.planet.myplanet.ui.community.CommunityServicesViewModelTest`
green, and `./gradlew testDefaultDebugUnitTest` stays green. The community
services screen still lists link teams and still answers membership checks.

size budget: ~35 changed lines, 3 files

out of scope: do not move `getTeamById` or any other fat-interface method; do not add a Hilt binding

---

### 8. stop my-life from depending on the write-capable user repository (roadmap 1+3+9)

context: `LifeViewModel` (`LifeViewModel.kt:18-20`) injects `UserRepository`,
which extends `UserReadRepository` (`UserRepository.kt:23`) and also exposes
guest creation, authentication, and profile writes. The viewmodel only reads.
`resolveUserId` (`LifeViewModel.kt:28-31`) calls `getCurrentUserId()`
(`UserRepository.kt:80`) and then `getUserModel()`. `getUserModel` is already
on `UserReadRepository` (`UserReadRepository.kt:8`). `getCurrentUserId` is
not, so the screen cannot narrow today. `UserReadRepository` is already bound
(`RepositoryModule.kt:229`). The read port is the one that can live in the
platform-free core; the viewmodel should not see write methods.

files: `app/src/main/java/org/ole/planet/myplanet/repository/UserReadRepository.kt`.
`app/src/main/java/org/ole/planet/myplanet/ui/life/LifeViewModel.kt`. Do not
touch `UserRepository.kt`, `UserRepositoryImpl.kt`, `LifeRepositoryImpl.kt`,
`MyLifeDao.kt` (task 3 owns it), `RepositoryModule.kt`, or
`LifeViewModelTest.kt`.

steps: 1. Add `suspend fun getCurrentUserId(): String?` to
`UserReadRepository`, same signature as `UserRepository.kt:80`. Do not remove
the declaration on `UserRepository`; a child interface may redeclare it, and
the existing impl override satisfies both. Do not edit the impl. 2. Change
`LifeViewModel`'s constructor parameter type and import from `UserRepository`
to `UserReadRepository`. Keep `resolveUserId` exactly: current user id, else
`getUserModel()?.id`, then drop blank and `"--"`. 3. Do not edit
`LifeViewModelTest`. It passes a `UserRepository` mock, which is a
`UserReadRepository`, so the constructor call still compiles. 4. Run
`LifeViewModelTest`.

acceptance: `./gradlew :app:testDefaultDebugUnitTest --tests org.ole.planet.myplanet.ui.life.LifeViewModelTest`
green, and `./gradlew testDefaultDebugUnitTest` stays green. My Life still
loads the signed-in user's rows, including the fallback from `getUserModel()`
when `getCurrentUserId()` is empty.

size budget: ~8 changed lines, 2 files

out of scope: do not change id normalization, do not add a new repository method on `LifeRepository`, do not narrow any other viewmodel here (task 10 owns those files)

---

### 9. ask the resources repository which next-step files still need download (roadmap 1+3+10)

context: `CoursesStepsViewModel.loadStep` (`CoursesStepsViewModel.kt:126-127`)
loads every resource for the next step with
`resourcesRepository.getAllStepResources` (`ResourcesRepository.kt:76`) and
then applies `!it.isResourceOffline()` in the viewmodel. `isResourceOffline`
(`MyLibrary.kt:144-145`) is the persistence rule
(`resourceOffline && _rev == downloadedRev`), not screen state. A future
Compose step screen should ask the repository for the download set and keep
only URL handoff in the viewmodel. `ResourcesRepositoryImpl` is in an open
PR, so the new method must be a default on the interface and must not require
an impl override. The prefetch test at `CoursesStepsViewModelTest.kt:214`
currently stubs `getAllStepResources` and will miss the new call unless it is
updated.

files: `app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepository.kt`
(add the default next to `getAllStepResources`).
`app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesStepsViewModel.kt`
(`loadStep` next-step block only).
`app/src/test/java/org/ole/planet/myplanet/ui/courses/CoursesStepsViewModelTest.kt`
(`loadStep_whenNextStepIdProvided_prefetchesNextStepResources` at line 214).
Do not touch `ResourcesRepositoryImpl.kt`, `MyLibrary.kt`, or
`ResourceDownloadCoordinator`.

steps: 1. Add a default method
`suspend fun stepResourcesNeedingDownload(stepId: String?): List<MyLibrary>`
on `ResourcesRepository` that returns `getAllStepResources(stepId)` filtered
to items where `isResourceOffline()` is false. A body is required so the
blocked impl does not need a new override. 2. In the `nextStepId != null`
block, replace `getAllStepResources` plus the filter with
`stepResourcesNeedingDownload`. Keep `UrlUtils.getUrl` and
`resourceDownloadCoordinator.startBackgroundDownload`. 3. Do not change the
current-step filter at line 82, `downloadResourcesPriority`, or
`refreshInlineResources` at line 163 (that path must still show every
resource, including ones already offline). 4. Update the prefetch test to
stub `stepResourcesNeedingDownload`, because a mock does not run the
interface default. Keep asserting the background download starts for the
returned item. Add a sibling test where the method returns empty and
`startBackgroundDownload` is not called.

acceptance: `./gradlew :app:testDefaultDebugUnitTest --tests org.ole.planet.myplanet.ui.courses.CoursesStepsViewModelTest`
green, and `./gradlew testDefaultDebugUnitTest` stays green. Opening a step
still prefetches the next step's not-yet-offline resources and does not
prefetch resources that are already offline. The current step still downloads
through `downloadResourcesPriority`.

size budget: ~35 changed lines, 3 files

out of scope: no SQL exclusion query, no change to how the current step's already-loaded resources are filtered

---

### 10. point read-only screens at UserReadRepository (roadmap 1+4+10)

context: Four viewmodels inject `UserRepository` but only call methods that
already exist on `UserReadRepository` (`UserReadRepository.kt:6-8`).
`PersonalsViewModel` calls `getUserModel()` (`PersonalsViewModel.kt:31-35`).
`DashboardElementViewModel.currentUser` calls `getUserModel()`
(`DashboardElementViewModel.kt:13-19`). `CommunityTabViewModel` calls
`getUserModel()` (`CommunityTabViewModel.kt:24-33`).
`SubmissionDetailViewModel` calls `getUserById`
(`SubmissionDetailViewModel.kt:30-38`). Each screen can therefore see
authentication, guest creation, and profile writes. State already lives in
the viewmodels; the leak is the port. Narrowing it is what keeps a later
Compose screen from depending on the write API. `UserRepository` extends
`UserReadRepository` (`UserRepository.kt:23`), so existing tests that pass a
`UserRepository` mock still compile.

files: `app/src/main/java/org/ole/planet/myplanet/ui/personals/PersonalsViewModel.kt`.
`app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardElementViewModel.kt`.
`app/src/main/java/org/ole/planet/myplanet/ui/community/CommunityTabViewModel.kt`.
`app/src/main/java/org/ole/planet/myplanet/ui/submissions/SubmissionDetailViewModel.kt`.
Do not touch `UserReadRepository.kt` (task 8 owns it), `UserRepository.kt`,
`UserRepositoryImpl.kt`, `RepositoryModule.kt`, the four matching
`*ViewModelTest.kt` files, `ActivitiesViewModel.kt` (same pattern at line 39,
left for a later task), or `ResourcesViewModel.kt`.

steps: 1. In each of the four viewmodels, change the constructor parameter
type and the import from `UserRepository` to `UserReadRepository`. Keep the
parameter name so call sites inside the file do not need a rename. 2. Do not
change any call. `getUserModel` and `getUserById` are already on the read
port. Do not add `getCurrentUserId` usage. 3. Do not edit the test files. A
`UserRepository` mock is a valid `UserReadRepository` argument. 4. Run the
four viewmodel test classes named below.

acceptance: `./gradlew :app:testDefaultDebugUnitTest --tests org.ole.planet.myplanet.ui.personals.PersonalsViewModelTest --tests org.ole.planet.myplanet.ui.dashboard.DashboardElementViewModelTest --tests org.ole.planet.myplanet.ui.community.CommunityTabViewModelTest --tests org.ole.planet.myplanet.ui.submissions.SubmissionDetailViewModelTest`
green, and `./gradlew testDefaultDebugUnitTest` stays green. Personal
resources still load for the signed-in user, the dashboard element still
reads the current user, the community tab still shows that user's planet
code, and submission detail still shows the submitter's name.

size budget: ~12 changed lines, 4 files

out of scope: do not narrow viewmodels that call `searchUsers`, `updateProfileFields`, `getActiveUserIdSuspending`, or any other write/search method

---

Signed: github-copilot grok-4.7 high
