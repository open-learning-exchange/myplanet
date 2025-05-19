package org.ole.planet.myplanet.service

import android.content.Context
import android.content.SharedPreferences
import android.net.wifi.SupplicantState
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.MutableLiveData
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import kotlinx.coroutines.*
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.SyncListener
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
import org.ole.planet.myplanet.utilities.Constants
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
import org.ole.planet.myplanet.utilities.JsonUtils.getJsonArray
import org.ole.planet.myplanet.utilities.JsonUtils.getString
import org.ole.planet.myplanet.utilities.NotificationUtil.cancel
import org.ole.planet.myplanet.utilities.NotificationUtil.create
import org.ole.planet.myplanet.utilities.Utilities
import java.util.Date
import kotlin.system.measureTimeMillis
import androidx.core.content.edit
import io.realm.Realm
import org.ole.planet.myplanet.datamanager.ApiClient
import org.ole.planet.myplanet.model.DocumentResponse
import org.ole.planet.myplanet.utilities.JsonUtils.getJsonObject

class SyncManager private constructor(private val context: Context) {
    private var td: Thread? = null
    private val settings: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var isSyncing = false
    private val stringArray = arrayOfNulls<String>(4)
    private var listener: SyncListener? = null
    private val dbService: DatabaseService = DatabaseService(context)
    private var backgroundSync: Job? = null
    val _syncState = MutableLiveData<Boolean>()

