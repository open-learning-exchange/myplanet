package org.ole.planet.myplanet.service

import android.content.Context
import android.content.SharedPreferences
import android.net.wifi.SupplicantState
import android.net.wifi.WifiManager
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import io.realm.Realm
import java.util.Date
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.MainApplication.Companion.createLog
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.SyncListener
import org.ole.planet.myplanet.datamanager.ApiClient
import org.ole.planet.myplanet.datamanager.ApiInterface
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.datamanager.ManagerSync
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
import org.ole.planet.myplanet.service.sync.SyncMode
import org.ole.planet.myplanet.service.sync.ThreadSafeRealmHelper
import org.ole.planet.myplanet.utilities.Constants
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
import org.ole.planet.myplanet.utilities.JsonUtils.getJsonArray
import org.ole.planet.myplanet.utilities.JsonUtils.getJsonObject
import org.ole.planet.myplanet.utilities.JsonUtils.getString
import org.ole.planet.myplanet.utilities.NotificationUtils.cancel
import org.ole.planet.myplanet.utilities.NotificationUtils.create
import org.ole.planet.myplanet.utilities.SyncTimeLogger
import org.ole.planet.myplanet.utilities.UrlUtils

@Singleton
class SyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val databaseService: DatabaseService,
    @AppPreferences private val settings: SharedPreferences,
    private val apiInterface: ApiInterface,
    private val improvedSyncManager: Lazy<ImprovedSyncManager>,
    @ApplicationScope private val syncScope: CoroutineScope
) {
    private var td: Thread? = null
    lateinit var mRealm: Realm
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
            ThreadSafeRealmHelper.closeThreadRealm()
        }
        cancelBackgroundSync()
        cancel(context, 111)
        isSyncing = false
        settings.edit { putLong("LastSync", Date().time) }
        listener?.onSyncComplete()
        _syncStatus.value = SyncStatus.Success("Sync completed")
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
        backgroundSync = syncScope.launch(syncDispatcher) {
            if (TransactionSyncManager.authenticate()) {
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
        try {
            val logger = SyncTimeLogger
            logger.startLogging()

            initializeSync()
            coroutineScope {
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
            ManagerSync.instance.syncAdmin()
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
                            logger.startProcess("library_sync")
                            myLibraryTransactionSync()
                            logger.endProcess("library_sync")
                        })

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
            ManagerSync.instance.syncAdmin()
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

    private fun cleanupMainSync() {
        cancel(context, 111)
        isSyncing = false
        if (!betaSync) {
            try {
                if (::mRealm.isInitialized) {
                    mRealm.close()
                }
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
        mRealm = databaseService.realmInstance
    }

    fun cancelBackgroundSync() {
        backgroundSync?.cancel()
        backgroundSync = null
    }

    private suspend fun resourceTransactionSync(backgroundRealm: Realm? = null) {
        val logger = SyncTimeLogger
        logger.startProcess("resource_sync")
        var processedItems = 0

        try {
            val realmInstance = backgroundRealm ?: mRealm
            val newIds: MutableList<String?> = ArrayList()
            var totalRows = 0
            ApiClient.executeWithRetryAndWrap {
                apiInterface.getJsonObject(UrlUtils.header, "${UrlUtils.getUrl()}/resources/_all_docs?limit=0").execute()
            }?.let { response ->
                response.body()?.let { body ->
                    if (body.has("total_rows")) {
                        totalRows = body.get("total_rows").asInt
                    }
                }
            }

            val batchSize = 50
            var skip = 0
            var batchCount = 0

            while (skip < totalRows || (totalRows == 0 && skip == 0)) {
                batchCount++

                try {
                    var response: JsonObject? = null
                    ApiClient.executeWithRetryAndWrap {
                        apiInterface.getJsonObject(UrlUtils.header, "${UrlUtils.getUrl()}/resources/_all_docs?include_docs=true&limit=$batchSize&skip=$skip").execute()
                    }?.let {
                        response = it.body()
                    }

                    if (response == null) {
                        skip += batchSize
                        continue
                    }

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

                    if (validDocuments.isNotEmpty()) {
                        try {
                            val chunkSize = 10
                            val chunks = validDocuments.chunked(chunkSize)
                            val idsWeAreProcessing = validDocuments.map { it.second }

                            val savedIds = mutableListOf<String>()
                            for ((_, chunk) in chunks.withIndex()) {
                                realmInstance.executeTransaction { realm ->
                                    val chunkDocuments = JsonArray()
                                    chunk.forEach { (doc, _) -> chunkDocuments.add(doc) }

                                    val chunkIds = save(chunkDocuments, realm)
                                    savedIds.addAll(chunkIds)
                                }
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
                        } catch (e: Exception) {
                            e.printStackTrace()

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
                        }
                    }

                    skip += rows.size()
                    if (batchCount % 10 == 0) {
                        val settings = MainApplication.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        settings.edit {
                            putLong("ResourceLastSyncTime", System.currentTimeMillis())
                            putInt("ResourceSyncPosition", skip)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    skip += batchSize
                }
            }

            try {
                val validNewIds = newIds.filter { !it.isNullOrBlank() }
                if (validNewIds.isNotEmpty() && validNewIds.size == newIds.size) {
                    removeDeletedResource(validNewIds, realmInstance)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
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
        logger.startProcess("library_sync")
        var processedItems = 0

        try {
            val shelvesWithData = getShelvesWithDataBatchOptimized()

            if (shelvesWithData.isEmpty()) {
                return
            }

            coroutineScope {
                val semaphore = Semaphore(3)
                val shelfJobs = shelvesWithData.map { shelfId ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            processShelfParallel(shelfId, apiInterface)
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

            for (i in 0 until validIds.size step batchSize) {
                val end = minOf(i + batchSize, validIds.size)
                val batch = validIds.subList(i, end)

                val keysObject = JsonObject()
                keysObject.add("keys", Gson().fromJson(Gson().toJson(batch), JsonArray::class.java))

                var response: JsonObject? = null
                ApiClient.executeWithRetryAndWrap {
                    apiInterface.findDocs(UrlUtils.header, "application/json", "${UrlUtils.getUrl()}/${shelfData.type}/_all_docs?include_docs=true", keysObject).execute()
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
                    safeRealmOperation { realm ->
                        realm.executeTransaction { realmTx ->
                            documentsToProcess.forEach { doc ->
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
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
        return processedCount
    }

    private fun <T> safeRealmOperation(operation: (Realm) -> T): T? {
        return ThreadSafeRealmHelper.withRealm(databaseService, operation)
    }

}
