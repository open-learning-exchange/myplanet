# Repository boundary work orders — refactor round

date: 2026-08-27  
base commit: `89fd72c251df68ed01094091d4de7ba7a2571ebe`  
open PRs checked: could not check open PRs (GitHub CLI authentication and network access were unavailable)

### 1. Push survey eligibility filtering into Room (roadmap 1+7+9)
context: `SurveysRepositoryImpl.getTeamOwnedSurveys` and `getAdoptableTeamSurveys` load every survey and filter it in Kotlin at lines 245-269. `ExamDao.getByType` at line 24 is the broad query causing that excess work; narrower DAO queries make the repository boundary explicit and move portable callers toward roadmap 9.
files: `app/src/main/java/org/ole/planet/myplanet/repository/SurveysRepositoryImpl.kt` — `getTeamOwnedSurveys`, `getAdoptableTeamSurveys`, and `getIndividualSurveys`; `app/src/main/java/org/ole/planet/myplanet/data/room/dao/ExamDao.kt` — add only the narrowly filtered survey queries. Leave `SurveysRepository.kt`, submission parsing, and adoption writes alone.
steps:
1. Add DAO queries for individually scoped surveys and share-allowed surveys, preserving current null/empty team semantics.
2. Replace the broad `getByType("surveys")` calls with those queries while retaining repository-side exclusion sets.
3. Keep result membership and ordering identical to the existing filters.
4. Remove imports or sequence operations made unused by the narrower reads.
5. Add no schema, migration, or entity changes.
acceptance:
- `./gradlew testDefaultDebugUnitTest` passes.
- `./gradlew lintDefaultDebug` passes.
- User-visible verification: team survey pages show the same owned, adoptable, and individual surveys, including adopted-source exclusions.
size budget: approximately 35 changed lines across 2 files.
out of scope: Do not change survey adoption, submission parsing, or any UI screen.

---

### 2. Query rating summaries without loading every rating row (roadmap 1+7+9)
context: `RatingsRepositoryImpl.getRatingSummary` loads all matching entities and calculates count and average at lines 45-57. `RatingDao.getByTypeAndItem` at lines 15-16 returns full rows even though only one user row plus aggregate values are needed.
files: `app/src/main/java/org/ole/planet/myplanet/repository/RatingsRepositoryImpl.kt` — `getRatingSummary`; `app/src/main/java/org/ole/planet/myplanet/data/room/dao/RatingDao.kt` — summary projection/query and existing-user lookup. Leave `RatingsRepository.kt`, upload methods, and rating mutation logic alone.
steps:
1. Define a minimal Room projection for count and average beside `RatingDao`.
2. Add one aggregate query scoped by type and item and reuse the existing exact-user lookup for the optional current-user rating.
3. Build `RatingSummary` from those results without materializing the full rating list.
4. Preserve zero-count average behavior and nullable-user behavior exactly.
5. Remove only imports made obsolete by this method.
acceptance:
- `./gradlew testDefaultDebugUnitTest` passes.
- `./gradlew lintDefaultDebug` passes.
- User-visible verification: course and resource detail views show unchanged average, total, and current-user rating values.
size budget: approximately 30 changed lines across 2 files.
out of scope: Do not alter rating rounding, JSON aggregation, submission, or upload state.

---

### 3. Return meetup member IDs directly from Room (roadmap 1+7+9)
context: `EventsRepositoryImpl.getJoinedMembers` loads complete `Meetup` rows only to extract `userId` at lines 66-78. `MeetupDao.getMembersByMeetupId` at lines 20-21 exposes storage entities where the repository needs only distinct identifiers.
files: `app/src/main/java/org/ole/planet/myplanet/repository/EventsRepositoryImpl.kt` — `getJoinedMembers`; `app/src/main/java/org/ole/planet/myplanet/data/room/dao/MeetupDao.kt` — replace the member-row query with an ID projection. Leave `EventsRepository.kt`, `UserDao`, attendance mutation, and sync insertion alone.
steps:
1. Add a `SELECT DISTINCT userId` DAO function that excludes null and blank identifiers.
2. Switch `getJoinedMembers` to consume the projected IDs directly.
3. Retain the empty-ID guard, 400-ID batching, and final user de-duplication.
4. Remove the superseded full-row member DAO function if no references remain.
5. Keep the repository return type and caller contract unchanged.
acceptance:
- `./gradlew testDefaultDebugUnitTest` passes.
- `./gradlew lintDefaultDebug` passes.
- User-visible verification: a meetup with duplicate membership rows displays each joined user once.
size budget: approximately 15 changed lines across 2 files.
out of scope: Do not introduce a cross-DAO Room relation or change user lookup batching.

---

