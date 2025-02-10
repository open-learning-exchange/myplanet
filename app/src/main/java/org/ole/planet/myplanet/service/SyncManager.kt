package org.ole.planet.myplanet.service

import android.content.Context
import android.content.SharedPreferences
import android.net.wifi.SupplicantState
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
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
    private val TAG = "SyncManager"
    val _syncState = MutableLiveData<Boolean>()

    fun start(listener: SyncListener?) {
        this.listener = listener
        if (!isSyncing) {
            Log.d(TAG, "Starting sync process")
            settings.edit().remove("concatenated_links").apply()
            listener?.onSyncStarted()
            authenticateAndSync()
        } else {
            Log.d(TAG, "Sync already in progress")
        }
    }

    private fun destroy() {
        Log.d(TAG, "Cleaning up sync process")
        cancelBackgroundSync() // Add this line
        cancel(context, 111)
        isSyncing = false
        ourInstance = null
        settings.edit().putLong("LastSync", Date().time).apply()
        listener?.onSyncComplete()
        try {
            if (::mRealm.isInitialized && !mRealm.isClosed) {
                mRealm.close()
                td?.interrupt()
                Log.d(TAG, "Closed Realm instance and interrupted sync thread")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun authenticateAndSync() {
        Log.d(TAG, "Starting authentication process")
        td = Thread {
            if (TransactionSyncManager.authenticate()) {
                Log.d(TAG, "Authentication successful")
                startSync()
            } else {
                Log.e(TAG, "Authentication failed")
                handleException(context.getString(R.string.invalid_configuration))
                cleanupMainSync()
            }
        }
        td?.start()
    }

    private fun startSync() {
        val isFastSync = settings.getBoolean("fastSync", false)
        if (isFastSync) {
            Log.d(TAG, "Fast sync enabled")
            startFastSync()
        } else {
            Log.d(TAG, "Fast sync disabled")
            startFullSync()
        }
    }

    private fun startFullSync() {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val wifiInfo = wifiManager.connectionInfo
            if (wifiInfo.supplicantState == SupplicantState.COMPLETED) {
                settings.edit().putString("LastWifiSSID", wifiInfo.ssid).apply()
            }
            isSyncing = true
            create(context, R.mipmap.ic_launcher, "Syncing data", "Please wait...")
            mRealm = dbService.realmInstance
            TransactionSyncManager.syncDb(mRealm, "tablet_users")
            myLibraryTransactionSync()
            TransactionSyncManager.syncDb(mRealm, "courses")
            TransactionSyncManager.syncDb(mRealm, "exams")
            TransactionSyncManager.syncDb(mRealm, "ratings")
            TransactionSyncManager.syncDb(mRealm, "courses_progress")
            TransactionSyncManager.syncDb(mRealm, "achievements")
            TransactionSyncManager.syncDb(mRealm, "tags")
            TransactionSyncManager.syncDb(mRealm, "submissions")
            TransactionSyncManager.syncDb(mRealm, "news")
            TransactionSyncManager.syncDb(mRealm, "feedback")
            TransactionSyncManager.syncDb(mRealm, "teams")
            TransactionSyncManager.syncDb(mRealm, "tasks")
            TransactionSyncManager.syncDb(mRealm, "login_activities")
            TransactionSyncManager.syncDb(mRealm, "meetups")
            TransactionSyncManager.syncDb(mRealm, "health")
            TransactionSyncManager.syncDb(mRealm, "certifications")
            TransactionSyncManager.syncDb(mRealm, "team_activities")
            TransactionSyncManager.syncDb(mRealm, "chat_history")
            ManagerSync.instance?.syncAdmin()
            resourceTransactionSync()
            onSynced(mRealm, settings)
            mRealm.close()
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
            Log.d(TAG, "Starting priority sync batch")
            syncFirstBatch()
            Log.d(TAG, "Priority sync batch completed")

            // Notify completion of priority sync
            settings.edit().putLong("LastSync", Date().time).apply()
            listener?.onSyncComplete()
            destroy()

            // Start background sync for remaining databases
            Log.d(TAG, "Initiating background sync for remaining databases")
            startBackgroundSync()
        } catch (err: Exception) {
            Log.e(TAG, "Error during sync: ${err.message}")
            err.printStackTrace()
            handleException(err.message)
//            cleanupMainSync()
            destroy()
        }
    }

    private fun cleanupMainSync() {
        Log.d(TAG, "Cleaning up main sync process")
        cancel(context, 111)
        isSyncing = false
        if (::mRealm.isInitialized && !mRealm.isClosed) {
            mRealm.close()
            td?.interrupt()
            Log.d(TAG, "Closed main Realm instance and interrupted sync thread")
        }
    }

    private fun cleanupBackgroundSync() {
        Log.d(TAG, "Cleaning up background sync")
        cancelBackgroundSync()
        ourInstance = null
    }


    private fun initializeSync() {
        Log.d(TAG, "Initializing sync process")
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val wifiInfo = wifiManager.connectionInfo
        if (wifiInfo.supplicantState == SupplicantState.COMPLETED) {
            settings.edit().putString("LastWifiSSID", wifiInfo.ssid).apply()
            Log.d(TAG, "Connected to WiFi SSID: ${wifiInfo.ssid}")
        }
        isSyncing = true
        create(context, R.mipmap.ic_launcher, "Syncing data", "Please wait...")
        mRealm = dbService.realmInstance
    }

    private fun syncFirstBatch() {
        Log.i(TAG, "=== Starting Priority Sync Batch ===")

        Log.d(TAG, "Syncing tablet_users")
        TransactionSyncManager.syncDb(mRealm, "tablet_users")

        Log.d(TAG, "Syncing admin data")
        ManagerSync.instance?.syncAdmin()

        Log.i(TAG, "=== Priority Sync Batch Completed ===")
    }

    private fun startBackgroundSync() {
        _syncState.postValue(true)
        Log.i(TAG, "=== Starting Background Sync ===")
        backgroundSync = MainApplication.applicationScope.launch(Dispatchers.IO) {
            val backgroundRealm = Realm.getDefaultInstance()

            try {
                // Regular database syncs
                val remainingDatabases = listOf(
                    "teams", "news", "team_activities", "courses", "courses_progress", "exams", "tasks",
                    "chat_history", "ratings", "achievements", "tags", "submissions", "feedback",
                     "meetups", "health", "certifications", "login_activities"
                )

                var completedCount = 0
                val totalDatabases = remainingDatabases.size

                remainingDatabases.forEach { database ->
                    try {
                        Log.d(TAG, "Background sync: Starting $database (${++completedCount}/$totalDatabases)")
                        val startTime = System.currentTimeMillis()

                        val databaseData = TransactionSyncManager.syncDb(backgroundRealm, database)
                        logDataSizeInBytes(database, databaseData)

                        val duration = System.currentTimeMillis() - startTime
                        Log.d(TAG, "Background sync: Completed $database in ${duration}ms")
                    } catch (e: Exception) {
                        Log.e(TAG, "Background sync: Failed to sync $database: ${e.message}")
                        e.printStackTrace()
                    }
                }

//                // Special sync functions with background realm
//                try {
//                    Log.d(TAG, "Background sync: Syncing admin data")
//                    val startTime = System.currentTimeMillis()
//                    ManagerSync.instance?.syncAdmin()
//                    val duration = System.currentTimeMillis() - startTime
//                    Log.d(TAG, "Background sync: Completed admin data in ${duration}ms")
//                } catch (e: Exception) {
//                    Log.e(TAG, "Background sync: Failed to sync admin data: ${e.message}")
//                    e.printStackTrace()
//                }

                try {
                    Log.d(TAG, "Background sync: Starting myLibrary sync")
                    val startTime = System.currentTimeMillis()
                    myLibraryTransactionSync(backgroundRealm)
                    val duration = System.currentTimeMillis() - startTime
                    Log.d(TAG, "Background sync: Completed myLibrary sync in ${duration}ms")
                } catch (e: Exception) {
                    Log.e(TAG, "Background sync: Failed to sync myLibrary: ${e.message}")
                    e.printStackTrace()
                }

                try {
                    Log.d(TAG, "Background sync: Starting resource sync")
                    val startTime = System.currentTimeMillis()
                    resourceTransactionSync(backgroundRealm)
                    val duration = System.currentTimeMillis() - startTime
                    Log.d(TAG, "Background sync: Completed resource sync in ${duration}ms")
                } catch (e: Exception) {
                    Log.e(TAG, "Background sync: Failed to sync resources: ${e.message}")
                    e.printStackTrace()
                }
                onSynced(mRealm, settings)

                Log.i(TAG, "=== Background Sync Completed ===")
                Log.d(TAG, "Successfully synced $completedCount out of $totalDatabases databases")

            } catch (e: Exception) {
                Log.e(TAG, "Error in background sync: ${e.message}")
                e.printStackTrace()
            } finally {
                backgroundRealm.close()
                Log.d(TAG, "Closed background Realm instance")
                _syncState.postValue(false)
                cleanupBackgroundSync()
            }
        }
    }

    fun cancelBackgroundSync() {
        Log.d(TAG, "Cancelling background sync")
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
        val apiInterface = client?.create(ApiInterface::class.java)
        try {
            if (backgroundRealm != null) {
                syncResource(apiInterface, backgroundRealm)
            } else {
                syncResource(apiInterface)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    @Throws(IOException::class)
    private fun syncResource(dbClient: ApiInterface?, backgroundRealm: Realm? = null) {
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
                    val ids: List<String?> = save(getJsonArray("rows", response.body()), realmInstance)
                    newIds.addAll(ids)
                }
                keys.clear()
            }
        }
        removeDeletedResource(newIds, realmInstance)
    }

    private fun myLibraryTransactionSync(backgroundRealm: Realm? = null) {
        val apiInterface = client?.create(ApiInterface::class.java)
        try {
            val res = apiInterface?.getDocuments(Utilities.header, "${Utilities.getUrl()}/shelf/_all_docs")?.execute()?.body()
            for (i in res?.rows!!.indices) {
                shelfDoc = res.rows!![i]
                if (backgroundRealm != null) {
                    populateShelfItems(apiInterface, backgroundRealm)
                } else {
                    populateShelfItems(apiInterface)
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun populateShelfItems(apiInterface: ApiInterface, backgroundRealm: Realm? = null) {
        try {
            val jsonDoc = apiInterface.getJsonObject(Utilities.header, "${Utilities.getUrl()}/shelf/${shelfDoc?.id}").execute().body()
            for (i in Constants.shelfDataList.indices) {
                val shelfData = Constants.shelfDataList[i]
                val array = getJsonArray(shelfData.key, jsonDoc)
                if (backgroundRealm != null) {
                    memberShelfData(array, shelfData, backgroundRealm)
                } else {
                    memberShelfData(array, shelfData)
                }
            }
        } catch (err: Exception) {
            err.printStackTrace()
        }
    }

    private fun memberShelfData(array: JsonArray, shelfData: ShelfData, backgroundRealm: Realm? = null) {
        if (array.size() > 0) {
            triggerInsert(shelfData.categoryKey, shelfData.type)
            if (backgroundRealm != null) {
                check(array, backgroundRealm)
            } else {
                check(array)
            }
        }
    }

    private fun triggerInsert(categoryId: String, categoryDBName: String) {
        stringArray[0] = shelfDoc?.id
        stringArray[1] = categoryId
        stringArray[2] = categoryDBName
    }

    private fun check(arrayCategoryIds: JsonArray, backgroundRealm: Realm? = null) {
        for (x in 0 until arrayCategoryIds.size()) {
            if (arrayCategoryIds[x] is JsonNull) {
                continue
            }
            if (backgroundRealm != null) {
                validateDocument(arrayCategoryIds, x, backgroundRealm)
            } else {
                validateDocument(arrayCategoryIds, x)
            }
        }
    }

    private fun validateDocument(arrayCategoryIds: JsonArray, x: Int, backgroundRealm: Realm? = null) {
        val apiInterface = client?.create(ApiInterface::class.java)
        try {
            val resourceDoc = apiInterface?.getJsonObject(Utilities.header,  "${Utilities.getUrl()}/${stringArray[2]}/${arrayCategoryIds[x].asString}")?.execute()?.body()
            if (backgroundRealm != null) {
                resourceDoc?.let { triggerInsert(stringArray, it, backgroundRealm)}
            } else {
                resourceDoc?.let { triggerInsert(stringArray, it) }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun triggerInsert(stringArray: Array<String?>, resourceDoc: JsonObject, backgroundRealm: Realm? = null) {
        when (stringArray[2]) {
            "resources" ->
                if (backgroundRealm != null) {
                    insertMyLibrary(stringArray[0], resourceDoc, backgroundRealm)
                } else {
                    insertMyLibrary(stringArray[0], resourceDoc, mRealm)
                }

            "meetups" ->
                if (backgroundRealm != null) {
                    insert(backgroundRealm, resourceDoc)
                } else {
                    insert(mRealm, resourceDoc)
                }

            "courses" -> {
                if (backgroundRealm != null) {
                    if (!backgroundRealm.isInTransaction) {
                        backgroundRealm.beginTransaction()
                    }
                    insertMyCourses(stringArray[0], resourceDoc, backgroundRealm)
                    if (backgroundRealm.isInTransaction) {
                        backgroundRealm.commitTransaction()
                    }
                } else {
                    if (!mRealm.isInTransaction) {
                        mRealm.beginTransaction()
                    }
                    insertMyCourses(stringArray[0], resourceDoc, mRealm)
                    if (mRealm.isInTransaction) {
                        mRealm.commitTransaction()
                    }
                }
            }

            "teams" ->
                if (backgroundRealm != null) {
                    insertMyTeams(resourceDoc, backgroundRealm)
                } else {
                    insertMyTeams(resourceDoc, mRealm)
                }
        }
        saveConcatenatedLinksToPrefs()
    }

    private fun logDataSizeInBytes(tag: String, data: Any?) {
        if (data != null) {
            val jsonString = Gson().toJson(data) // Convert data to JSON string
            val byteSize = jsonString.toByteArray().size // Calculate the size in bytes
            Log.d(TAG, "$tag size: $byteSize bytes (${byteSize / 1024} KB)")
        } else {
            Log.d(TAG, "$tag size: 0 bytes")
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
