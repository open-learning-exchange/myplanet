package org.ole.planet.myplanet.service

import android.content.*
import android.util.Base64
import com.google.gson.*
import io.realm.kotlin.Realm
import kotlinx.coroutines.*
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.callback.SyncListener
import org.ole.planet.myplanet.datamanager.*
import org.ole.planet.myplanet.model.*
import org.ole.planet.myplanet.utilities.*
import retrofit2.Response
import java.io.IOException

object TransactionSyncManager {
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    fun authenticate(): Boolean {
        val apiInterface = ApiClient.client?.create(ApiInterface::class.java)
        return try {
            val response: Response<DocumentResponse>? = apiInterface?.getDocuments(Utilities.header, "${Utilities.getUrl()}/tablet_users/_all_docs")?.execute()
            response?.code() == 200
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }

    fun syncAllHealthData(mRealm: Realm, settings: SharedPreferences, listener: SyncListener) {
        listener.onSyncStarted()
        val userName = settings.getString("loginUserName", "")
        val password = settings.getString("loginUserPassword", "")
        val header = "Basic " + Base64.encodeToString("$userName:$password".toByteArray(), Base64.NO_WRAP)

        coroutineScope.launch {
            try {
                mRealm.write {
                    val users = query<RealmUserModel>(RealmUserModel::class).find()
                    users.forEach { userModel ->
                        syncHealthData(userModel, header)
                    }
                }
                listener.onSyncComplete()
            } catch (error: Throwable) {
                error.message?.let { listener.onSyncFailed(it) }
            }
        }
    }

    private fun syncHealthData(userModel: RealmUserModel?, header: String) {
        val table = "userdb-${userModel?.planetCode?.let { Utilities.toHex(it) }}-${userModel?.name?.let { Utilities.toHex(it) }}"
        val apiInterface = ApiClient.client?.create(ApiInterface::class.java)
        try {
            val response = apiInterface?.getDocuments(header, "${Utilities.getUrl()}/$table/_all_docs")?.execute()

            response?.body()?.rows?.firstOrNull()?.let { r ->
                val jsonDoc = apiInterface.getJsonObject(header, "${Utilities.getUrl()}/$table/${r.id}").execute().body()
                userModel?.key = JsonUtils.getString("key", jsonDoc)
                userModel?.iv = JsonUtils.getString("iv", jsonDoc)
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
        val header = "Basic " + Base64.encodeToString(
            "$userName:$password".toByteArray(),
            Base64.NO_WRAP
        )

        coroutineScope.launch {
            try {
                mRealm.write {
                    query<RealmUserModel>(RealmUserModel::class, "id == $0", model?.id)
                        .first().find()?.let { userModel ->
                            syncHealthData(userModel, header)
                        }
                }
                listener.onSyncComplete()
            } catch (error: Throwable) {
                error.message?.let { listener.onSyncFailed(it) }
            }
        }
    }

    fun syncDb(realm: Realm, table: String) {
        coroutineScope.launch {
            try {
                realm.write {
                    val apiInterface = ApiClient.client?.create(ApiInterface::class.java)
                    val allDocs = apiInterface?.getJsonObject(Utilities.header, "${Utilities.getUrl()}/$table/_all_docs?include_doc=false")

                    val all = allDocs?.execute()
                    val rows = JsonUtils.getJsonArray("rows", all?.body())
                    val keys = mutableListOf<String>()

                    rows.forEachIndexed { index, element ->
                        val obj = element.asJsonObject
                        JsonUtils.getString("id", obj).takeIf { it.isNotEmpty() }?.let {
                            keys.add(JsonUtils.getString("key", obj))
                        }

                        if (index == rows.size() - 1 || keys.size == 1000) {
                            val requestObj = JsonObject().apply {
                                add("keys", Gson().fromJson(Gson().toJson(keys), JsonArray::class.java))
                            }

                            val response = apiInterface?.findDocs(Utilities.header, "application/json", "${Utilities.getUrl()}/$table/_all_docs?include_docs=true", requestObj)?.execute()

                            response?.body()?.let { body ->
                                val arr = JsonUtils.getJsonArray("rows", body)
                                coroutineScope.launch {
                                    when (table) {
                                        "chat_history" -> insertToChat(arr, realm)
                                        else -> insertDocs(arr, realm, table)
                                    }
                                }
                            }
                            keys.clear()
                        }
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun insertToChat(arr: JsonArray, realm: Realm) {
        arr.forEach { element ->
            RealmChatHistory.insert(realm, JsonUtils.getJsonObject("doc", element.asJsonObject))
        }
    }

    private suspend fun insertDocs(arr: JsonArray, realm: Realm, table: String) {
        arr.forEach { element ->
            element.asJsonObject.let { jsonDoc ->
                JsonUtils.getJsonObject("doc", jsonDoc).let { doc ->
                    JsonUtils.getString("_id", doc).takeIf { !it.startsWith("_design") }?.let {
                        continueInsert(realm, table, doc)
                    }
                }
            }
        }
    }

    private suspend fun continueInsert(realm: Realm, table: String, jsonDoc: JsonObject) {
        val settings = MainApplication.context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)

        when (table) {
            "exams" -> RealmStepExam.insertCourseStepsExams("", "", jsonDoc, realm)
            "tablet_users" -> RealmUserModel.populateUsersTable(jsonDoc, realm, settings)
            else -> callMethod(realm, jsonDoc, table)
        }

        RealmMyCourse.saveConcatenatedLinksToPrefs()

        val syncFiles = settings.getBoolean("download_sync_files", false)

        if (syncFiles) {
            RealmMeetup.meetupWriteCsv()
            RealmAchievement.achievementWriteCsv()
            RealmCertification.certificationWriteCsv()
            RealmChatHistory.chatWriteCsv()
            RealmCourseProgress.progressWriteCsv()
            RealmFeedback.feedbackWriteCsv()
            RealmMyCourse.courseWriteCsv()
            RealmMyHealthPojo.healthWriteCsv()
            RealmMyLibrary.libraryWriteCsv()
            RealmTeamLog.teamLogWriteCsv()
            RealmMyTeam.teamWriteCsv()
            RealmNews.newsWriteCsv()
            RealmOfflineActivity.offlineWriteCsv()
            RealmRating.ratingWriteCsv()
            RealmStepExam.stepExamWriteCsv()
            RealmSubmission.submissionWriteCsv()
            RealmTag.tagWriteCsv()
            RealmTeamTask.teamTaskWriteCsv()
            RealmUserModel.userWriteCsv()
        }
    }

    private fun callMethod(realm: Realm, jsonDoc: JsonObject, type: String) {
        try {
            Constants.classList[type]?.methods?.find { it.name == "insert" }?.invoke(null, realm, jsonDoc)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}