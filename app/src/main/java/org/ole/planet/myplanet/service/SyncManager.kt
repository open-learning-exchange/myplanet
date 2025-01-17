package org.ole.planet.myplanet.service

import android.content.Context
import android.content.SharedPreferences
import android.net.wifi.SupplicantState
import android.net.wifi.WifiManager
import android.text.TextUtils
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

    private fun startSync() {
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

    private fun handleException(message: String?) {
        if (listener != null) {
            isSyncing = false
            MainApplication.syncFailedCount++
            listener?.onSyncFailed(message)
        }
    }

    private fun resourceTransactionSync() {
        val apiInterface = client?.create(ApiInterface::class.java)
        try {
            syncResource(apiInterface)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun syncResource(dbClient: ApiInterface?) {
        val newIds: MutableList<String?> = ArrayList()
        try {
            val allDocs = dbClient?.getJsonObject(Utilities.header,  "${Utilities.getUrl()}/resources/_all_docs?include_doc=false")
            val all = allDocs?.execute()
            if (all?.isSuccessful != true) {
                return
            }

            val rows = getJsonArray("rows", all.body())
            val keys: MutableList<String> = ArrayList()
            val failedIds: MutableList<String> = ArrayList()

            for (i in 0 until rows.size()) {
                val `object` = rows[i].asJsonObject
                if (!TextUtils.isEmpty(getString("id", `object`))) {
                    keys.add(getString("key", `object`))
                }

                if (i == rows.size() - 1 || keys.size == 1000) {
                    val obj = JsonObject()
                    obj.add("keys", Gson().fromJson(Gson().toJson(keys), JsonArray::class.java))
                    val response = dbClient.findDocs(Utilities.header, "application/json", "${Utilities.getUrl()}/resources/_all_docs?include_docs=true", obj).execute()

                    when {
                        response.isSuccessful == true -> {
                            response.body()?.let { body ->
                                val ids: List<String?> = save(getJsonArray("rows", body), mRealm)
                                newIds.addAll(ids)
                            }
                        }
                        response.code() == 404 -> {
                            failedIds.addAll(keys)
                        }
                        else -> {
                            val errorMessage = "Failed to sync resources: ${response.code()}"
                            handleException(errorMessage)

                            when (response.code()) {
                                in 500..599 -> {
                                    addToRetryQueue(keys)
                                }
                                401, 403 -> {
                                    handleAuthenticationError()
                                }
                                else -> {
                                    failedIds.addAll(keys)
                                }
                            }
                        }
                    }
                    keys.clear()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                removeDeletedResource(newIds, mRealm)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun addToRetryQueue(keys: List<String>) {
        settings.edit().apply {
            val existingQueue = settings.getStringSet("retry_queue", setOf()) ?: setOf()
            putStringSet("retry_queue", existingQueue + keys)
            apply()
        }
    }

    private fun handleAuthenticationError() {
        settings.edit().remove("credentials").apply()
        handleException("Authentication failed.")
    }

    private fun myLibraryTransactionSync() {
        val apiInterface = client?.create(ApiInterface::class.java)
        try {
            val response = apiInterface?.getDocuments(Utilities.header, "${Utilities.getUrl()}/shelf/_all_docs")?.execute()

            val res = response?.body()
            res?.rows?.let { rows ->
                for (i in rows.indices) {
                    shelfDoc = rows[i]
                    populateShelfItems(apiInterface)
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun populateShelfItems(apiInterface: ApiInterface?) {
        try {
            val response = apiInterface?.getJsonObject(Utilities.header, "${Utilities.getUrl()}/shelf/${shelfDoc?.id}")?.execute()

            response?.body()?.let { jsonDoc ->
                for (i in Constants.shelfDataList.indices) {
                    val shelfData = Constants.shelfDataList[i]
                    val array = getJsonArray(shelfData.key, jsonDoc)
                    memberShelfData(array, shelfData)
                }
            }
        } catch (err: Exception) {
            err.printStackTrace()
        }
    }

    private fun memberShelfData(array: JsonArray, shelfData: ShelfData) {
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
        val apiInterface = client?.create(ApiInterface::class.java)
        try {
            val response = apiInterface?.getJsonObject(Utilities.header, "${Utilities.getUrl()}/${stringArray[2]}/${arrayCategoryIds[x].asString}")?.execute()

            when {
                response?.isSuccessful == true -> {
                    response.body()?.let { resourceDoc ->
                        triggerInsert(stringArray, resourceDoc)
                    }
                }
                response?.code() == 404 -> {
                    return
                }
                else -> {
                    val errorMessage = "Failed to validate document: ${response?.code()}"
                    handleException(errorMessage)
                    if (response?.code() in 500..599) {
                        throw IOException(errorMessage)
                    }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun triggerInsert(stringArray: Array<String?>, resourceDoc: JsonObject) {
        when (stringArray[2]) {
            "resources" -> insertMyLibrary(stringArray[0], resourceDoc, mRealm)
            "meetups" -> insert(mRealm, resourceDoc)
            "courses" -> {
                if (!mRealm.isInTransaction){
                    mRealm.beginTransaction()
                }
                insertMyCourses(stringArray[0], resourceDoc, mRealm)
                if (mRealm.isInTransaction){
                    mRealm.commitTransaction()
                }
            }
            "teams" -> insertMyTeams(resourceDoc, mRealm)
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