### 4. Make My Life de-duplication deterministic (roadmap 1+8+9)
context: `LifeRepositoryImpl.dedupKey` falls back to `System.identityHashCode` at lines 67-71, so equivalent blank legacy rows can survive differently on each load. `getMyLifeByUserId` at lines 74-76 consequently exposes storage-object identity through repository view modelling.
files: `app/src/main/java/org/ole/planet/myplanet/repository/LifeRepositoryImpl.kt` — `dedupKey` and `getMyLifeByUserId`. Leave `LifeRepository.kt`, `MyLifeDao`, cache serialization, seeding, and list-order writes alone.
steps:
1. Replace object identity fallback with a stable key derived from persisted fields already present on `MyLife`.
2. Preserve the current precedence of nonblank image ID, title, and document ID.
3. Ensure genuinely distinct legacy rows remain distinct when their persisted content differs.
4. Keep first-occurrence ordering from `distinctBy` unchanged.
5. Add focused repository unit coverage only if it can remain in this one file budget; otherwise rely on the existing suite.
acceptance:
- `./gradlew testDefaultDebugUnitTest` passes.
- `./gradlew lintDefaultDebug` passes.
- User-visible verification: repeated dashboard refreshes render the same deduplicated My Life entries in the same order.
size budget: approximately 15 changed lines in 1 file.
out of scope: Do not change cache format, seed content, DAO schema, or visibility behavior.

---

### 5. Stabilize enterprise report flow comparisons (roadmap 1+7+9)
context: `EnterprisesRepositoryImpl.getReportsFlow` filters and sorts reports at lines 87-93, then considers only `_id` and `_rev` at lines 94-97. Locally edited report fields can change while `_rev` remains fixed, allowing the repository to suppress a user-visible update.
files: `app/src/main/java/org/ole/planet/myplanet/repository/EnterprisesRepositoryImpl.kt` — `getReportsFlow` only. Leave `EnterprisesRepository.kt`, `TeamDao`, CSV export, image attachment, and report write methods alone.
steps:
1. Replace the partial `_id`/`_rev` comparator with a content comparison covering fields rendered or exported by report consumers.
2. Preserve archived filtering and descending creation-date ordering.
3. Avoid JSON serialization or reflection in the comparator.
4. Keep comparison work on the configured default dispatcher.
5. Remove no public API and introduce no new model.
acceptance:
- `./gradlew testDefaultDebugUnitTest` passes.
- `./gradlew lintDefaultDebug` passes.
- User-visible verification: editing a report immediately refreshes its displayed financial values without requiring server sync.
size budget: approximately 20 changed lines in 1 file.
out of scope: Do not change report persistence, archive semantics, CSV formatting, or DAO SQL.

---

### 6. Remove the repository-to-repository hop from course visit logging (roadmap 1+4+9)
context: `ActivitiesRepositoryImpl.logCourseVisit` crosses into `UserRepository` to fetch two provenance fields at lines 86-103. That repository-to-repository read widens the data-layer graph even though this repository already owns data persistence and only needs one local user lookup.
files: `app/src/main/java/org/ole/planet/myplanet/repository/ActivitiesRepositoryImpl.kt` — constructor and `logCourseVisit` only. Leave `ActivitiesRepository.kt`, `UserRepository`, callers, other activity logging, and course navigation alone.
steps:
1. Replace the lazy `UserRepository` dependency with the existing `UserDao` abstraction used elsewhere in repositories.
2. Read the same user record by username directly in `logCourseVisit`.
3. Remove the lazy repository dependency and its imports from `ActivitiesRepositoryImpl`.
4. Preserve generated IDs, timestamps, type, title, course ID, and user ID.
5. Clean imports according to the Kotlin import skill conventions.
acceptance:
- `./gradlew testDefaultDebugUnitTest` passes.
- `./gradlew lintDefaultDebug` passes.
- User-visible verification: opening a course still records one visit with the same user and provenance metadata.
size budget: approximately 15 changed lines in 1 file.
out of scope: Do not change activity entities, DAO methods, upload payloads, or other course behavior.

---

### 7. Parse server availability without allocating a full token list (roadmap 1+7+9)
context: `ConfigurationsRepositoryImpl.checkServerAvailability(url)` reads the entire response, splits it, drops trailing empties, and allocates a list solely to test whether it has eight entries at lines 209-217. The repository can preserve this protocol boundary with an early, allocation-light token count.
files: `app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationsRepositoryImpl.kt` — `checkServerAvailability(url)` only. Leave `ConfigurationsRepository.kt`, URL selection, version checks, database clearing, and API definitions alone.
steps:
1. Replace `split` plus `dropLastWhile` with an early count of non-trailing comma-separated fields.
2. Preserve the current threshold of eight fields and the treatment of empty response bodies.
3. Preserve HTTP 401 as an available server and all other unsuccessful responses as unavailable.
4. Keep response-body consumption on the IO dispatcher.
5. Avoid adding a general parser or changing the endpoint contract.
acceptance:
- `./gradlew testDefaultDebugUnitTest` passes.
- `./gradlew lintDefaultDebug` passes.
- User-visible verification: server setup accepts the same valid response and rejects the same short or empty responses.
size budget: approximately 15 changed lines in 1 file.
out of scope: Do not change networking, retry policy, configuration persistence, or user-facing strings.

