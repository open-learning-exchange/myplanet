package org.ole.planet.myplanet.repository

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.data.api.ApiClient
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.UserDataWorker
import org.ole.planet.myplanet.services.sync.AdaptiveBatchProcessor
import org.ole.planet.myplanet.services.sync.TransactionSyncManager
import org.ole.planet.myplanet.utils.Constants
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.JsonUtils.getJsonArray
import org.ole.planet.myplanet.utils.JsonUtils.getJsonObject
import org.ole.planet.myplanet.utils.JsonUtils.gson
import org.ole.planet.myplanet.utils.SyncTimeLogger
import org.ole.planet.myplanet.utils.TimeProvider
import org.ole.planet.myplanet.utils.UrlUtils

@Singleton
class SyncRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiInterface: ApiInterface,
    private val dispatcherProvider: DispatcherProvider,
    private val resourcesRepository: ResourcesRepository,
    private val coursesRepository: CoursesRepository,
    private val eventsRepository: EventsSyncWriter,
    private val teamsSyncRepository: TeamsSyncRepository,
    private val transactionSyncManager: dagger.Lazy<TransactionSyncManager>,
    private val syncTimeLogger: SyncTimeLogger,
    private val sharedPrefManager: SharedPrefManager,
    private val timeProvider: TimeProvider
) : SyncRepository {
    override fun uploadLoginData(): Flow<SyncUiState> =
        enqueueUserDataUpload("UploadUserData_Login", UserDataWorker.UPLOAD_TYPE_LOGIN)

    override fun uploadBulkData(): Flow<SyncUiState> =
        enqueueUserDataUpload("UploadUserData_Bulk", UserDataWorker.UPLOAD_TYPE_BULK)

    private fun enqueueUserDataUpload(uniqueWorkName: String, uploadType: String): Flow<SyncUiState> {
        val workRequest = OneTimeWorkRequest.Builder(UserDataWorker::class.java)
            .setInputData(workDataOf(UserDataWorker.KEY_UPLOAD_TYPE to uploadType))
            .build()
        val workManager = WorkManager.getInstance(context)
        workManager.enqueueUniqueWork(
            uniqueWorkName,
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
        return workManager.getWorkInfoByIdFlow(workRequest.id).map { workInfo ->
            mapWorkInfoToState(workInfo)
        }
    }

    private fun mapWorkInfoToState(workInfo: WorkInfo?): SyncUiState {
        return when (workInfo?.state) {
            WorkInfo.State.SUCCEEDED -> {
                val message = workInfo.outputData.getString(UserDataWorker.KEY_SUCCESS_MESSAGE)
                SyncUiState.Success(message)
            }
            WorkInfo.State.FAILED -> SyncUiState.Error("Upload failed")
            WorkInfo.State.CANCELLED -> SyncUiState.Error("Upload cancelled")
            WorkInfo.State.RUNNING -> SyncUiState.Loading
            else -> SyncUiState.Idle
        }
    }

    override suspend fun processShelfParallel(shelfId: String): Int {
        var processedItems = 0

        try {
            val shelfDoc: JsonObject? = withContext(dispatcherProvider.io) {
                var doc: JsonObject? = null
                ApiClient.executeWithRetryAndWrap {
                    apiInterface.getJsonObject(
                        UrlUtils.header,
                        "${UrlUtils.getUrl()}/shelf/$shelfId"
                    )
                }?.let {
                    doc = it.body()
                }
                coroutineContext.ensureActive()
                doc
            }

            if (shelfDoc == null) {
                return 0
            }

            coroutineScope {
                val dataJobs = Constants.shelfDataList.mapNotNull { shelfData ->
                    val array = getJsonArray(shelfData.key, shelfDoc)
                    if (!array.isEmpty()) {
                        async(dispatcherProvider.io) {
                            processShelfDataOptimizedSync(shelfId, shelfData, shelfDoc)
                        }
                    } else null
                }

                processedItems = dataJobs.awaitAll().sum()
            }
        } catch (e: Exception) {
            Log.e("SyncRepositoryImpl", "Error in processShelfParallel", e)
        }

        return processedItems
    }

    private suspend fun processShelfDataOptimizedSync(shelfId: String?, shelfData: Constants.ShelfData, shelfDoc: JsonObject?): Int {
        var processedCount = 0
        val logger = syncTimeLogger

        try {
            val array = getJsonArray(shelfData.key, shelfDoc)
            if (array.isEmpty()) return 0

            val validIds = mutableListOf<String>()
            for (element in array) {
                if (element !is JsonNull) {
                    validIds.add(element.asString)
                }
            }

            if (validIds.isEmpty()) return 0

            val batchSizer = AdaptiveBatchProcessor(initialSize = 50)
            var i = 0
            var batchNum = 0

            while (i < validIds.size) {
                batchNum++
                val batchStartTime = SystemClock.elapsedRealtime()

                val end = minOf(i + batchSizer.currentSize, validIds.size)
                val batch = validIds.subList(i, end)
                i = end

                val keysObject = JsonObject()
                keysObject.add("keys", gson.toJsonTree(batch))

                // API call
                val apiStartTime = SystemClock.elapsedRealtime()
                var response: JsonObject? = null
                ApiClient.executeWithRetryAndWrap {
                    apiInterface.postDoc(UrlUtils.header, "application/json", "${UrlUtils.getUrl()}/${shelfData.type}/_all_docs?include_docs=true", keysObject)
                }?.let {
                    response = it.body()
                }
                val apiDuration = SystemClock.elapsedRealtime() - apiStartTime

                if (response == null) {
                    batchSizer.recordFailure()
                    logger.logApiCall("${UrlUtils.getUrl()}/${shelfData.type}/_all_docs (shelf batch $batchNum)", apiDuration, false, 0)
                    continue
                }
                batchSizer.recordSuccess(apiDuration)

                val responseRows = getJsonArray("rows", response)
                logger.logApiCall("${UrlUtils.getUrl()}/${shelfData.type}/_all_docs (shelf batch $batchNum)", apiDuration, true, responseRows.size())

                if (responseRows.isEmpty()) continue

                val documentsToProcess = mutableListOf<JsonObject>()
                for (rowElement in responseRows) {
                    val rowObj = rowElement.asJsonObject
                    if (rowObj.has("doc")) {
                        val doc = getJsonObject("doc", rowObj)
                        documentsToProcess.add(doc)
                    }
                }

                if (documentsToProcess.isNotEmpty()) {
                    val realmStartTime = SystemClock.elapsedRealtime()
                    when (shelfData.type) {
                        "resources" -> processedCount += resourcesRepository.batchInsertMyLibrary(shelfId, documentsToProcess)
                        "courses" -> processedCount += coursesRepository.batchInsertMyCourses(shelfId, documentsToProcess)
                        "meetups" -> processedCount += eventsRepository.batchInsertMeetups(documentsToProcess)
                        "teams" -> processedCount += teamsSyncRepository.batchInsertMyTeams(documentsToProcess)
                    }
                    val realmDuration = SystemClock.elapsedRealtime() - realmStartTime
                    logger.logDbOperation("shelf_insert", shelfData.type, realmDuration, documentsToProcess.size)
                }

                val batchDuration = SystemClock.elapsedRealtime() - batchStartTime
                if (batchDuration > 1000) {
                    logger.logDetail("shelf_sync", "Shelf $shelfId ${shelfData.type} batch $batchNum ($end/${validIds.size} ids): ${batchDuration}ms for ${documentsToProcess.size} docs")
                }
            }

        } catch (e: Exception) {
            Log.e("SyncRepositoryImpl", "Error in processShelfDataOptimizedSync", e)
            logger.logDetail("shelf_sync", "Shelf $shelfId ${shelfData.type} failed: ${e.message}")
        }
        return processedCount
    }

    override suspend fun syncDashboardKeyId(role: String?): SyncUiState {
        return try {
            transactionSyncManager.get().syncDashboardKeyId(role)
            SyncUiState.Success(null)
        } catch (e: Exception) {
            SyncUiState.Error(e.message)
        }
    }

    override fun getCachedShelvesWithData(): List<String> {
        val cacheTime = sharedPrefManager.getRawLong(CACHE_KEY_SHELVES_CACHE_TIME, 0)
        val now = timeProvider.now()

        if (now - cacheTime < CACHE_VALIDITY_HOURS * 60 * 60 * 1000) {
            val cachedData = sharedPrefManager.getRawString(CACHE_KEY_SHELVES_WITH_DATA, "")
            if (cachedData.isNotEmpty()) {
                return cachedData.split(",").filter { it.isNotBlank() }
            }
        }
        return emptyList()
    }

    override fun cacheShelvesWithData(shelves: List<String>) {
        sharedPrefManager.setRawString(CACHE_KEY_SHELVES_WITH_DATA, shelves.joinToString(","))
        sharedPrefManager.setRawLong(CACHE_KEY_SHELVES_CACHE_TIME, timeProvider.now())
    }

    private companion object {
        const val CACHE_KEY_SHELVES_WITH_DATA = "shelves_with_data"
        const val CACHE_KEY_SHELVES_CACHE_TIME = "shelves_cache_time"
        const val CACHE_VALIDITY_HOURS = 6
    }
}
