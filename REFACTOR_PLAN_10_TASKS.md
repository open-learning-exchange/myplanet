### Drop unused DatabaseService dependency from UploadManager

`UploadManager` injects `DatabaseService` in its constructor but never calls it anywhere in the file — every bit of data access already flows through injected repositories and `UploadCoordinator`. Removing this dangling dependency tightens the data-layer boundary so the service talks only to repositories, and is a one-file deletion that cannot collide with any other PR in this round.

:codex-file-citation[codex-file-citation]{line_range_start=52 line_range_end=71 path=app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt#L52-L71"}

:::task-stub{title="Remove unused DatabaseService injection from UploadManager"}
1. Open `app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt` and delete the `private val databaseService: DatabaseService,` constructor parameter at line 55.
2. Remove the now-unused `import org.ole.planet.myplanet.data.DatabaseService` at the top of the file.
3. Build the `default` flavor (`./gradlew assembleDefaultDebug`) and confirm no other reference to `databaseService` remains in the file with a grep.
4. Do not touch any other file — Hilt will pick up the new constructor signature automatically.
:::

### Convert CheckboxAdapter to ListAdapter with DiffUtils.itemCallback

`CheckboxAdapter` is the only RecyclerView adapter in the project still extending `RecyclerView.Adapter` directly with a `List<String>` and no diffing. Every other adapter has already moved to `ListAdapter` with the shared `DiffUtils.itemCallback` helper, so migrating this one closes the loop and lets items be updated via `submitList` without `notifyDataSetChanged`.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=50 path=app/src/main/java/org/ole/planet/myplanet/ui/components/CheckboxAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/components/CheckboxAdapter.kt#L1-L50"}

:::task-stub{title="Migrate CheckboxAdapter to ListAdapter + DiffUtils.itemCallback"}
1. Open `app/src/main/java/org/ole/planet/myplanet/ui/components/CheckboxAdapter.kt`.
2. Replace `RecyclerView.Adapter<CheckboxAdapter.ViewHolder>()` with `androidx.recyclerview.widget.ListAdapter<String, CheckboxAdapter.ViewHolder>(DiffUtils.itemCallback(areItemsTheSame = { a, b -> a == b }, areContentsTheSame = { a, b -> a == b }))` using `org.ole.planet.myplanet.utils.DiffUtils`.
3. Delete the primary-constructor `items: List<String>` field and read items through `getItem(position)` + `itemCount` (inherited) instead of `items[position]` / `items.size`.
4. In call sites that previously constructed the adapter with a list, invoke `adapter.submitList(items)` after construction; keep the `initialSelectedItems` and `checkChangeListener` parameters as-is.
5. Remove the now-unused `override fun getItemCount()` method.
:::

### Move RealmCertification.insert into CoursesRepositoryImpl

`RealmCertification` has a `@JvmStatic` `insert(mRealm, json)` helper in its companion object that performs database upserts, but it is called from exactly one place — `CoursesRepositoryImpl.bulkInsertCertificationsFromSync`. Folding that logic into the repository removes a static data function from the model layer and keeps the Realm write where it belongs.

:codex-file-citation[codex-file-citation]{line_range_start=22 line_range_end=35 path=app/src/main/java/org/ole/planet/myplanet/model/RealmCertification.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/model/RealmCertification.kt#L22-L35"}

:codex-file-citation[codex-file-citation]{line_range_start=683 line_range_end=700 path=app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepositoryImpl.kt#L683-L700"}

:::task-stub{title="Fold RealmCertification.insert into CoursesRepositoryImpl"}
1. Open `app/src/main/java/org/ole/planet/myplanet/model/RealmCertification.kt` and delete the entire `companion object { @JvmStatic fun insert(...) { ... } }` block.
2. In `app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepositoryImpl.kt`, add a `private fun insertCertification(realm: Realm, doc: JsonObject)` that inlines the previous upsert body (query by `_id`, create if null, copy fields from `doc`).
3. In `bulkInsertCertificationsFromSync` (around line 694) replace `RealmCertification.insert(realm, jsonDoc)` with `insertCertification(realm, jsonDoc)`.
4. Remove the `import org.ole.planet.myplanet.model.RealmCertification.Companion.insert` (or the static import) if present, and confirm `RealmCertification.kt` now only contains the Realm fields.
:::

### Move RealmCourseProgress.insert into ProgressRepositoryImpl

`RealmCourseProgress.insert` is another static companion helper that only runs from `ProgressRepositoryImpl.bulkInsertFromSync`. Moving it into the repository continues the pattern of making repositories the single owner of their Realm writes and leaves the model class as a pure data holder.

:codex-file-citation[codex-file-citation]{line_range_start=40 line_range_end=60 path=app/src/main/java/org/ole/planet/myplanet/model/RealmCourseProgress.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/model/RealmCourseProgress.kt#L40-L60"}

:codex-file-citation[codex-file-citation]{line_range_start=205 line_range_end=230 path=app/src/main/java/org/ole/planet/myplanet/repository/ProgressRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/ProgressRepositoryImpl.kt#L205-L230"}

:::task-stub{title="Fold RealmCourseProgress.insert into ProgressRepositoryImpl"}
1. Open `app/src/main/java/org/ole/planet/myplanet/model/RealmCourseProgress.kt` and delete the `companion object { @JvmStatic fun insert(...) { ... } }` block.
2. In `app/src/main/java/org/ole/planet/myplanet/repository/ProgressRepositoryImpl.kt`, add a `private fun insertCourseProgress(realm: Realm, doc: JsonObject)` whose body matches the moved code.
3. Replace the call at line 222 (`RealmCourseProgress.insert(realm, jsonDoc)`) with `insertCourseProgress(realm, jsonDoc)`.
4. Delete any leftover `import org.ole.planet.myplanet.model.RealmCourseProgress.Companion.insert` and double-check `RealmCourseProgress.kt` compiles with no companion object.
:::

### Move RealmAchievement.insert into UserRepositoryImpl

`RealmAchievement.insert` is a companion-object helper that is referenced only by `UserRepositoryImpl.bulkInsertAchievementsFromSync`. Relocating it into `UserRepositoryImpl` tightens the user repository as the sole owner of achievement writes and removes yet another static data function from the model package.

:codex-file-citation[codex-file-citation]{line_range_start=130 line_range_end=160 path=app/src/main/java/org/ole/planet/myplanet/model/RealmAchievement.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/model/RealmAchievement.kt#L130-L160"}

:codex-file-citation[codex-file-citation]{line_range_start=825 line_range_end=850 path=app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt#L825-L850"}

:::task-stub{title="Fold RealmAchievement.insert into UserRepositoryImpl"}
1. Open `app/src/main/java/org/ole/planet/myplanet/model/RealmAchievement.kt` and delete the `companion object { @JvmStatic fun insert(...) { ... } }` block.
2. In `app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt`, add a `private fun insertAchievement(realm: Realm, doc: JsonObject)` with the previous body.
3. In `bulkInsertAchievementsFromSync` at line 840 replace `RealmAchievement.insert(realm, jsonDoc)` with `insertAchievement(realm, jsonDoc)`.
4. Remove any now-dead `import org.ole.planet.myplanet.model.RealmAchievement.Companion.insert` and verify the model file no longer references `Realm`, `JsonObject`, or `JsonUtils`.
:::

### Move RealmMyTeam.insertMyTeams into TeamsRepositoryImpl

`RealmMyTeam` still hosts `insertMyTeams` and a thin `insert(mRealm, doc)` alias that together embed team-membership business logic (stale-request cleanup, membership-vs-request branching) directly in the model. The only caller is `TeamsRepositoryImpl.bulkInsertFromSync`, so the function can be lifted into that repository as a private helper without touching any other feature.

:codex-file-citation[codex-file-citation]{line_range_start=156 line_range_end=200 path=app/src/main/java/org/ole/planet/myplanet/model/RealmMyTeam.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/model/RealmMyTeam.kt#L156-L200"}

:codex-file-citation[codex-file-citation]{line_range_start=1355 line_range_end=1395 path=app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt#L1355-L1395"}

:::task-stub{title="Fold RealmMyTeam.insertMyTeams into TeamsRepositoryImpl"}
1. In `app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt`, add a `private fun insertMyTeam(realm: Realm, doc: JsonObject)` that inlines the body of `RealmMyTeam.insertMyTeams` (archived-skip check, stale-request deletion, `already-member` guard, `createObject` + `populateTeamFields`).
2. Replace the `RealmMyTeam.insert(realm, jsonDoc)` call at line 1371 with `insertMyTeam(realm, jsonDoc)`.
3. In `app/src/main/java/org/ole/planet/myplanet/model/RealmMyTeam.kt`, delete the `@JvmStatic fun insertMyTeams(doc, mRealm)` and its one-line `insert(mRealm, doc)` wrapper.
4. Keep `populateTeamFields` and `processDescription` in the model for now (they are shared by other paths); only move the top-level insert.
5. Grep for any stragglers (`RealmMyTeam.insertMyTeams`, `RealmMyTeam.Companion.insert`) to confirm there are no other callers.
:::

### Move RealmSubmission.serialize into SubmissionsRepositoryImpl

`RealmSubmission.serialize` lives in the model but reaches across features — it queries `RealmStepExam` and renders JSON for upload. Its sole caller is `UploadConfigs` inside `services/upload`. Moving the function into `SubmissionsRepositoryImpl` puts the Realm read and cross-feature lookup inside the submissions repository and deletes a static helper that today couples `model/` to `services/`.

:codex-file-citation[codex-file-citation]{line_range_start=44 line_range_end=96 path=app/src/main/java/org/ole/planet/myplanet/model/RealmSubmission.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/model/RealmSubmission.kt#L44-L96"}

:codex-file-citation[codex-file-citation]{line_range_start=198 line_range_end=210 path=app/src/main/java/org/ole/planet/myplanet/services/upload/UploadConfigs.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/upload/UploadConfigs.kt#L198-L210"}

:::task-stub{title="Move RealmSubmission.serialize into SubmissionsRepositoryImpl"}
1. In `app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepository.kt`, add a new method `fun serializeSubmission(realm: Realm, submission: RealmSubmission, context: Context, source: String, parentCode: String): JsonObject` (keep signature identical to the current `RealmSubmission.serialize`).
2. In `SubmissionsRepositoryImpl.kt`, implement it by pasting the body of `RealmSubmission.serialize`.
3. In `app/src/main/java/org/ole/planet/myplanet/services/upload/UploadConfigs.kt` at line 203, inject `submissionsRepository` (already available via Hilt module — check constructor) and replace `RealmSubmission.serialize(realm, submission, context, …)` with `submissionsRepository.serializeSubmission(realm, submission, context, …)`.
4. Delete the `companion object { @JvmStatic fun serialize(...) }` block from `RealmSubmission.kt`.
5. Confirm nothing else references `RealmSubmission.serialize`.
:::

### Tighten ActivitiesRepositoryImpl by removing cross-feature RealmUser queries

`ActivitiesRepositoryImpl` is an activities-domain repository but reaches directly into `RealmUser` in two places (line 79 for `name`, line 288 for `id`). That is a cross-feature data leak — activities code should ask `UserRepository` instead. `UserRepository` already exposes suspend lookups, so the fix is a constructor-level swap confined to this single file.

:codex-file-citation[codex-file-citation]{line_range_start=70 line_range_end=95 path=app/src/main/java/org/ole/planet/myplanet/repository/ActivitiesRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/ActivitiesRepositoryImpl.kt#L70-L95"}

:codex-file-citation[codex-file-citation]{line_range_start=280 line_range_end=300 path=app/src/main/java/org/ole/planet/myplanet/repository/ActivitiesRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/ActivitiesRepositoryImpl.kt#L280-L300"}

:::task-stub{title="Replace direct RealmUser queries in ActivitiesRepositoryImpl"}
1. Open `app/src/main/java/org/ole/planet/myplanet/repository/ActivitiesRepositoryImpl.kt` and add `private val userRepository: UserRepository` (as a `Lazy<UserRepository>` if a cycle is detected) to the constructor.
2. Replace the two `realm.where(RealmUser::class.java)…findFirst()` calls at lines 79 and 288 with suspend calls to the existing `userRepository.getUserByName(...)` / `userRepository.getUserById(...)` helpers, bridging the callers that are currently inside a `withRealm { }` block to a surrounding `suspend` scope where needed.
3. Delete the `import org.ole.planet.myplanet.model.RealmUser` from the file if no other reference remains.
4. Leave the repository interface untouched — this is purely an internal implementation change.
5. Grep `ActivitiesRepositoryImpl.kt` for `RealmUser` to confirm no leak remains.
:::

### Tighten NotificationsRepositoryImpl by removing cross-feature RealmUser reads

`NotificationsRepositoryImpl` reads `RealmUser` directly at lines 227 and 303 to resolve sender/recipient info while building notifications. This is another cross-feature leak — notifications should not open the user table. Delegating both reads to the already-injectable `UserRepository` tightens the boundary with a change limited to this single file.

:codex-file-citation[codex-file-citation]{line_range_start=220 line_range_end=240 path=app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt#L220-L240"}

:codex-file-citation[codex-file-citation]{line_range_start=295 line_range_end=315 path=app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt#L295-L315"}

:::task-stub{title="Delegate RealmUser lookups in NotificationsRepositoryImpl to UserRepository"}
1. Open `app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt` and add `private val userRepository: Lazy<UserRepository>` to the constructor (using `Lazy` to avoid a Hilt cycle, since `UserRepositoryImpl` is heavy).
2. Replace `realm.where(RealmUser::class.java)...findFirst()` at lines 227 and 303 with calls to `userRepository.get().getUserById(...)` or `getUserByName(...)` as appropriate; move those calls out of the `withRealm { }` closure into the enclosing `suspend fun` body.
3. Do NOT touch the `RealmMyTeam` or `RealmStepExam` queries in the same file — keep this PR scoped strictly to the two `RealmUser` leaks.
4. Delete the `import org.ole.planet.myplanet.model.RealmUser` if no references remain.
5. Confirm the file still compiles and the interface is unchanged.
:::

### Move RealmMeetup data helpers into CommunityRepositoryImpl

`RealmMeetup` exposes `getMyMeetUpIds(realm, userId)` and `insert(realm, doc)` as static companion methods; the former is called from `UploadToShelfService`, the latter from `CommunityRepositoryImpl.bulkInsertFromSync`. Both are database accesses that belong in the community repository, closing a meetup data-access leak out of both `services/` and the model package in one small, contained move.

:codex-file-citation[codex-file-citation]{line_range_start=40 line_range_end=80 path=app/src/main/java/org/ole/planet/myplanet/model/RealmMeetup.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/model/RealmMeetup.kt#L40-L80"}

:codex-file-citation[codex-file-citation]{line_range_start=350 line_range_end=400 path=app/src/main/java/org/ole/planet/myplanet/services/UploadToShelfService.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/UploadToShelfService.kt#L350-L400"}

:::task-stub{title="Fold RealmMeetup helpers into CommunityRepositoryImpl"}
1. In `app/src/main/java/org/ole/planet/myplanet/repository/CommunityRepository.kt`, add `suspend fun getMyMeetupIds(userId: String?): com.google.gson.JsonArray` to the interface.
2. In `app/src/main/java/org/ole/planet/myplanet/repository/CommunityRepositoryImpl.kt`, implement `getMyMeetupIds` using `withRealm { }` and the body previously in `RealmMeetup.getMyMeetUpIds`; also add a `private fun insertMeetup(realm: Realm, doc: JsonObject)` for the companion `insert` and switch `bulkInsertFromSync` (line 78) to call it.
3. In `app/src/main/java/org/ole/planet/myplanet/services/UploadToShelfService.kt`, inject `communityRepository: CommunityRepository` into the constructor, remove the `getMyMeetUpIds(realm, userId)` call inside `getShelfData` and replace it with `communityRepository.getMyMeetupIds(userId)` invoked before entering the `dbService.withRealm { }` block; pass the resulting `JsonArray` in.
4. Delete the `getMyMeetUpIds` and `insert` companion functions from `RealmMeetup.kt`, along with the `import org.ole.planet.myplanet.model.RealmMeetup.Companion.getMyMeetUpIds` at the top of `UploadToShelfService.kt` and the `Companion.insert` import in `SyncManager.kt` if the latter is unused; keep the unrelated `getHashMap` helper alone.
5. Run a final grep for `RealmMeetup.getMyMeetUpIds` and `RealmMeetup.insert` to make sure no caller remains.
:::
