Wait, I need to check if there are other callers for these methods.

1. `grep -rn 'RealmMyTeam.getResourceIds'` => No results.
2. `grep -rn 'RealmMyTeam.getResourceIdsByUser'` => No results.
3. `grep -rn 'RealmMyTeam.getTeamCreator'` => No results.
4. `grep -rn 'RealmMyTeam.isTeamLeader'` => No results.
5. `grep -rn 'RealmMyTeam.getMyTeamsByUserId'` => No results.

Wait! What about without `RealmMyTeam.`?
`getResourceIdsByUser` => found only in `RealmMyTeam.kt`
`getMyTeamsByUserId` => found only in `RealmMyTeam.kt`

Ah, `RealmMyTeam.kt` itself uses `getMyTeamsByUserId` inside the `TeamsRepositoryImpl` or elsewhere? Let's check `grep -rn 'getMyTeamsByUserId' app/src` again.
Only one result:
`app/src/main/java/org/ole/planet/myplanet/model/RealmMyTeam.kt:351:        fun getMyTeamsByUserId(mRealm: Realm, settings: SharedPreferences?): RealmResults<RealmMyTeam> {`

Wait... maybe they are called in `.java` files? `grep -rn 'getResourceIds' app/src/` showed it used in `TeamsRepositoryImpl` but it's calling its own `private suspend fun getResourceIds`! So the `RealmMyTeam` companion methods are mostly unused right now, or they were used before. Wait, if they are unused, I just need to move the logic, add to the interface, and deprecate the old ones.

Wait, what about `TeamsRepository` interface changes? The prompt says: "Add `getResourceIds(teamId)`, `getResourceIdsByUser(userId)`, `getTeamCreator(teamId)`, and `isTeamLeader(teamId, userId)` to `TeamsRepository` interface. Move the Realm query logic from `RealmMyTeam.companion` into `TeamsRepositoryImpl`."

Let me plan exactly:

1. Modifying `app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepository.kt`:
   - Search for `interface TeamsRepository {`
   - Add `suspend fun getResourceIds(teamId: String): List<String>`
   - Add `suspend fun getResourceIdsByUser(userId: String?): List<String>`
   - Add `suspend fun getMyTeamsByUserId(userId: String): List<RealmMyTeam>`
   - Note: `getTeamCreator` and `isTeamLeader` already exist in the interface! I should double check their signature. `suspend fun isTeamLeader(teamId: String, userId: String?): Boolean`, `suspend fun getTeamCreator(teamId: String): String?`. Yes, they match.