    fun start(listener: SyncListener?, type: String) {
        this.listener = listener
        if (!isSyncing) {
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
    }

    private fun authenticateAndSync(type: String) {
        td = Thread {
            if (TransactionSyncManager.authenticate()) {
                startSync(type)
            } else {
                handleException(context.getString(R.string.invalid_configuration))
                cleanupMainSync()
            }
        }
        td?.start()
    }

    private fun startSync(type: String) {
        val isFastSync = settings.getBoolean("fastSync", false)
        if (!isFastSync || type == "upload") {
            startFullSync()
        } else {
            startFastSync()
        }
    }

    private fun startFullSync() {
        try {
            initializeSync()
            runBlocking {
                val syncJobs = listOf(
                    async {
                        val realm = Realm.getDefaultInstance()
                        try {
                            TransactionSyncManager.syncDb(realm, "tablet_users")
                        } finally {
                            realm.close()
                        }
                    },
                    async {
                        val realm = Realm.getDefaultInstance()
                        try {
                            myLibraryTransactionSync(realm)
                        } finally {
                            realm.close()
                        }
                    },
                    async {
                        val realm = Realm.getDefaultInstance()
                        try {
                            TransactionSyncManager.syncDb(realm, "courses")
                        } finally {
                            realm.close()
                        }
                    },
                    async {
                        val realm = Realm.getDefaultInstance()
                        try {
                            TransactionSyncManager.syncDb(realm, "exams")
                        } finally {
                            realm.close()
                        }
                    },
                    async {
                        val realm = Realm.getDefaultInstance()
                        try {
                            TransactionSyncManager.syncDb(realm, "ratings")
                        } finally {
                            realm.close()
                        }
                    },
                    async {
                        val realm = Realm.getDefaultInstance()
                        try {
                            TransactionSyncManager.syncDb(realm, "courses_progress")
                        } finally {
                            realm.close()
                        }
                    },
                    async {
                        val realm = Realm.getDefaultInstance()
                        try {
                            TransactionSyncManager.syncDb(realm, "achievements")
                        } finally {
                            realm.close()
                        }
                    },
                    async {
                        val realm = Realm.getDefaultInstance()
                        try {
                            TransactionSyncManager.syncDb(realm, "tags")
                        } finally {
                            realm.close()
                        }
                    },
                    async {
                        val realm = Realm.getDefaultInstance()
                        try {
                            TransactionSyncManager.syncDb(realm, "submissions")
                        } finally {
                            realm.close()
                        }
                    },
                    async {
                        val realm = Realm.getDefaultInstance()
                        try {
                            TransactionSyncManager.syncDb(realm, "news")
                        } finally {
                            realm.close()
                        }
                    },
                    async {
                        val realm = Realm.getDefaultInstance()
                        try {
                            TransactionSyncManager.syncDb(realm, "feedback")
                        } finally {
                            realm.close()
                        }
                    },
                    async {
                        val realm = Realm.getDefaultInstance()
                        try {
                            TransactionSyncManager.syncDb(realm, "teams")
                        } finally {
                            realm.close()
                        }
                    },
                    async {
                        val realm = Realm.getDefaultInstance()
                        try {
                            TransactionSyncManager.syncDb(realm, "tasks")
                        } finally {
                            realm.close()
                        }
                    },
                    async {
                        val realm = Realm.getDefaultInstance()
                        try {
                            TransactionSyncManager.syncDb(realm, "login_activities")
                        } finally {
                            realm.close()
                        }
                    },
                    async {
                        val realm = Realm.getDefaultInstance()
                        try {
                            TransactionSyncManager.syncDb(realm, "meetups")
                        } finally {
                            realm.close()
                        }
                    },
                    async {
                        val realm = Realm.getDefaultInstance()
                        try {
                            TransactionSyncManager.syncDb(realm, "health")
                        } finally {
                            realm.close()
                        }
                    },
                    async {
                        val realm = Realm.getDefaultInstance()
                        try {
                            TransactionSyncManager.syncDb(realm, "certifications")
                        } finally {
                            realm.close()
                        }
                    },
                    async {
                        val realm = Realm.getDefaultInstance()
                        try {
                            TransactionSyncManager.syncDb(realm, "team_activities")
                        } finally {
                            realm.close()
                        }
                    },
                    async {
                        val realm = Realm.getDefaultInstance()
                        try {
                            TransactionSyncManager.syncDb(realm, "chat_history")
                        } finally {
                            realm.close()
                        }
                    }
                )
                syncJobs.awaitAll()
            }


            ManagerSync.instance?.syncAdmin()
            val realm = Realm.getDefaultInstance()
            try {
                resourceTransactionSync(realm)
            } finally {
                realm.close()
            }
            val srealm = Realm.getDefaultInstance()
            try {
                onSynced(srealm, settings)
            } finally {
                srealm.close()
            }
        } catch (err: Exception) {
            err.printStackTrace()
            handleException(err.message)
        } finally {
            destroy()
        }
    }

    private fun startFastSync() {
        try {
            initializeSync()
            syncFirstBatch()

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
    }

    private fun syncFirstBatch() {
        val fRealm = Realm.getDefaultInstance()
        TransactionSyncManager.syncDb(fRealm, "tablet_users")
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

                // Measure the total sync time
                val totalTime = measureTimeMillis {
                    // Launch sync operations in parallel
                    val syncJobs = remainingDatabases.map { database ->
                        async(Dispatchers.IO) { // Ensure each coroutine runs on IO thread
                            try {
                                // Create a new Realm instance for this coroutine
                                val realmInstance = Realm.getDefaultInstance()
                                val timeTaken = measureTimeMillis {
                                    TransactionSyncManager.syncDb(realmInstance, database)
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
                }

                Log.d("SYNC", "All database syncs completed in $totalTime ms")

                // Run additional sync tasks in parallel
                val extraSyncJobs = listOf(
                    async(Dispatchers.IO) {
                        try {
                            val realmInstance = Realm.getDefaultInstance()
                            val timeTaken = measureTimeMillis {
                                myLibraryTransactionSync(realmInstance)
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
                                resourceTransactionSync(realmInstance)
                            }
                            Log.d("SYNC", "Resource sync completed in $timeTaken ms")
                            realmInstance.close()
                        } catch (e: Exception) {
                            Log.e("SYNC", "Error syncing resources: ${e.message}", e)
                        }
                    }
                )

                extraSyncJobs.awaitAll()

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

    private fun resourceTransactionSync(backgroundRealm: Realm? = null) {
        var processedItems = 0

        try {
            val apiInterface = ApiClient.getEnhancedClient()
            val realmInstance = backgroundRealm ?: Realm.getDefaultInstance()
            val newIds: MutableList<String?> = ArrayList()

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

            val batchSize = 200
            var skip = 0

            while (skip < totalRows || (totalRows == 0 && skip == 0)) {
                try {
                    var response: JsonObject? = null
                    ApiClient.executeWithRetry {
                        apiInterface.getJsonObject(Utilities.header, "${Utilities.getUrl()}/resources/_all_docs?include_docs=true&limit=$batchSize&skip=$skip").execute()
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

                    for (i in 0 until rows.size()) {
                        val rowObj = rows[i].asJsonObject
                        if (rowObj.has("doc")) {
                            val doc = getJsonObject("doc", rowObj)
                            val id = getString("_id", doc)

                            if (!id.startsWith("_design")) {
                                try {
                                    realmInstance.beginTransaction()
                                    val singleDocArray = JsonArray()
                                    singleDocArray.add(doc)

                                    val ids = save(singleDocArray, realmInstance)
                                    if (ids.isNotEmpty()) {
                                        newIds.addAll(ids)
                                        processedItems++
                                    }

                                    if (realmInstance.isInTransaction) {
                                        realmInstance.commitTransaction()
                                    }
                                } catch (e: Exception) {
                                    if (realmInstance.isInTransaction) {
                                        realmInstance.cancelTransaction()
                                    }
                                }
                            }
                        }
                    }

                    skip += rows.size()

                    val settings = MainApplication.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    settings.edit {
                        putLong("ResourceLastSyncTime", System.currentTimeMillis())
                        putInt("ResourceSyncPosition", skip)
                    }

                } catch (e: Exception) {
                    skip += batchSize
                }
            }

            try {
                realmInstance.beginTransaction()
                removeDeletedResource(newIds, realmInstance)

                if (realmInstance.isInTransaction) {
                    realmInstance.commitTransaction()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                if (realmInstance.isInTransaction) {
                    realmInstance.cancelTransaction()
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun myLibraryTransactionSync(backgroundRealm: Realm) {
        val Lrealm = backgroundRealm
        var processedItems = 0

        try {
            val apiInterface = ApiClient.getEnhancedClient()
            var toInsert = mutableListOf<Triple<String, String, JsonObject>>()

            var shelfResponse: DocumentResponse? = null
            ApiClient.executeWithRetry {
                apiInterface.getDocuments(Utilities.header, "${Utilities.getUrl()}/shelf/_all_docs?include_docs=true").execute()
            }?.let {
                shelfResponse = it.body()
            }

            if (shelfResponse?.rows == null || shelfResponse.rows?.isEmpty() == true) {
                return
            }

            for (row in shelfResponse.rows) {
                val shelfId = row.id ?: continue


                var shelfDoc: JsonObject? = null
                ApiClient.executeWithRetry {
                    apiInterface.getJsonObject(Utilities.header, "${Utilities.getUrl()}/shelf/$shelfId").execute()
                }?.let {
                    shelfDoc = it.body()
                }

                if (shelfDoc == null) continue

                for (shelfData in Constants.shelfDataList) {
                    val array = getJsonArray(shelfData.key, shelfDoc)
                    if (array.size() == 0) continue

                    stringArray[0] = shelfId
                    stringArray[1] = shelfData.categoryKey
                    stringArray[2] = shelfData.type

                    val validIds = mutableListOf<String>()
                    for (i in 0 until array.size()) {
                        if (array[i] !is JsonNull) {
                            validIds.add(array[i].asString)
                        }
                    }

                    if (validIds.isEmpty()) continue
                    val batchSize = 50

                    for (i in 0 until validIds.size step batchSize) {
                        val end = minOf(i + batchSize, validIds.size)
                        val batch = validIds.subList(i, end)
                        if (batch.isEmpty()) continue

                        try {
                            val keysObject = JsonObject()
                            keysObject.add("keys", Gson().fromJson(Gson().toJson(batch), JsonArray::class.java))

                            var response: JsonObject? = null
                            ApiClient.executeWithRetry {
                                apiInterface.findDocs(Utilities.header, "application/json", "${Utilities.getUrl()}/${shelfData.type}/_all_docs?include_docs=true", keysObject).execute()
                            }?.let {
                                response = it.body()
                            }

                            if (response == null) continue

                            val rows = getJsonArray("rows", response)
                            for (rowObj in rows) {
                                val docEl = rowObj.asJsonObject
                                if (!docEl.has("doc")) continue
                                val doc = getJsonObject("doc", docEl)
                                if (doc.entrySet().isEmpty()) continue

                                toInsert += Triple(shelfData.type, shelfId, doc)
                            }

                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }

            Lrealm.executeTransaction { bgRealm ->
                for ((i, entry) in toInsert.withIndex()) {
                    val (type, shelfId, doc) = entry
                    try {
                        println(shelfId)
                        when (type) {
                            "resources" -> insertMyLibrary(shelfId, doc, bgRealm)
                            "meetups"   -> insert(bgRealm, doc)
                            "courses"   -> insertMyCourses(shelfId, doc, bgRealm)
                            "teams"     -> insertMyTeams(doc, bgRealm)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            saveConcatenatedLinksToPrefs()

        } catch (e: Exception) {
            e.printStackTrace()
        }
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
