package org.ole.planet.myplanet.service

import android.content.Context
import android.content.SharedPreferences
import android.net.wifi.SupplicantState
import android.net.wifi.WifiManager
import android.text.TextUtils
import android.util.Log
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

//    private fun startSync() {
//        try {
//            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
//            val wifiInfo = wifiManager.connectionInfo
//            if (wifiInfo.supplicantState == SupplicantState.COMPLETED) {
//                settings.edit().putString("LastWifiSSID", wifiInfo.ssid).apply()
//            }
//            isSyncing = true
//            create(context, R.mipmap.ic_launcher, " Syncing data", "Please wait...")
//            mRealm = dbService.realmInstance
//            TransactionSyncManager.syncDb(mRealm, "tablet_users")
//            myLibraryTransactionSync()
//            ManagerSync.instance?.syncAdmin()
//            resourceTransactionSync()
//            TransactionSyncManager.syncDb(mRealm, "courses")
//
//            TransactionSyncManager.syncDb(mRealm, "exams")
//            TransactionSyncManager.syncDb(mRealm, "ratings")
//            TransactionSyncManager.syncDb(mRealm, "courses_progress")
//            TransactionSyncManager.syncDb(mRealm, "achievements")
//            TransactionSyncManager.syncDb(mRealm, "tags")
//            TransactionSyncManager.syncDb(mRealm, "submissions")
//            TransactionSyncManager.syncDb(mRealm, "news")
//            TransactionSyncManager.syncDb(mRealm, "feedback")
//            TransactionSyncManager.syncDb(mRealm, "teams")
//            TransactionSyncManager.syncDb(mRealm, "tasks")
//            TransactionSyncManager.syncDb(mRealm, "login_activities")
//            TransactionSyncManager.syncDb(mRealm, "meetups")
//            TransactionSyncManager.syncDb(mRealm, "health")
//            TransactionSyncManager.syncDb(mRealm, "certifications")
//            TransactionSyncManager.syncDb(mRealm, "team_activities")
//            TransactionSyncManager.syncDb(mRealm, "chat_history")
//
//            onSynced(mRealm, settings)
//            mRealm.close()
//        } catch (err: Exception) {
//            err.printStackTrace()
//            handleException(err.message)
//        } finally {
//            destroy()
//        }
//    }

    private fun startSync() {
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
        Log.i(TAG, "=== Starting Background Sync ===")
        backgroundSync = MainApplication.applicationScope.launch(Dispatchers.IO) {
            val backgroundRealm = Realm.getDefaultInstance()
            try {
                // Special sync functions with background realm
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

                // Regular database syncs
                val remainingDatabases = listOf(
                    "courses", "exams", "ratings", "courses_progress",
                    "achievements", "tags", "submissions", "news", "feedback",
                    "teams", "tasks", "login_activities", "meetups", "health",
                    "certifications", "team_activities", "chat_history"
                )

                var completedCount = 0
                val totalDatabases = remainingDatabases.size

                remainingDatabases.forEach { database ->
                    try {
                        Log.d(TAG, "Background sync: Starting $database (${++completedCount}/$totalDatabases)")
                        val startTime = System.currentTimeMillis()

                        TransactionSyncManager.syncDb(backgroundRealm, database)

                        val duration = System.currentTimeMillis() - startTime
                        Log.d(TAG, "Background sync: Completed $database in ${duration}ms")
                    } catch (e: Exception) {
                        Log.e(TAG, "Background sync: Failed to sync $database: ${e.message}")
                        e.printStackTrace()
                    }
                }

                Log.i(TAG, "=== Background Sync Completed ===")
                Log.d(TAG, "Successfully synced $completedCount out of $totalDatabases databases")

            } catch (e: Exception) {
                Log.e(TAG, "Error in background sync: ${e.message}")
                e.printStackTrace()
            } finally {
                backgroundRealm.close()
                Log.d(TAG, "Closed background Realm instance")
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

    private fun resourceTransactionSync(backgroundRealm: Realm) {
        val apiInterface = client?.create(ApiInterface::class.java)
        try {
            syncResource(apiInterface, backgroundRealm)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    @Throws(IOException::class)
    private fun syncResource(dbClient: ApiInterface?, backgroundRealm: Realm) {
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
                    val ids: List<String?> = save(getJsonArray("rows", response.body()), backgroundRealm)
                    newIds.addAll(ids)
                }
                keys.clear()
            }
        }
        removeDeletedResource(newIds, backgroundRealm)
    }

    private fun myLibraryTransactionSync(backgroundRealm: Realm) {
        val apiInterface = client?.create(ApiInterface::class.java)
        try {
            val res = apiInterface?.getDocuments(Utilities.header, "${Utilities.getUrl()}/shelf/_all_docs")?.execute()?.body()
            for (i in res?.rows!!.indices) {
                shelfDoc = res.rows!![i]
                populateShelfItems(apiInterface, backgroundRealm)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun populateShelfItems(apiInterface: ApiInterface, backgroundRealm: Realm) {
        try {
            val jsonDoc = apiInterface.getJsonObject(Utilities.header, "${Utilities.getUrl()}/shelf/${shelfDoc?.id}").execute().body()
            for (i in Constants.shelfDataList.indices) {
                val shelfData = Constants.shelfDataList[i]
                val array = getJsonArray(shelfData.key, jsonDoc)
                memberShelfData(array, shelfData, backgroundRealm)
            }
        } catch (err: Exception) {
            err.printStackTrace()
        }
    }

    private fun memberShelfData(array: JsonArray, shelfData: ShelfData, backgroundRealm: Realm) {
        if (array.size() > 0) {
            triggerInsert(shelfData.categoryKey, shelfData.type)
            check(array, backgroundRealm)
        }
    }

    private fun triggerInsert(categoryId: String, categoryDBName: String) {
        stringArray[0] = shelfDoc?.id
        stringArray[1] = categoryId
        stringArray[2] = categoryDBName
    }

    private fun check(arrayCategoryIds: JsonArray, backgroundRealm: Realm) {
        for (x in 0 until arrayCategoryIds.size()) {
            if (arrayCategoryIds[x] is JsonNull) {
                continue
            }
            validateDocument(arrayCategoryIds, x, backgroundRealm)
        }
    }

    private fun validateDocument(arrayCategoryIds: JsonArray, x: Int, backgroundRealm: Realm) {
        val apiInterface = client?.create(ApiInterface::class.java)
        try {
            val resourceDoc = apiInterface?.getJsonObject(Utilities.header,  "${Utilities.getUrl()}/${stringArray[2]}/${arrayCategoryIds[x].asString}")?.execute()?.body()
            resourceDoc?.let { triggerInsert(stringArray, it, backgroundRealm) }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun triggerInsert(stringArray: Array<String?>, resourceDoc: JsonObject, backgroundRealm: Realm) {
        when (stringArray[2]) {
            "resources" -> insertMyLibrary(stringArray[0], resourceDoc, backgroundRealm)
            "meetups" -> insert(backgroundRealm, resourceDoc)
            "courses" -> {
                if (!backgroundRealm.isInTransaction){
                    backgroundRealm.beginTransaction()
                }
                insertMyCourses(stringArray[0], resourceDoc, backgroundRealm)
                if (backgroundRealm.isInTransaction){
                    backgroundRealm.commitTransaction()
                }
            }
            "teams" -> insertMyTeams(resourceDoc, backgroundRealm)
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
