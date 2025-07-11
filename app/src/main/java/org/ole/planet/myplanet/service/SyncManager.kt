package org.ole.planet.myplanet.service

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
import io.realm.Realm
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.SyncListener
import org.ole.planet.myplanet.datamanager.ApiClient
import org.ole.planet.myplanet.datamanager.ApiInterface
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.datamanager.ManagerSync
import org.ole.planet.myplanet.model.DocumentResponse
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
import org.ole.planet.myplanet.utilities.NotificationUtil.cancel
import org.ole.planet.myplanet.utilities.NotificationUtil.create
import org.ole.planet.myplanet.utilities.SyncTimeLogger
import org.ole.planet.myplanet.utilities.Utilities

class SyncManager private constructor(private val context: Context) {
    private var td: Thread? = null
    private val settings: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    lateinit var mRealm: Realm
    private var isSyncing = false
    private val stringArray = arrayOfNulls<String>(4)
    private var listener: SyncListener? = null
    private val dbService: DatabaseService = DatabaseService(context)
    private var backgroundSync: Job? = null
    private val syncScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val semaphore = Semaphore(5)
    private var betaSync = false

    fun start(listener: SyncListener?, type: String, syncTables: List<String>? = null) {
        this.listener = listener
        if (!isSyncing) {
            settings.edit { remove("concatenated_links") }
            listener?.onSyncStarted()
            authenticateAndSync(type, syncTables)
        }
    }

