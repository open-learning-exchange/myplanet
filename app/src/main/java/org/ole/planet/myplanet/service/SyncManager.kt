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

    fun start(listener: SyncListener?) {
        this.listener = listener
        if (!isSyncing) {
            settings.edit().remove("concatenated_links").apply()
            listener?.onSyncStarted()
            authenticateAndSync()
        }
    }

    private fun destroy() {
        cancel(context, 111)
        isSyncing = false
        ourInstance = null
        settings.edit().putLong("LastSync", Date().time).apply()
        if (listener != null) {
            listener?.onSyncComplete()
        }
        try {
            if (::mRealm.isInitialized && !mRealm.isClosed) {
                mRealm.close()
                td?.interrupt()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun authenticateAndSync() {
        td = Thread {
            if (TransactionSyncManager.authenticate()) {
                startSync()
            } else {
                handleException(context.getString(R.string.invalid_configuration))
                destroy()
            }
        }
        td?.start()
    }

//    private fun startSync() {
//        try {
//            val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
//            val wifiInfo = wifiManager.connectionInfo
//            if (wifiInfo.supplicantState == SupplicantState.COMPLETED) {
//                settings.edit().putString("LastWifiSSID", wifiInfo.ssid).apply()
//            }
//            isSyncing = true
//            create(context, R.mipmap.ic_launcher, " Syncing data", "Please wait...")
//            mRealm = dbService.realmInstance
//            TransactionSyncManager.syncDb(mRealm, "tablet_users")
//            myLibraryTransactionSync()
//            TransactionSyncManager.syncDb(mRealm, "courses")
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
//            ManagerSync.instance?.syncAdmin()
//            resourceTransactionSync()
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
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val wifiInfo = wifiManager.connectionInfo
            if (wifiInfo.supplicantState == SupplicantState.COMPLETED) {
                settings.edit().putString("LastWifiSSID", wifiInfo.ssid).apply()
            }
            isSyncing = true
            create(context, R.mipmap.ic_launcher, " Syncing data", "Please wait...")
            mRealm = dbService.realmInstance
            Log.d("SyncManager", "Syncing tablet_users")
            syncDb("tablet_users")
            Log.d("SyncManager", "Syncing my library")
            myLibraryTransactionSync()
            syncDb("courses")
            syncDb("exams")
            syncDb("ratings")
            syncDb("courses_progress")
            syncDb("achievements")
            syncDb("tags")
            syncDb("submissions")
            syncDb("news")
            syncDb("feedback")
            syncDb("teams")
            syncDb("tasks")
            syncDb("login_activities")
            syncDb("meetups")
            syncDb("health")
            syncDb("certifications")
            syncDb("team_activities")
            syncDb("chat_history")
            ManagerSync.instance?.syncAdmin()
            Log.d("SyncManager", "Syncing resources")
            resourceTransactionSync()
            Log .d("SyncManager", "Before OnSynced")
            onSynced(mRealm, settings)
            Log.d("SyncManager", "After OnSynced")
            mRealm.close()
        } catch (err: Exception) {
            err.printStackTrace()
            handleException(err.message)
        } finally {
            destroy()
        }
    }

    private fun syncDb(dbName: String) {
        Log.d("SyncManager", "Starting transaction for $dbName")
        mRealm.beginTransaction()
        try {
            TransactionSyncManager.syncDb(mRealm, dbName)
            Log.d("SyncManager", "Transaction committed for $dbName")
            mRealm.commitTransaction()
        } catch (e: Exception) {
            mRealm.cancelTransaction()
            Log.e("SyncManager", "Error syncing $dbName: ${e.message}")
        }
    }

    private fun handleException(message: String?) {
        if (listener != null) {
            isSyncing = false
            MainApplication.syncFailedCount++
            listener?.onSyncFailed(message)
        }
    }

    private fun resourceTransactionSync() {
        Log.d("SyncManager", "Starting resource transaction")
        val apiInterface = client?.create(ApiInterface::class.java)
        try {
            syncResource(apiInterface)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    @Throws(IOException::class)
    private fun syncResource(dbClient: ApiInterface?) {
        Log.d("SyncManager", "Syncing resources")
        val newIds: MutableList<String?> = ArrayList()
        val allDocs = dbClient?.getJsonObject(Utilities.header, Utilities.getUrl() + "/resources/_all_docs?include_doc=false")
        val all = allDocs?.execute()
        val rows = getJsonArray("rows", all?.body())
        val keys: MutableList<String> = ArrayList()
        for (i in 0 until rows.size()) {
            val `object` = rows[i].asJsonObject
            if (!TextUtils.isEmpty(getString("id", `object`))) keys.add(getString("key", `object`))
            if (i == rows.size() - 1 || keys.size == 1000) {
                val obj = JsonObject()
                obj.add("keys", Gson().fromJson(Gson().toJson(keys), JsonArray::class.java))
                val response = dbClient?.findDocs(Utilities.header, "application/json", Utilities.getUrl() + "/resources/_all_docs?include_docs=true", obj)?.execute()
                if (response?.body() != null) {
                    val ids: List<String?> = save(getJsonArray("rows", response.body()), mRealm)
                    newIds.addAll(ids)
                }
                keys.clear()
            }
        }
        removeDeletedResource(newIds, mRealm)
    }

    private fun myLibraryTransactionSync() {
        Log.d("SyncManager", "Starting my library transaction")
        val apiInterface = client?.create(ApiInterface::class.java)
        try {
            val res = apiInterface?.getDocuments(Utilities.header, Utilities.getUrl() + "/shelf/_all_docs")?.execute()?.body()
            res?.rows?.forEach { row ->
                shelfDoc = row
                populateShelfItems(apiInterface)
            }
        } catch (e: IOException) {
            e.printStackTrace()
            Log.e("SyncManager", "Error in myLibraryTransactionSync: ${e.message}")
        }
    }

    private fun populateShelfItems(apiInterface: ApiInterface) {
        Log.d("SyncManager", "Populating shelf items")
        try {
            val jsonDoc = apiInterface.getJsonObject(Utilities.header, Utilities.getUrl() + "/shelf/" + shelfDoc?.id).execute().body()
            Constants.shelfDataList.forEach { shelfData ->
                val array = getJsonArray(shelfData.key, jsonDoc)
                memberShelfData(array, shelfData)
            }
        } catch (err: Exception) {
            err.printStackTrace()
            Log.e("SyncManager", "Error in populateShelfItems: ${err.message}")
        }
    }

    private fun memberShelfData(array: JsonArray, shelfData: ShelfData) {
        Log.d("SyncManager", "Processing shelf data")
        if (array.size() > 0) {
            triggerInsert(shelfData.categoryKey, shelfData.type)
            check(array)
        }
    }

    private fun triggerInsert(categoryId: String, categoryDBName: String) {
        stringArray[0] = shelfDoc?.id
        stringArray[1] = categoryId
        stringArray[2] = categoryDBName
    }

    private fun check(arrayCategoryIds: JsonArray) {
        for (x in 0 until arrayCategoryIds.size()) {
            if (arrayCategoryIds[x] is JsonNull) {
                continue
            }
            validateDocument(arrayCategoryIds, x)
        }
    }

    private fun validateDocument(arrayCategoryIds: JsonArray, x: Int) {
        val apiInterface = client!!.create(ApiInterface::class.java)
        try {
            val resourceDoc = apiInterface.getJsonObject(Utilities.header, Utilities.getUrl() + "/" + stringArray[2] + "/" + arrayCategoryIds[x].asString).execute().body()
            resourceDoc?.let { triggerInsert(stringArray, it) }
        } catch (e: IOException) {
            e.printStackTrace()
            Log.e("SyncManager", "Error in validateDocument: ${e.message}")
        }
    }

//    private fun triggerInsert(stringArray: Array<String?>, resourceDoc: JsonObject) {
//        when (stringArray[2]) {
//            "resources" -> insertMyLibrary(stringArray[0], resourceDoc, mRealm)
//            "meetups" -> insert(mRealm, resourceDoc)
//            "courses" -> {
//                if (!mRealm.isInTransaction){
//                    mRealm.beginTransaction()
//                }
//                insertMyCourses(stringArray[0], resourceDoc, mRealm)
//                if (mRealm.isInTransaction){
//                    mRealm.commitTransaction()
//                }
//            }
//            "teams" -> insertMyTeams(resourceDoc, mRealm)
//        }
//        saveConcatenatedLinksToPrefs()
//    }

    private fun triggerInsert(stringArray: Array<String?>, resourceDoc: JsonObject) {
        val realm = Realm.getDefaultInstance()
        try {
            // Start transaction if not already in progress
            if (!realm.isInTransaction) {
                realm.beginTransaction()
                Log.d("SyncManager", "Transaction started for ${stringArray[2]}")
            } else {
                Log.e("SyncManager", "Transaction already in progress for ${stringArray[2]}")
            }

            // Perform insertion based on category
            when (stringArray[2]) {
                "resources" -> {
                    insertMyLibrary(stringArray[0], resourceDoc, realm)
                }
                "meetups" -> {
                    insert(realm, resourceDoc)
                }
                "courses" -> {
                    insertMyCourses(stringArray[0], resourceDoc, realm)
                }
                "teams" -> {
                    insertMyTeams(resourceDoc, realm)
                }
            }

            // Commit transaction if it is still in progress
            if (realm.isInTransaction) {
                realm.commitTransaction()
                Log.d("SyncManager", "Transaction committed for ${stringArray[2]}")
            } else {
                Log.e("SyncManager", "No transaction in progress to commit for ${stringArray[2]}")
            }
        } catch (e: Exception) {
            // Cancel transaction if an exception occurs and transaction is in progress
            if (realm.isInTransaction) {
                realm.cancelTransaction()
                Log.e("SyncManager", "Transaction canceled for ${stringArray[2]}: ${e.message}")
            }
            Log.e("SyncManager", "Error inserting into ${stringArray[2]}: ${e.message}")
        } finally {
            realm.close()
        }
        saveConcatenatedLinksToPrefs()
    }
//        when (stringArray[2]) {
//            "resources" -> {
//                mRealm.beginTransaction()
//                try {
//                    insertMyLibrary(stringArray[0], resourceDoc, mRealm)
//                    mRealm.commitTransaction()
//                } catch (e: Exception) {
//                    mRealm.cancelTransaction()
//                    Log.e("SyncManager", "Error inserting into resources: ${e.message}")
//                }
//            }
//            "meetups" -> {
//                mRealm.beginTransaction()
//                try {
//                    insert(mRealm, resourceDoc)
//                    mRealm.commitTransaction()
//                } catch (e: Exception) {
//                    mRealm.cancelTransaction()
//                    Log.e("SyncManager", "Error inserting into meetups: ${e.message}")
//                }
//            }
//            "courses" -> {
//                mRealm.beginTransaction()
//                try {
//                    insertMyCourses(stringArray[0], resourceDoc, mRealm)
//                    mRealm.commitTransaction()
//                } catch (e: Exception) {
//                    mRealm.cancelTransaction()
//                    Log.e("SyncManager", "Error inserting into courses: ${e.message}")
//                }
//            }
//            "teams" -> {
//                mRealm.beginTransaction()
//                try {
//                    insertMyTeams(resourceDoc, mRealm)
//                    mRealm.commitTransaction()
//                } catch (e: Exception) {
//                    mRealm.cancelTransaction()
//                    Log.e("SyncManager", "Error inserting into teams: ${e.message}")
//                }
//            }
//        }
//        saveConcatenatedLinksToPrefs()
//    }

    companion object {
        private var ourInstance: SyncManager? = null
        val instance: SyncManager?
            get() {
                ourInstance = SyncManager(MainApplication.context)
                return ourInstance
            }
    }
}