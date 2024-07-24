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
import org.ole.planet.myplanet.model.DocumentResponse
import org.ole.planet.myplanet.model.RealmAchievement.Companion.achievementWriteCsv
import org.ole.planet.myplanet.model.RealmCertification.Companion.certificationWriteCsv
import org.ole.planet.myplanet.model.RealmChatHistory.Companion.chatWriteCsv
import org.ole.planet.myplanet.model.RealmChatHistory.Companion.insert
import org.ole.planet.myplanet.model.RealmCourseProgress.Companion.progressWriteCsv
import org.ole.planet.myplanet.model.RealmFeedback.Companion.feedbackWriteCsv
import org.ole.planet.myplanet.model.RealmMeetup.Companion.meetupWriteCsv
import org.ole.planet.myplanet.model.RealmMyCourse.Companion.courseWriteCsv
import org.ole.planet.myplanet.model.RealmMyCourse.Companion.saveConcatenatedLinksToPrefs
import org.ole.planet.myplanet.model.RealmMyHealthPojo.Companion.healthWriteCsv
import org.ole.planet.myplanet.model.RealmMyLibrary.Companion.libraryWriteCsv
import org.ole.planet.myplanet.model.RealmMyTeam.Companion.teamWriteCsv
import org.ole.planet.myplanet.model.RealmNews.Companion.newsWriteCsv
import org.ole.planet.myplanet.model.RealmOfflineActivity.Companion.offlineWriteCsv
import org.ole.planet.myplanet.model.RealmRating.Companion.ratingWriteCsv
import org.ole.planet.myplanet.model.RealmStepExam.Companion.insertCourseStepsExams
import org.ole.planet.myplanet.model.RealmStepExam.Companion.stepExamWriteCsv
import org.ole.planet.myplanet.model.RealmSubmission.Companion.submissionWriteCsv
import org.ole.planet.myplanet.model.RealmTag.Companion.tagWriteCsv
import org.ole.planet.myplanet.model.RealmTeamLog.Companion.teamLogWriteCsv
import org.ole.planet.myplanet.model.RealmTeamTask.Companion.teamTaskWriteCsv
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.model.RealmUserModel.Companion.populateUsersTable
import org.ole.planet.myplanet.model.RealmUserModel.Companion.userWriteCsv
import org.ole.planet.myplanet.utilities.Constants
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
import org.ole.planet.myplanet.utilities.JsonUtils.getJsonArray
import org.ole.planet.myplanet.utilities.JsonUtils.getJsonObject
import org.ole.planet.myplanet.utilities.JsonUtils.getString
import org.ole.planet.myplanet.utilities.Utilities
import retrofit2.Response
import java.io.IOException

