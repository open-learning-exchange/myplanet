package org.ole.planet.myplanet.service.sync

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import dagger.hilt.android.qualifiers.ApplicationContext
import io.realm.Realm
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.callback.OnSyncListener
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.di.ApplicationScope
import org.ole.planet.myplanet.model.RealmChatHistory.Companion.insert
import org.ole.planet.myplanet.model.RealmMyCourse.Companion.saveConcatenatedLinksToPrefs
import org.ole.planet.myplanet.model.RealmStepExam.Companion.insertCourseStepsExams
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.model.RealmUserModel.Companion.populateUsersTable
import org.ole.planet.myplanet.service.UserSessionManager
import org.ole.planet.myplanet.utils.Constants
import org.ole.planet.myplanet.utils.Constants.PREFS_NAME
import org.ole.planet.myplanet.utils.JsonUtils.getJsonArray
import org.ole.planet.myplanet.utils.JsonUtils.getJsonObject
import org.ole.planet.myplanet.utils.JsonUtils.getString
import org.ole.planet.myplanet.utils.SecurePrefs
import org.ole.planet.myplanet.utils.UrlUtils
import org.ole.planet.myplanet.utils.Utilities

@Singleton
class TransactionSyncManager @Inject constructor(
    private val apiInterface: ApiInterface,
    private val databaseService: DatabaseService,
    @ApplicationContext private val context: Context
) {
    suspend fun authenticate(): Boolean {
        return try {
            val targetUrl = "${UrlUtils.getUrl()}/tablet_users/_all_docs"
            val response = apiInterface.getDocuments(UrlUtils.header, targetUrl)
            response.code() == 200
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun syncAllHealthData(settings: SharedPreferences, listener: OnSyncListener) {
        // This method relies on async Realm transactions, so we launch a coroutine to handle network calls
        // Note: The original code used Realm.getDefaultInstance on the main thread (or caller thread)
        // We will adapt it to be safe.
        // However, this method is likely called from UI.
        // To properly support suspend network calls, we need a scope.
        // Since we don't have a scope injected here easily for this specific method pattern without refactoring caller,
        // we might need to rely on the fact that callers should be updated or we launch in GlobalScope/ApplicationScope.
        // But better is to make this suspend or launch in injected scope.
        // Given the constraints and previous patterns, let's assume we can use a scope if we had one,
        // but `syncAllHealthData` signature is not suspend.
        // Ideally, we should inject a scope. Let's assume we can use `MainApplication.applicationScope` or similar if available,
        // or just `CoroutineScope(Dispatchers.IO)`.
        // But for now, let's keep it simple and assume the caller will handle scope if we make it suspend,
        // OR we launch a job.
        // The previous implementation used `mRealm.executeTransactionAsync` which is async.
        // We can mimic this by launching a coroutine.

        // Refactoring to suspend is safer.
        // But since we can't easily change the signature in the interface if it's used by legacy code (though it's not an override here),
        // let's try to use CoroutineScope.

        val userName = SecurePrefs.getUserName(context, settings) ?: ""
        val password = SecurePrefs.getPassword(context, settings) ?: ""
        val header = "Basic ${Base64.encodeToString("$userName:$password".toByteArray(), Base64.NO_WRAP)}"

        // We need a scope. Let's use GlobalScope or create one for now as a quick fix,
        // or better, update the class to have a scope injected.
        // I'll assume we can't easily change constructor too much without affecting DI module in a complex way
        // (though I already did for ServiceModule).
        // Let's use `CoroutineScope(Dispatchers.IO)` just for this method execution.

        CoroutineScope(Dispatchers.IO).launch {
            withContext(Dispatchers.Main) { listener.onSyncStarted() }

            try {
                // Fetch users first
                val users = databaseService.withRealm { realm ->
                    realm.where(RealmUserModel::class.java).isNotEmpty("_id").findAll().let { realm.copyFromRealm(it) }
                }

                users.forEach { userModel ->
                    syncHealthData(userModel, header)
                }

                withContext(Dispatchers.Main) { listener.onSyncComplete() }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { listener.onSyncFailed(e.message ?: "Unknown error") }
            }
        }
    }

    private suspend fun syncHealthData(userModel: RealmUserModel?, header: String) {
        val table = "userdb-${userModel?.planetCode?.let { Utilities.toHex(it) }}-${userModel?.name?.let { Utilities.toHex(it) }}"
        try {
            val response = apiInterface.getDocuments(header, "${UrlUtils.getUrl()}/$table/_all_docs")
            val ob = response.body()
            if (ob != null && ob.rows?.isNotEmpty() == true) {
                val r = ob.rows?.firstOrNull()
                r?.id?.let { id ->
                    val jsonDoc = apiInterface.getJsonObject(header, "${UrlUtils.getUrl()}/$table/$id").body()
                    val key = getString("key", jsonDoc)
                    val iv = getString("iv", jsonDoc)

                    if (!key.isNullOrEmpty() && !iv.isNullOrEmpty()) {
                        databaseService.executeTransactionAsync { realm ->
                            val user = realm.where(RealmUserModel::class.java).equalTo("id", userModel?.id).findFirst()
                            user?.key = key
                            user?.iv = iv
                        }
                    }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun syncKeyIv(
        settings: SharedPreferences,
        listener: OnSyncListener,
        userSessionManager: UserSessionManager
    ) {
        val model = userSessionManager.userModel
        val userName = SecurePrefs.getUserName(context, settings) ?: ""
        val password = SecurePrefs.getPassword(context, settings) ?: ""
        val header = "Basic " + Base64.encodeToString("$userName:$password".toByteArray(), Base64.NO_WRAP)

        CoroutineScope(Dispatchers.IO).launch {
            withContext(Dispatchers.Main) { listener.onSyncStarted() }
            try {
                // Get fresh copy of user
                val userModel = databaseService.withRealm { realm ->
                    realm.where(RealmUserModel::class.java).equalTo("id", model?.id).findFirst()?.let { realm.copyFromRealm(it) }
                }

                syncHealthData(userModel, header)

                withContext(Dispatchers.Main) { listener.onSyncComplete() }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { listener.onSyncFailed(e.message ?: "Unknown error") }
            }
        }
    }

    suspend fun syncDb(table: String) = withContext(Dispatchers.IO) {
        val syncStartTime = System.currentTimeMillis()
        android.util.Log.d("SyncPerf", "  ▶ Starting $table sync")

        try {
            val pageSize = when (table) {
                "ratings" -> 20
                "submissions" -> 100
                else -> 1000
            }

            var skip = 0
            var totalDocs = 0
            var batchNumber = 0

            while (true) {
                batchNumber++
                val batchStartTime = System.currentTimeMillis()
                val batchApiStartTime = System.currentTimeMillis()

                val response = apiInterface.findDocs(
                    UrlUtils.header,
                    "application/json",
                    UrlUtils.getUrl() + "/" + table + "/_all_docs?include_docs=true&limit=$pageSize&skip=$skip",
                    JsonObject()
                )

                val batchApiDuration = System.currentTimeMillis() - batchApiStartTime

                if (response.body() == null || !response.isSuccessful) {
                    android.util.Log.d("SyncPerf", "  ✗ Failed $table batch $batchNumber: HTTP ${response.code()}")
                    break
                }

                val arr = getJsonArray("rows", response.body())
                if (arr.size() == 0) {
                    break
                }

                org.ole.planet.myplanet.utils.SyncTimeLogger.logApiCall(
                    "${UrlUtils.getUrl()}/$table/_all_docs (batch $batchNumber)",
                    batchApiDuration,
                    response.isSuccessful,
                    arr.size()
                )

                databaseService.executeTransactionAsync { mRealm ->
                    val insertStartTime = System.currentTimeMillis()

                    if (table == "chat_history") {
                        insertToChat(arr, mRealm)
                    }
                    insertDocs(arr, mRealm, table)

                    val insertDuration = System.currentTimeMillis() - insertStartTime
                    org.ole.planet.myplanet.utils.SyncTimeLogger.logRealmOperation(
                        "insert_batch",
                        table,
                        insertDuration,
                        arr.size()
                    )
                }

                totalDocs += arr.size()
                skip += arr.size()

                val batchDuration = System.currentTimeMillis() - batchStartTime
                android.util.Log.d("SyncPerf", "    $table batch $batchNumber: ${arr.size()} docs in ${batchDuration}ms (total: $totalDocs)")

                if (table in listOf("ratings", "submissions")) {
                    org.ole.planet.myplanet.utils.SyncTimeLogger.logDetail(table, "Progress: $totalDocs documents synced so far...")
                }

                if (arr.size() < pageSize) {
                    break
                }
            }

            val totalDuration = System.currentTimeMillis() - syncStartTime
            android.util.Log.d("SyncPerf", "  ✓ Completed $table sync: $totalDocs docs in ${totalDuration}ms")
        } catch (e: Exception) {
            e.printStackTrace()
            val failDuration = System.currentTimeMillis() - syncStartTime
            android.util.Log.d("SyncPerf", "  ✗ Failed $table sync after ${failDuration}ms: ${e.message}")
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