2. Modifying `app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt`:
   - `getResourceIds` is already a private method: `private suspend fun getResourceIds(teamId: String): List<String>`. Wait, the prompt says "Move the Realm query logic from RealmMyTeam.companion into TeamsRepositoryImpl".
     - In `RealmMyTeam`, `getResourceIds` is:
       ```kotlin
       val teams = realm.where(RealmMyTeam::class.java).equalTo("teamId", teamId).findAll()
       val ids = mutableListOf<String>()
       for (team in teams) {
           if (!team.resourceId.isNullOrBlank()) {
               ids.add(team.resourceId!!)
           }
       }
       return ids
       ```
     - In `TeamsRepositoryImpl`, `getResourceIds` is:
       ```kotlin
       return queryList(RealmMyTeam::class.java) {
           equalTo("teamId", teamId)
           beginGroup()
               .isNull("docType")
               .or().equalTo("docType", "")
               .or().equalTo("docType", "resourceLink")
               .or().equalTo("docType", "link")
           endGroup()
           isNotNull("resourceId")
           isNotEmpty("resourceId")
       }.mapNotNull { it.resourceId }
       ```
       I'll change the `private` to `override` in `TeamsRepositoryImpl.kt` since it already exists, or I can update it to exactly match the RealmMyTeam logic (which doesn't filter by `docType`). Wait, if I change the logic of `TeamsRepositoryImpl`'s `getResourceIds` to not filter by docType, it might break existing callers in `TeamsRepositoryImpl`. Let me check callers of `getResourceIds` in `TeamsRepositoryImpl`.
       Line 218: `val resourceIds = getResourceIds(teamId)` inside `getTeamResources`. `getTeamResources` gets `RealmMyLibrary` items using those `resourceIds`. I probably shouldn't break existing `TeamsRepositoryImpl` functionality, so I will just make it `override` and leave its logic or maybe change it to match `RealmMyTeam`. Since it's already there and works better (filters docType), I'll make it `override`.

   - `getResourceIdsByUser`:
     - In `RealmMyTeam`:
       ```kotlin
       val list = realm.where(RealmMyTeam::class.java)
           .equalTo("userId", userId)
           .equalTo("docType", "membership")
           .findAll()
       // get teamIds
       // then query l2 in(teamId, teamIds) && docType == "resourceLink"
       ```
     - I will add to `TeamsRepositoryImpl`:
       ```kotlin
       override suspend fun getResourceIdsByUser(userId: String?): List<String> {
           if (userId.isNullOrBlank()) return emptyList()
           val list = queryList(RealmMyTeam::class.java) {
               equalTo("userId", userId)
               equalTo("docType", "membership")
           }
           val teamIds = list.mapNotNull { it.teamId }
           if (teamIds.isEmpty()) return emptyList()

           val l2 = queryList(RealmMyTeam::class.java) {
               `in`("teamId", teamIds.toTypedArray())
               equalTo("docType", "resourceLink")
           }
           return l2.mapNotNull { it.resourceId }
       }
       ```

   - `getMyTeamsByUserId`:
     - In `RealmMyTeam`:
       ```kotlin
       val list = mRealm.where(RealmMyTeam::class.java)
           .equalTo("userId", userId)
           .equalTo("docType", "membership")
           .findAll()
       val teamIds = list.map { it.teamId }.toTypedArray()
       return mRealm.where(RealmMyTeam::class.java)
           .`in`("_id", teamIds)
           .notEqualTo("status", "archived")
           .findAll()
       ```
     - In `TeamsRepositoryImpl`:
       ```kotlin
       override suspend fun getMyTeamsByUserId(userId: String): List<RealmMyTeam> {
           val list = queryList(RealmMyTeam::class.java) {
               equalTo("userId", userId)
               equalTo("docType", "membership")
           }
           val teamIds = list.mapNotNull { it.teamId }
           if (teamIds.isEmpty()) return emptyList()

           return queryList(RealmMyTeam::class.java) {
               `in`("_id", teamIds.toTypedArray())
               notEqualTo("status", "archived")
           }
       }
       ```

   - `getTeamCreator`:
     - `TeamsRepositoryImpl` already has:
       ```kotlin
       override suspend fun getTeamCreator(teamId: String): String? {
           if (teamId.isBlank()) return null
           return findByField(RealmMyTeam::class.java, "_id", teamId)?.userId
       }
       ```
       Wait, `RealmMyTeam.getTeamCreator` looks like:
       ```kotlin
       val teams = realm?.where(RealmMyTeam::class.java)?.equalTo("teamId", teamId)?.findFirst()
       return teams?.userId ?: ""
       ```
       Notice `equalTo("teamId", teamId)` vs `findByField(..., "_id", teamId)`. I should probably change `TeamsRepositoryImpl` to query `teamId` instead of `_id` to strictly move the logic. Let me check `TeamsRepositoryImpl.kt` lines 1221-1224.
       ```kotlin
       override suspend fun getTeamCreator(teamId: String): String? {
           if (teamId.isBlank()) return null
           return findByField(RealmMyTeam::class.java, "teamId", teamId)?.userId
       }
       ```

   - `isTeamLeader`:
     - `TeamsRepositoryImpl` already has:
       ```kotlin
       override suspend fun isTeamLeader(teamId: String, userId: String?): Boolean {
           if (userId == null) return false
           val team = findByField(RealmMyTeam::class.java, "teamId", teamId)
           ...
       }
       ```
       Wait, let me read the `isTeamLeader` implementation in `TeamsRepositoryImpl`.
