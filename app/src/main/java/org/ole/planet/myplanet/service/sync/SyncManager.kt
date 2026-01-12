package org.ole.planet.myplanet.service.sync

import android.content.Context
import android.content.SharedPreferences
import android.net.wifi.SupplicantState
import android.net.wifi.WifiManager
import android.util.Log
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import io.realm.Realm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.MainApplication.Companion.createLog
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.SyncListener
import org.ole.planet.myplanet.data.ApiClient
import org.ole.planet.myplanet.data.ApiInterface
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.di.AppPreferences
import org.ole.planet.myplanet.di.ApplicationScope
import org.ole.planet.myplanet.model.RealmMeetup.Companion.insert
import org.ole.planet.myplanet.model.RealmMyCourse.Companion.insertMyCourses
import org.ole.planet.myplanet.model.RealmMyCourse.Companion.saveConcatenatedLinksToPrefs
import org.ole.planet.myplanet.model.RealmMyLibrary.Companion.insertMyLibrary
import org.ole.planet.myplanet.model.RealmMyLibrary.Companion.removeDeletedResource
import org.ole.planet.myplanet.model.RealmMyLibrary.Companion.save
import org.ole.planet.myplanet.model.RealmMyTeam.Companion.insertMyTeams
import org.ole.planet.myplanet.model.RealmResourceActivity.Companion.onSynced
import org.ole.planet.myplanet.model.Rows
import org.ole.planet.myplanet.utilities.Constants
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
import org.ole.planet.myplanet.utilities.JsonUtils.getJsonArray
import org.ole.planet.myplanet.utilities.JsonUtils.getJsonObject
import org.ole.planet.myplanet.utilities.JsonUtils.getString
import org.ole.planet.myplanet.utilities.NotificationUtils.cancel
import org.ole.planet.myplanet.utilities.NotificationUtils.create
import org.ole.planet.myplanet.utilities.SyncTimeLogger
import org.ole.planet.myplanet.utilities.UrlUtils
import java.util.Date
import java.util.concurrent.Executors
import javax.inject.Singleton

