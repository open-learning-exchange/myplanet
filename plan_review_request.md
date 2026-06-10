1. **Modify `getNewsByTeamId` in `VoicesRepositoryImpl.kt`**:
   - Use `run_in_bash_session` to replace the manual loop logic.
   - Update `getNewsByTeamId` to use Realm query predicates instead:
     ```kotlin
     override suspend fun getNewsByTeamId(teamId: String): List<RealmNews> {
         return withRealm { realm ->
             val allNews = realm.where(RealmNews::class.java)
                 .isEmpty("replyTo")
                 .beginGroup()
                 .equalTo("viewableBy", "teams", Case.INSENSITIVE)
                 .equalTo("viewableId", teamId, Case.INSENSITIVE)
                 .or()
                 .contains("viewIn", "\"_id\":\"\$teamId\"", Case.INSENSITIVE)
                 .endGroup()
                 .sort("time", Sort.DESCENDING)
                 .findAll()

             realm.copyFromRealm(allNews)
         }
     }
     ```
2. **Modify `getDiscussionsByTeamIdFlow` in `VoicesRepositoryImpl.kt`**:
   - Use `run_in_bash_session` to replace the manual `filter` logic.
   - Update `getDiscussionsByTeamIdFlow` to apply the same compound predicate inside the `queryListFlow` block and remove the `.map { discussions.filter { ... } }`.
     ```kotlin
     override suspend fun getDiscussionsByTeamIdFlow(teamId: String): Flow<List<RealmNews>> {
         return queryListFlow(RealmNews::class.java) {
             isEmpty("replyTo")
             beginGroup()
             equalTo("viewableBy", "teams", Case.INSENSITIVE)
             equalTo("viewableId", teamId, Case.INSENSITIVE)
             or()
             contains("viewIn", "\"_id\":\"\$teamId\"", Case.INSENSITIVE)
             endGroup()
             sort("time", Sort.DESCENDING)
         }.flowOn(dispatcherProvider.default)
     }
     ```
3. **Run Unit Tests**:
   - Use `run_in_bash_session` to run tests: `./gradlew testDebugUnitTest --tests org.ole.planet.myplanet.repository.VoicesRepositoryImplTest`
4. **Pre-commit checks**:
   - Complete pre-commit steps to ensure proper testing, verification, review, and reflection are done.
5. **Submit changes**:
   - Submit the PR with the changes using the `submit` tool.
