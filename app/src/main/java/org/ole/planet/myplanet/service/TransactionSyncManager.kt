package org.ole.planet.myplanet.service

import android.content.Context
import android.content.SharedPreferences
import android.text.TextUtils
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import dagger.hilt.android.qualifiers.ApplicationContext
import io.realm.Realm
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import org.ole.planet.myplanet.callback.SyncListener
import org.ole.planet.myplanet.datamanager.ApiInterface
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.DocumentResponse
import org.ole.planet.myplanet.model.RealmChatHistory.Companion.insert
import org.ole.planet.myplanet.model.RealmMyCourse.Companion.saveConcatenatedLinksToPrefs
import org.ole.planet.myplanet.model.RealmStepExam.Companion.insertCourseStepsExams
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.model.RealmUserModel.Companion.populateUsersTable
import org.ole.planet.myplanet.utilities.Constants
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
import org.ole.planet.myplanet.utilities.JsonUtils.getJsonArray
import org.ole.planet.myplanet.utilities.JsonUtils.getJsonObject
import org.ole.planet.myplanet.utilities.JsonUtils.getString
import org.ole.planet.myplanet.utilities.SecurePrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.di.ApplicationScope
import org.ole.planet.myplanet.utilities.UrlUtils
import org.ole.planet.myplanet.utilities.Utilities
import retrofit2.Response

