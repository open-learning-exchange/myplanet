package org.ole.planet.myplanet.service.sync

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.realm.Realm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.datamanager.ApiClient
import org.ole.planet.myplanet.datamanager.ApiInterface
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.utilities.Constants
import org.ole.planet.myplanet.utilities.JsonUtils
import org.ole.planet.myplanet.utilities.UrlUtils
import java.io.IOException

class OptimizedSyncStrategy : SyncStrategy {
    override suspend fun syncTable(
        table: String,
        realm: Realm,
        config: SyncConfig
    ): Flow<SyncResult> = flow {
        val startTime = System.currentTimeMillis()
        val apiService = ApiClient.client!!.create(ApiInterface::class.java)
        var processedItems = 0

        try {
            val localDocs = getLocalDocuments(realm, table)
            val remoteDocs = getRemoteDocuments(apiService, table)
            val (newDocIds, updatedDocIds, deletedDocIds) = compareDocuments(localDocs, remoteDocs)

            processDeletions(realm, table, deletedDocIds)
            processedItems += deletedDocIds.size

            val upsertIds = newDocIds + updatedDocIds
            if (upsertIds.isNotEmpty()) {
                processUpserts(apiService, realm, table, upsertIds, config)
                processedItems += upsertIds.size
            }

            val endTime = System.currentTimeMillis()
            emit(
                SyncResult(
                    table = table,
                    processedItems = processedItems,
                    success = true,
                    duration = endTime - startTime,
                    strategy = getStrategyName()
                )
            )
        } catch (e: Exception) {
            val endTime = System.currentTimeMillis()
            emit(
                SyncResult(
                    table = table,
                    processedItems = 0,
                    success = false,
                    errorMessage = e.message,
                    duration = endTime - startTime,
                    strategy = getStrategyName()
                )
            )
        }
    }

    private fun getLocalDocuments(realm: Realm, table: String): Map<String, String> {
        val modelClass = Constants.classList[table] ?: return emptyMap()
        val dynamicRealm = Realm.getInstance(realm.configuration)
        return try {
            val results = dynamicRealm.where(modelClass.simpleName).findAll()
            results.mapNotNull {
                val id = it.getString("id")
                val rev = it.getString("rev")
                if (id != null && rev != null) id to rev else null
            }.toMap()
        } finally {
            dynamicRealm.close()
        }
    }

    private suspend fun getRemoteDocuments(apiService: ApiInterface, table: String): Map<String, String> = withContext(Dispatchers.IO) {
        val response = apiService.getDocuments(
            UrlUtils.header,
            "${UrlUtils.getUrl()}/$table/_all_docs"
        ).execute()

        if (!response.isSuccessful) {
            throw IOException("Failed to fetch remote documents for table $table: ${response.code()} ${response.message()}")
        }
        response.body()?.rows?.associate { it.id to it.value.rev } ?: emptyMap()
    }

    private fun compareDocuments(
        localDocs: Map<String, String>,
        remoteDocs: Map<String, String>
    ): Triple<List<String>, List<String>, List<String>> {
        val remoteKeys = remoteDocs.keys
        val localKeys = localDocs.keys

        val newDocIds = (remoteKeys - localKeys).toList()
        val deletedDocIds = (localKeys - remoteKeys).toList()

        val potentiallyUpdatedIds = localKeys.intersect(remoteKeys)
        val updatedDocIds = potentiallyUpdatedIds.filter { id ->
            localDocs[id] != remoteDocs[id]
        }
        return Triple(newDocIds, updatedDocIds, deletedDocIds)
    }

    private fun processDeletions(realm: Realm, table: String, deletedDocIds: List<String>) {
        if (deletedDocIds.isEmpty()) return
        val modelClass = Constants.classList[table] ?: return

        realm.executeTransaction { r ->
            r.where(modelClass)
                .isIn("id", deletedDocIds.toTypedArray())
                .findAll()
                .deleteAllFromRealm()
        }
    }

    private suspend fun processUpserts(
        apiService: ApiInterface,
        realm: Realm,
        table: String,
        idsToFetch: List<String>,
        config: SyncConfig
    ) {
        coroutineScope {
            val jobs = idsToFetch.chunked(config.batchSize).map { batch ->
                async(Dispatchers.IO) {
                    val body = JsonObject().apply {
                        add("keys", Gson().toJsonTree(batch))
                    }
                    val response = apiService.findDocs(
                        UrlUtils.header,
                        "application/json",
                        "${UrlUtils.getUrl()}/$table/_all_docs?include_docs=true",
                        body
                    ).execute()

                    if (response.isSuccessful) {
                        response.body()?.let {
                            val arr = JsonUtils.getJsonArray("rows", it)
                            val realmInstance = Realm.getInstance(realm.configuration)
                            try {
                                realmInstance.executeTransaction { transactionRealm ->
                                    if (table == "chat_history") {
                                        insertToChat(arr, transactionRealm)
                                    }
                                    insertDocs(arr, transactionRealm, table)
                                }
                            } finally {
                                realmInstance.close()
                            }
                        }
                    } else {
                        System.err.println("Failed to fetch batch for table $table: ${response.code()}")
                    }
                }
            }
            jobs.awaitAll()
        }
    }

    private fun insertToChat(arr: JsonArray, mRealm: Realm) {
        val chatHistoryList = mutableListOf<JsonObject>()
        for (j in arr) {
            var jsonDoc = j.asJsonObject
            jsonDoc = JsonUtils.getJsonObject("doc", jsonDoc)
            chatHistoryList.add(jsonDoc)
        }
        chatHistoryList.forEach { jsonDoc ->
            org.ole.planet.myplanet.model.RealmChatHistory.insert(mRealm, jsonDoc)
        }
    }

    private fun insertDocs(arr: JsonArray, mRealm: Realm, table: String) {
        val documentList = mutableListOf<JsonObject>()
        for (j in arr) {
            var jsonDoc = j.asJsonObject
            jsonDoc = JsonUtils.getJsonObject("doc", jsonDoc)
            val id = JsonUtils.getString("_id", jsonDoc)
            if (!id.startsWith("_design")) {
                documentList.add(jsonDoc)
            }
        }
        documentList.forEach { jsonDoc ->
            continueInsert(mRealm, table, jsonDoc)
        }
    }

    private fun continueInsert(mRealm: Realm, table: String, jsonDoc: JsonObject) {
        val settings = MainApplication.context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        when (table) {
            "exams" -> RealmStepExam.insertCourseStepsExams("", "", jsonDoc, mRealm)
            "tablet_users" -> RealmUserModel.populateUsersTable(jsonDoc, mRealm, settings)
            else -> callMethod(mRealm, jsonDoc, table)
        }
        RealmMyCourse.saveConcatenatedLinksToPrefs()
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

    override fun getStrategyName(): String = "optimized"

    override fun isSupported(table: String): Boolean = true
}
