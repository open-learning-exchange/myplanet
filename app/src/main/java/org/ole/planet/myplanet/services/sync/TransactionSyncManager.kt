package org.ole.planet.myplanet.services.sync

import android.net.Uri
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import dagger.Lazy
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
import kotlinx.serialization.json.jsonObject
import org.ole.planet.myplanet.data.api.PlanetApi
import org.ole.planet.myplanet.model.MyCourse
import org.ole.planet.myplanet.model.MyTeam
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.model.getAttachmentFile
import org.ole.planet.myplanet.model.getCoverImageFile
import org.ole.planet.myplanet.model.getFirstAttachmentName
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
import org.ole.planet.myplanet.utils.AppLog
import org.ole.planet.myplanet.utils.AppStorage
import org.ole.planet.myplanet.utils.CredentialStore
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.GsonUtils.getJsonArray
import org.ole.planet.myplanet.utils.GsonUtils.getJsonObject
import org.ole.planet.myplanet.utils.GsonUtils.getString
import org.ole.planet.myplanet.utils.JsonUtils
import org.ole.planet.myplanet.utils.SyncTimeLogger
import org.ole.planet.myplanet.utils.TimeProvider
import org.ole.planet.myplanet.utils.UrlUtils
import org.ole.planet.myplanet.utils.Utilities
import org.ole.planet.myplanet.utils.toGson
import org.ole.planet.myplanet.utils.toKotlinx