@Singleton
class TransactionSyncManager @Inject constructor(
    private val apiInterface: ApiInterface,
    @ApplicationContext private val context: Context,
    private val databaseService: DatabaseService,
) {
    suspend fun authenticate(): Boolean {
        return try {
            val response = withContext(Dispatchers.IO) {
                apiInterface.getDocuments(
                    UrlUtils.header,
                    "${UrlUtils.getUrl()}/tablet_users/_all_docs"
                ).execute()
            }
            response.code() == 200
        } catch (e: IOException) {
            Log.e("TransactionSyncManager", "authenticate: ${e.message}", e)
            false
        }
    }

    suspend fun syncAllHealthData(settings: SharedPreferences, listener: SyncListener) {
        listener.onSyncStarted()
        val userName = SecurePrefs.getUserName(context, settings) ?: ""
        val password = SecurePrefs.getPassword(context, settings) ?: ""
        val header = "Basic ${Base64.encodeToString("$userName:$password".toByteArray(), Base64.NO_WRAP)}"
        try {
            val users = databaseService.withRealmAsync { realm ->
                realm.where(RealmUserModel::class.java).isNotEmpty("_id").findAll().let {
                    realm.copyFromRealm(it)
                }
            }
            users.forEach { userModel ->
                syncHealthData(userModel, header)
            }
            withContext(Dispatchers.Main) {
                listener.onSyncComplete()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                e.message?.let { listener.onSyncFailed(it) }
            }
        }
    }

    private suspend fun syncHealthData(userModel: RealmUserModel?, header: String) {
        val table = "userdb-${userModel?.planetCode?.let { Utilities.toHex(it) }}-${userModel?.name?.let { Utilities.toHex(it) }}"
        try {
            val response = withContext(Dispatchers.IO) {
                apiInterface.getDocuments(header, "${UrlUtils.getUrl()}/$table/_all_docs").execute()
            }
            val ob = response.body()
            if (ob != null && ob.rows?.isNotEmpty() == true) {
                val r = ob.rows?.firstOrNull()
                r?.id?.let { id ->
                    val jsonDoc = withContext(Dispatchers.IO) {
                        apiInterface.getJsonObject(header, "${UrlUtils.getUrl()}/$table/$id").execute().body()
                    }
                    databaseService.executeTransactionAsync { realm ->
                        userModel?.let {
                            val model = realm.where(RealmUserModel::class.java).equalTo("id", it.id).findFirst()
                            model?.key = getString("key", jsonDoc)
                            model?.iv = getString("iv", jsonDoc)
                        }
                    }
                }
            }
        } catch (e: IOException) {
            Log.e("TransactionSyncManager", "syncHealthData: ${e.message}", e)
        }
    }

    suspend fun syncKeyIv(
        settings: SharedPreferences,
        listener: SyncListener,
        userProfileDbHandler: UserProfileDbHandler
    ) {
        listener.onSyncStarted()
        val model = userProfileDbHandler.userModel
        val userName = SecurePrefs.getUserName(context, settings) ?: ""
        val password = SecurePrefs.getPassword(context, settings) ?: ""
        val header = "Basic " + Base64.encodeToString("$userName:$password".toByteArray(), Base64.NO_WRAP)
        val id = model?.id
        try {
            val user = databaseService.withRealmAsync { realm ->
                realm.where(RealmUserModel::class.java).equalTo("id", id).findFirst()?.let {
                    realm.copyFromRealm(it)
                }
            }
            syncHealthData(user, header)
            withContext(Dispatchers.Main) {
                listener.onSyncComplete()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                e.message?.let { listener.onSyncFailed(it) }
            }
        }
    }

    suspend fun syncDb(table: String) {
        try {
            val allDocs = withContext(Dispatchers.IO) {
                apiInterface.getJsonObject(
                    UrlUtils.header,
                    UrlUtils.getUrl() + "/" + table + "/_all_docs?include_doc=false"
                ).execute()
            }
            val rows = getJsonArray("rows", allDocs.body())
            val keys: MutableList<String> = ArrayList()

            for (i in 0 until rows.size()) {
                val `object` = rows[i].asJsonObject
                if (!TextUtils.isEmpty(getString("id", `object`))) {
                    keys.add(getString("key", `object`))
                }
                if (i == rows.size() - 1 || keys.size == 1000) {
                    val obj = JsonObject()
                    obj.add("keys", Gson().fromJson(Gson().toJson(keys), JsonArray::class.java))

                    val response = withContext(Dispatchers.IO) {
                        apiInterface.findDocs(
                            UrlUtils.header,
                            "application/json",
                            UrlUtils.getUrl() + "/" + table + "/_all_docs?include_docs=true",
                            obj
                        ).execute()
                    }

                    if (response.body() != null) {
                        val arr = getJsonArray("rows", response.body())
                        databaseService.executeTransactionAsync { mRealm ->
                            if (table == "chat_history") {
                                insertToChat(arr, mRealm)
                            }
                            insertDocs(arr, mRealm, table)
                        }
                    }
                    keys.clear()
                }
            }
        } catch (e: IOException) {
            Log.e("TransactionSyncManager", "syncDb for table $table: ${e.message}", e)
        }
    }

    private fun insertToChat(arr: JsonArray, mRealm: Realm) {
        val chatHistoryList = mutableListOf<JsonObject>()
        for (j in arr) {
            var jsonDoc = j.asJsonObject
            jsonDoc = getJsonObject("doc", jsonDoc)
            chatHistoryList.add(jsonDoc)
        }

        chatHistoryList.forEach { jsonDoc ->
            insert(mRealm, jsonDoc)
        }
    }

    private fun insertDocs(arr: JsonArray, mRealm: Realm, table: String) {
        val documentList = mutableListOf<JsonObject>()

        for (j in arr) {
            var jsonDoc = j.asJsonObject
            jsonDoc = getJsonObject("doc", jsonDoc)
            val id = getString("_id", jsonDoc)
            if (!id.startsWith("_design")) {
                documentList.add(jsonDoc)
            }
        }

        documentList.forEach { jsonDoc ->
            continueInsert(mRealm, table, jsonDoc)
        }
    }

    private fun continueInsert(mRealm: Realm, table: String, jsonDoc: JsonObject) {
        val settings = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        when (table) {
            "exams" -> {
                insertCourseStepsExams("", "", jsonDoc, mRealm)
            }

            "tablet_users" -> {
                populateUsersTable(jsonDoc, mRealm, settings)
            }

            else -> {
                callMethod(mRealm, jsonDoc, table)
            }
        }
    }

    private fun callMethod(mRealm: Realm, jsonDoc: JsonObject, type: String) {
        try {
            val methods = Constants.classList[type]?.methods
            methods?.let {
                for (m in it) {
                    if ("insert" == m.name) {
                        m.invoke(null, mRealm, jsonDoc)
                        break
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("TransactionSyncManager", "callMethod for type $type: ${e.message}", e)
        }
    }
}
