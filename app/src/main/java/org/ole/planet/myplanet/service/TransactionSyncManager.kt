package org.ole.planet.myplanet.service

import android.content.Context
import android.content.SharedPreferences
import android.text.TextUtils
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.realm.Realm
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.callback.SyncListener
import org.ole.planet.myplanet.datamanager.ApiClient.client
import org.ole.planet.myplanet.datamanager.ApiInterface
import org.ole.planet.myplanet.model.RealmChatHistory.Companion.insert
import org.ole.planet.myplanet.model.RealmStepExam.Companion.insertCourseStepsExams
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.model.RealmUserModel.Companion.populateUsersTable
import org.ole.planet.myplanet.ui.sync.SyncActivity
import org.ole.planet.myplanet.utilities.Constants
import org.ole.planet.myplanet.utilities.JsonUtils.getJsonArray
import org.ole.planet.myplanet.utilities.JsonUtils.getJsonObject
import org.ole.planet.myplanet.utilities.JsonUtils.getString
import org.ole.planet.myplanet.utilities.Utilities
import retrofit2.Response
import java.io.IOException

object TransactionSyncManager {
    fun authenticate(): Boolean {
        val apiInterface = client!!.create(ApiInterface::class.java)
        try {
            val response: Response<*> = apiInterface.getDocuments(Utilities.header, Utilities.getUrl() + "/tablet_users/_all_docs").execute()
            return response.code() == 200
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return false
    }

    fun syncAllHealthData(mRealm: Realm, settings: SharedPreferences, listener: SyncListener) {
        listener.onSyncStarted()
        val userName = settings.getString("loginUserName", "")
        val password = settings.getString("loginUserPassword", "")
        val header = "Basic " + Base64.encodeToString("$userName:$password".toByteArray(), Base64.NO_WRAP)
        mRealm.executeTransactionAsync({ realm: Realm ->
            Utilities.log("Sync")
            val users = realm.where(RealmUserModel::class.java).isNotEmpty("_id").findAll()
            for (userModel in users) {
                Utilities.log("Sync " + userModel.name)
                syncHealthData(userModel, header)
            }
        }, { listener.onSyncComplete() }) { error: Throwable ->
            listener.onSyncFailed(
                error.message!!
            )
        }
    }

    private fun syncHealthData(userModel: RealmUserModel?, header: String) {
        val table = "userdb-" + Utilities.toHex(userModel!!.planetCode!!) + "-" + Utilities.toHex(userModel.name!!)
        val apiInterface = client!!.create(ApiInterface::class.java)
        val response: Response<*>
        try {
            response = apiInterface.getDocuments(header, Utilities.getUrl() + "/" + table + "/_all_docs").execute()
            val ob = response.body()
            if (ob != null && ob.rows!!.isNotEmpty()) {
                val r = ob.rows!![0]
                val jsonDoc = apiInterface.getJsonObject(header, Utilities.getUrl() + "/" + table + "/" + r.id).execute().body()
                userModel.key = getString("key", jsonDoc)
                userModel.iv = getString("iv", jsonDoc)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun syncKeyIv(mRealm: Realm, settings: SharedPreferences, listener: SyncListener) {
        listener.onSyncStarted()
        val model = UserProfileDbHandler(MainApplication.context).userModel
        val userName = settings.getString("loginUserName", "")
        val password = settings.getString("loginUserPassword", "")
        val table = "userdb-" + Utilities.toHex(model!!.planetCode!!) + "-" + Utilities.toHex(model.name!!)
        val header = "Basic " + Base64.encodeToString("$userName:$password".toByteArray(), Base64.NO_WRAP)
        val id = model.id
        mRealm.executeTransactionAsync({ realm: Realm ->
            val userModel = realm.where(RealmUserModel::class.java).equalTo("id", id).findFirst()
            syncHealthData(userModel, header)
        }, { listener.onSyncComplete() }) { error: Throwable ->
            listener.onSyncFailed(error.message!!)
        }
    }

    fun syncDb(realm: Realm, table: String) {
        realm.executeTransactionAsync { mRealm: Realm ->
            val apiInterface = client!!.create(ApiInterface::class.java)
            val allDocs = apiInterface.getJsonObject(Utilities.header, Utilities.getUrl() + "/" + table + "/_all_docs?include_doc=false")
            try {
                val all = allDocs.execute()
                val rows = getJsonArray("rows", all.body())
                val keys: MutableList<String> = ArrayList()
                for (i in 0 until rows.size()) {
                    val `object` = rows[i].asJsonObject
                    if (!TextUtils.isEmpty(getString("id", `object`))) keys.add(
                        getString("key", `object`)
                    )
                    if (i == rows.size() - 1 || keys.size == 1000) {
                        val obj = JsonObject()
                        obj.add("keys", Gson().fromJson(Gson().toJson(keys), JsonArray::class.java))
                        val response = apiInterface.findDocs(Utilities.header, "application/json", Utilities.getUrl() + "/" + table + "/_all_docs?include_docs=true", obj).execute()
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
        for (j in arr) {
            var jsonDoc = j.asJsonObject
            jsonDoc = getJsonObject("doc", jsonDoc)
            insert(mRealm, jsonDoc)
        }
    }

    private fun insertDocs(arr: JsonArray, mRealm: Realm, table: String) {
        for (j in arr) {
            var jsonDoc = j.asJsonObject
            jsonDoc = getJsonObject("doc", jsonDoc)
            val id = getString("_id", jsonDoc)
            if (!id.startsWith("_design")) {
                continueInsert(mRealm, table, jsonDoc)
            }
        }
    }

    private fun continueInsert(mRealm: Realm, table: String, jsonDoc: JsonObject) {
        val settings = MainApplication.context.getSharedPreferences(SyncActivity.PREFS_NAME, Context.MODE_PRIVATE)
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
            val methods = Constants.classList[type]!!.methods
            for (m in methods) {
                if ("insert" == m.name) {
                    m.invoke(null, mRealm, jsonDoc)
                    break
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
