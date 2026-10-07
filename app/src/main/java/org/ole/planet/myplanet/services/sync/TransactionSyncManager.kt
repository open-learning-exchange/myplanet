package org.ole.planet.myplanet.services.sync

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.model.MyCourse
import org.ole.planet.myplanet.model.MyTeam
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.repository.ActivitiesRepository
import org.ole.planet.myplanet.repository.ChatSyncWriter
import org.ole.planet.myplanet.repository.CoursesRepository
import org.ole.planet.myplanet.repository.EventsSyncWriter
import org.ole.planet.myplanet.repository.FeedbackSyncWriter
import org.ole.planet.myplanet.repository.HealthRepository
import org.ole.planet.myplanet.repository.NotificationsRepository
import org.ole.planet.myplanet.repository.ProgressRepository
import org.ole.planet.myplanet.repository.RatingsRepository
import org.ole.planet.myplanet.repository.SubmissionsRepository
import org.ole.planet.myplanet.repository.SurveysRepository
import org.ole.planet.myplanet.repository.TagsRepository
import org.ole.planet.myplanet.repository.TeamsSyncRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.repository.UserSyncRepository
import org.ole.planet.myplanet.repository.VoicesRepository
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.UserSessionManager
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.FileUtils
import org.ole.planet.myplanet.utils.JsonUtils
import org.ole.planet.myplanet.utils.KJson
import org.ole.planet.myplanet.utils.KJson.getJsonArray
import org.ole.planet.myplanet.utils.KJson.getJsonObject
import org.ole.planet.myplanet.utils.KJson.getString
import org.ole.planet.myplanet.utils.SecurePrefs
import org.ole.planet.myplanet.utils.SyncTimeLogger
import org.ole.planet.myplanet.utils.UrlUtils
import org.ole.planet.myplanet.utils.Utilities
import org.ole.planet.myplanet.utils.toGson
import org.ole.planet.myplanet.utils.withGsonBridgedNumbers

