package org.ole.planet.myplanet.service

import android.content.Context
import android.content.SharedPreferences
import android.net.wifi.SupplicantState
import android.net.wifi.WifiManager
import android.text.TextUtils
import android.util.Log
import androidx.lifecycle.MutableLiveData
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import io.realm.Realm
import kotlinx.coroutines.*
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.SyncListener
import org.ole.planet.myplanet.datamanager.ApiClient.client
import org.ole.planet.myplanet.datamanager.ApiInterface
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.datamanager.ManagerSync
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
import org.ole.planet.myplanet.utilities.Constants.ShelfData
import org.ole.planet.myplanet.utilities.JsonUtils.getJsonArray
import org.ole.planet.myplanet.utilities.JsonUtils.getString
import org.ole.planet.myplanet.utilities.NotificationUtil.cancel
import org.ole.planet.myplanet.utilities.NotificationUtil.create
import org.ole.planet.myplanet.utilities.Utilities
import java.io.IOException
import java.util.Date
import kotlin.system.measureTimeMillis
import androidx.core.content.edit
import org.ole.planet.myplanet.utilities.JsonUtils
import org.ole.planet.myplanet.utilities.RealmTransactionManager
import org.ole.planet.myplanet.utilities.SyncTimingLogger

class SyncManager private constructor(private val context: Context) {
    private var td: Thread? = null
    private val settings: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    lateinit var mRealm: Realm
    private var isSyncing = false
    private val stringArray = arrayOfNulls<String>(4)
    private var shelfDoc: Rows? = null
    private var listener: SyncListener? = null
    private val dbService: DatabaseService = DatabaseService(context)
    private var backgroundSync: Job? = null
    val _syncState = MutableLiveData<Boolean>()

    fun start(listener: SyncListener?, type: String) {
        this.listener = listener
        if (!isSyncing) {
            SyncTimingLogger.startSync()
            settings.edit { remove("concatenated_links") }
            listener?.onSyncStarted()
            authenticateAndSync(type)
        }
    }