@Singleton
class TransactionSyncManager @Inject constructor(
    private val planetApi: PlanetApi,
    private val appStorage: AppStorage,
    private val credentialStore: CredentialStore,
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
    private val timeProvider: TimeProvider,
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
    
    private val tableSyncHandlers: Map<String, suspend (JsonArray) -> Unit> = mapOf(
        "news" to { arr -> voicesRepository.insertNewsList(extractDocs(arr)) },
        "feedback" to { arr -> feedbackRepository.insertFeedbackList(extractDocs(arr)) },
        "chat_history" to { arr -> chatRepository.insertChatHistoryFromSync(arr.map { it.asJsonObject }) },
        "tablet_users" to { arr -> userSyncRepository.insertUsersFromSync(arr.map { it.asJsonObject }) },
        "meetups" to { arr -> eventsSyncWriter.insertMeetupsFromSync(extractDocs(arr)) },
        "login_activities" to { arr -> activitiesRepository.insertLoginActivitiesFromSync(extractDocs(arr)) },
        "courses_progress" to { arr -> progressRepository.insertCourseProgressFromSync(extractDocs(arr)) },
        "ratings" to { arr -> ratingsRepository.insertRatingsFromSync(extractDocs(arr)) },
        "certifications" to { arr -> coursesRepository.insertCertificationsFromSync(arr) },
        "tags" to { arr -> tagsRepository.insert(extractDocs(arr)) },
        "team_activities" to { arr -> teamsSyncRepository.get().bulkInsertTeamActivitiesFromSync(arr) },
        "tasks" to { arr -> teamsSyncRepository.get().bulkInsertTasksFromSync(arr) },
        "notifications" to { arr -> notificationsRepository.bulkInsertFromSync(arr) },
        "achievements" to { arr -> userSyncRepository.bulkInsertAchievementsFromSync(arr) },
        "health" to { arr -> healthRepository.bulkInsertFromSync(arr) },
        "courses" to { arr ->
            val insertStartTime = timeProvider.elapsedRealtime()
            coursesRepository.bulkInsertFromSync(arr)
            val insertDuration = timeProvider.elapsedRealtime() - insertStartTime
            AppLog.d("SyncPerf", "    courses insertDuration: ${insertDuration}ms for ${arr.size()} items")
        },
        "exams" to { arr -> surveysRepository.bulkInsertExamsFromSync(arr) },
        "submissions" to { arr -> submissionsRepository.bulkInsertFromSync(arr) },
        "teams" to { arr -> teamsSyncRepository.get().bulkInsertFromSync(arr) }
    )

    suspend fun authenticate(): Boolean {
        try {
            val targetUrl = "${UrlUtils.getUrl()}/tablet_users/_all_docs"
            val response = planetApi.getDocuments(UrlUtils.header, targetUrl)
            return response.code == 200 && response.body != null
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }


    suspend fun syncDashboardKeyId(role: String?) {
        if (role?.contains("health") == true) {
            syncAllHealthData()
        } else {
            syncKeyIv(userSessionManager)
        }
    }

    private suspend fun syncAllHealthData() {
        val userName = credentialStore.getUserName() ?: ""
        val password = credentialStore.getPassword() ?: ""
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
                planetApi.getDocuments(header, "${UrlUtils.getUrl()}/$table/_all_docs")
            val ob = response.body
            if (ob != null && ob.rows?.isNotEmpty() == true) {
                val r = ob.rows?.firstOrNull()
                r?.id?.let { id ->
                    val jsonDoc = planetApi.getJsonObject(header, "${UrlUtils.getUrl()}/$table/$id").body?.toGson()
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

    private suspend fun syncKeyIv(userSessionManager: UserSessionManager) {
        val userName = credentialStore.getUserName() ?: ""
        val password = credentialStore.getPassword() ?: ""
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
        val syncStartTime = timeProvider.elapsedRealtime()
        AppLog.d("SyncPerf", "  ▶ Starting $table sync")
        try {
            val pageSize = when (table) {
                "ratings" -> 20
                "submissions" -> 100
                "courses_progress", "login_activities", "team_activities" -> 200
                else -> 1000
            }
            var skip = if (useCheckpoint) {
                val saved = sharedPrefManager.getHeavySyncSkip(table)
                if (saved > 0) AppLog.d("SyncPerf", "  ↻ Resuming $table from skip=$saved")
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
                    sharedPrefManager.setHeavySyncSkip(table, skip)
                }
                val batchStartTime = timeProvider.elapsedRealtime()
                val batchApiStartTime = timeProvider.elapsedRealtime()
                val response = planetApi.postDoc(
                    authHeader,
                    "application/json",
                    "$url/$table/_all_docs?include_docs=true&limit=$pageSize&skip=$skip",
                    JsonObject().toKotlinx().jsonObject // Empty body for GET-style query
                )
                val batchApiDuration = timeProvider.elapsedRealtime() - batchApiStartTime
                if (response.body == null || !response.isSuccessful) {
                    AppLog.d("SyncPerf", "  ✗ Failed $table batch $batchNumber: HTTP ${response.code}")
                    break
                }
                val arr = getJsonArray("rows", response.body?.toGson())
                if (arr.isEmpty()) {
                    syncCompletedFully = true
                    break
                }
                syncTimeLogger.logApiCall(
                    "$url/$table/_all_docs (batch $batchNumber)",
                    batchApiDuration,
                    response.isSuccessful,
                    arr.size()
                )
                val handler = tableSyncHandlers[table]
                if (handler != null) {
                    timedBatchInsert(table, arr.size()) { handler(arr) }
                } else {
                    AppLog.e("SyncPerf", "Unknown table: $table")
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
                totalDocs += arr.size()
                skip += arr.size()
                // Persist progress immediately after a batch is committed so an interruption
                // resumes past it rather than re-processing the just-inserted page.
                if (useCheckpoint) {
                    sharedPrefManager.setHeavySyncSkip(table, skip)
                }
                val batchDuration = timeProvider.elapsedRealtime() - batchStartTime
                AppLog.d("SyncPerf", "    $table batch $batchNumber: ${arr.size()} docs in ${batchDuration}ms (total: $totalDocs)")
                // Show progress for slow syncs
                if (table in listOf("ratings", "submissions")) {
                    syncTimeLogger.logDetail(table, "Progress: $totalDocs documents synced so far...")
                }
                // If we got less than pageSize, we're done
                if (arr.size() < pageSize) {
                    syncCompletedFully = true
                    break
                }
            }
            if (useCheckpoint && syncCompletedFully) {
                sharedPrefManager.clearHeavySyncSkip(table)
            }
            val totalDuration = timeProvider.elapsedRealtime() - syncStartTime
            AppLog.d("SyncPerf", "  ✓ Completed $table sync: $totalDocs docs in ${totalDuration}ms")
            totalDocs
        } catch (e: CancellationException) {
            // Worker was stopped (network lost / process shutdown). Progress is checkpointed;
            // let cancellation propagate so WorkManager reschedules cleanly.
            val stopDuration = timeProvider.elapsedRealtime() - syncStartTime
            AppLog.d("SyncPerf", "  ⏸ Interrupted $table sync after ${stopDuration}ms; will resume from checkpoint")
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            val failDuration = timeProvider.elapsedRealtime() - syncStartTime
            AppLog.d("SyncPerf", "  ✗ Failed $table sync after ${failDuration}ms: ${e.message}")
            0
        }
    }

    private suspend fun timedBatchInsert(table: String, batchSize: Int, insert: suspend () -> Unit) {
        val insertStartTime = timeProvider.elapsedRealtime()
        dbWriteMutex.withLock { insert() }
        val insertDuration = timeProvider.elapsedRealtime() - insertStartTime
        syncTimeLogger.logDbOperation(
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

    private suspend fun downloadCvAttachmentsFromBatch(arr: JsonArray) = coroutineScope {
        val inProgress = mutableSetOf<String>()
        val semaphore = Semaphore(MAX_CONCURRENT_ATTACHMENT_DOWNLOADS)
        for (j in arr) {
            val jsonDoc = getJsonObject("doc", j.asJsonObject)
            val docId = getString("_id", jsonDoc)
            if (docId.startsWith("_design")) continue
            val resumeFileName = getString("resumeFileName", jsonDoc)
            val hasAttachment = jsonDoc.getAsJsonObject("_attachments")?.has("resume.pdf") == true
            if (resumeFileName.isNotEmpty() && hasAttachment) {
                val destFile = File(
                    appStorage.olePath() + "cv/$resumeFileName"
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
            val jsonDoc = getJsonObject("doc", j.asJsonObject)
            val docId = getString("_id", jsonDoc)
            if (docId.startsWith("_design")) continue
            val attachmentName = MyTeam
                .getFirstAttachmentName(jsonDoc) ?: continue
            val destFile = MyTeam
                .getAttachmentFile(appStorage.olePath(), docId, attachmentName) ?: continue
            if (!destFile.exists()) {
                launch { semaphore.withPermit { downloadTeamAttachment(docId, attachmentName, destFile) } }
            }
        }
    }

    private suspend fun downloadCourseCoversFromBatch(arr: JsonArray) = coroutineScope {
        val semaphore = Semaphore(MAX_CONCURRENT_ATTACHMENT_DOWNLOADS)
        for (j in arr) {
            val jsonDoc = getJsonObject("doc", j.asJsonObject)
            val docId = getString("_id", jsonDoc)
            if (docId.startsWith("_design")) continue
            val coverFileName = getString("coverFileName", jsonDoc)
            val hasAttachment = jsonDoc.getAsJsonObject("_attachments")?.has(coverFileName) == true
            if (coverFileName.isNotEmpty() && hasAttachment) {
                val destFile = MyCourse
                    .getCoverImageFile(appStorage.olePath(), docId, coverFileName) ?: continue
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
            val response = planetApi.downloadFile(UrlUtils.header, url)
            if (response.isSuccessful) {
                response.body?.let { body ->
                    destFile.parentFile?.mkdirs()
                    destFile.outputStream().use { out ->
                        body.source().inputStream().use { it.copyTo(out) }
                    }
                }
            }
        } catch (_: Exception) { }
    }

    private suspend fun downloadTeamAttachment(docId: String, attachmentName: String, destFile: File) {
        try {
            val encodedName = Uri.encode(attachmentName)
            val url = "${UrlUtils.getUrl()}/teams/$docId/$encodedName"
            val response = planetApi.downloadFile(UrlUtils.header, url)
            if (response.isSuccessful) {
                response.body?.let { body ->
                    destFile.parentFile?.mkdirs()
                    destFile.outputStream().use { out ->
                        body.source().inputStream().use { it.copyTo(out) }
                    }
                }
            }
        } catch (_: Exception) { }
    }

    private suspend fun downloadCvAttachment(docId: String, destFile: File) {
        try {
            val url = "${UrlUtils.getUrl()}/achievements/$docId/resume.pdf"
            val response = planetApi.downloadFile(UrlUtils.header, url)
            if (response.isSuccessful) {
                response.body?.let { body ->
                    destFile.parentFile?.mkdirs()
                    destFile.outputStream().use { out ->
                        body.source().inputStream().use { it.copyTo(out) }
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
                    addProperty("time", notification.createdAt)
                }
                try {
                    val response = planetApi.putDoc(
                        UrlUtils.header,
                        "application/json",
                        "${UrlUtils.getUrl()}/notifications/${notification.id}",
                        body.toKotlinx().jsonObject
                    )
                    if (response.isSuccessful) {
                        val newRev = JsonUtils.getString("rev", response.body).takeIf { it.isNotEmpty() }
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
