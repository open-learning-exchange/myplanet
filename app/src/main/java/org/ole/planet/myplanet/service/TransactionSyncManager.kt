package org.ole.planet.myplanet.service

import android.content.Context
import android.content.SharedPreferences
import android.text.TextUtils
import android.util.Base64
import android.util.Log
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
import org.ole.planet.myplanet.utilities.SyncTimeLogger
import org.ole.planet.myplanet.utilities.Utilities
import retrofit2.Response
import java.io.IOException

object TransactionSyncManager {
    fun authenticate(): Boolean {
        val apiInterface = client?.create(ApiInterface::class.java)
        try {
            val response: Response<DocumentResponse>? = apiInterface?.getDocuments(Utilities.header, "${Utilities.getUrl()}/tablet_users/_all_docs")?.execute()
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
        val userName = settings.getString("loginUserName", "")
        val password = settings.getString("loginUserPassword", "")
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
        val table = "userdb-${userModel?.planetCode?.let { Utilities.toHex(it) }}-${userModel?.name?.let { Utilities.toHex(it) }}"
        val apiInterface = client?.create(ApiInterface::class.java)
        val response: Response<DocumentResponse>?
        try {
            response = apiInterface?.getDocuments(header, "${Utilities.getUrl()}/$table/_all_docs")?.execute()
            val ob = response?.body()
            if (ob != null && ob.rows?.isNotEmpty() == true) {
                val r = ob.rows!![0]
                val jsonDoc = apiInterface.getJsonObject(header, "${Utilities.getUrl()}/$table/${r.id}")
                    .execute().body()
                userModel?.key = getString("key", jsonDoc)
                userModel?.iv = getString("iv", jsonDoc)
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

    fun syncDb(realm: Realm, table: String): Boolean {
        val logger = SyncTimeLogger.getInstance()
        logger.startResourceSync("sync_db_$table")
        var success = true

        realm.executeTransactionAsync({ mRealm: Realm ->
            val apiInterface = client?.create(ApiInterface::class.java)
            try {
                // Start timing for fetching all docs
                logger.startResourceSync("${table}_fetch_all_docs")

                val allDocs = apiInterface?.getJsonObject(
                    Utilities.header,
                    "${Utilities.getUrl()}/$table/_all_docs?include_doc=false"
                )
                val all = allDocs?.execute()

                // End timing for fetching all docs
                logger.endResourceSync("${table}_fetch_all_docs")

                val rows = getJsonArray("rows", all?.body())
                val keys: MutableList<String> = ArrayList()

                // Track total number of documents
                val totalDocs = rows.size()
                var processedDocs = 0

                logger.startResourceSync("${table}_process_docs")

                for (i in 0 until rows.size()) {
                    val `object` = rows[i].asJsonObject
                    if (!TextUtils.isEmpty(getString("id", `object`))) {
                        keys.add(getString("key", `object`))
                    }

                    if (i == rows.size() - 1 || keys.size == 1000) {
                        // Start timing for batch processing
                        val batchId = "${table}_batch_${processedDocs}_to_${processedDocs + keys.size}"
                        logger.startResourceSync(batchId)

                        val obj = JsonObject()
                        obj.add("keys", Gson().fromJson(Gson().toJson(keys), JsonArray::class.java))

                        // Start timing for API request
                        logger.startResourceSync("${batchId}_api_request")

                        val response = apiInterface?.findDocs(
                            Utilities.header,
                            "application/json",
                            "${Utilities.getUrl()}/$table/_all_docs?include_docs=true",
                            obj
                        )?.execute()

                        // End timing for API request
                        logger.endResourceSync("${batchId}_api_request")

                        if (response?.body() != null) {
                            val arr = getJsonArray("rows", response.body())

                            // Start timing for document insertion
                            logger.startResourceSync("${batchId}_insert_docs")

                            if (table == "chat_history") {
                                insertToChat(arr, mRealm)
                            } else {
                                // Process documents individually to track timing
                                processDocsWithTiming(arr, mRealm, table, logger)
                            }

                            // End timing for document insertion
                            logger.endResourceSync("${batchId}_insert_docs")
                        }

                        processedDocs += keys.size

                        // Log progress
                        val progress = (processedDocs * 100.0 / totalDocs).toInt()
                        Log.d("SYNC", "$table sync progress: $progress% ($processedDocs/$totalDocs)")

                        // End timing for batch processing
                        logger.endResourceSync(batchId)

                        keys.clear()
                    }
                }

                // End timing for processing all docs
                logger.endResourceSync("${table}_process_docs")

            } catch (e: IOException) {
                Log.e("SYNC", "Error in syncDb for $table: ${e.message}", e)
                e.printStackTrace()
                success = false
            } catch (e: Exception) {
                Log.e("SYNC", "Unexpected error in syncDb for $table: ${e.message}", e)
                e.printStackTrace()
                success = false
            }
        })

        // End timing for the whole syncDb operation
        logger.endResourceSync("sync_db_$table")

        return success
    }

    private fun processDocsWithTiming(arr: JsonArray, mRealm: Realm, table: String, logger: SyncTimeLogger) {
        val documentList = mutableListOf<JsonObject>()

        // Extract documents first
        for (j in arr) {
            var jsonDoc = j.asJsonObject
            jsonDoc = getJsonObject("doc", jsonDoc)
            val id = getString("_id", jsonDoc)
            if (!id.startsWith("_design")) {
                documentList.add(jsonDoc)
            }
        }

        // Process each document with timing
        documentList.forEach { jsonDoc ->
            val docId = getString("_id", jsonDoc)
            logger.startResourceSync("${table}_doc_$docId")

            try {
                continueInsert(mRealm, table, jsonDoc)
            } catch (e: Exception) {
                Log.e("SYNC", "Error processing doc $docId in $table: ${e.message}", e)
            } finally {
                logger.endResourceSync("${table}_doc_$docId")
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
}