---

### 8. Eliminate redundant collection copies in progress assembly (roadmap 1+7+9)
context: `ProgressRepositoryImpl.getCourseProgress` copies DAO lists with no-op `map { it }` at lines 34-40, and `fetchCourseData` repeats that copy for exams at lines 63-68. These copies increase allocations on course dashboards while adding no repository modelling value.
files: `app/src/main/java/org/ole/planet/myplanet/repository/ProgressRepositoryImpl.kt` — `getCourseProgress` and `fetchCourseData`. Leave `ProgressRepository.kt`, all DAOs, calculation formulas, JSON shape, and activity updates alone.
steps:
1. Remove the identity mappings from course-step and exam DAO results.
2. Reuse a single empty-list guard pattern without changing query execution conditions.
3. Retain grouping keys, iteration order, and `CourseProgressState` values exactly.
4. Check the surrounding method for another identity copy and remove it only when semantics are identical.
5. Keep dispatcher boundaries unchanged.
acceptance:
- `./gradlew testDefaultDebugUnitTest` passes.
- `./gradlew lintDefaultDebug` passes.
- User-visible verification: course cards and exported progress data show unchanged current and maximum progress.
size budget: approximately 10 changed lines in 1 file.
out of scope: Do not redesign progress calculation, repository dependencies, DAO queries, or JSON export.

---

### 9. Make personal-resource deletion one DAO transaction (roadmap 1+5+9)
context: `PersonalsRepositoryImpl.deletePersonalResource` performs two independent deletes at lines 62-64 because an input may be either the CouchDB document ID or local ID. A cancellation or failure between calls can leave duplicate identity forms behind.
files: `app/src/main/java/org/ole/planet/myplanet/repository/PersonalsRepositoryImpl.kt` — `deletePersonalResource`; `app/src/main/java/org/ole/planet/myplanet/data/room/dao/PersonalDao.kt` — transactional delete-by-either-ID operation. Leave `PersonalsRepository.kt`, upload methods, attachment handling, and personal-resource UI alone.
steps:
1. Add a DAO transaction method that invokes the existing document-ID and local-ID deletes as one unit.
2. Have `deletePersonalResource` call that single boundary method.
3. Preserve the current behavior when neither identifier matches and when both forms match different legacy rows.
4. Keep existing primitive delete queries available only if other callers still use them.
5. Add no schema or migration changes.
acceptance:
- `./gradlew testDefaultDebugUnitTest` passes.
- `./gradlew lintDefaultDebug` passes.
- User-visible verification: deleting a personal resource removes it whether the UI holds its local ID or synced document ID.
size budget: approximately 15 changed lines across 2 files.
out of scope: Do not trigger uploads, delete attachment files, or change resource editing.

---

### 10. Make dictionary population idempotent at the repository boundary (roadmap 1+8+9)
context: `DictionaryRepositoryImpl.insertDictionaryData` checks `dictionaryDao.count()` before parsing and inserting at lines 30-60, but concurrent calls can both observe zero and perform duplicate work. Unlike the My Life repository, this repository has no serialization around its check-then-insert workflow.
files: `app/src/main/java/org/ole/planet/myplanet/repository/DictionaryRepositoryImpl.kt` — constructor state and `insertDictionaryData`. Leave `DictionaryRepository.kt`, `DictionaryDao`, asset file utilities, entity fields, and lookup behavior alone.
steps:
1. Add a repository-local coroutine mutex dedicated to dictionary population.
2. Place the existence check, file parse, and insertion inside `withLock` while retaining the IO context.
3. Preserve `FileMissing`, `AlreadyPopulated`, `Inserted`, and `Failed` outcomes.
4. Ensure the file-existence check remains before expensive parsing.
5. Clean coroutine imports and do not expose the mutex publicly.
acceptance:
- `./gradlew testDefaultDebugUnitTest` passes.
- `./gradlew lintDefaultDebug` passes.
- User-visible verification: two concurrent dictionary initialization requests perform at most one parse/insert and both complete with valid outcomes.
size budget: approximately 12 changed lines in 1 file.
out of scope: Do not change dictionary JSON format, DAO conflict strategy, model fields, or search UI.
