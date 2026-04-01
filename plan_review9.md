1. **ServiceModule**: Add `@Provides fun provideRealmRepository(databaseService: DatabaseService): RealmRepository = RealmRepository(databaseService)`
2. **TransactionSyncManager**: Add `private val realmRepository: RealmRepository` to constructor.
3. **TransactionSyncManager**: Update the sync call:
```kotlin
                        when (table) {
                            "chat_history" -> chatRepository.insertChatHistoryBatch(mRealm, arr)
                            "exams" -> coursesRepository.bulkInsertFromSync(mRealm, arr)
                            "tablet_users" -> userRepository.bulkInsertFromSync(mRealm, arr)
                            else -> realmRepository.bulkInsertFromSync(mRealm, arr, table)
                        }
                        if (table != "chat_history") {
                            org.ole.planet.myplanet.model.RealmMyCourse.saveConcatenatedLinksToPrefs(sharedPrefManager)
                        }
```
4. **RealmRepository.kt**: Update `bulkInsertFromSync(realm: Realm, jsonArray: JsonArray, table: String)` to use a `when(table)` block matching `Constants.classList` instead of reflection.
```kotlin
    open fun bulkInsertFromSync(realm: Realm, jsonArray: com.google.gson.JsonArray, table: String) {
        val documentList = mutableListOf<com.google.gson.JsonObject>()
        for (j in jsonArray) {
            var jsonDoc = j.asJsonObject
            jsonDoc = org.ole.planet.myplanet.utils.JsonUtils.getJsonObject("doc", jsonDoc)
            val id = org.ole.planet.myplanet.utils.JsonUtils.getString("_id", jsonDoc)
            if (!id.startsWith("_design")) {
                documentList.add(jsonDoc)
            }
        }
        documentList.forEach { jsonDoc ->
            when (table) {
                "news" -> org.ole.planet.myplanet.model.RealmNews.insert(realm, jsonDoc)
                "tags" -> org.ole.planet.myplanet.model.RealmTag.insert(realm, jsonDoc)
                "login_activities" -> org.ole.planet.myplanet.model.RealmOfflineActivity.insert(realm, jsonDoc)
                "ratings" -> org.ole.planet.myplanet.model.RealmRating.insert(realm, jsonDoc)
                "submissions" -> org.ole.planet.myplanet.model.RealmSubmission.insert(realm, jsonDoc)
                "courses" -> org.ole.planet.myplanet.model.RealmMyCourse.insert(realm, jsonDoc)
                "achievements" -> org.ole.planet.myplanet.model.RealmAchievement.insert(realm, jsonDoc)
                "feedback" -> org.ole.planet.myplanet.model.RealmFeedback.insert(realm, jsonDoc)
                "teams" -> org.ole.planet.myplanet.model.RealmMyTeam.insert(realm, jsonDoc)
                "tasks" -> org.ole.planet.myplanet.model.RealmTeamTask.insert(realm, jsonDoc)
                "meetups" -> org.ole.planet.myplanet.model.RealmMeetup.insert(realm, jsonDoc)
                "health" -> org.ole.planet.myplanet.model.RealmHealthExamination.insert(realm, jsonDoc)
                "certifications" -> org.ole.planet.myplanet.model.RealmCertification.insert(realm, jsonDoc)
                "team_activities" -> org.ole.planet.myplanet.model.RealmTeamLog.insert(realm, jsonDoc)
                "courses_progress" -> org.ole.planet.myplanet.model.RealmCourseProgress.insert(realm, jsonDoc)
                "notifications" -> org.ole.planet.myplanet.model.RealmNotification.insert(realm, jsonDoc)
            }
        }
    }
```
5. Remove `Constants.classList` mapping from `Constants.kt` since it is no longer needed? (Optional, but good cleanup if no one else uses it).
Wait, does anything else use `Constants.classList`? Let's check!