object TransactionSyncManager {
    fun authenticate(): Boolean {
        val apiInterface = client?.create(ApiInterface::class.java)
        val start = System.currentTimeMillis()
        try {
            val response: Response<DocumentResponse>? = apiInterface?.getDocuments(Utilities.header, Utilities.getUrl() + "/tablet_users/_all_docs")?.execute()
            if (response != null) {
                val end = System.currentTimeMillis()
                logDuration(start, end, "authenticate")
                return response.code() == 200
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        val end = System.currentTimeMillis()
        logDuration(start, end, "authenticate")
        return false
    }

    fun syncAllHealthData(mRealm: Realm, settings: SharedPreferences, listener: SyncListener) {
        val start = System.currentTimeMillis()
        listener.onSyncStarted()
        val userName = settings.getString("loginUserName", "")
        val password = settings.getString("loginUserPassword", "")
        val header = "Basic " + Base64.encodeToString("$userName:$password".toByteArray(), Base64.NO_WRAP)
        mRealm.executeTransactionAsync({ realm: Realm ->
            val users = realm.where(RealmUserModel::class.java).isNotEmpty("_id").findAll()
            for (userModel in users) {
                syncHealthData(userModel, header)
            }
        }, {
            listener.onSyncComplete()
            val end = System.currentTimeMillis()
            logDuration(start, end, "syncAllHealthData")
        }) { error: Throwable ->
            error.message?.let { listener.onSyncFailed(it) }
            val end = System.currentTimeMillis()
            logDuration(start, end, "syncAllHealthData")
        }
    }

    private fun syncHealthData(userModel: RealmUserModel?, header: String) {
        val table = "userdb-" + userModel?.planetCode?.let { Utilities.toHex(it) } + "-" + userModel?.name?.let { Utilities.toHex(it) }
        val apiInterface = client?.create(ApiInterface::class.java)
        val response: Response<DocumentResponse>?
        try {
            response = apiInterface?.getDocuments(header, Utilities.getUrl() + "/" + table + "/_all_docs")?.execute()
            val ob = response?.body()
            if (ob != null && ob.rows?.isNotEmpty() == true) {
                val r = ob.rows!![0]
                val jsonDoc = apiInterface?.getJsonObject(header, Utilities.getUrl() + "/" + table + "/" + r.id)?.execute()?.body()
                userModel?.key = getString("key", jsonDoc)
                userModel?.iv = getString("iv", jsonDoc)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun syncKeyIv(mRealm: Realm, settings: SharedPreferences, listener: SyncListener) {
        val start = System.currentTimeMillis()
        listener.onSyncStarted()
        val model = UserProfileDbHandler(MainApplication.context).userModel
        val userName = settings.getString("loginUserName", "")
        val password = settings.getString("loginUserPassword", "")
//        val table = "userdb-" + model?.planetCode?.let { Utilities.toHex(it) } + "-" + model?.name?.let { Utilities.toHex(it) }
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
            val apiInterface = client?.create(ApiInterface::class.java)
            val allDocs = apiInterface?.getJsonObject(Utilities.header, Utilities.getUrl() + "/" + table + "/_all_docs?include_doc=false")
            try {
                val all = allDocs?.execute()
                val rows = getJsonArray("rows", all?.body())
                val keys: MutableList<String> = ArrayList()
                for (i in 0 until rows.size()) {
                    val `object` = rows[i].asJsonObject
                    if (!TextUtils.isEmpty(getString("id", `object`))) keys.add(getString("key", `object`))
                    if (i == rows.size() - 1 || keys.size == 1000) {
                        val obj = JsonObject()
                        obj.add("keys", Gson().fromJson(Gson().toJson(keys), JsonArray::class.java))
                        val response = apiInterface?.findDocs(Utilities.header, "application/json", Utilities.getUrl() + "/" + table + "/_all_docs?include_docs=true", obj)?.execute()
                        if (response?.body() != null) {
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

        mRealm.executeTransactionAsync { bgRealm ->
            chatHistoryList.forEach { jsonDoc ->
               insert(bgRealm, jsonDoc)
            }
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
        mRealm.executeTransactionAsync { bgRealm ->
            documentList.forEach { jsonDoc ->
                continueInsert(bgRealm, table, jsonDoc)
            }
        }
    }

    private fun continueInsert(mRealm: Realm, table: String, jsonDoc: JsonObject) {
        val settings = MainApplication.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
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

        val syncFiles = settings.getBoolean("download_sync_files", false)

        if (syncFiles) {
            meetupWriteCsv()
            achievementWriteCsv()
            certificationWriteCsv()
            chatWriteCsv()
            progressWriteCsv()
            feedbackWriteCsv()
            courseWriteCsv()
            healthWriteCsv()
            libraryWriteCsv()
            teamLogWriteCsv()
            teamWriteCsv()
            newsWriteCsv()
            offlineWriteCsv()
            ratingWriteCsv()
            stepExamWriteCsv()
            submissionWriteCsv()
            tagWriteCsv()
            teamTaskWriteCsv()
            userWriteCsv()
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
            e.printStackTrace()
        }
    }

    fun logDuration(start: Long, end: Long, operation: String) {
        val duration = end - start
        println("Operation $operation took $duration milliseconds")
    }
}
