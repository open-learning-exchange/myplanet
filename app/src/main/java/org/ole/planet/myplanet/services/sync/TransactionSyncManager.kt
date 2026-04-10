package org.ole.planet.myplanet.services.sync

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import io.realm.Realm
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.callback.OnSyncListener
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.di.ApplicationScope
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyCourse.Companion.saveConcatenatedLinksToPrefs
import org.ole.planet.myplanet.model.RealmStepExam.Companion.insertCourseStepsExams
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.repository.ChatRepository
import org.ole.planet.myplanet.repository.FeedbackRepository
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.UserSessionManager
import org.ole.planet.myplanet.utils.Constants
import org.ole.planet.myplanet.utils.JsonUtils.getJsonArray
import org.ole.planet.myplanet.utils.JsonUtils.getJsonObject
import org.ole.planet.myplanet.utils.JsonUtils.getString
import org.ole.planet.myplanet.utils.SecurePrefs
import org.ole.planet.myplanet.utils.UrlUtils
import org.ole.planet.myplanet.utils.Utilities

@Singleton
class TransactionSyncManager @Inject constructor(
    private val apiInterface: ApiInterface,
    private val databaseService: DatabaseService,
    @param:ApplicationContext private val context: Context,
    private val voicesRepository: org.ole.planet.myplanet.repository.VoicesRepository,
    private val chatRepository: ChatRepository,
    private val feedbackRepository: FeedbackRepository,
    private val sharedPrefManager: SharedPrefManager,
    private val userRepository: UserRepository,
    private val activitiesRepository: org.ole.planet.myplanet.repository.ActivitiesRepository,
    private val teamsRepository: Lazy<TeamsRepository>,
    private val notificationsRepository: org.ole.planet.myplanet.repository.NotificationsRepository,
    private val tagsRepository: org.ole.planet.myplanet.repository.TagsRepository,
    private val ratingsRepository: org.ole.planet.myplanet.repository.RatingsRepository,
    private val submissionsRepository: org.ole.planet.myplanet.repository.SubmissionsRepository,
    private val coursesRepository: org.ole.planet.myplanet.repository.CoursesRepository,
    private val communityRepository: org.ole.planet.myplanet.repository.CommunityRepository,
    private val healthRepository: org.ole.planet.myplanet.repository.HealthRepository,
    private val progressRepository: org.ole.planet.myplanet.repository.ProgressRepository,
    private val surveysRepository: org.ole.planet.myplanet.repository.SurveysRepository,
    @ApplicationScope private val applicationScope: CoroutineScope
) {
    suspend fun authenticate(): Boolean {
        try {
            val targetUrl = "${UrlUtils.getUrl()}/tablet_users/_all_docs"
            val response = apiInterface.getDocuments(
                UrlUtils.header,
                targetUrl
            )
            val code = response.code()
            return code == 200
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    fun syncAllHealthData(settings: SharedPreferences, listener: OnSyncListener) {
        listener.onSyncStarted()
        val userName = SecurePrefs.getUserName(context, settings) ?: ""
        val password = SecurePrefs.getPassword(context, settings) ?: ""
        val header = "Basic ${Base64.encodeToString("$userName:$password".toByteArray(), Base64.NO_WRAP)}"

        applicationScope.launch(Dispatchers.IO) {
            try {
                val usersToSync = userRepository.getUsersForHealthSync()
                usersToSync.forEach { userModel ->
                    syncHealthData(userModel, header)
                }
                withContext(Dispatchers.Main) {
                    listener.onSyncComplete()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    listener.onSyncFailed(e.message)
                }
            }
        }
    }

    private suspend fun syncHealthData(userModel: RealmUser, header: String) {
        val table =
            "userdb-${userModel.planetCode?.let { Utilities.toHex(it) }}-${userModel.name?.let { Utilities.toHex(it) }}"
        try {
            val response =
                apiInterface.getDocuments(header, "${UrlUtils.getUrl()}/$table/_all_docs")
            val ob = response.body()
            if (ob != null && ob.rows?.isNotEmpty() == true) {
                val r = ob.rows?.firstOrNull()
                r?.id?.let { id ->
                    val jsonDoc = apiInterface.getJsonObject(header, "${UrlUtils.getUrl()}/$table/$id").body()
                    val key = getString("key", jsonDoc)
                    val iv = getString("iv", jsonDoc)

                    if (!key.isNullOrEmpty() || !iv.isNullOrEmpty()) {
                        userModel.id?.let {
                            userRepository.markUserKeyIvSaved(it, key, iv)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun syncKeyIv(
        settings: SharedPreferences,
        listener: OnSyncListener,
        userSessionManager: UserSessionManager
    ) {
        listener.onSyncStarted()
        val userName = SecurePrefs.getUserName(context, settings) ?: ""
        val password = SecurePrefs.getPassword(context, settings) ?: ""
        val header = "Basic " + Base64.encodeToString("$userName:$password".toByteArray(), Base64.NO_WRAP)

        applicationScope.launch(Dispatchers.IO) {
            val model = userSessionManager.getUserModel()
            val id = model?.id
            try {
                val userModel = id?.let { userRepository.getUserById(it) }
                if (userModel != null) {
                    syncHealthData(userModel, header)
                }
                withContext(Dispatchers.Main) {
                    listener.onSyncComplete()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    listener.onSyncFailed(e.message)
                }
            }
        }
    }

    suspend fun syncDb(table: String): Int = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val syncStartTime = System.currentTimeMillis()
        android.util.Log.d("SyncPerf", "  ▶ Starting $table sync")
        try {
            // Determine pagination size based on table (smaller for slow endpoints)
            val pageSize = when (table) {
                "ratings" -> 20      // Small batches for slow endpoint
                "submissions" -> 100  // Medium batches for slow endpoint
                else -> 1000          // Large batches for fast endpoints
            }
            var skip = 0
            var totalDocs = 0
            var batchNumber = 0

            // Paginated fetching to avoid long-blocking API calls
            while (true) {
                batchNumber++
                val batchStartTime = System.currentTimeMillis()
                // Time the batch API call (much faster with pagination)
                val batchApiStartTime = System.currentTimeMillis()
                val response = apiInterface.findDocs(
                    UrlUtils.header,
                    "application/json",
                    UrlUtils.getUrl() + "/" + table + "/_all_docs?include_docs=true&limit=$pageSize&skip=$skip",
                    JsonObject() // Empty body for GET-style query
                )
                val batchApiDuration = System.currentTimeMillis() - batchApiStartTime
                if (response.body() == null || !response.isSuccessful) {
                    android.util.Log.d("SyncPerf", "  ✗ Failed $table batch $batchNumber: HTTP ${response.code()}")
                    break
                }
                val arr = getJsonArray("rows", response.body())
                if (arr.size() == 0) {
                    break // No more documents
                }
                org.ole.planet.myplanet.utils.SyncTimeLogger.logApiCall(
                    "${UrlUtils.getUrl()}/$table/_all_docs (batch $batchNumber)",
                    batchApiDuration,
                    response.isSuccessful,
                    arr.size()
                )
                if (table == "news") {
                    val insertStartTime = System.currentTimeMillis()
                    val docs = ArrayList<JsonObject>(arr.size())
                    for (j in arr) {
                        var jsonDoc = j.asJsonObject
                        jsonDoc = getJsonObject("doc", jsonDoc)
                        val id = getString("_id", jsonDoc)
                        if (!id.startsWith("_design")) {
                            docs.add(jsonDoc)
                        }
                    }
                    voicesRepository.insertNewsList(docs)
                    val insertDuration = System.currentTimeMillis() - insertStartTime
                    org.ole.planet.myplanet.utils.SyncTimeLogger.logRealmOperation(
                        "insert_batch",
                        table,
                        insertDuration,
                        arr.size()
                    )
                } else if (table == "feedback") {
                    val insertStartTime = System.currentTimeMillis()
                    val docs = ArrayList<JsonObject>(arr.size())
                    for (j in arr) {
                        var jsonDoc = j.asJsonObject
                        jsonDoc = getJsonObject("doc", jsonDoc)
                        val id = getString("_id", jsonDoc)
                        if (!id.startsWith("_design")) {
                            docs.add(jsonDoc)
                        }
                    }
                    feedbackRepository.insertFeedbackList(docs)
                    val insertDuration = System.currentTimeMillis() - insertStartTime
                    org.ole.planet.myplanet.utils.SyncTimeLogger.logRealmOperation(
                        "insert_batch",
                        table,
                        insertDuration,
                        arr.size()
                    )
                } else if (table == "chat_history") {
                    databaseService.executeTransactionAsync { mRealm: Realm ->
                        val insertStartTime = System.currentTimeMillis()
                        chatRepository.insertChatHistoryBatch(mRealm, arr)
                        val insertDuration = System.currentTimeMillis() - insertStartTime
                        org.ole.planet.myplanet.utils.SyncTimeLogger.logRealmOperation(
                            "insert_batch",
                            table,
                            insertDuration,
                            arr.size()
                        )
                    }
                } else {
                    // Use async transaction to avoid blocking (ANR-safe)
                    databaseService.executeTransactionAsync { mRealm: Realm ->
                        val insertStartTime = System.currentTimeMillis()
                                                when (table) {
                            "tablet_users" -> userRepository.bulkInsertUsersFromSync(mRealm, arr, sharedPrefManager.rawPreferences)
                            "exams" -> surveysRepository.bulkInsertExamsFromSync(mRealm, arr)
                            "team_activities" -> teamsRepository.get().bulkInsertTeamActivitiesFromSync(mRealm, arr)
                            "login_activities" -> activitiesRepository.bulkInsertLoginActivitiesFromSync(mRealm, arr)
                            "tags" -> tagsRepository.bulkInsertFromSync(mRealm, arr)
                            "ratings" -> ratingsRepository.bulkInsertFromSync(mRealm, arr)
                            "submissions" -> submissionsRepository.bulkInsertFromSync(mRealm, arr)
                            "courses" -> {
                                coursesRepository.bulkInsertFromSync(mRealm, arr)
                                org.ole.planet.myplanet.model.RealmMyCourse.saveConcatenatedLinksToPrefs(sharedPrefManager)
                            }
                            "achievements" -> userRepository.bulkInsertAchievementsFromSync(mRealm, arr)
                            "teams" -> teamsRepository.get().bulkInsertFromSync(mRealm, arr)
                            "tasks" -> teamsRepository.get().bulkInsertTasksFromSync(mRealm, arr)
                            "meetups" -> communityRepository.bulkInsertFromSync(mRealm, arr)
                            "health" -> healthRepository.bulkInsertFromSync(mRealm, arr)
                            "certifications" -> coursesRepository.bulkInsertCertificationsFromSync(mRealm, arr)
                            "courses_progress" -> progressRepository.bulkInsertFromSync(mRealm, arr)
                            "notifications" -> notificationsRepository.bulkInsertFromSync(mRealm, arr)
                            else -> android.util.Log.e("SyncPerf", "Unknown table: $table")
                        }
                        val insertDuration = System.currentTimeMillis() - insertStartTime
                        if (table == "courses") {
                            android.util.Log.d("SyncPerf", "    $table insertDuration: ${insertDuration}ms for ${arr.size()} items")
                        }
                        org.ole.planet.myplanet.utils.SyncTimeLogger.logRealmOperation(
                            "insert_batch",
                            table,
                            insertDuration,
                            arr.size()
                        )
                    }
                }
                totalDocs += arr.size()
                skip += arr.size()
                val batchDuration = System.currentTimeMillis() - batchStartTime
                android.util.Log.d("SyncPerf", "    $table batch $batchNumber: ${arr.size()} docs in ${batchDuration}ms (total: $totalDocs)")
                // Show progress for slow syncs
                if (table in listOf("ratings", "submissions")) {
                    org.ole.planet.myplanet.utils.SyncTimeLogger.logDetail(table, "Progress: $totalDocs documents synced so far...")
                }
                // If we got less than pageSize, we're done
                if (arr.size() < pageSize) {
                    break
                }
            }
            val totalDuration = System.currentTimeMillis() - syncStartTime
            android.util.Log.d("SyncPerf", "  ✓ Completed $table sync: $totalDocs docs in ${totalDuration}ms")
            totalDocs
        } catch (e: Exception) {
            e.printStackTrace()
            val failDuration = System.currentTimeMillis() - syncStartTime
            android.util.Log.d("SyncPerf", "  ✗ Failed $table sync after ${failDuration}ms: ${e.message}")
            0
        }
    }

    suspend fun syncNotificationReads() = withContext(Dispatchers.IO) {
        val pending = notificationsRepository.getPendingSyncNotifications()
        if (pending.isEmpty()) return@withContext

        val successfulSyncs = pending.map { notification ->
            async {
                val rev = notification.rev ?: return@async null
                val body = JsonObject().apply {
                    addProperty("_id", notification.id)
                    addProperty("_rev", rev)
                    addProperty("status", "read")
                    addProperty("user", notification.userId)
                    addProperty("message", notification.message)
                    addProperty("type", notification.type)
                    notification.link?.let { addProperty("link", it) }
                    addProperty("priority", notification.priority)
                    addProperty("time", notification.createdAt.time)
                }
                try {
                    val response = apiInterface.putDoc(
                        UrlUtils.header,
                        "application/json",
                        "${UrlUtils.getUrl()}/notifications/${notification.id}",
                        body
                    )
                    if (response.isSuccessful) {
                        val newRev = response.body()?.get("rev")?.asString
                        Pair(notification.id, newRev)
                    } else null
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }
        }.awaitAll().filterNotNull()

        if (successfulSyncs.isNotEmpty()) {
            notificationsRepository.markNotificationsSynced(successfulSyncs)
        }
    }
}