@Singleton
class TransactionSyncManager @Inject constructor(
    private val apiInterface: ApiInterface,
    @param:ApplicationContext private val context: Context,
    private val voicesRepository: VoicesRepository,
    private val chatRepository: ChatSyncWriter,
    private val feedbackRepository: FeedbackSyncWriter,
    private val sharedPrefManager: SharedPrefManager,
    private val userRepository: UserRepository,
    private val userSyncRepository: UserSyncRepository,
    private val activitiesRepository: ActivitiesRepository,
    private val teamsSyncRepository: Lazy<TeamsSyncRepository>,
    private val notificationsRepository: NotificationsRepository,
    private val tagsRepository: TagsRepository,
    private val ratingsRepository: RatingsRepository,
    private val submissionsRepository: SubmissionsRepository,
    private val coursesRepository: CoursesRepository,
    private val eventsSyncWriter: EventsSyncWriter,
    private val healthRepository: HealthRepository,
    private val progressRepository: ProgressRepository,
    private val surveysRepository: SurveysRepository,
    private val dispatcherProvider: DispatcherProvider,
    private val userSessionManager: UserSessionManager,
    private val syncTimeLogger: SyncTimeLogger
) {
    // The heavy tables are fetched in parallel (see SyncManager), but SQLite has a single
    // writer, so running ~14 batch inserts concurrently just thrashes the write lock/WAL — the
    // same inserts that take ~1ms/doc uncontended balloon to >100ms/doc under contention. This
    // mutex serializes only the DB-write portion of each batch; network fetches still overlap.
    private val dbWriteMutex = Mutex()

    companion object {
        private const val MAX_CONCURRENT_ATTACHMENT_DOWNLOADS = 6
    }
    
    /**
     * Each handler hands its writer the trees the writers have always seen: the page as it looked
     * after the response was bridged to Gson on arrival. Writers on kotlinx get it through
     * [bridged] (numbers held as Gson held them, see [withGsonBridgedNumbers]); the writers still
     * on Gson get the page bridged with [toGson].
     */
    private val tableSyncHandlers: Map<String, suspend (JsonArray) -> Unit> = mapOf(
        "news" to { arr -> voicesRepository.insertNewsList(extractDocs(arr).bridged()) },
        "feedback" to { arr -> feedbackRepository.insertFeedbackList(extractDocs(arr).toGsonList()) },
        "chat_history" to { arr -> chatRepository.insertChatHistoryFromSync(rowObjects(arr).toGsonList()) },
        "tablet_users" to { arr -> userSyncRepository.insertUsersFromSync(rowObjects(arr).toGsonList()) },
        "meetups" to { arr -> eventsSyncWriter.insertMeetupsFromSync(extractDocs(arr).toGsonList()) },
        "login_activities" to { arr -> activitiesRepository.insertLoginActivitiesFromSync(extractDocs(arr).toGsonList()) },
        "courses_progress" to { arr -> progressRepository.insertCourseProgressFromSync(extractDocs(arr).toGsonList()) },
        "ratings" to { arr -> ratingsRepository.insertRatingsFromSync(extractDocs(arr).toGsonList()) },
        "certifications" to { arr -> coursesRepository.insertCertificationsFromSync(arr.toGson()) },
        "tags" to { arr -> tagsRepository.insert(extractDocs(arr).toGsonList()) },
        "team_activities" to { arr -> teamsSyncRepository.get().bulkInsertTeamActivitiesFromSync(arr.toGson()) },
        "tasks" to { arr -> teamsSyncRepository.get().bulkInsertTasksFromSync(arr.toGson()) },
        "notifications" to { arr -> notificationsRepository.bulkInsertFromSync(arr.toGson()) },
        "achievements" to { arr -> userSyncRepository.bulkInsertAchievementsFromSync(arr.toGson()) },
        "health" to { arr -> healthRepository.bulkInsertFromSync(arr.toGson()) },
        "courses" to { arr ->
            val insertStartTime = SystemClock.elapsedRealtime()
            coursesRepository.bulkInsertFromSync(arr.toGson())
            val insertDuration = SystemClock.elapsedRealtime() - insertStartTime
            Log.d("SyncPerf", "    courses insertDuration: ${insertDuration}ms for ${arr.size} items")
        },
        "exams" to { arr -> surveysRepository.bulkInsertExamsFromSync(arr.toGson()) },
        "submissions" to { arr -> submissionsRepository.bulkInsertFromSync(arr.bridged()) },
        "teams" to { arr -> teamsSyncRepository.get().bulkInsertFromSync(arr.toGson()) }
    )

    private fun List<JsonObject>.toGsonList() = map { it.toGson() }

    private fun List<JsonObject>.bridged() = map { it.withGsonBridgedNumbers() }

    private fun JsonArray.bridged() = withGsonBridgedNumbers()

    /** Each row as an object, throwing as Gson's `asJsonObject` did for anything else. */
    private fun rowObjects(arr: JsonArray): List<JsonObject> = arr.map { KJson.asJsonObject(it) }

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


    suspend fun syncDashboardKeyId(role: String?) {
        val settings = sharedPrefManager.rawPreferences
        if (role?.contains("health") == true) {
            syncAllHealthData(settings)
        } else {
            syncKeyIv(settings, userSessionManager)
        }
    }

    private suspend fun syncAllHealthData(settings: SharedPreferences) {
        val userName = SecurePrefs.getUserName(context, settings) ?: ""
        val password = SecurePrefs.getPassword(context, settings) ?: ""
        val header = UrlUtils.basicAuthHeader(userName, password)

        withContext(dispatcherProvider.io) {
            val usersToSync = userRepository.getUsersForHealthSync()
            usersToSync.forEach { userModel ->
                syncHealthData(userModel, header)
            }
        }
    }

    private suspend fun syncHealthData(userModel: UserEntity, header: String) {
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun syncKeyIv(
        settings: SharedPreferences,
        userSessionManager: UserSessionManager
    ) {
        val userName = SecurePrefs.getUserName(context, settings) ?: ""
        val password = SecurePrefs.getPassword(context, settings) ?: ""
        val header = UrlUtils.basicAuthHeader(userName, password)

        withContext(dispatcherProvider.io) {
            val model = userSessionManager.getUserModel()
            val id = model?.id
            val userModel = id?.let { userRepository.getUserById(it) }
            if (userModel != null) {
                syncHealthData(userModel, header)
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
                // Bail out cleanly if the worker was stopped (e.g. network constraint lost)
                // so we propagate cancellation instead of hammering the server mid-shutdown.
                currentCoroutineContext().ensureActive()
                batchNumber++
                if (useCheckpoint) {
                    sharedPrefManager.rawPreferences.edit().putInt(checkpointKey, skip).apply()
                }
                val batchStartTime = SystemClock.elapsedRealtime()
                val batchApiStartTime = SystemClock.elapsedRealtime()
                val response = apiInterface.postDoc(
                    authHeader,
                    "application/json",
                    "$url/$table/_all_docs?include_docs=true&limit=$pageSize&skip=$skip",
                    JsonObject(emptyMap()) // Empty body for GET-style query
                )
                val batchApiDuration = SystemClock.elapsedRealtime() - batchApiStartTime
                if (response.body() == null || !response.isSuccessful) {
                    Log.d("SyncPerf", "  ✗ Failed $table batch $batchNumber: HTTP ${response.code()}")
                    break
                }
                val arr = getJsonArray("rows", response.body())
                if (arr.isEmpty()) {
                    syncCompletedFully = true
                    break
                }
                syncTimeLogger.logApiCall(
                    "$url/$table/_all_docs (batch $batchNumber)",
                    batchApiDuration,
                    response.isSuccessful,
                    arr.size
                )
                val handler = tableSyncHandlers[table]
                if (handler != null) {
                    timedBatchInsert(table, arr.size) { handler(arr) }
                } else {
                    Log.e("SyncPerf", "Unknown table: $table")
                }

                if (table == "achievements") {
                    downloadCvAttachmentsFromBatch(arr)
                }
                if (table == "teams") {
                    downloadTeamAttachmentsFromBatch(arr)
                }
                if (table == "courses") {
                    downloadCourseCoversFromBatch(arr)
                    // Resources embedded in course steps were queued during course upserts;
                    // persist them to the Room library table now.
                    coursesRepository.flushPendingCourseResources()
                }
                totalDocs += arr.size
                skip += arr.size
                // Persist progress immediately after a batch is committed so an interruption
                // resumes past it rather than re-processing the just-inserted page.
                if (useCheckpoint) {
                    sharedPrefManager.rawPreferences.edit().putInt(checkpointKey, skip).apply()
                }
                val batchDuration = SystemClock.elapsedRealtime() - batchStartTime
                Log.d("SyncPerf", "    $table batch $batchNumber: ${arr.size} docs in ${batchDuration}ms (total: $totalDocs)")
                // Show progress for slow syncs
                if (table in listOf("ratings", "submissions")) {
                    syncTimeLogger.logDetail(table, "Progress: $totalDocs documents synced so far...")
                }
                // If we got less than pageSize, we're done
                if (arr.size < pageSize) {
                    syncCompletedFully = true
                    break
                }
            }
            if (useCheckpoint && syncCompletedFully) {
                sharedPrefManager.rawPreferences.edit().remove(checkpointKey).apply()
            }
            val totalDuration = SystemClock.elapsedRealtime() - syncStartTime
            Log.d("SyncPerf", "  ✓ Completed $table sync: $totalDocs docs in ${totalDuration}ms")
            totalDocs
        } catch (e: CancellationException) {
            // Worker was stopped (network lost / process shutdown). Progress is checkpointed;
            // let cancellation propagate so WorkManager reschedules cleanly.
            val stopDuration = SystemClock.elapsedRealtime() - syncStartTime
            Log.d("SyncPerf", "  ⏸ Interrupted $table sync after ${stopDuration}ms; will resume from checkpoint")
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            val failDuration = SystemClock.elapsedRealtime() - syncStartTime
            Log.d("SyncPerf", "  ✗ Failed $table sync after ${failDuration}ms: ${e.message}")
            0
        }
    }

    private suspend fun timedBatchInsert(table: String, batchSize: Int, insert: suspend () -> Unit) {
        val insertStartTime = SystemClock.elapsedRealtime()
        dbWriteMutex.withLock { insert() }
        val insertDuration = SystemClock.elapsedRealtime() - insertStartTime
        syncTimeLogger.logDbOperation(
            "insert_batch",
            table,
            insertDuration,
            batchSize
        )
    }

    private fun extractDocs(arr: JsonArray): List<JsonObject> {
        val docs = ArrayList<JsonObject>(arr.size)
        for (j in arr) {
            val jsonDoc = getJsonObject("doc", KJson.asJsonObject(j))
            if (!getString("_id", jsonDoc).startsWith("_design")) {
                docs.add(jsonDoc)
            }
        }
        return docs
    }

    /** Gson's `getAsJsonObject("_attachments")`: null when absent, a `ClassCastException` for a non-object. */
    private fun attachments(doc: JsonObject): JsonObject? = doc["_attachments"]?.let { it as JsonObject }

    private suspend fun downloadCvAttachmentsFromBatch(arr: JsonArray) = coroutineScope {
        val inProgress = mutableSetOf<String>()
        val semaphore = Semaphore(MAX_CONCURRENT_ATTACHMENT_DOWNLOADS)
        for (j in arr) {
            val jsonDoc = getJsonObject("doc", KJson.asJsonObject(j))
            val docId = getString("_id", jsonDoc)
            if (docId.startsWith("_design")) continue
            val resumeFileName = getString("resumeFileName", jsonDoc)
            val hasAttachment = attachments(jsonDoc)?.containsKey("resume.pdf") == true
            if (resumeFileName.isNotEmpty() && hasAttachment) {
                val destFile = File(
                    FileUtils.getOlePath(context) + "cv/$resumeFileName"
                )
                if (!destFile.exists() && inProgress.add(resumeFileName)) {
                    launch { semaphore.withPermit { downloadCvAttachment(docId, destFile) } }
                }
            }
        }
    }

    private suspend fun downloadTeamAttachmentsFromBatch(arr: JsonArray) = coroutineScope {
        val semaphore = Semaphore(MAX_CONCURRENT_ATTACHMENT_DOWNLOADS)
        for (j in arr) {
            val jsonDoc = getJsonObject("doc", KJson.asJsonObject(j))
            val docId = getString("_id", jsonDoc)
            if (docId.startsWith("_design")) continue
            val attachmentName = MyTeam
                .getFirstAttachmentName(jsonDoc) ?: continue
            val destFile = MyTeam
                .getAttachmentFile(context, docId, attachmentName) ?: continue
            if (!destFile.exists()) {
                launch { semaphore.withPermit { downloadTeamAttachment(docId, attachmentName, destFile) } }
            }
        }
    }

    private suspend fun downloadCourseCoversFromBatch(arr: JsonArray) = coroutineScope {
        val semaphore = Semaphore(MAX_CONCURRENT_ATTACHMENT_DOWNLOADS)
        for (j in arr) {
            val jsonDoc = getJsonObject("doc", KJson.asJsonObject(j))
            val docId = getString("_id", jsonDoc)
            if (docId.startsWith("_design")) continue
            val coverFileName = getString("coverFileName", jsonDoc)
            val hasAttachment = attachments(jsonDoc)?.containsKey(coverFileName) == true
            if (coverFileName.isNotEmpty() && hasAttachment) {
                val destFile = MyCourse
                    .getCoverImageFile(context, docId, coverFileName) ?: continue
                if (!destFile.exists()) {
                    launch { semaphore.withPermit { downloadCourseCover(docId, coverFileName, destFile) } }
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
                val body = buildJsonObject {
                    put("_id", notification.id)
                    put("_rev", rev)
                    put("status", "read")
                    put("user", notification.userId)
                    put("message", notification.message)
                    put("type", notification.type)
                    notification.link?.let { put("link", it) }
                    put("priority", notification.priority)
                    put("time", notification.createdAt.time)
                }
                try {
                    val response = apiInterface.putDoc(
                        UrlUtils.header,
                        "application/json",
                        "${UrlUtils.getUrl()}/notifications/${notification.id}",
                        body
                    )
                    if (response.isSuccessful) {
                        val newRev = JsonUtils.getString("rev", response.body()).takeIf { it.isNotEmpty() }
                        Pair(notification.id, newRev)
                    } else null
                } catch (e: CancellationException) {
                    throw e
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