@Singleton
class SyncManager constructor(
    @ApplicationContext private val context: Context,
    private val databaseService: DatabaseService,
    @AppPreferences private val settings: SharedPreferences,
    private val apiInterface: ApiInterface,
    private val improvedSyncManager: Lazy<ImprovedSyncManager>,
    private val transactionSyncManager: TransactionSyncManager,
    @ApplicationScope private val syncScope: CoroutineScope
) {
    private var td: Thread? = null
    private var isSyncing = false
    private val stringArray = arrayOfNulls<String>(4)
    private var listener: SyncListener? = null
    private var backgroundSync: Job? = null
    private var betaSync = false
    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus
    private val syncDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val initializationJob: Job by lazy {
        syncScope.launch {
            improvedSyncManager.get().initialize()
        }
    }

    fun start(listener: SyncListener?, type: String, syncTables: List<String>? = null) {
        this.listener = listener
        if (!isSyncing) {
            _syncStatus.value = SyncStatus.Idle
            settings.edit { remove("concatenated_links") }
            listener?.onSyncStarted()
            _syncStatus.value = SyncStatus.Syncing

            // Use improved sync manager if beta sync is enabled
            val useImproved = settings.getBoolean("useImprovedSync", false)
            val isSyncRequest = type.equals("sync", ignoreCase = true)
            if (useImproved && isSyncRequest) {
                initializeAndStartImprovedSync(listener, syncTables)
            } else {
                if (useImproved && !isSyncRequest) {
                    createLog("sync_manager_route", "legacy|reason=$type")
                } else if (!useImproved) {
                    createLog("sync_manager_route", "legacy")
                }
                authenticateAndSync(type, syncTables)
            }
        }
    }

    sealed class SyncStatus {
        object Idle : SyncStatus()
        object Syncing : SyncStatus()
        data class Success(val message: String) : SyncStatus()
        data class Error(val message: String) : SyncStatus()
    }

    private fun initializeAndStartImprovedSync(listener: SyncListener?, syncTables: List<String>?) {
        syncScope.launch {
            try {
                initializationJob.join()

                val manager = improvedSyncManager.get()
                val syncMode = if (settings.getBoolean("fastSync", false)) {
                    SyncMode.Fast
                } else {
                    SyncMode.Standard
                }
                createLog("sync_manager_route", "improved|mode=${syncMode.javaClass.simpleName}")
                manager.start(listener, syncMode, syncTables)
            } catch (e: Exception) {
                listener?.onSyncFailed(e.message)
                _syncStatus.value = SyncStatus.Error(e.message ?: "Unknown error")
            }
        }
    }

    private fun destroy() {
        if (betaSync) {
            syncScope.cancel()
            ThreadSafeRealmManager.closeThreadRealm()
        }
        cancelBackgroundSync()
        cancel(context, 111)
        isSyncing = false
        settings.edit { putLong("LastSync", Date().time) }
        listener?.onSyncComplete()
        listener = null
        _syncStatus.value = SyncStatus.Success("Sync completed")
        try {
            if (!betaSync) {
                td?.interrupt()
            } else {
                td?.interrupt()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun authenticateAndSync(type: String, syncTables: List<String>?) {
        backgroundSync = syncScope.launch(syncDispatcher) {
            if (transactionSyncManager.authenticate()) {
                startSync(type, syncTables)
            } else {
                handleException(context.getString(R.string.invalid_configuration))
                cleanupMainSync()
            }
        }
    }

    private suspend fun startSync(type: String, syncTables: List<String>?) {
        val isFastSync = settings.getBoolean("fastSync", false)
        if (!isFastSync || type == "upload") {
            startFullSync()
        } else {
            startFastSync(syncTables)
        }
    }

    private suspend fun startFullSync() {
        val syncStartTime = System.currentTimeMillis()
        val logger = SyncTimeLogger
        logger.startLogging()
        Log.d("SyncPerf", "═══════════════════════════════════════════════════════════════")
        Log.d("SyncPerf", "FULL SYNC STARTED at ${java.text.SimpleDateFormat("HH:mm:ss.SSS").format(java.util.Date())}")
        Log.d("SyncPerf", "═══════════════════════════════════════════════════════════════")
        try {

            initializeSync()

            // Phase 1: Sync non-library tables in parallel
            // Note: teams and meetups base tables are synced here, then augmented by library sync
            coroutineScope {
                val syncJobs = listOf(
                    async {
                        logger.startProcess("tablet_users_sync")
                        transactionSyncManager.syncDb("tablet_users")
                        logger.endProcess("tablet_users_sync")
                    },
                    async {
                        logger.startProcess("exams_sync")
                        transactionSyncManager.syncDb("exams")
                        logger.endProcess("exams_sync")
                    },
                    async {
                        logger.startProcess("ratings_sync")
                        transactionSyncManager.syncDb("ratings")
                        logger.endProcess("ratings_sync")
                    },
                    async {
                        logger.startProcess("courses_progress_sync")
                        transactionSyncManager.syncDb("courses_progress")
                        logger.endProcess("courses_progress_sync")
                    },
                    async {
                        logger.startProcess("achievements_sync")
                        transactionSyncManager.syncDb("achievements")
                        logger.endProcess("achievements_sync")
                    },
                    async {
                        logger.startProcess("tags_sync")
                        transactionSyncManager.syncDb("tags")
                        logger.endProcess("tags_sync")
                    },
                    async {
                        logger.startProcess("submissions_sync")
                        transactionSyncManager.syncDb("submissions")
                        logger.endProcess("submissions_sync")
                    },
                    async {
                        logger.startProcess("news_sync")
                        transactionSyncManager.syncDb("news")
                        logger.endProcess("news_sync")
                    },
                    async {
                        logger.startProcess("feedback_sync")
                        transactionSyncManager.syncDb("feedback")
                        logger.endProcess("feedback_sync")
                    },
                    async {
                        logger.startProcess("tasks_sync")
                        transactionSyncManager.syncDb("tasks")
                        logger.endProcess("tasks_sync")
                    },
                    async {
                        logger.startProcess("login_activities_sync")
                        transactionSyncManager.syncDb("login_activities")
                        logger.endProcess("login_activities_sync")
                    },
                    async {
                        logger.startProcess("health_sync")
                        transactionSyncManager.syncDb("health")
                        logger.endProcess("health_sync")
                    },
                    async {
                        logger.startProcess("certifications_sync")
                        transactionSyncManager.syncDb("certifications")
                        logger.endProcess("certifications_sync")
                    },
                    async {
                        logger.startProcess("team_activities_sync")
                        transactionSyncManager.syncDb("team_activities")
                        logger.endProcess("team_activities_sync")
                    },
                    async {
                        logger.startProcess("chat_history_sync")
                        transactionSyncManager.syncDb("chat_history")
                        logger.endProcess("chat_history_sync")
                    },
                    async {
                        logger.startProcess("teams_sync")
                        transactionSyncManager.syncDb("teams")
                        logger.endProcess("teams_sync")
                    },
                    async {
                        logger.startProcess("meetups_sync")
                        transactionSyncManager.syncDb("meetups")
                        logger.endProcess("meetups_sync")
                    }
                )
                syncJobs.awaitAll()
            }

            // Phase 2: Sync courses base table
            Log.d("SyncPerf", "  ▶ Starting courses base table sync")
            logger.startProcess("courses_sync")
            transactionSyncManager.syncDb("courses")
            logger.endProcess("courses_sync")

            // Phase 3: Sync library (augments courses, resources, teams, meetups with shelf data)
            logger.startProcess("library_sync")
            myLibraryTransactionSync()
            logger.endProcess("library_sync")

            // Phase 4: Sync resources base table
            logger.startProcess("resource_sync")
            resourceTransactionSync()
            logger.endProcess("resource_sync")

            // Phase 5: Admin and finalization
            logger.startProcess("admin_sync")
            LoginSyncManager.instance.syncAdmin()
            logger.endProcess("admin_sync")

            databaseService.withRealm { realm ->
                logger.startProcess("on_synced")
                onSynced(realm, settings)
                logger.endProcess("on_synced")
            }

            logger.stopLogging()

            val syncEndTime = System.currentTimeMillis()
            val totalSyncTime = syncEndTime - syncStartTime
            val minutes = totalSyncTime / 60000
            val seconds = (totalSyncTime % 60000) / 1000
            Log.d("SyncPerf", "═══════════════════════════════════════════════════════════════")
            Log.d("SyncPerf", "FULL SYNC COMPLETED at ${java.text.SimpleDateFormat("HH:mm:ss.SSS").format(java.util.Date())}")
            Log.d("SyncPerf", "TOTAL SYNC TIME: ${minutes}m ${seconds}s (${totalSyncTime}ms)")
            Log.d("SyncPerf", "═══════════════════════════════════════════════════════════════")
        } catch (err: Exception) {
            val syncEndTime = System.currentTimeMillis()
            val totalSyncTime = syncEndTime - syncStartTime
            Log.d("SyncPerf", "═══════════════════════════════════════════════════════════════")
            Log.d("SyncPerf", "SYNC FAILED after ${totalSyncTime}ms")
            Log.d("SyncPerf", "Error: ${err.message}")
            Log.d("SyncPerf", "═══════════════════════════════════════════════════════════════")
            err.printStackTrace()
            handleException(err.message)
        } finally {
            destroy()
        }
    }

    private suspend fun startFastSync(syncTables: List<String>? = null) {
        try {
            val logger = SyncTimeLogger
            logger.startLogging()

            initializeSync()
            coroutineScope {
                val syncJobs = mutableListOf<Deferred<Unit>>()
                if (syncTables?.contains("tablet_users") != false) {
                    syncJobs.add(
                        async {
                            logger.startProcess("tablet_users_sync")
                            transactionSyncManager.syncDb("tablet_users")
                            logger.endProcess("tablet_users_sync")
                        })

                    syncJobs.add(
                        async {
                            logger.startProcess("login_activities_sync")
                            transactionSyncManager.syncDb("login_activities")
                            logger.endProcess("login_activities_sync")
                        })

                    syncJobs.add(
                        async {
                            logger.startProcess("tags_sync")
                            transactionSyncManager.syncDb("tags")
                            logger.endProcess("tags_sync")
                        })

                    syncJobs.add(
                        async {
                            logger.startProcess("teams_sync")
                            transactionSyncManager.syncDb("teams")
                            logger.endProcess("teams_sync")
                        })

                    syncJobs.add(
                        async {
                            logger.startProcess("news_sync")
                            transactionSyncManager.syncDb("news")
                            logger.endProcess("news_sync")
                        })
                }

                if (syncTables?.contains("resources") == true) {
                    syncJobs.add(
                        async {
                            logger.startProcess("library_sync")
                            myLibraryTransactionSync()
                            logger.endProcess("library_sync")
                        })

                    syncJobs.add(
                        async {
                            logger.startProcess("resource_sync")
                            resourceTransactionSync()
                            logger.endProcess("resource_sync")
                        })
                }

                if (syncTables?.contains("courses") == true) {
                    syncJobs.add(
                        async {
                            logger.startProcess("library_sync")
                            myLibraryTransactionSync()
                            logger.endProcess("library_sync")
                        })

                    syncJobs.add(
                        async {
                            logger.startProcess("courses_sync")
                            transactionSyncManager.syncDb("courses")
                            logger.endProcess("courses_sync")
                        })

                    syncJobs.add(
                        async {
                            logger.startProcess("courses_progress_sync")
                            transactionSyncManager.syncDb("courses_progress")
                            logger.endProcess("courses_progress_sync")
                        })

                    syncJobs.add(
                        async {
                            logger.startProcess("ratings_sync")
                            transactionSyncManager.syncDb("ratings")
                            logger.endProcess("ratings_sync")
                        })
                }

                if (syncTables?.contains("tasks") == true) {
                    syncJobs.add(
                        async {
                            logger.startProcess("tasks_sync")
                            transactionSyncManager.syncDb("tasks")
                            logger.endProcess("tasks_sync")
                        })
                }

                if (syncTables?.contains("meetups") == true) {
                    syncJobs.add(
                        async {
                            logger.startProcess("meetups_sync")
                            transactionSyncManager.syncDb("meetups")
                            logger.endProcess("meetups_sync")
                        })
                }

                if (syncTables?.contains("team_activities") == true) {
                    syncJobs.add(
                        async {
                            logger.startProcess("team_activities_sync")
                            transactionSyncManager.syncDb("team_activities")
                            logger.endProcess("team_activities_sync")
                        })
                }

                if (syncTables?.contains("chat_history") == true) {
                    syncJobs.add(
                        async {
                            logger.startProcess("chat_history_sync")
                            transactionSyncManager.syncDb("chat_history")
                            logger.endProcess("chat_history_sync")
                        })
                }

                if (syncTables?.contains("feedback") == true) {
                    syncJobs.add(
                        async {
                            logger.startProcess("feedback_sync")
                            transactionSyncManager.syncDb("feedback")
                            logger.endProcess("feedback_sync")
                        })
                }

                if (syncTables?.contains("achievements") == true) {
                    syncJobs.add(
                        async {
                            logger.startProcess("achievements_sync")
                            transactionSyncManager.syncDb("achievements")
                            logger.endProcess("achievements_sync")
                        })
                }

                if (syncTables?.contains("health") == true) {
                    syncJobs.add(
                        async {
                            logger.startProcess("health_sync")
                            transactionSyncManager.syncDb("health")
                            logger.endProcess("health_sync")
                        })

                    syncJobs.add(
                        async {
                            logger.startProcess("certifications_sync")
                            transactionSyncManager.syncDb("certifications")
                            logger.endProcess("certifications_sync")
                        })
                }

                if (syncTables?.contains("courses") == true || syncTables?.contains("exams") == true) {
                    syncJobs.add(
                        async {
                            logger.startProcess("exams_sync")
                            transactionSyncManager.syncDb("exams")
                            logger.endProcess("exams_sync")
                        })

                    syncJobs.add(
                        async {
                            logger.startProcess("submissions_sync")
                            transactionSyncManager.syncDb("submissions")
                            logger.endProcess("submissions_sync")
                        })
                }

                syncJobs.awaitAll()
            }

            logger.startProcess("admin_sync")
            LoginSyncManager.instance.syncAdmin()
            logger.endProcess("admin_sync")

            databaseService.withRealm { realm ->
                logger.startProcess("on_synced")
                onSynced(realm, settings)
                logger.endProcess("on_synced")
            }

            logger.stopLogging()
        } catch (err: Exception) {
            err.printStackTrace()
            handleException(err.message)
        } finally {
            destroy()
        }
    }

    private fun cleanupMainSync() {
        cancel(context, 111)
        isSyncing = false
        if (!betaSync) {
            try {
            } catch (e: Exception) {
                e.printStackTrace()
            }
            td?.interrupt()
        } else {
            td?.interrupt()
        }
    }

    private fun initializeSync() {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val wifiInfo = wifiManager.connectionInfo
        if (wifiInfo.supplicantState == SupplicantState.COMPLETED) {
            settings.edit { putString("LastWifiSSID", wifiInfo.ssid) }
        }
        isSyncing = true
        create(context, R.mipmap.ic_launcher, "Syncing data", "Please wait...")
    }

    fun cancelBackgroundSync() {
        backgroundSync?.cancel()
        backgroundSync = null
        listener = null
    }

    private suspend fun resourceTransactionSync() {
        val resourceSyncStartTime = System.currentTimeMillis()
        Log.d("SyncPerf", "  ▶ Starting resource sync")

        val logger = SyncTimeLogger
        logger.startProcess("resource_sync_main")
        var processedItems = 0

        try {
            val newIds: MutableList<String?> = ArrayList()
            var totalRows = 0

            // Get total count
            logger.startProcess("resource_get_total_count")
            val countApiStartTime = System.currentTimeMillis()
            ApiClient.executeWithRetryAndWrap {
                apiInterface.getJsonObject(UrlUtils.header, "${UrlUtils.getUrl()}/resources/_all_docs?limit=0").execute()
            }?.let { response ->
                response.body()?.let { body ->
                    if (body.has("total_rows")) {
                        totalRows = body.get("total_rows").asInt
                    }
                }
            }
            val countApiDuration = System.currentTimeMillis() - countApiStartTime
            logger.logApiCall("${UrlUtils.getUrl()}/resources/_all_docs?limit=0", countApiDuration, true, totalRows)
            logger.endProcess("resource_get_total_count")

            val batchSize = 50
            var skip = 0
            var batchCount = 0

            Log.d("SyncPerf", "    Resources: Found $totalRows documents to sync")
            logger.logDetail("resource_sync", "Total resources: $totalRows, batch size: $batchSize")

            while (skip < totalRows || (totalRows == 0 && skip == 0)) {
                batchCount++
                val batchStartTime = System.currentTimeMillis()

                try {
                    // Fetch batch of documents
                    val batchApiStartTime = System.currentTimeMillis()
                    var response: JsonObject? = null
                    ApiClient.executeWithRetryAndWrap {
                        apiInterface.getJsonObject(UrlUtils.header, "${UrlUtils.getUrl()}/resources/_all_docs?include_docs=true&limit=$batchSize&skip=$skip").execute()
                    }?.let {
                        response = it.body()
                    }
                    val batchApiDuration = System.currentTimeMillis() - batchApiStartTime

                    if (response == null) {
                        logger.logApiCall("${UrlUtils.getUrl()}/resources/_all_docs (batch $batchCount)", batchApiDuration, false, 0)
                        skip += batchSize
                        continue
                    }

                    val rows = getJsonArray("rows", response)
                    logger.logApiCall("${UrlUtils.getUrl()}/resources/_all_docs (batch $batchCount)", batchApiDuration, true, rows.size())

                    if (rows.size() == 0) {
                        break
                    }

                    // Parse documents
                    val parseStartTime = System.currentTimeMillis()
                    val batchDocuments = JsonArray()
                    val validDocuments = mutableListOf<Pair<JsonObject, String>>()

                    for (i in 0 until rows.size()) {
                        val rowObj = rows[i].asJsonObject
                        if (rowObj.has("doc")) {
                            val doc = getJsonObject("doc", rowObj)
                            val id = getString("_id", doc)

                            if (!id.startsWith("_design") && id.isNotBlank()) {
                                batchDocuments.add(doc)
                                validDocuments.add(Pair(doc, id))
                            }
                        }
                    }
                    val parseDuration = System.currentTimeMillis() - parseStartTime
                    if (parseDuration > 100) {
                        logger.logDetail("resource_sync", "Batch $batchCount: Parse took ${parseDuration}ms for ${rows.size()} docs")
                    }

                    if (validDocuments.isNotEmpty()) {
                        try {
                            val chunkSize = 50  // Increased from 10 to reduce transaction count
                            val chunks = validDocuments.chunked(chunkSize)
                            val idsWeAreProcessing = validDocuments.map { it.second }

                            val savedIds = mutableListOf<String>()
                            val realmInsertStartTime = System.currentTimeMillis()

                            for ((chunkIndex, chunk) in chunks.withIndex()) {
                                val chunkStartTime = System.currentTimeMillis()
                                databaseService.withRealm { realm ->
                                    realm.executeTransaction { realmTx ->
                                        val chunkDocuments = JsonArray()
                                        chunk.forEach { (doc, _) -> chunkDocuments.add(doc) }

                                        val chunkIds = save(chunkDocuments, realmTx)
                                        savedIds.addAll(chunkIds)
                                    }
                                }
                                val chunkDuration = System.currentTimeMillis() - chunkStartTime
                                if (chunkDuration > 500) {
                                    logger.logDetail("resource_sync", "Batch $batchCount chunk $chunkIndex: Realm insert took ${chunkDuration}ms for ${chunk.size} docs")
                                }
                            }

                            val realmInsertDuration = System.currentTimeMillis() - realmInsertStartTime
                            logger.logRealmOperation("insert_chunks", "resources", realmInsertDuration, validDocuments.size)

                            if (savedIds.isNotEmpty()) {
                                val validIds = savedIds.filter { it.isNotBlank() }
                                if (validIds.isNotEmpty()) {
                                    newIds.addAll(validIds)
                                    processedItems += validIds.size
                                } else {
                                    newIds.addAll(idsWeAreProcessing)
                                    processedItems += idsWeAreProcessing.size
                                }
                            } else {
                                newIds.addAll(idsWeAreProcessing)
                                processedItems += idsWeAreProcessing.size
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()

                            for ((doc, _) in validDocuments) {
                                try {
                                    databaseService.withRealm { realm ->
                                        realm.executeTransaction { realmTx ->
                                            val singleDocArray = JsonArray()
                                            singleDocArray.add(doc)
                                            val singleIds = save(singleDocArray, realmTx)
                                            if (singleIds.isNotEmpty()) {
                                                newIds.addAll(singleIds)
                                                processedItems++
                                            }
                                        }
                                    }
                                } catch (e2: Exception) {
                                    e2.printStackTrace()
                                }
                            }
                        }
                    }

                    skip += rows.size()

                    val batchEndTime = System.currentTimeMillis()
                    val batchTime = batchEndTime - batchStartTime
                    if (batchCount % 10 == 0) {
                        Log.d("SyncPerf", "    Resources batch $batchCount: ${batchTime}ms - Progress: $skip/$totalRows (${(skip * 100 / totalRows.coerceAtLeast(1))}%)")
                        logger.logDetail("resource_sync", "Batch $batchCount progress: $skip/$totalRows (${(skip * 100 / totalRows.coerceAtLeast(1))}%)")
                        val settings = MainApplication.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        settings.edit {
                            putLong("ResourceLastSyncTime", System.currentTimeMillis())
                            putInt("ResourceSyncPosition", skip)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    logger.logDetail("resource_sync", "Batch $batchCount failed: ${e.message}")
                    skip += batchSize
                }
            }

            try {
                logger.startProcess("resource_cleanup")
                val cleanupStartTime = System.currentTimeMillis()
                val validNewIds = newIds.filter { !it.isNullOrBlank() }
                if (validNewIds.isNotEmpty() && validNewIds.size == newIds.size) {
                    val deletedCount = newIds.size - validNewIds.size
                    Log.d("SyncPerf", "    Resources: Removing $deletedCount deleted resources")
                    databaseService.withRealm { realm -> removeDeletedResource(validNewIds, realm) }
                }
                val cleanupDuration = System.currentTimeMillis() - cleanupStartTime
                logger.endProcess("resource_cleanup")
                if (cleanupDuration > 100) {
                    logger.logRealmOperation("delete_cleanup", "resources", cleanupDuration, newIds.size - validNewIds.size)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                logger.logDetail("resource_sync", "Cleanup failed: ${e.message}")
            }
            logger.endProcess("resource_sync_main", processedItems)

            val resourceSyncEndTime = System.currentTimeMillis()
            val resourceSyncTime = resourceSyncEndTime - resourceSyncStartTime
            val minutes = resourceSyncTime / 60000
            val seconds = (resourceSyncTime % 60000) / 1000
            Log.d("SyncPerf", "  ✓ Resources sync completed: ${minutes}m ${seconds}s - $processedItems items")
        } catch (e: Exception) {
            e.printStackTrace()
            logger.endProcess("resource_sync_main", processedItems)
            val resourceSyncEndTime = System.currentTimeMillis()
            Log.d("SyncPerf", "  ✗ Resources sync failed after ${resourceSyncEndTime - resourceSyncStartTime}ms: ${e.message}")
        }
    }

    private fun handleException(message: String?) {
        if (listener != null) {
            isSyncing = false
            MainApplication.syncFailedCount++
            listener?.onSyncFailed(message)
            _syncStatus.value = SyncStatus.Error(message ?: "Unknown error")
        }
    }

    private suspend fun getShelvesWithDataBatchOptimized(): List<String> {
        val shelvesWithData = mutableListOf<String>()
        val cachedShelves = getCachedShelvesWithData()
        if (cachedShelves.isNotEmpty()) {
            return cachedShelves
        }

        val allShelves = ApiClient.executeWithRetryAndWrap {
            apiInterface.getDocuments(UrlUtils.header, "${UrlUtils.getUrl()}/shelf/_all_docs").execute()
        }?.body()?.rows ?: return emptyList()

        coroutineScope {
            val semaphore = Semaphore(8)
            val checkJobs = allShelves.chunked(25).map { shelfBatch ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        checkShelfBatchForDataOptimized(shelfBatch, apiInterface)
                    }
                }
            }

            checkJobs.awaitAll().flatten().let { validShelves ->
                shelvesWithData.addAll(validShelves)
            }
        }

        cacheShelvesWithData(shelvesWithData)
        return shelvesWithData
    }

    private suspend fun checkShelfBatchForDataOptimized(shelfBatch: List<Rows>, apiInterface: ApiInterface): List<String> {
        val shelvesWithData = mutableListOf<String>()
        val shelfIds = shelfBatch.map { it.id }
        val keysObject = JsonObject().apply {
            add("keys", Gson().fromJson(Gson().toJson(shelfIds), JsonArray::class.java))
        }

        val response = ApiClient.executeWithRetryAndWrap {
            apiInterface.findDocs(UrlUtils.header, "application/json", "${UrlUtils.getUrl()}/shelf/_all_docs?include_docs=true", keysObject).execute()
        }?.body()

        response?.let { responseBody ->
            val rows = getJsonArray("rows", responseBody)
            for (i in 0 until rows.size()) {
                val row = rows[i].asJsonObject
                if (row.has("doc")) {
                    val doc = getJsonObject("doc", row)
                    val shelfId = getString("_id", doc)

                    if (hasShelfDataUltraFast(doc)) {
                        shelvesWithData.add(shelfId)
                    }
                }
            }
        }
        return shelvesWithData
    }

    private fun hasShelfDataUltraFast(shelfDoc: JsonObject): Boolean {
        return listOf("resourceIds", "courseIds", "meetupIds", "teamIds").any { key ->
            shelfDoc.has(key) && shelfDoc.get(key).let { element ->
                element.isJsonArray && element.asJsonArray.size() > 0
            }
        }
    }

    private fun getCachedShelvesWithData(): List<String> {
        val cacheKey = "shelves_with_data"
        val cacheTimeKey = "shelves_cache_time"
        val cacheValidityHours = 6

        val cacheTime = settings.getLong(cacheTimeKey, 0)
        val now = System.currentTimeMillis()

        if (now - cacheTime < cacheValidityHours * 60 * 60 * 1000) {
            val cachedData = settings.getString(cacheKey, "") ?: ""
            if (cachedData.isNotEmpty()) {
                return cachedData.split(",").filter { it.isNotBlank() }
            }
        }
        return emptyList()
    }

    private fun cacheShelvesWithData(shelves: List<String>) {
        val cacheKey = "shelves_with_data"
        val cacheTimeKey = "shelves_cache_time"

        settings.edit {
            putString(cacheKey, shelves.joinToString(","))
            putLong(cacheTimeKey, System.currentTimeMillis())
        }
    }

    private suspend fun myLibraryTransactionSync() {
        val logger = SyncTimeLogger
        val librarySyncStartTime = System.currentTimeMillis()
        Log.d("SyncPerf", "  ▶ Starting library sync")

        logger.startProcess("library_sync_main")
        var processedItems = 0

        try {
            logger.startProcess("library_get_shelves")
            val shelvesStartTime = System.currentTimeMillis()
            val shelvesWithData = getShelvesWithDataBatchOptimized()
            val shelvesDuration = System.currentTimeMillis() - shelvesStartTime
            logger.endProcess("library_get_shelves", shelvesWithData.size)
            Log.d("SyncPerf", "    Library: Found ${shelvesWithData.size} shelves with data in ${shelvesDuration}ms")

            if (shelvesWithData.isEmpty()) {
                logger.logDetail("library_sync", "No shelves with data found, skipping library sync")
                logger.endProcess("library_sync_main", 0)
                return
            }

            logger.startProcess("library_process_shelves")
            val processStartTime = System.currentTimeMillis()

            coroutineScope {
                val semaphore = Semaphore(3)
                val shelfJobs = shelvesWithData.mapIndexed { index, shelfId ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            val shelfStartTime = System.currentTimeMillis()
                            val items = processShelfParallel(shelfId, apiInterface)
                            val shelfDuration = System.currentTimeMillis() - shelfStartTime
                            if (items > 0) {
                                logger.logDetail("library_sync", "Shelf ${index + 1}/${shelvesWithData.size} ($shelfId): $items items in ${shelfDuration}ms")
                            }
                            items
                        }
                    }
                }

                processedItems = shelfJobs.awaitAll().sum()
            }

            val processDuration = System.currentTimeMillis() - processStartTime
            logger.endProcess("library_process_shelves", processedItems)

            saveConcatenatedLinksToPrefs()
            logger.endProcess("library_sync_main", processedItems)

            val totalDuration = System.currentTimeMillis() - librarySyncStartTime
            Log.d("SyncPerf", "  ✓ Library sync completed: ${totalDuration}ms - $processedItems items from ${shelvesWithData.size} shelves")
        } catch (e: Exception) {
            e.printStackTrace()
            logger.endProcess("library_sync_main", processedItems)
            val failDuration = System.currentTimeMillis() - librarySyncStartTime
            Log.d("SyncPerf", "  ✗ Library sync failed after ${failDuration}ms: ${e.message}")
        }
    }

    private suspend fun processShelfParallel(shelfId: String, apiInterface: ApiInterface): Int {
        var processedItems = 0

        try {
            val shelfDoc: JsonObject? = withContext(Dispatchers.IO) {
                var doc: JsonObject? = null
                ApiClient.executeWithRetryAndWrap {
                    apiInterface.getJsonObject(
                        UrlUtils.header,
                        "${UrlUtils.getUrl()}/shelf/$shelfId"
                    ).execute()
                }?.let {
                    doc = it.body()
                }
                coroutineContext.ensureActive()
                doc
            }

            if (shelfDoc == null) {
                return 0
            }

            coroutineScope {
                val dataJobs = Constants.shelfDataList.mapNotNull { shelfData ->
                    val array = getJsonArray(shelfData.key, shelfDoc)
                    if (array.size() > 0) {
                        async(Dispatchers.IO) {
                            processShelfDataOptimizedSync(shelfId, shelfData, shelfDoc, apiInterface)
                        }
                    } else null
                }

                processedItems = dataJobs.awaitAll().sum()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return processedItems
    }

    private suspend fun processShelfDataOptimizedSync(shelfId: String?, shelfData: Constants.ShelfData, shelfDoc: JsonObject?, apiInterface: ApiInterface): Int {
        var processedCount = 0
        val logger = SyncTimeLogger

        try {
            val array = getJsonArray(shelfData.key, shelfDoc)
            if (array.size() == 0) return 0

            stringArray[0] = shelfId
            stringArray[1] = shelfData.categoryKey
            stringArray[2] = shelfData.type

            val validIds = mutableListOf<String>()
            for (i in 0 until array.size()) {
                if (array[i] !is JsonNull) {
                    validIds.add(array[i].asString)
                }
            }

            if (validIds.isEmpty()) return 0

            val batchSize = 25
            val totalBatches = (validIds.size + batchSize - 1) / batchSize

            for (i in 0 until validIds.size step batchSize) {
                val batchNum = (i / batchSize) + 1
                val batchStartTime = System.currentTimeMillis()

                val end = minOf(i + batchSize, validIds.size)
                val batch = validIds.subList(i, end)

                val keysObject = JsonObject()
                keysObject.add("keys", Gson().fromJson(Gson().toJson(batch), JsonArray::class.java))

                // API call
                val apiStartTime = System.currentTimeMillis()
                var response: JsonObject? = null
                ApiClient.executeWithRetryAndWrap {
                    apiInterface.findDocs(UrlUtils.header, "application/json", "${UrlUtils.getUrl()}/${shelfData.type}/_all_docs?include_docs=true", keysObject).execute()
                }?.let {
                    response = it.body()
                }
                val apiDuration = System.currentTimeMillis() - apiStartTime

                if (response == null) {
                    logger.logApiCall("${UrlUtils.getUrl()}/${shelfData.type}/_all_docs (shelf batch $batchNum/$totalBatches)", apiDuration, false, 0)
                    continue
                }

                val responseRows = getJsonArray("rows", response)
                logger.logApiCall("${UrlUtils.getUrl()}/${shelfData.type}/_all_docs (shelf batch $batchNum/$totalBatches)", apiDuration, true, responseRows.size())

                if (responseRows.size() == 0) continue

                val documentsToProcess = mutableListOf<JsonObject>()
                for (j in 0 until responseRows.size()) {
                    val rowObj = responseRows[j].asJsonObject
                    if (rowObj.has("doc")) {
                        val doc = getJsonObject("doc", rowObj)
                        documentsToProcess.add(doc)
                    }
                }

                if (documentsToProcess.isNotEmpty()) {
                    val realmStartTime = System.currentTimeMillis()
                    // Batch insert documents in chunks to reduce transaction overhead
                    val chunkSize = 50  // Increased from processing one batch at a time
                    documentsToProcess.chunked(chunkSize).forEach { chunk ->
                        safeRealmOperation { realm ->
                            realm.executeTransaction { realmTx ->
                                chunk.forEach { doc ->
                                    try {
                                        when (shelfData.type) {
                                            "resources" -> insertMyLibrary(shelfId, doc, realmTx)
                                            "meetups" -> insert(realmTx, doc)
                                            "courses" -> insertMyCourses(shelfId, doc, realmTx)
                                            "teams" -> insertMyTeams(doc, realmTx)
                                        }
                                        processedCount++
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            }
                        }
                    }
                    val realmDuration = System.currentTimeMillis() - realmStartTime
                    logger.logRealmOperation("shelf_insert", shelfData.type, realmDuration, documentsToProcess.size)
                }

                val batchDuration = System.currentTimeMillis() - batchStartTime
                if (batchDuration > 1000) {
                    logger.logDetail("shelf_sync", "Shelf $shelfId ${shelfData.type} batch $batchNum/$totalBatches: ${batchDuration}ms for ${documentsToProcess.size} docs")
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
            logger.logDetail("shelf_sync", "Shelf $shelfId ${shelfData.type} failed: ${e.message}")
        }
        return processedCount
    }

    private fun <T> safeRealmOperation(operation: (Realm) -> T): T? {
        return ThreadSafeRealmManager.withRealm(databaseService, operation)
    }

}