    private fun destroy() {
        cancelBackgroundSync()
        cancel(context, 111)
        isSyncing = false
        ourInstance = null
        settings.edit { putLong("LastSync", Date().time) }
        listener?.onSyncComplete()
        try {
            if (::mRealm.isInitialized && !mRealm.isClosed) {
                mRealm.close()
                td?.interrupt()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun authenticateAndSync(type: String) {
        SyncTimingLogger.logOperation("authenticate_start")
        td = Thread {
            if (TransactionSyncManager.authenticate()) {
                SyncTimingLogger.logOperation("authenticate_success")
                startSync(type)
            } else {
                SyncTimingLogger.logOperation("authenticate_failed")
                handleException(context.getString(R.string.invalid_configuration))
                cleanupMainSync()
            }
        }
        td?.start()
    }

    private fun startSync(type: String) {
        val isFastSync = settings.getBoolean("fastSync", true)
        SyncTimingLogger.logOperation("sync_type_decision")
        if (!isFastSync || type == "upload") {
            SyncTimingLogger.logOperation("start_full_sync")
            startFullSync()
        } else {
            SyncTimingLogger.logOperation("start_fast_sync")
            startFastSync()
        }
    }

    private fun startFullSync() {
        SyncTimingLogger.logOperation("initialize_sync_start")
        try {
            initializeSync()
            SyncTimingLogger.logOperation("initialize_sync_complete")
            runBlocking {
                val batchSize = 1000
                SyncTimingLogger.logOperation("create_batch_processor")
                val batchProcessor = RealmBatchProcessor(mRealm, batchSize)

                SyncTimingLogger.logOperation("start_parallel_sync_jobs")
                val syncJobs = listOf(
                    async {
                        SyncTimingLogger.logOperation("sync_tablet_users_start")
                        TransactionSyncManager.syncDb(mRealm, "tablet_users", batchProcessor)
                        SyncTimingLogger.logOperation("sync_tablet_users_complete")
                          },
                    async {
                        SyncTimingLogger.logOperation("sync_library_start")
                        myLibraryTransactionSync(batchProcessor = batchProcessor)
                        SyncTimingLogger.logOperation("sync_library_complete")
                          },
                    async {
                        SyncTimingLogger.logOperation("sync_courses_start")
                        TransactionSyncManager.syncDb(mRealm, "courses", batchProcessor)
                        SyncTimingLogger.logOperation("sync_courses_complete")
                          },
                    async {
                        SyncTimingLogger.logOperation("sync_exams_start")
                        TransactionSyncManager.syncDb(mRealm, "exams", batchProcessor)
                        SyncTimingLogger.logOperation("sync_exams_complete") },
                    async {
                        SyncTimingLogger.logOperation("sync_ratings_start")
                        TransactionSyncManager.syncDb(mRealm, "ratings", batchProcessor)
                        SyncTimingLogger.logOperation("sync_ratings_complete") },
                    async {
                        SyncTimingLogger.logOperation("sync_course_progress_start")
                        TransactionSyncManager.syncDb(mRealm, "courses_progress", batchProcessor)
                        SyncTimingLogger.logOperation("sync_course_progress_complete") },
                    async {
                        SyncTimingLogger.logOperation("sync_achievements_start")
                        TransactionSyncManager.syncDb(mRealm, "achievements", batchProcessor)
                        SyncTimingLogger.logOperation("sync_achievements_complete") },
                    async {
                        SyncTimingLogger.logOperation("sync_tags_start")
                        TransactionSyncManager.syncDb(mRealm, "tags", batchProcessor)
                        SyncTimingLogger.logOperation("sync_tags_complete") },
                    async {
                        SyncTimingLogger.logOperation("sync_submissions_start")
                        TransactionSyncManager.syncDb(mRealm, "submissions", batchProcessor)
                        SyncTimingLogger.logOperation("sync_submissions_complete") },
                    async {
                        SyncTimingLogger.logOperation("sync_news_start")
                        TransactionSyncManager.syncDb(mRealm, "news", batchProcessor)
                        SyncTimingLogger.logOperation("sync_news_complete") },
                    async {
                        SyncTimingLogger.logOperation("sync_feedback_start")
                        TransactionSyncManager.syncDb(mRealm, "feedback", batchProcessor)
                        SyncTimingLogger.logOperation("sync_feedback_complete") },
                    async {
                        SyncTimingLogger.logOperation("sync_teams_start")
                        TransactionSyncManager.syncDb(mRealm, "teams", batchProcessor)
                        SyncTimingLogger.logOperation("sync_teams_complete") },
                    async {
                        SyncTimingLogger.logOperation("sync_tasks_start")
                        TransactionSyncManager.syncDb(mRealm, "tasks", batchProcessor)
                        SyncTimingLogger.logOperation("sync_tasks_complete") },
                    async {
                        SyncTimingLogger.logOperation("sync_login_activities_start")
                        TransactionSyncManager.syncDb(mRealm, "login_activities", batchProcessor)
                        SyncTimingLogger.logOperation("sync_login_activities_complete") },
                    async {
                        SyncTimingLogger.logOperation("sync_meetups_start")
                        TransactionSyncManager.syncDb(mRealm, "meetups", batchProcessor)
                        SyncTimingLogger.logOperation("sync_meetups_complete")
                          },
                    async { SyncTimingLogger.logOperation("sync_health_start")
                        TransactionSyncManager.syncDb(mRealm, "health", batchProcessor)
                        SyncTimingLogger.logOperation("sync_health_complete") },
                    async { SyncTimingLogger.logOperation("sync_certifications_start")
                        TransactionSyncManager.syncDb(mRealm, "certifications", batchProcessor)
                        SyncTimingLogger.logOperation("sync_certifications_complete") },
                    async { SyncTimingLogger.logOperation("sync_team_activities_start")
                        TransactionSyncManager.syncDb(mRealm, "team_activities", batchProcessor)
                        SyncTimingLogger.logOperation("sync_team_activities_complete") },
                    async { SyncTimingLogger.logOperation("sync_chat_history_start")
                        TransactionSyncManager.syncDb(mRealm, "chat_history", batchProcessor)
                        SyncTimingLogger.logOperation("sync_chat_history_complete")  }
                )

                syncJobs.awaitAll()
                SyncTimingLogger.logOperation("all_sync_jobs_complete")

                // Flush any remaining batch operations
                SyncTimingLogger.logOperation("flush_batch_processor_start")
                batchProcessor.flushAll()
                SyncTimingLogger.logOperation("flush_batch_processor_complete")
            }

            SyncTimingLogger.logOperation("sync_admin_start")
            ManagerSync.instance?.syncAdmin()
            SyncTimingLogger.logOperation("sync_admin_complete")
            SyncTimingLogger.logOperation("resource_sync_start")
            resourceTransactionSync(batchProcessor = RealmBatchProcessor(mRealm, 1000))
            SyncTimingLogger.logOperation("resource_sync_complete")

            SyncTimingLogger.logOperation("on_synced_start")
            onSynced(mRealm, settings)
            SyncTimingLogger.logOperation("on_synced_complete")

            mRealm.close()
            SyncTimingLogger.logOperation("realm_closed")
        } catch (err: Exception) {
            SyncTimingLogger.logOperation("sync_error_occurred: ${err.message}")
            err.printStackTrace()
            handleException(err.message)
        } finally {
            SyncTimingLogger.logOperation("destroy_start")
            destroy()
            SyncTimingLogger.logOperation("destroy_complete")
        }
    }

    private fun startFastSync() {
        try {
            initializeSync()

            // Create a batch processor for fast sync
            val batchProcessor = RealmBatchProcessor(mRealm, 1000)

            // Sync only essential data in fast sync mode
            TransactionSyncManager.syncDb(mRealm, "tablet_users", batchProcessor)
            ManagerSync.instance?.syncAdmin()

            // Flush any remaining batch operations
            batchProcessor.flushAll()

            settings.edit { putLong("LastSync", Date().time) }
            listener?.onSyncComplete()
            destroy()
            startBackgroundSync()
        } catch (err: Exception) {
            err.printStackTrace()
            handleException(err.message)
            destroy()
        }
    }

    private fun cleanupMainSync() {
        cancel(context, 111)
        isSyncing = false
        if (::mRealm.isInitialized && !mRealm.isClosed) {
            mRealm.close()
            td?.interrupt()
        }
    }

    private fun cleanupBackgroundSync() {
        cancelBackgroundSync()
        ourInstance = null
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

    private fun syncFirstBatch() {
        TransactionSyncManager.syncDb(mRealm, "tablet_users")
        ManagerSync.instance?.syncAdmin()
    }

    private fun startBackgroundSync() {
        _syncState.postValue(true)
        backgroundSync = MainApplication.applicationScope.launch(Dispatchers.IO) {
            try {
                Log.d("SYNC", "Starting parallel background sync...")

                val remainingDatabases = listOf(
                    "teams", "news", "team_activities", "courses", "courses_progress", "exams", "tasks",
                    "chat_history", "ratings", "achievements", "tags", "submissions", "feedback",
                    "meetups", "health", "certifications", "login_activities"
                )

                // Create batch processor for background sync
                val batchProcessor = RealmBatchProcessor(Realm.getDefaultInstance(), 1000)

                // Measure the total sync time
                val totalTime = measureTimeMillis {
                    // Launch sync operations in parallel
                    val syncJobs = remainingDatabases.map { database ->
                        async(Dispatchers.IO) { // Ensure each coroutine runs on IO thread
                            try {
                                // Create a new Realm instance for this coroutine
                                val realmInstance = Realm.getDefaultInstance()
                                val timeTaken = measureTimeMillis {
                                    TransactionSyncManager.syncDb(realmInstance, database, batchProcessor)
                                }
                                Log.d("SYNC", "Sync for $database completed in $timeTaken ms")
                                realmInstance.close() // Close the Realm instance to avoid leaks
                            } catch (e: Exception) {
                                Log.e("SYNC", "Error syncing $database: ${e.message}", e)
                            }
                        }
                    }

                    // Wait for all sync jobs to complete
                    syncJobs.awaitAll()

                    // Flush any remaining batch operations
                    batchProcessor.flushAll()
                }

                Log.d("SYNC", "All database syncs completed in $totalTime ms")

                // Create a new batch processor for library and resource syncs
                val extraBatchProcessor = RealmBatchProcessor(Realm.getDefaultInstance(), 1000)

                // Run additional sync tasks in parallel
                val extraSyncJobs = listOf(
                    async(Dispatchers.IO) {
                        try {
                            val realmInstance = Realm.getDefaultInstance()
                            val timeTaken = measureTimeMillis {
                                myLibraryTransactionSync(realmInstance, extraBatchProcessor)
                            }
                            Log.d("SYNC", "Library sync completed in $timeTaken ms")
                            realmInstance.close()
                        } catch (e: Exception) {
                            Log.e("SYNC", "Error syncing library: ${e.message}", e)
                        }
                    },
                    async(Dispatchers.IO) {
                        try {
                            val realmInstance = Realm.getDefaultInstance()
                            val timeTaken = measureTimeMillis {
                                resourceTransactionSync(realmInstance, extraBatchProcessor)
                            }
                            Log.d("SYNC", "Resource sync completed in $timeTaken ms")
                            realmInstance.close()
                        } catch (e: Exception) {
                            Log.e("SYNC", "Error syncing resources: ${e.message}", e)
                        }
                    }
                )

                extraSyncJobs.awaitAll()

                // Flush any remaining operations in the extra batch processor
                extraBatchProcessor.flushAll()

                // Final sync completion
                val finalRealm = Realm.getDefaultInstance()
                onSynced(finalRealm, settings)
                finalRealm.close()

            } catch (e: Exception) {
                Log.e("SYNC", "Error during background sync: ${e.message}", e)
            } finally {
                _syncState.postValue(false)
                cleanupBackgroundSync()
                Log.d("SYNC", "Background sync completed.")
            }
        }
    }

    fun cancelBackgroundSync() {
        backgroundSync?.cancel()
        backgroundSync = null
    }

    private fun handleException(message: String?) {
        if (listener != null) {
            isSyncing = false
            MainApplication.syncFailedCount++
            listener?.onSyncFailed(message)
        }
    }

    private fun resourceTransactionSync(backgroundRealm: Realm? = null, batchProcessor: RealmBatchProcessor? = null) {
        val apiInterface = client?.create(ApiInterface::class.java)
        try {
            if (backgroundRealm != null) {
                syncResource(apiInterface, backgroundRealm, batchProcessor)
            } else {
                syncResource(apiInterface, batchProcessor = batchProcessor)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    @Throws(IOException::class)
    private fun syncResource(dbClient: ApiInterface?, backgroundRealm: Realm? = null, batchProcessor: RealmBatchProcessor? = null) {
        val realmInstance = backgroundRealm ?: mRealm
        val newIds: MutableList<String?> = ArrayList()
        val allDocs = dbClient?.getJsonObject(Utilities.header, "${Utilities.getUrl()}/resources/_all_docs?include_doc=false")
        val all = allDocs?.execute()
        val rows = getJsonArray("rows", all?.body())
        val keys: MutableList<String> = ArrayList()

        for (i in 0 until rows.size()) {
            val `object` = rows[i].asJsonObject
            if (!TextUtils.isEmpty(getString("id", `object`))) keys.add(getString("key", `object`))
            if (i == rows.size() - 1 || keys.size == 1000) {
                val obj = JsonObject()
                obj.add("keys", Gson().fromJson(Gson().toJson(keys), JsonArray::class.java))
                val response = dbClient?.findDocs(Utilities.header, "application/json", "${Utilities.getUrl()}/resources/_all_docs?include_docs=true", obj)?.execute()
                if (response?.body() != null) {
                    if (batchProcessor != null) {
                        // Process resources in batches for improved performance
                        val rowsArray = getJsonArray("rows", response.body())
                        val resourceIds = processBatchResources(rowsArray, realmInstance, batchProcessor)
                        newIds.addAll(resourceIds)
                    } else {
                        // Make sure save() handles its own transaction
                        val ids: List<String?> = save(getJsonArray("rows", response.body()), realmInstance)
                        newIds.addAll(ids)
                    }
                }
                keys.clear()
            }
        }

        // Handle removing deleted resources separately, with proper transaction management
        removeDeletedResource(newIds, realmInstance)
    }

    private fun processBatchResources(rows: JsonArray, realm: Realm, batchProcessor: RealmBatchProcessor): List<String?> {
        val resourceIds = mutableListOf<String?>()

        for (i in 0 until rows.size()) {
            val rowObj = rows[i].asJsonObject
            val document = JsonUtils.getJsonObject("doc", rowObj)
            val id = getString("_id", document)

            if (!id.startsWith("_design")) {
                resourceIds.add(id)
                batchProcessor.addToBatch("resources", document)
            }
        }

        return resourceIds
    }

    private fun myLibraryTransactionSync(backgroundRealm: Realm? = null, batchProcessor: RealmBatchProcessor? = null) {
        val apiInterface = client?.create(ApiInterface::class.java)
        try {
            val res = apiInterface?.getDocuments(
                Utilities.header,
                "${Utilities.getUrl()}/shelf/_all_docs"
            )?.execute()?.body()
            for (i in res?.rows!!.indices) {
                shelfDoc = res.rows!![i]
                if (backgroundRealm != null) {
                    populateShelfItems(apiInterface, backgroundRealm, batchProcessor)
                } else {
                    populateShelfItems(apiInterface, batchProcessor = batchProcessor)
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun populateShelfItems(apiInterface: ApiInterface, backgroundRealm: Realm? = null, batchProcessor: RealmBatchProcessor? = null) {
        try {
            val jsonDoc = apiInterface.getJsonObject(Utilities.header, "${Utilities.getUrl()}/shelf/${shelfDoc?.id}").execute().body()
            for (i in Constants.shelfDataList.indices) {
                val shelfData = Constants.shelfDataList[i]
                val array = getJsonArray(shelfData.key, jsonDoc)
                if (backgroundRealm != null) {
                    memberShelfData(array, shelfData, backgroundRealm, batchProcessor)
                } else {
                    memberShelfData(array, shelfData, batchProcessor = batchProcessor)
                }
            }
        } catch (err: Exception) {
            err.printStackTrace()
        }
    }

    private fun memberShelfData(array: JsonArray, shelfData: ShelfData, backgroundRealm: Realm? = null, batchProcessor: RealmBatchProcessor? = null) {
        if (array.size() > 0) {
            triggerInsert(shelfData.categoryKey, shelfData.type)
            if (backgroundRealm != null) {
                check(array, backgroundRealm, batchProcessor)
            } else {
                check(array, batchProcessor = batchProcessor)
            }
        }
    }

    private fun triggerInsert(categoryId: String, categoryDBName: String) {
        stringArray[0] = shelfDoc?.id
        stringArray[1] = categoryId
        stringArray[2] = categoryDBName
    }

    private fun check(arrayCategoryIds: JsonArray, backgroundRealm: Realm? = null, batchProcessor: RealmBatchProcessor? = null) {
        for (x in 0 until arrayCategoryIds.size()) {
            if (arrayCategoryIds[x] is JsonNull) {
                continue
            }
            if (backgroundRealm != null) {
                validateDocument(arrayCategoryIds, x, backgroundRealm, batchProcessor)
            } else {
                validateDocument(arrayCategoryIds, x, batchProcessor = batchProcessor)
            }
        }
    }

    private fun validateDocument(arrayCategoryIds: JsonArray, x: Int, backgroundRealm: Realm? = null, batchProcessor: RealmBatchProcessor? = null) {
        val apiInterface = client?.create(ApiInterface::class.java)
        try {
            val resourceDoc = apiInterface?.getJsonObject(Utilities.header,  "${Utilities.getUrl()}/${stringArray[2]}/${arrayCategoryIds[x].asString}")?.execute()?.body()
            if (backgroundRealm != null) {
                resourceDoc?.let { triggerInsert(stringArray, it, backgroundRealm, batchProcessor) }
            } else {
                resourceDoc?.let { triggerInsert(stringArray, it, batchProcessor = batchProcessor) }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun triggerInsert(stringArray: Array<String?>, resourceDoc: JsonObject, backgroundRealm: Realm? = null, batchProcessor: RealmBatchProcessor? = null) {
        val realm = backgroundRealm ?: mRealm

        when (stringArray[2]) {
            "resources" ->
                if (batchProcessor != null) {
                    batchProcessor.addToBatch("resources", resourceDoc)
                } else {
                    RealmTransactionManager.executeInTransaction(realm) { r ->
                        insertMyLibrary(stringArray[0], resourceDoc, r)
                    }
                }

            "meetups" ->
                if (batchProcessor != null) {
                    batchProcessor.addToBatch("meetups", resourceDoc)
                } else {
                    RealmTransactionManager.executeInTransaction(realm) { r ->
                        insert(r, resourceDoc)
                    }
                }

            "courses" -> {
                if (batchProcessor != null) {
                    batchProcessor.addToBatch("courses", resourceDoc)
                } else {
                    RealmTransactionManager.executeInTransaction(realm) { r ->
                        insertMyCourses(stringArray[0], resourceDoc, r)
                    }
                }
            }

            "teams" ->
                if (batchProcessor != null) {
                    batchProcessor.addToBatch("teams", resourceDoc)
                } else {
                    RealmTransactionManager.executeInTransaction(realm) { r ->
                        insertMyTeams(resourceDoc, r)
                    }
                }
        }
        saveConcatenatedLinksToPrefs()
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