    private fun destroy() {
        if (betaSync) {
            cleanup()
        }
        cancelBackgroundSync()
        cancel(context, 111)
        isSyncing = false
        ourInstance = null
        settings.edit { putLong("LastSync", Date().time) }
        listener?.onSyncComplete()
        try {
            if (!betaSync) {
                if (::mRealm.isInitialized && !mRealm.isClosed) {
                    mRealm.close()
                    td?.interrupt()
                }
            } else {
                td?.interrupt()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun authenticateAndSync(type: String, syncTables: List<String>?) {
        td = Thread {
            if (TransactionSyncManager.authenticate()) {
                startSync(type, syncTables)
            } else {
                handleException(context.getString(R.string.invalid_configuration))
                cleanupMainSync()
            }
        }
        td?.start()
    }

    private fun startSync(type: String, syncTables: List<String>?) {
        val isFastSync = settings.getBoolean("fastSync", false)
        if (!isFastSync || type == "upload") {
            startFullSync()
        } else {
            startFastSync(syncTables)
        }
    }

    private fun startFullSync() {
        try {
            val logger = SyncTimeLogger.getInstance()
            logger.startLogging()

            initializeSync()
            runBlocking {
                val syncJobs = listOf(
                    async {
                        logger.startProcess("tablet_users_sync")
                        TransactionSyncManager.syncDb(mRealm, "tablet_users")
                        logger.endProcess("tablet_users_sync")
                    },
                    async {
                        logger.startProcess("library_sync")
                        myLibraryTransactionSync()
                        logger.endProcess("library_sync")
                    },
                    async { logger.startProcess("courses_sync")
                        TransactionSyncManager.syncDb(mRealm, "courses")
                        logger.endProcess("courses_sync")
                    },
                    async { logger.startProcess("exams_sync")
                        TransactionSyncManager.syncDb(mRealm, "exams")
                        logger.endProcess("exams_sync")
                    },
                    async { logger.startProcess("ratings_sync")
                        TransactionSyncManager.syncDb(mRealm, "ratings")
                        logger.endProcess("ratings_sync")
                    },
                    async { logger.startProcess("courses_progress_sync")
                        TransactionSyncManager.syncDb(mRealm, "courses_progress")
                        logger.endProcess("courses_progress_sync")
                    },
                    async { logger.startProcess("achievements_sync")
                        TransactionSyncManager.syncDb(mRealm, "achievements")
                        logger.endProcess("achievements_sync")
                    },
                    async { logger.startProcess("tags_sync")
                        TransactionSyncManager.syncDb(mRealm, "tags")
                        logger.endProcess("tags_sync")
                    },
                    async { logger.startProcess("submissions_sync")
                        TransactionSyncManager.syncDb(mRealm, "submissions")
                        logger.endProcess("submissions_sync")
                    },
                    async { logger.startProcess("news_sync")
                        TransactionSyncManager.syncDb(mRealm, "news")
                        logger.endProcess("news_sync")
                    },
                    async { logger.startProcess("feedback_sync")
                        TransactionSyncManager.syncDb(mRealm, "feedback")
                        logger.endProcess("feedback_sync")
                    },
                    async { logger.startProcess("teams_sync")
                        TransactionSyncManager.syncDb(mRealm, "teams")
                        logger.endProcess("teams_sync")
                    },
                    async { logger.startProcess("tasks_sync")
                        TransactionSyncManager.syncDb(mRealm, "tasks")
                        logger.endProcess("tasks_sync")
                    },
                    async { logger.startProcess("login_activities_sync")
                        TransactionSyncManager.syncDb(mRealm, "login_activities")
                        logger.endProcess("login_activities_sync")
                    },
                    async { logger.startProcess("meetups_sync")
                        TransactionSyncManager.syncDb(mRealm, "meetups")
                        logger.endProcess("meetups_sync")
                    },
                    async { logger.startProcess("health_sync")
                        TransactionSyncManager.syncDb(mRealm, "health")
                        logger.endProcess("health_sync")
                    },
                    async { logger.startProcess("certifications_sync")
                        TransactionSyncManager.syncDb(mRealm, "certifications")
                        logger.endProcess("certifications_sync")
                    },
                    async { logger.startProcess("team_activities_sync")
                        TransactionSyncManager.syncDb(mRealm, "team_activities")
                        logger.endProcess("team_activities_sync")
                    },
                    async { logger.startProcess("chat_history_sync")
                        TransactionSyncManager.syncDb(mRealm, "chat_history")
                        logger.endProcess("chat_history_sync")
                    }
                )
                syncJobs.awaitAll()
            }

            logger.startProcess("admin_sync")
            ManagerSync.instance?.syncAdmin()
            logger.endProcess("admin_sync")

            logger.startProcess("resource_sync")
            resourceTransactionSync()
            logger.endProcess("resource_sync")

            logger.startProcess("on_synced")
            onSynced(mRealm, settings)
            logger.endProcess("on_synced")
            mRealm.close()

            logger.stopLogging()
        } catch (err: Exception) {
            err.printStackTrace()
            handleException(err.message)
        } finally {
            destroy()
        }
    }

    private fun startFastSync(syncTables: List<String>? = null) {
        try {
            val logger = SyncTimeLogger.getInstance()
            logger.startLogging()

            initializeSync()
            runBlocking {
                val syncJobs = mutableListOf<Deferred<Unit>>()
                if (syncTables?.contains("tablet_users") != false) {
                    syncJobs.add(
                        async {
                            logger.startProcess("tablet_users_sync")
                            TransactionSyncManager.syncDb(mRealm, "tablet_users")
                            logger.endProcess("tablet_users_sync")
                        })

                    syncJobs.add(
                        async { logger.startProcess("login_activities_sync")
                            TransactionSyncManager.syncDb(mRealm, "login_activities")
                            logger.endProcess("login_activities_sync")
                        })

                    syncJobs.add(
                        async {
                            logger.startProcess("tags_sync")
                            TransactionSyncManager.syncDb(mRealm, "tags")
                            logger.endProcess("tags_sync")
                        })

                    syncJobs.add(
                        async {
                            logger.startProcess("teams_sync")
                            TransactionSyncManager.syncDb(mRealm, "teams")
                            logger.endProcess("teams_sync")
                        })

                    syncJobs.add(
                        async {
                            logger.startProcess("news_sync")
                            TransactionSyncManager.syncDb(mRealm, "news")
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
                            logger.startProcess("courses_sync")
                            TransactionSyncManager.syncDb(mRealm, "courses")
                            logger.endProcess("courses_sync")
                        })

                    syncJobs.add(
                        async {
                            logger.startProcess("courses_progress_sync")
                            TransactionSyncManager.syncDb(mRealm, "courses_progress")
                            logger.endProcess("courses_progress_sync")
                        })

                    syncJobs.add(
                        async {
                            logger.startProcess("ratings_sync")
                            TransactionSyncManager.syncDb(mRealm, "ratings")
                            logger.endProcess("ratings_sync")
                        })
                }

                if (syncTables?.contains("tasks") == true) {
                    syncJobs.add(
                        async {
                            logger.startProcess("tasks_sync")
                            TransactionSyncManager.syncDb(mRealm, "tasks")
                            logger.endProcess("tasks_sync")
                        })
                }

                if (syncTables?.contains("meetups") == true) {
                    syncJobs.add(
                        async {
                            logger.startProcess("meetups_sync")
                            TransactionSyncManager.syncDb(mRealm, "meetups")
                            logger.endProcess("meetups_sync")
                        })
                }

                if (syncTables?.contains("team_activities") == true) {
                    syncJobs.add(
                        async {
                            logger.startProcess("team_activities_sync")
                            TransactionSyncManager.syncDb(mRealm, "team_activities")
                            logger.endProcess("team_activities_sync")
                        })
                }

                if (syncTables?.contains("chat_history") == true) {
                    syncJobs.add(
                        async {
                            logger.startProcess("chat_history_sync")
                            TransactionSyncManager.syncDb(mRealm, "chat_history")
                            logger.endProcess("chat_history_sync")
                        })
                }

                if (syncTables?.contains("feedback") == true) {
                    syncJobs.add(
                        async {
                            logger.startProcess("feedback_sync")
                            TransactionSyncManager.syncDb(mRealm, "feedback")
                            logger.endProcess("feedback_sync")
                        })
                }

                if (syncTables?.contains("achievements") == true) {
                    syncJobs.add(
                        async {
                            logger.startProcess("achievements_sync")
                            TransactionSyncManager.syncDb(mRealm, "achievements")
                            logger.endProcess("achievements_sync")
                        })
                }

                if (syncTables?.contains("health") == true) {
                    syncJobs.add(
                        async { logger.startProcess("health_sync")
                            TransactionSyncManager.syncDb(mRealm, "health")
                            logger.endProcess("health_sync")
                        })

                    syncJobs.add(
                        async {
                            logger.startProcess("certifications_sync")
                            TransactionSyncManager.syncDb(mRealm, "certifications")
                            logger.endProcess("certifications_sync")
                        })
                }

                if (syncTables?.contains("courses") == true || syncTables?.contains("exams") == true) {
                    syncJobs.add(
                        async {
                            logger.startProcess("exams_sync")
                            TransactionSyncManager.syncDb(mRealm, "exams")
                            logger.endProcess("exams_sync")
                        })

                    syncJobs.add(
                        async {
                            logger.startProcess("submissions_sync")
                            TransactionSyncManager.syncDb(mRealm, "submissions")
                            logger.endProcess("submissions_sync")
                        })
                }

                syncJobs.awaitAll()
            }

            logger.startProcess("admin_sync")
            ManagerSync.instance?.syncAdmin()
            logger.endProcess("admin_sync")

            logger.startProcess("on_synced")
            onSynced(mRealm, settings)
            logger.endProcess("on_synced")
            mRealm.close()

            logger.stopLogging()
        } catch (err: Exception) {
            err.printStackTrace()
            handleException(err.message)
        } finally {
            destroy()
        }
    }

    private fun startFastSync() {
        betaSync = true
        var mainRealm: Realm? = null
        try {
            val logger = SyncTimeLogger.getInstance()
            logger.startLogging()

            initializeSync()
            mainRealm = dbService.realmInstance
            runBlocking {
                // Phase 1: Critical syncs (sequential)
                async {
                    syncWithSemaphore("tablet_users") {
                        safeRealmOperation { realm ->
                            TransactionSyncManager.syncDb(realm, "tablet_users")
                        }
                    }
                }.await()

                // Phase 2: Major syncs in parallel (this is the key optimization)
                val majorSyncs = listOf(
                    async(Dispatchers.IO) { fastResourceTransactionSync() },
                    async(Dispatchers.IO) { fastMyLibraryTransactionSync() }
                )
                majorSyncs.awaitAll()

                // Phase 3: Remaining syncs in parallel
                val remainingSyncs = listOf(
                    async { syncWithSemaphore("courses") {
                        safeRealmOperation { realm -> TransactionSyncManager.syncDb(realm, "courses") }
                    }},
                    async { syncWithSemaphore("exams") {
                        safeRealmOperation { realm -> TransactionSyncManager.syncDb(realm, "exams") }
                    }},
                    async { syncWithSemaphore("ratings") {
                        safeRealmOperation { realm -> TransactionSyncManager.syncDb(realm, "ratings") }
                    }},
                    async { syncWithSemaphore("achievements") {
                        safeRealmOperation { realm -> TransactionSyncManager.syncDb(realm, "achievements") }
                    }},
                    async { syncWithSemaphore("tags") {
                        safeRealmOperation { realm -> TransactionSyncManager.syncDb(realm, "tags") }
                    }},
                    async { syncWithSemaphore("news") {
                        safeRealmOperation { realm -> TransactionSyncManager.syncDb(realm, "news") }
                    }},
                    async { syncWithSemaphore("feedback") {
                        safeRealmOperation { realm -> TransactionSyncManager.syncDb(realm, "feedback") }
                    }},
                    async { syncWithSemaphore("teams") {
                        safeRealmOperation { realm -> TransactionSyncManager.syncDb(realm, "teams") }
                    }},
                    async { syncWithSemaphore("meetups") {
                        safeRealmOperation { realm -> TransactionSyncManager.syncDb(realm, "meetups") }
                    }},
                    async { syncWithSemaphore("health") {
                        safeRealmOperation { realm -> TransactionSyncManager.syncDb(realm, "health") }
                    }},
                    async { syncWithSemaphore("certifications") {
                        safeRealmOperation { realm -> TransactionSyncManager.syncDb(realm, "certifications") }
                    }},
                    async { syncWithSemaphore("courses_progress") {
                        safeRealmOperation { realm -> TransactionSyncManager.syncDb(realm, "courses_progress") }
                    }},
                    async { syncWithSemaphore("submissions") {
                        safeRealmOperation { realm -> TransactionSyncManager.syncDb(realm, "submissions") }
                    }},
                    async { syncWithSemaphore("tasks") {
                        safeRealmOperation { realm -> TransactionSyncManager.syncDb(realm, "tasks") }
                    }},
                    async { syncWithSemaphore("login_activities") {
                        safeRealmOperation { realm -> TransactionSyncManager.syncDb(realm, "login_activities") }
                    }},
                    async { syncWithSemaphore("team_activities") {
                        safeRealmOperation { realm -> TransactionSyncManager.syncDb(realm, "team_activities") }
                    }},
                    async { syncWithSemaphore("chat_history") {
                        safeRealmOperation { realm -> TransactionSyncManager.syncDb(realm, "chat_history") }
                    }}
                )
                remainingSyncs.awaitAll()
            }

            logger.startProcess("admin_sync")
            ManagerSync.instance?.syncAdmin()
            logger.endProcess("admin_sync")

            logger.startProcess("on_synced")
            onSynced(mainRealm, settings)
            logger.endProcess("on_synced")

            logger.stopLogging()

        } catch (err: Exception) {
            err.printStackTrace()
            handleException(err.message)
        } finally {
            mainRealm?.close()
            destroy()
        }
    }

    private fun cleanupMainSync() {
        cancel(context, 111)
        isSyncing = false
        if (!betaSync) {
            if (::mRealm.isInitialized && !mRealm.isClosed) {
                mRealm.close()
                td?.interrupt()
            }
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
        mRealm = dbService.realmInstance
    }

    fun cancelBackgroundSync() {
        backgroundSync?.cancel()
        backgroundSync = null
    }

    private suspend fun syncWithSemaphore(name: String, syncOperation: suspend () -> Unit) {
        semaphore.withPermit {
            val logger = SyncTimeLogger.getInstance()
            logger.startProcess("${name}_sync")
            try {
                syncOperation()
            } finally {
                logger.endProcess("${name}_sync")
            }
        }
    }

    private fun resourceTransactionSync(backgroundRealm: Realm? = null) {
        val logger = SyncTimeLogger.getInstance()
        logger.startProcess("resource_sync")
        var processedItems = 0

        try {
            val initStartTime = System.currentTimeMillis()
            val apiInterface = ApiClient.getEnhancedClient()
            val realmInstance = backgroundRealm ?: mRealm
            val newIds: MutableList<String?> = ArrayList()
            Log.d("RESOURCE_SYNC","RESOURCE_SYNC: Initialization took ${System.currentTimeMillis() - initStartTime}ms")

            val totalRowsStartTime = System.currentTimeMillis()
            var totalRows = 0
            ApiClient.executeWithRetry {
                apiInterface.getJsonObject(Utilities.header, "${Utilities.getUrl()}/resources/_all_docs?limit=0").execute()
            }?.let { response ->
                response.body()?.let { body ->
                    if (body.has("total_rows")) {
                        totalRows = body.get("total_rows").asInt
                    }
                }
            }
            Log.d("RESOURCE_SYNC","RESOURCE_SYNC: Get total rows took ${System.currentTimeMillis() - totalRowsStartTime}ms (Total: $totalRows)")

            // UPDATED: Smaller batch size for more consistent performance
            val batchSize = 50 // Reduced from 200 to avoid large transaction bottlenecks
            var skip = 0
            var batchCount = 0
            val batchProcessingStartTime = System.currentTimeMillis()

            while (skip < totalRows || (totalRows == 0 && skip == 0)) {
                batchCount++
                val batchStartTime = System.currentTimeMillis()

                try {
                    val apiCallStartTime = System.currentTimeMillis()
                    var response: JsonObject? = null
                    ApiClient.executeWithRetry {
                        apiInterface.getJsonObject(Utilities.header, "${Utilities.getUrl()}/resources/_all_docs?include_docs=true&limit=$batchSize&skip=$skip").execute()
                    }?.let {
                        response = it.body()
                    }
                    val apiCallTime = System.currentTimeMillis() - apiCallStartTime
                    Log.d("RESOURCE_SYNC","RESOURCE_SYNC: Batch $batchCount API call took ${apiCallTime}ms")

                    if (response == null) {
                        skip += batchSize
                        continue
                    }

                    val parseStartTime = System.currentTimeMillis()
                    val rows = getJsonArray("rows", response)

                    if (rows.size() == 0) {
                        break
                    }

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
                    val parseTime = System.currentTimeMillis() - parseStartTime
                    Log.d("RESOURCE_SYNC","RESOURCE_SYNC: Batch $batchCount parsing took ${parseTime}ms (${validDocuments.size} valid docs)")

                    if (validDocuments.isNotEmpty()) {
                        val dbSaveStartTime = System.currentTimeMillis()
                        try {
                            // UPDATED: Chunked processing to avoid large transaction bottlenecks
                            val chunkSize = 10 // Process 10 documents at a time
                            val chunks = validDocuments.chunked(chunkSize)
                            val idsWeAreProcessing = validDocuments.map { it.second }

                            val savedIds = mutableListOf<String>()

                            for ((chunkIndex, chunk) in chunks.withIndex()) {
                                val chunkStartTime = System.currentTimeMillis()
                                realmInstance.executeTransaction { realm ->
                                    val chunkDocuments = JsonArray()
                                    chunk.forEach { (doc, _) -> chunkDocuments.add(doc) }

                                    val chunkIds = save(chunkDocuments, realm)
                                    savedIds.addAll(chunkIds)
                                }
                                val chunkTime = System.currentTimeMillis() - chunkStartTime
                                Log.d("RESOURCE_SYNC","RESOURCE_SYNC: Batch $batchCount chunk ${chunkIndex + 1} took ${chunkTime}ms")
                            }

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

                            val dbSaveTime = System.currentTimeMillis() - dbSaveStartTime
                            Log.d("RESOURCE_SYNC","RESOURCE_SYNC: Batch $batchCount database save took ${dbSaveTime}ms")
                        } catch (e: Exception) {
                            e.printStackTrace()

                            val fallbackStartTime = System.currentTimeMillis()
                            for ((doc, _) in validDocuments) {
                                try {
                                    realmInstance.executeTransaction { realm ->
                                        val singleDocArray = JsonArray()
                                        singleDocArray.add(doc)
                                        val singleIds = save(singleDocArray, realm)
                                        if (singleIds.isNotEmpty()) {
                                            newIds.addAll(singleIds)
                                            processedItems++
                                        }
                                    }
                                } catch (e2: Exception) {
                                    e2.printStackTrace()
                                }
                            }
                            val fallbackTime = System.currentTimeMillis() - fallbackStartTime
                            Log.d("RESOURCE_SYNC","RESOURCE_SYNC: Batch $batchCount individual save fallback took ${fallbackTime}ms")
                        }
                    }

                    skip += rows.size()

                    // UPDATED: Update preferences less frequently
                    if (batchCount % 10 == 0) { // Update every 10 batches instead of every batch
                        val prefsStartTime = System.currentTimeMillis()
                        val settings = MainApplication.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        settings.edit {
                            putLong("ResourceLastSyncTime", System.currentTimeMillis())
                            putInt("ResourceSyncPosition", skip)
                        }
                        val prefsTime = System.currentTimeMillis() - prefsStartTime
                        Log.d("RESOURCE_SYNC","RESOURCE_SYNC: Preferences update took ${prefsTime}ms")
                    }

                    val batchTotalTime = System.currentTimeMillis() - batchStartTime
                    Log.d("RESOURCE_SYNC","RESOURCE_SYNC: Batch $batchCount completed in ${batchTotalTime}ms (API: ${apiCallTime}ms, Parse: ${parseTime}ms)")

                } catch (e: Exception) {
                    e.printStackTrace()
                    skip += batchSize
                    Log.d("RESOURCE_SYNC","RESOURCE_SYNC: Batch $batchCount failed with exception")
                }
            }

            val batchProcessingTime = System.currentTimeMillis() - batchProcessingStartTime
            Log.d("RESOURCE_SYNC","RESOURCE_SYNC: All batch processing took ${batchProcessingTime}ms ($batchCount batches)")

            val cleanupStartTime = System.currentTimeMillis()
            try {
                val validNewIds = newIds.filter { !it.isNullOrBlank() }
                if (validNewIds.isNotEmpty() && validNewIds.size == newIds.size) {
                    removeDeletedResource(validNewIds, realmInstance)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            val cleanupTime = System.currentTimeMillis() - cleanupStartTime
            Log.d("RESOURCE_SYNC", "RESOURCE_SYNC: Cleanup deleted resources took ${cleanupTime}ms")

            logger.endProcess("resource_sync", processedItems)
        } catch (e: Exception) {
            e.printStackTrace()
            logger.endProcess("resource_sync", processedItems)
        }
    }

    private fun handleException(message: String?) {
        if (listener != null) {
            isSyncing = false
            MainApplication.syncFailedCount++
            listener?.onSyncFailed(message)
        }
    }

    private fun fastResourceTransactionSync() {
        val logger = SyncTimeLogger.getInstance()
        logger.startProcess("resource_sync")
        var processedItems = 0

        try {
            val apiInterface = ApiClient.getEnhancedClient()
            val newIds = ConcurrentHashMap.newKeySet<String>()

            var totalRows = 0
            ApiClient.executeWithRetry {
                apiInterface.getJsonObject(Utilities.header, "${Utilities.getUrl()}/resources/_all_docs?limit=0").execute()
            }?.let { response ->
                response.body()?.let { body ->
                    if (body.has("total_rows")) {
                        totalRows = body.get("total_rows").asInt
                    }
                }
            }

            val batchSize = 1000
            val numBatches = (totalRows + batchSize - 1) / batchSize

            runBlocking {
                val semaphore = Semaphore(3)
                val batches = (0 until numBatches).map { batchIndex ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            processBatchOptimized(
                                batchIndex * batchSize,
                                batchSize,
                                apiInterface,
                                newIds
                            )
                        }
                    }
                }

                processedItems = batches.awaitAll().sum()
            }

            safeRealmOperation { realmInstance ->
                realmInstance.executeTransaction { realm ->
                    removeDeletedResource(newIds.toList(), realm)
                }
            }

            logger.endProcess("resource_sync", processedItems)
        } catch (e: Exception) {
            e.printStackTrace()
            logger.endProcess("resource_sync", processedItems)
        }
    }

    private suspend fun processBatchOptimized(skip: Int, batchSize: Int, apiInterface: ApiInterface, newIds: MutableSet<String>): Int {
        var processedCount = 0

        try {
            var response: JsonObject? = null
            ApiClient.executeWithRetry {
                apiInterface.getJsonObject(Utilities.header, "${Utilities.getUrl()}/resources/_all_docs?include_docs=true&limit=$batchSize&skip=$skip").execute()
            }?.let {
                response = it.body()
            }

            if (response == null) return 0

            val rows = getJsonArray("rows", response)
            if (rows.size() == 0) return 0

            val validDocs = mutableListOf<JsonObject>()
            val batchIds = mutableListOf<String>()

            for (i in 0 until rows.size()) {
                val rowObj = rows[i].asJsonObject
                if (rowObj.has("doc")) {
                    val doc = getJsonObject("doc", rowObj)
                    val id = getString("_id", doc)

                    if (!id.startsWith("_design")) {
                        validDocs.add(doc)
                        batchIds.add(id)
                    }
                }
            }

            if (validDocs.isEmpty()) return 0

            safeRealmOperation { realmInstance ->
                realmInstance.executeTransaction { realm ->
                    val bulkArray = JsonArray()
                    validDocs.forEach { doc -> bulkArray.add(doc) }

                    try {
                        val savedIds = save(bulkArray, realm)
                        newIds.addAll(savedIds)
                        processedCount = savedIds.size
                    } catch (e: Exception) {
                        e.printStackTrace()
                        validDocs.forEach { doc ->
                            try {
                                val singleDocArray = JsonArray()
                                singleDocArray.add(doc)
                                val ids = save(singleDocArray, realm)
                                if (ids.isNotEmpty()) {
                                    newIds.addAll(ids)
                                    processedCount++
                                }
                            } catch (individualE: Exception) {
                                individualE.printStackTrace()
                            }
                        }
                    }
                }
            }

            if (skip % (batchSize * 10) == 0) {
                val settings = MainApplication.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                settings.edit {
                    putLong("ResourceLastSyncTime", System.currentTimeMillis())
                    putInt("ResourceSyncPosition", skip + rows.size())
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }

        return processedCount
    }


    // OPTIMIZATION 1: Faster pre-filtering with larger batches and caching
    private suspend fun getShelvesWithDataBatchOptimized(): List<String> {
        val apiInterface = ApiClient.getEnhancedClient()
        val shelvesWithData = mutableListOf<String>()

        // Check cache first
        val cachedShelves = getCachedShelvesWithData()
        if (cachedShelves.isNotEmpty()) {
            Log.d("LIBRARY_SYNC", "Using cached shelf data (${cachedShelves.size} shelves)")
            return cachedShelves
        }

        // Get all shelf IDs first
        val allShelves = ApiClient.executeWithRetry {
            apiInterface.getDocuments(Utilities.header, "${Utilities.getUrl()}/shelf/_all_docs").execute()
        }?.body()?.rows ?: return emptyList()

        // Process shelves in larger parallel batches
        runBlocking {
            val semaphore = Semaphore(8) // Increased from 5
            val checkJobs = allShelves.chunked(25).map { shelfBatch -> // Increased from 10
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

        // Cache the results
        cacheShelvesWithData(shelvesWithData)

        return shelvesWithData
    }

    private suspend fun checkShelfBatchForDataOptimized(
        shelfBatch: List<Rows>,
        apiInterface: ApiInterface
    ): List<String> {
        val shelvesWithData = mutableListOf<String>()

        // Get multiple shelf documents in one API call
        val shelfIds = shelfBatch.map { it.id }
        val keysObject = JsonObject().apply {
            add("keys", Gson().fromJson(Gson().toJson(shelfIds), JsonArray::class.java))
        }

        val response = ApiClient.executeWithRetry {
            apiInterface.findDocs(
                Utilities.header,
                "application/json",
                "${Utilities.getUrl()}/shelf/_all_docs?include_docs=true",
                keysObject
            ).execute()
        }?.body()

        response?.let { responseBody ->
            val rows = getJsonArray("rows", responseBody)
            for (i in 0 until rows.size()) {
                val row = rows[i].asJsonObject
                if (row.has("doc")) {
                    val doc = getJsonObject("doc", row)
                    val shelfId = getString("_id", doc)

                    // Use even faster data check
                    if (hasShelfDataUltraFast(doc)) {
                        shelvesWithData.add(shelfId)
                    }
                }
            }
        }

        return shelvesWithData
    }

    // OPTIMIZATION 2: Ultra-fast shelf data detection
    private fun hasShelfDataUltraFast(shelfDoc: JsonObject): Boolean {
        // Check if any of the expected data fields exist and are non-empty arrays
        return listOf("resourceIds", "courseIds", "meetupIds", "teamIds").any { key ->
            shelfDoc.has(key) && shelfDoc.get(key).let { element ->
                element.isJsonArray && element.asJsonArray.size() > 0
            }
        }
    }

    // OPTIMIZATION 3: Simple caching mechanism
    private fun getCachedShelvesWithData(): List<String> {
        val cacheKey = "shelves_with_data"
        val cacheTimeKey = "shelves_cache_time"
        val cacheValidityHours = 6 // Cache for 6 hours

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

    private fun myLibraryTransactionSync(backgroundRealm: Realm? = null) {
        val logger = SyncTimeLogger.getInstance()
        logger.startProcess("library_sync")
        var processedItems = 0

        try {
            val initStartTime = System.currentTimeMillis()
            val apiInterface = ApiClient.getEnhancedClient()
            val realmInstance = backgroundRealm ?: mRealm
            Log.d("LIBRARY_SYNC","LIBRARY_SYNC: Initialization took ${System.currentTimeMillis() - initStartTime}ms")

            // Use optimized pre-filtering
            val preFilterStartTime = System.currentTimeMillis()
            val shelvesWithData = runBlocking { getShelvesWithDataBatchOptimized() }
            Log.d("LIBRARY_SYNC","LIBRARY_SYNC: Pre-filtering took ${System.currentTimeMillis() - preFilterStartTime}ms. Found ${shelvesWithData.size} shelves with data")

            if (shelvesWithData.isEmpty()) {
                Log.d("LIBRARY_SYNC","LIBRARY_SYNC: No shelves with data found")
                return
            }

            val shelvesProcessingStartTime = System.currentTimeMillis()

            // UPDATED: Process shelves in parallel instead of sequentially
            runBlocking {
                val semaphore = Semaphore(3) // Limit concurrent shelf processing
                val shelfJobs = shelvesWithData.map { shelfId ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            processShelfParallel(shelfId, apiInterface, realmInstance)
                        }
                    }
                }

                processedItems = shelfJobs.awaitAll().sum()
            }

            val shelvesProcessingTime = System.currentTimeMillis() - shelvesProcessingStartTime
            Log.d("LIBRARY_SYNC","LIBRARY_SYNC: All shelves processing took ${shelvesProcessingTime}ms (${shelvesWithData.size} shelves with data)")

            val linksStartTime = System.currentTimeMillis()
            saveConcatenatedLinksToPrefs()
            val linksTime = System.currentTimeMillis() - linksStartTime
            Log.d("LIBRARY_SYNC","LIBRARY_SYNC: Save concatenated links took ${linksTime}ms")

            logger.endProcess("library_sync", processedItems)
        } catch (e: Exception) {
            e.printStackTrace()
            logger.endProcess("library_sync", processedItems)
        }
    }

    private suspend fun processShelfParallel(
        shelfId: String,
        apiInterface: ApiInterface,
        realmInstance: Realm
    ): Int {
        var processedItems = 0

        try {
            val shelfStartTime = System.currentTimeMillis()

            // Get shelf document
            var shelfDoc: JsonObject? = null
            ApiClient.executeWithRetry {
                apiInterface.getJsonObject(Utilities.header, "${Utilities.getUrl()}/shelf/$shelfId").execute()
            }?.let {
                shelfDoc = it.body()
            }

            if (shelfDoc == null) {
                Log.d("LIBRARY_SYNC","LIBRARY_SYNC: Shelf ($shelfId) document fetch failed")
                return 0
            }

            // Process shelf data types in parallel
            coroutineScope {
                val dataJobs = Constants.shelfDataList.mapNotNull { shelfData ->
                    val array = getJsonArray(shelfData.key, shelfDoc)
                    if (array.size() > 0) {
                        async(Dispatchers.IO) {
                            processShelfDataOptimizedSync(shelfId, shelfData, shelfDoc, apiInterface, realmInstance)
                        }
                    } else null
                }

                processedItems = dataJobs.awaitAll().sum()
            }

            val shelfTotalTime = System.currentTimeMillis() - shelfStartTime
            Log.d("LIBRARY_SYNC","LIBRARY_SYNC: Shelf ($shelfId) completed in ${shelfTotalTime}ms")

        } catch (e: Exception) {
            e.printStackTrace()
        }

        return processedItems
    }

    private suspend fun processShelfDataOptimizedSync(
        shelfId: String?,
        shelfData: Constants.ShelfData,
        shelfDoc: JsonObject?,
        apiInterface: ApiInterface,
        realmInstance: Realm
    ): Int {
        var processedCount = 0

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

            // Process in smaller batches for better performance
            val batchSize = 25 // Smaller batches for more consistent performance

            for (i in 0 until validIds.size step batchSize) {
                val end = minOf(i + batchSize, validIds.size)
                val batch = validIds.subList(i, end)

                val keysObject = JsonObject()
                keysObject.add("keys", Gson().fromJson(Gson().toJson(batch), JsonArray::class.java))

                var response: JsonObject? = null
                ApiClient.executeWithRetry {
                    apiInterface.findDocs(
                        Utilities.header,
                        "application/json",
                        "${Utilities.getUrl()}/${shelfData.type}/_all_docs?include_docs=true",
                        keysObject
                    ).execute()
                }?.let {
                    response = it.body()
                }

                if (response == null) continue

                val responseRows = getJsonArray("rows", response)
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
                    // Use synchronized block to avoid Realm threading issues
                    synchronized(realmInstance) {
                        realmInstance.executeTransaction { realm ->
                            documentsToProcess.forEach { doc ->
                                try {
                                    when (shelfData.type) {
                                        "resources" -> insertMyLibrary(shelfId, doc, realm)
                                        "meetups" -> insert(realm, doc)
                                        "courses" -> insertMyCourses(shelfId, doc, realm)
                                        "teams" -> insertMyTeams(doc, realm)
                                    }
                                    processedCount++
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    }
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }

        return processedCount
    }

    private fun fastMyLibraryTransactionSync() {
        val logger = SyncTimeLogger.getInstance()
        logger.startProcess("library_sync")
        var processedItems = 0

        try {
            val apiInterface = ApiClient.getEnhancedClient()

            var shelfResponse: DocumentResponse? = null
            ApiClient.executeWithRetry {
                apiInterface.getDocuments(Utilities.header, "${Utilities.getUrl()}/shelf/_all_docs?include_docs=true").execute()
            }?.let {
                shelfResponse = it.body()
            }

            val rows = shelfResponse?.rows
            if (rows == null || rows.isEmpty()) {
                return
            }

            runBlocking {
                val semaphore = Semaphore(4)
                val shelfJobs = rows.map { row ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            processShelfUltraOptimized(row, apiInterface)
                        }
                    }
                }

                processedItems = shelfJobs.awaitAll().sum()
            }

            saveConcatenatedLinksToPrefs()
            logger.endProcess("library_sync", processedItems)

        } catch (e: Exception) {
            e.printStackTrace()
            logger.endProcess("library_sync", processedItems)
        }
    }

    private suspend fun processShelfUltraOptimized(row: Rows, apiInterface: ApiInterface): Int {
        var processedItems = 0
        val shelfId = row.id

        try {
            var shelfDoc: JsonObject? = null
            ApiClient.executeWithRetry {
                apiInterface.getJsonObject(Utilities.header, "${Utilities.getUrl()}/shelf/$shelfId").execute()
            }?.let {
                shelfDoc = it.body()
            }

            if (shelfDoc == null) return 0
            coroutineScope {
                val shelfDataJobs = Constants.shelfDataList.map { shelfData ->
                    async(Dispatchers.IO) {
                        try {
                            ensureActive()
                            processShelfDataOptimized(shelfId, shelfData, shelfDoc, apiInterface)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            e.printStackTrace()
                            0
                        }
                    }
                }

                processedItems = shelfDataJobs.awaitAll().sum()
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }

        return processedItems
    }

    private suspend fun processShelfDataOptimized(shelfId: String?, shelfData: Constants.ShelfData, shelfDoc: JsonObject, apiInterface: ApiInterface): Int {
        var processedCount = 0

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

            val batchSize = 500
            val results = validIds.chunked(batchSize).map { batch ->
                withContext(Dispatchers.IO) {
                    safeRealmOperation { threadRealm ->
                        processBatchForShelfData(batch, shelfData, shelfId, apiInterface, threadRealm)
                    } ?: 0
                }
            }

            processedCount = results.sum()

        } catch (e: Exception) {
            e.printStackTrace()
        }

        return processedCount
    }

    private fun <T> safeRealmOperation(operation: (Realm) -> T): T? {
        var realm: Realm? = null
        return try {
            realm = Realm.getDefaultInstance()
            operation(realm)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            realm?.let { r ->
                try {
                    if (!r.isClosed) {
                        r.close()
                    }
                } catch (e: IllegalStateException) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun processBatchForShelfData(batch: List<String>, shelfData: Constants.ShelfData, shelfId: String?, apiInterface: ApiInterface, realmInstance: Realm): Int {
        var processedCount = 0

        try {
            val keysObject = JsonObject()
            keysObject.add("keys", Gson().fromJson(Gson().toJson(batch), JsonArray::class.java))

            var response: JsonObject? = null
            ApiClient.executeWithRetry {
                apiInterface.findDocs(Utilities.header, "application/json", "${Utilities.getUrl()}/${shelfData.type}/_all_docs?include_docs=true", keysObject).execute()
            }?.let {
                response = it.body()
            }

            if (response == null) return 0

            val responseRows = getJsonArray("rows", response)
            if (responseRows.size() == 0) return 0

            val documentsToProcess = mutableListOf<JsonObject>()
            for (j in 0 until responseRows.size()) {
                val rowObj = responseRows[j].asJsonObject
                if (rowObj.has("doc")) {
                    val doc = getJsonObject("doc", rowObj)
                    documentsToProcess.add(doc)
                }
            }

            if (documentsToProcess.isNotEmpty()) {
                realmInstance.executeTransaction { realm ->
                    documentsToProcess.forEach { doc ->
                        try {
                            when (shelfData.type) {
                                "resources" -> insertMyLibrary(shelfId, doc, realm)
                                "meetups" -> insert(realm, doc)
                                "courses" -> insertMyCourses(shelfId, doc, realm)
                                "teams" -> insertMyTeams(doc, realm)
                            }
                            processedCount++
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }

        return processedCount
    }

    fun cleanup() {
        syncScope.cancel()
    }

    companion object {
        private var ourInstance: SyncManager? = null
        val instance: SyncManager?
            get() {
                ourInstance = SyncManager(MainApplication.context)
                return ourInstance
            }
    }
}

