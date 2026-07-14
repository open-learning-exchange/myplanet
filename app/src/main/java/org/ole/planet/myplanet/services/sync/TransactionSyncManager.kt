package org.ole.planet.myplanet.services.sync

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.SystemClock
import android.util.Base64
import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import io.realm.Realm
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.callback.OnSyncListener
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.di.ApplicationScope
import org.ole.planet.myplanet.di.LegacyRealmDispatcher
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.repository.ActivitiesRepository
import org.ole.planet.myplanet.repository.ChatRepository
import org.ole.planet.myplanet.repository.CommunityRepository
import org.ole.planet.myplanet.repository.CoursesRepository
import org.ole.planet.myplanet.repository.FeedbackRepository
import org.ole.planet.myplanet.repository.HealthRepository
import org.ole.planet.myplanet.repository.NotificationsRepository
import org.ole.planet.myplanet.repository.ProgressRepository
import org.ole.planet.myplanet.repository.RatingsRepository
import org.ole.planet.myplanet.repository.RealmRepository
import org.ole.planet.myplanet.repository.SubmissionsRepository
import org.ole.planet.myplanet.repository.SurveysRepository
import org.ole.planet.myplanet.repository.TagsRepository
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.repository.TeamsSyncRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.repository.UserSyncRepository
import org.ole.planet.myplanet.repository.VoicesRepository
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.UserSessionManager
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.FileUtils
import org.ole.planet.myplanet.utils.JsonUtils.getJsonArray
import org.ole.planet.myplanet.utils.JsonUtils.getJsonObject
import org.ole.planet.myplanet.utils.JsonUtils.getString
import org.ole.planet.myplanet.utils.SecurePrefs
import org.ole.planet.myplanet.utils.SyncTimeLogger
import org.ole.planet.myplanet.utils.UrlUtils
import org.ole.planet.myplanet.utils.Utilities

