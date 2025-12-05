package org.ole.planet.myplanet.service

import android.content.Context
import android.content.SharedPreferences
import android.text.TextUtils
import android.util.Base64
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
import org.ole.planet.myplanet.di.ApplicationScope
import org.ole.planet.myplanet.utilities.UrlUtils
import org.ole.planet.myplanet.utilities.Utilities
import retrofit2.Response

@Singleton
class TransactionSyncManager @Inject constructor(
    private val apiInterface: ApiInterface,
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope
) {
    fun authenticate(): Boolean {
        try {
            val response: Response<DocumentResponse>? = apiInterface.getDocuments(
                UrlUtils.header,
                "${UrlUtils.getUrl()}/tablet_users/_all_docs"
            ).execute()
            if (response != null) {
                return response.code() == 200
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return false
    }

    fun syncAllHealthData(mRealm: Realm, settings: SharedPreferences, listener: SyncListener) {
        listener.onSyncStarted()
        val userName = SecurePrefs.getUserName(context, settings) ?: ""
        val password = SecurePrefs.getPassword(context, settings) ?: ""
        val header = "Basic ${Base64.encodeToString("$userName:$password".toByteArray(), Base64.NO_WRAP)}"
        mRealm.executeTransactionAsync({ realm: Realm ->
            val users = realm.where(RealmUserModel::class.java).isNotEmpty("_id").findAll()
            for (userModel in users) {
                syncHealthData(userModel, header)
            }
        }, { listener.onSyncComplete() }) { error: Throwable ->
            error.message?.let { listener.onSyncFailed(it) }
        }
    }

    private fun syncHealthData(userModel: RealmUserModel?, header: String) {
        val table =
            "userdb-${userModel?.planetCode?.let { Utilities.toHex(it) }}-${userModel?.name?.let { Utilities.toHex(it) }}"
        val response: Response<DocumentResponse>?
        try {
            response =
                apiInterface.getDocuments(header, "${UrlUtils.getUrl()}/$table/_all_docs").execute()
            val ob = response?.body()
            if (ob != null && ob.rows?.isNotEmpty() == true) {
                val r = ob.rows?.firstOrNull()
                r?.id?.let { id ->
                    val jsonDoc = apiInterface.getJsonObject(header, "${UrlUtils.getUrl()}/$table/$id")
                        .execute().body()
                    userModel?.key = getString("key", jsonDoc)
                    userModel?.iv = getString("iv", jsonDoc)
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun syncKeyIv(
        mRealm: Realm,
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
        mRealm.executeTransactionAsync({ realm: Realm ->
            val userModel = realm.where(RealmUserModel::class.java).equalTo("id", id).findFirst()
            syncHealthData(userModel, header)
        }, { listener.onSyncComplete() }) { error: Throwable ->
            error.message?.let { listener.onSyncFailed(it) }
        }
    }

    fun syncDb(realm: Realm, table: String) {
        realm.executeTransactionAsync { mRealm: Realm ->
            val allDocs = apiInterface.getJsonObject(
                UrlUtils.header,
                UrlUtils.getUrl() + "/" + table + "/_all_docs?include_doc=false"
            )
            try {
                val all = allDocs.execute()
                val rows = getJsonArray("rows", all?.body())
                val keys: MutableList<String> = ArrayList()
                for (i in 0 until rows.size()) {
                    val `object` = rows[i].asJsonObject
                    if (!TextUtils.isEmpty(getString("id", `object`))) keys.add(
                        getString(
                            "key",
                            `object`
                        )
                    )
                    if (i == rows.size() - 1 || keys.size == 1000) {
                        val obj = JsonObject()
                        obj.add("keys", Gson().fromJson(Gson().toJson(keys), JsonArray::class.java))
                        val response = apiInterface.findDocs(
                            UrlUtils.header,
                            "application/json",
                            UrlUtils.getUrl() + "/" + table + "/_all_docs?include_docs=true",
                            obj
                        ).execute()
                        if (response.body() != null) {
                            val arr = getJsonArray("rows", response.body())
                            if (table == "chat_history") {
                                insertToChat(arr, mRealm)
                            }
                            insertDocs(arr, mRealm, table)
                        }
                        keys.clear()
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
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
        saveConcatenatedLinksToPrefs()
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
            e.printStackTrace()
        }
    }
}