@Singleton
class TransactionSyncManager @Inject constructor(
    private val apiInterface: ApiInterface,
    databaseService: DatabaseService,
    @LegacyRealmDispatcher legacyRealmDispatcher: CoroutineDispatcher,
    @param:ApplicationContext private val context: Context,
    private val voicesRepository: VoicesRepository,
    private val chatRepository: ChatRepository,
    private val feedbackRepository: FeedbackRepository,
    private val sharedPrefManager: SharedPrefManager,
    private val userRepository: UserRepository,
    private val userSyncRepository: UserSyncRepository,
    private val activitiesRepository: ActivitiesRepository,
    private val teamsRepository: Lazy<TeamsRepository>,
    private val teamsSyncRepository: Lazy<TeamsSyncRepository>,
    private val notificationsRepository: NotificationsRepository,
    private val tagsRepository: TagsRepository,
    private val ratingsRepository: RatingsRepository,
    private val submissionsRepository: SubmissionsRepository,
    private val coursesRepository: CoursesRepository,
    private val communityRepository: CommunityRepository,
    private val healthRepository: HealthRepository,
    private val progressRepository: ProgressRepository,
    private val surveysRepository: SurveysRepository,
    @ApplicationScope private val applicationScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider
) : RealmRepository(databaseService, legacyRealmDispatcher) {
    suspend fun authenticate(): Boolean {
        try {
            val targetUrl = "${UrlUtils.getUrl()}/tablet_users/_all_docs"
            val response = apiInterface.getDocuments(UrlUtils.header, targetUrl)
            return response.code() == 200 && response.body() != null
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

        applicationScope.launch(dispatcherProvider.io) {
            try {
                val usersToSync = userRepository.getUsersForHealthSync()
                usersToSync.forEach { userModel ->
                    syncHealthData(userModel, header)
                }
                withContext(dispatcherProvider.main) {
                    listener.onSyncComplete()
                }
            } catch (e: Exception) {
                withContext(dispatcherProvider.main) {
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

                    if (key.isNotEmpty() || iv.isNotEmpty()) {
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

        applicationScope.launch(dispatcherProvider.io) {
            val model = userSessionManager.getUserModel()
            val id = model?.id
            try {
                val userModel = id?.let { userRepository.getUserById(it) }
                if (userModel != null) {
                    syncHealthData(userModel, header)
                }
                withContext(dispatcherProvider.main) {
                    listener.onSyncComplete()
                }
            } catch (e: Exception) {
                withContext(dispatcherProvider.main) {
                    listener.onSyncFailed(e.message)
                }
            }
        }
    }

    suspend fun syncDb(table: String, useCheckpoint: Boolean = false): Int = withContext(dispatcherProvider.io) {
        val syncStartTime = SystemClock.elapsedRealtime()
        val checkpointKey = "heavy_sync_skip_$table"
        Log.d("SyncPerf", "  ▶ Starting $table sync")
        try {
            val pageSize = when (table) {
                "ratings" -> 20
                "submissions" -> 100
                "courses_progress", "login_activities", "team_activities" -> 200
                else -> 1000
            }
            var skip = if (useCheckpoint) {
                val saved = sharedPrefManager.rawPreferences.getInt(checkpointKey, 0)
                if (saved > 0) Log.d("SyncPerf", "  ↻ Resuming $table from skip=$saved")
                saved
            } else 0
            var totalDocs = 0
            var batchNumber = if (useCheckpoint) skip / pageSize else 0
            var syncCompletedFully = false
            val url = UrlUtils.getUrl()
            val authHeader = UrlUtils.header

            while (true) {
                batchNumber++
                if (useCheckpoint) {
                    sharedPrefManager.rawPreferences.edit().putInt(checkpointKey, skip).commit()
                }
                val batchStartTime = SystemClock.elapsedRealtime()
                val batchApiStartTime = SystemClock.elapsedRealtime()
                val response = apiInterface.findDocs(
                    authHeader,
                    "application/json",
                    "$url/$table/_all_docs?include_docs=true&limit=$pageSize&skip=$skip",
                    JsonObject() // Empty body for GET-style query
                )
                val batchApiDuration = SystemClock.elapsedRealtime() - batchApiStartTime
                if (response.body() == null || !response.isSuccessful) {
                    Log.d("SyncPerf", "  ✗ Failed $table batch $batchNumber: HTTP ${response.code()}")
                    break
                }
                val arr = getJsonArray("rows", response.body())
                if (arr.size() == 0) {
                    syncCompletedFully = true
                    break
                }
                SyncTimeLogger.logApiCall(
                    "$url/$table/_all_docs (batch $batchNumber)",
                    batchApiDuration,
                    response.isSuccessful,
                    arr.size()
                )
                when (table) {
                    "news" -> timedBatchInsert(table, arr.size()) {
                        voicesRepository.insertNewsList(extractDocs(arr))
                    }
                    "feedback" -> timedBatchInsert(table, arr.size()) {
                        feedbackRepository.insertFeedbackList(extractDocs(arr))
                    }
                    "chat_history" -> timedBatchInsert(table, arr.size()) {
                        chatRepository.insertChatHistoryFromSync(arr.map { it.asJsonObject })
                    }
                    "tablet_users" -> timedBatchInsert(table, arr.size()) {
                        userSyncRepository.insertUsersFromSync(arr.map { it.asJsonObject })
                    }
                    "meetups" -> timedBatchInsert(table, arr.size()) {
                        communityRepository.insertMeetupsFromSync(extractDocs(arr))
                    }
                    "login_activities" -> timedBatchInsert(table, arr.size()) {
                        activitiesRepository.insertLoginActivitiesFromSync(extractDocs(arr))
                    }
                    "courses_progress" -> timedBatchInsert(table, arr.size()) {
                        progressRepository.insertCourseProgressFromSync(extractDocs(arr))
                    }
                    else -> {
                        // Use async transaction to avoid blocking (ANR-safe)
                        executeTransaction { mRealm: Realm ->
                            val insertStartTime = SystemClock.elapsedRealtime()
                            when (table) {
                                "exams" -> surveysRepository.bulkInsertExamsFromSync(mRealm, arr)
                                "team_activities" -> teamsSyncRepository.get().bulkInsertTeamActivitiesFromSync(mRealm, arr)
                                "tags" -> tagsRepository.bulkInsertFromSync(mRealm, arr)
                                "ratings" -> ratingsRepository.bulkInsertFromSync(mRealm, arr)
                                "submissions" -> submissionsRepository.bulkInsertFromSync(mRealm, arr)
                                "courses" -> coursesRepository.bulkInsertFromSync(mRealm, arr)
                                "achievements" -> userSyncRepository.bulkInsertAchievementsFromSync(mRealm, arr)
                                "teams" -> teamsSyncRepository.get().bulkInsertFromSync(mRealm, arr)
                                "tasks" -> teamsSyncRepository.get().bulkInsertTasksFromSync(mRealm, arr)
                                "health" -> healthRepository.bulkInsertFromSync(mRealm, arr)
                                "certifications" -> coursesRepository.bulkInsertCertificationsFromSync(mRealm, arr)
                                "notifications" -> notificationsRepository.bulkInsertFromSync(mRealm, arr)
                                else -> Log.e("SyncPerf", "Unknown table: $table")
                            }
                            val insertDuration = SystemClock.elapsedRealtime() - insertStartTime
                            if (table == "courses") {
                                Log.d(
                                    "SyncPerf",
                                    "    $table insertDuration: ${insertDuration}ms for ${arr.size()} items"
                                )
                            }
                            SyncTimeLogger.logRealmOperation(
                                "insert_batch",
                                table,
                                insertDuration,
                                arr.size()
                            )
                        }
                    }
                }

                if (table == "achievements") {
                    downloadCvAttachmentsFromBatch(arr)
                }
                if (table == "teams") {
                    downloadTeamAttachmentsFromBatch(arr)
                }
                if (table == "courses") {
                    downloadCourseCoversFromBatch(arr)
                }
                totalDocs += arr.size()
                skip += arr.size()
                val batchDuration = SystemClock.elapsedRealtime() - batchStartTime
                Log.d("SyncPerf", "    $table batch $batchNumber: ${arr.size()} docs in ${batchDuration}ms (total: $totalDocs)")
                // Show progress for slow syncs
                if (table in listOf("ratings", "submissions")) {
                    SyncTimeLogger.logDetail(table, "Progress: $totalDocs documents synced so far...")
                }
                // If we got less than pageSize, we're done
                if (arr.size() < pageSize) {
                    syncCompletedFully = true
                    break
                }
            }
            if (useCheckpoint && syncCompletedFully) {
                sharedPrefManager.rawPreferences.edit().remove(checkpointKey).commit()
            }
            val totalDuration = SystemClock.elapsedRealtime() - syncStartTime
            Log.d("SyncPerf", "  ✓ Completed $table sync: $totalDocs docs in ${totalDuration}ms")
            totalDocs
        } catch (e: Exception) {
            e.printStackTrace()
            val failDuration = SystemClock.elapsedRealtime() - syncStartTime
            Log.d("SyncPerf", "  ✗ Failed $table sync after ${failDuration}ms: ${e.message}")
            0
        }
    }

    private suspend fun timedBatchInsert(table: String, batchSize: Int, insert: suspend () -> Unit) {
        val insertStartTime = SystemClock.elapsedRealtime()
        insert()
        val insertDuration = SystemClock.elapsedRealtime() - insertStartTime
        SyncTimeLogger.logRealmOperation(
            "insert_batch",
            table,
            insertDuration,
            batchSize
        )
    }

    private fun extractDocs(arr: JsonArray): List<JsonObject> {
        val docs = ArrayList<JsonObject>(arr.size())
        for (j in arr) {
            val jsonDoc = getJsonObject("doc", j.asJsonObject)
            if (!getString("_id", jsonDoc).startsWith("_design")) {
                docs.add(jsonDoc)
            }
        }
        return docs
    }

    private suspend fun downloadCvAttachmentsFromBatch(arr: JsonArray) {
        for (j in arr) {
            val jsonDoc = getJsonObject("doc", j.asJsonObject)
            val docId = getString("_id", jsonDoc)
            if (docId.startsWith("_design")) continue
            val resumeFileName = getString("resumeFileName", jsonDoc)
            val hasAttachment = jsonDoc.getAsJsonObject("_attachments")?.has("resume.pdf") == true
            if (resumeFileName.isNotEmpty() && hasAttachment) {
                val destFile = File(
                    FileUtils.getOlePath(context) + "cv/$resumeFileName"
                )
                if (!destFile.exists()) {
                    downloadCvAttachment(docId, destFile)
                }
            }
        }
    }

    private suspend fun downloadTeamAttachmentsFromBatch(arr: JsonArray) {
        for (j in arr) {
            val jsonDoc = getJsonObject("doc", j.asJsonObject)
            val docId = getString("_id", jsonDoc)
            if (docId.startsWith("_design")) continue
            val attachmentName = RealmMyTeam
                .getFirstAttachmentName(jsonDoc) ?: continue
            val destFile = RealmMyTeam
                .getAttachmentFile(context, docId, attachmentName) ?: continue
            if (!destFile.exists()) {
                downloadTeamAttachment(docId, attachmentName, destFile)
            }
        }
    }

    private suspend fun downloadCourseCoversFromBatch(arr: JsonArray) {
        for (j in arr) {
            val jsonDoc = getJsonObject("doc", j.asJsonObject)
            val docId = getString("_id", jsonDoc)
            if (docId.startsWith("_design")) continue
            val coverFileName = getString("coverFileName", jsonDoc)
            val hasAttachment = jsonDoc.getAsJsonObject("_attachments")?.has(coverFileName) == true
            if (coverFileName.isNotEmpty() && hasAttachment) {
                val destFile = RealmMyCourse
                    .getCoverImageFile(context, docId, coverFileName) ?: continue
                if (!destFile.exists()) {
                    downloadCourseCover(docId, coverFileName, destFile)
                }
            }
        }
    }

    private suspend fun downloadCourseCover(docId: String, coverFileName: String, destFile: File) {
        try {
            val encodedName = android.net.Uri.encode(coverFileName)
            val url = "${UrlUtils.getUrl()}/courses/$docId/$encodedName"
            val response = apiInterface.downloadFile(UrlUtils.header, url)
            if (response.isSuccessful) {
                response.body()?.let { body ->
                    destFile.parentFile?.mkdirs()
                    destFile.outputStream().use { out ->
                        body.byteStream().use { it.copyTo(out) }
                    }
                }
            }
        } catch (_: Exception) { }
    }

    private suspend fun downloadTeamAttachment(docId: String, attachmentName: String, destFile: File) {
        try {
            val encodedName = Uri.encode(attachmentName)
            val url = "${UrlUtils.getUrl()}/teams/$docId/$encodedName"
            val response = apiInterface.downloadFile(UrlUtils.header, url)
            if (response.isSuccessful) {
                response.body()?.let { body ->
                    destFile.parentFile?.mkdirs()
                    destFile.outputStream().use { out ->
                        body.byteStream().use { it.copyTo(out) }
                    }
                }
            }
        } catch (_: Exception) { }
    }

    private suspend fun downloadCvAttachment(docId: String, destFile: File) {
        try {
            val url = "${UrlUtils.getUrl()}/achievements/$docId/resume.pdf"
            val response = apiInterface.downloadFile(UrlUtils.header, url)
            if (response.isSuccessful) {
                response.body()?.let { body ->
                    destFile.parentFile?.mkdirs()
                    destFile.outputStream().use { out ->
                        body.byteStream().use { it.copyTo(out) }
                    }
                }
            }
        } catch (_: Exception) { }
    }

    suspend fun syncNotificationReads() = withContext(dispatcherProvider.io) {
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
