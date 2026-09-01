package org.ole.planet.myplanet.services.upload

import android.util.Log
import com.google.gson.JsonObject
import java.io.IOException
import java.util.concurrent.CancellationException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.model.StepExam
import org.ole.planet.myplanet.model.Submission
import org.ole.planet.myplanet.repository.UploadRepository
import org.ole.planet.myplanet.repository.UploadUpdateContract
import org.ole.planet.myplanet.repository.UploadUpdateType
import org.ole.planet.myplanet.repository.UploadedItemResult
import org.ole.planet.myplanet.services.retry.RetryQueue
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.JsonUtils.getString
import org.ole.planet.myplanet.utils.UrlUtils

@Singleton
class UploadCoordinator @Inject constructor(
    private val uploadRepository: UploadRepository,
    private val retryQueue: RetryQueue,
    private val dispatcherProvider: DispatcherProvider
) {

    companion object {
        private const val TAG = "UploadCoordinator"
    }

    suspend fun <T : Any> upload(
        config: UploadConfig<T>
    ): UploadResult<Int> = withContext(dispatcherProvider.io) {
        try {
            val itemsToUpload = queryItemsToUpload(config)

            if (itemsToUpload.isEmpty()) {
                return@withContext UploadResult.Empty
            }

            Log.d(TAG, "Uploading ${itemsToUpload.size} ${config.modelClass.simpleName} items")

            val allSucceeded = mutableListOf<UploadedItem>()
            val allFailed = mutableListOf<UploadError>()

            itemsToUpload.chunked(config.batchSize).forEachIndexed { batchIndex, batch ->
                Log.d(TAG, "Processing batch ${batchIndex + 1} with ${batch.size} items")

                val (succeeded, failed) = uploadBatch(batch, config)

                var dbFailedErrors = emptyList<UploadError>()
                if (succeeded.isNotEmpty()) {
                    val dbFailed = updateDatabaseBatch(succeeded, config)
                    val (actuallySucceeded, dbErrors) = reconcileDbFailures(succeeded, dbFailed)
                    dbFailedErrors = dbErrors
                    allSucceeded.addAll(actuallySucceeded)
                }

                allFailed.addAll(failed)
                allFailed.addAll(dbFailedErrors)
            }

            if (allFailed.isNotEmpty()) {
                queueRetryableFailures(config, allFailed, itemsToUpload)
            }

            Log.d(TAG, "Upload complete: ${allSucceeded.size} succeeded, ${allFailed.size} failed")
            when {
                allFailed.isEmpty() -> UploadResult.Success(
                    data = allSucceeded.size,
                    items = allSucceeded
                )
                allSucceeded.isEmpty() -> UploadResult.Failure(allFailed)
                else -> UploadResult.PartialSuccess(allSucceeded, allFailed)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Critical error during upload", e)
            UploadResult.Failure(
                listOf(UploadError("", e, retryable = true))
            )
        }
    }

    private suspend fun <T : Any> queryItemsToUpload(
        config: UploadConfig<T>
    ): List<PreparedUpload<T>> {
        val items = config.fetchPendingItems.invoke()

        val tempResults = items.mapNotNull { copiedItem ->
            if (config.filterGuests && config.guestUserIdExtractor != null) {
                val userId = config.guestUserIdExtractor.invoke(copiedItem)
                if (userId?.startsWith("guest") == true) {
                    Log.d(TAG, "Filtering out guest user item: $userId")
                    return@mapNotNull null
                }
            }

            val serialized = try {
                when (val serializer = config.serializer) {
                    is UploadSerializer.Simple -> serializer.serialize(copiedItem)
                    is UploadSerializer.Async -> null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Serialization failed for item", e)
                return@mapNotNull null
            }

            Triple(copiedItem, serialized, config.idExtractor(copiedItem) ?: "")
        }

        return tempResults.mapNotNull { (copiedItem, preSerialized, localId) ->
            val serialized = try {
                when (val serializer = config.serializer) {
                    is UploadSerializer.Async -> serializer.serialize(copiedItem)
                    else -> preSerialized ?: return@mapNotNull null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Serialization failed for item", e)
                return@mapNotNull null
            }

            val dbId = config.dbIdExtractor?.invoke(copiedItem)
            PreparedUpload(
                item = copiedItem,
                localId = localId,
                dbId = dbId,
                serialized = serialized
            )
        }
    }

    private suspend fun <T : Any> uploadBatch(
        batch: List<PreparedUpload<T>>,
        config: UploadConfig<T>
    ): Pair<List<UploadedItem>, List<UploadError>> {
        val baseUrl = UrlUtils.getUrl()
        val semaphore = Semaphore(6)

        val batchResults = coroutineScope {
            batch.map { preparedItem ->
                async {
                    coroutineContext.ensureActive()
                    try {
                        config.beforeUpload?.invoke(preparedItem.item)

                        val requestUrl = if (preparedItem.dbId.isNullOrEmpty()) {
                            "$baseUrl/${config.endpoint}"
                        } else {
                            "$baseUrl/${config.endpoint}/${preparedItem.dbId}"
                        }

                        val response = semaphore.withPermit {
                            if (preparedItem.dbId.isNullOrEmpty()) {
                                uploadRepository.postUpload(requestUrl, preparedItem.serialized)
                            } else {
                                uploadRepository.putUpload(requestUrl, preparedItem.serialized)
                            }
                        }

                        val responseBody = response.body()
                        if (response.isSuccessful && responseBody != null) {
                            val (idField, revField) = when (config.responseHandler) {
                                is ResponseHandler.Standard -> "id" to "rev"
                                is ResponseHandler.Custom -> config.responseHandler.idField to config.responseHandler.revField
                            }

                            val uploadedItem = normalizeUploadResult(
                                preparedItem.localId,
                                responseBody,
                                idField,
                                revField
                            )

                            config.afterUpload?.invoke(preparedItem.item, uploadedItem)
                            BatchItemResult.Success(uploadedItem)
                        } else if (response.code() == 409) {
                            try {
                                val docId = preparedItem.dbId ?: preparedItem.localId
                                val getResponse = semaphore.withPermit {
                                    uploadRepository.fetchExistingDoc("$baseUrl/${config.endpoint}/$docId")
                                }
                                val existingDoc = getResponse.body()
                                if (getResponse.isSuccessful && existingDoc != null) {
                                    val uploadedItem = normalizeUploadResult(
                                        preparedItem.localId,
                                        existingDoc,
                                        "_id",
                                        "_rev"
                                    )
                                    config.afterUpload?.invoke(preparedItem.item, uploadedItem)
                                    BatchItemResult.Success(uploadedItem)
                                } else {
                                    BatchItemResult.Error(UploadError(
                                        preparedItem.localId,
                                        Exception("Document exists (409) but couldn't fetch revision"),
                                        retryable = false,
                                        httpCode = 409
                                    ))
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: IOException) {
                                Log.w(TAG, "Network error fetching existing doc for 409 recovery on item ${preparedItem.localId}", e)
                                BatchItemResult.Error(UploadError(preparedItem.localId, e, retryable = true, httpCode = 409))
                            } catch (e: Exception) {
                                BatchItemResult.Error(UploadError(
                                    preparedItem.localId,
                                    Exception("Document exists (409) but fetch failed: ${e.message}"),
                                    retryable = false, httpCode = 409
                                ))
                            }
                        } else {
                            val errorMsg = "Upload failed: HTTP ${response.code()}"
                            Log.w(TAG, "$errorMsg for item ${preparedItem.localId}")
                            BatchItemResult.Error(UploadError(
                                preparedItem.localId,
                                Exception(errorMsg),
                                retryable = response.code() >= 500,
                                httpCode = response.code()
                            ))
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: IOException) {
                        Log.w(TAG, "Network error uploading item ${preparedItem.localId}", e)
                        BatchItemResult.Error(UploadError(preparedItem.localId, e, retryable = true))
                    } catch (e: Exception) {
                        Log.e(TAG, "Unexpected error uploading item ${preparedItem.localId}", e)
                        BatchItemResult.Error(UploadError(preparedItem.localId, e, retryable = false))
                    }
                }
            }.awaitAll()
        }

        val succeeded = mutableListOf<UploadedItem>()
        val failed = mutableListOf<UploadError>()

        batchResults.forEach { result ->
            when (result) {
                is BatchItemResult.Success -> succeeded.add(result.item)
                is BatchItemResult.Error -> failed.add(result.error)
            }
        }

        return succeeded to failed
    }

    private suspend fun <T : Any> updateDatabaseBatch(
        succeeded: List<UploadedItem>,
        config: UploadConfig<T>
    ): List<UploadedItem> {
        val updateContract = UploadUpdateContract(
            updateType = when (config.modelClass) {
                StepExam::class -> UploadUpdateType.Exams
                Submission::class -> UploadUpdateType.Submissions
                else -> error("Unsupported upload update config: ${config.modelClass.qualifiedName}")
            }
        )

        val itemResults = succeeded.map {
            UploadedItemResult(it.localId, it.remoteId, it.remoteRev, it.response)
        }

        val failedResults = uploadRepository.markUploaded(updateContract, itemResults)

        if (failedResults.isEmpty()) return emptyList()

        val succeededMap = succeeded.associateBy { it.localId }
        val failedLocally = failedResults.mapNotNull { failedResult ->
            succeededMap[failedResult.localId]
        }

        return failedLocally
    }

    private suspend fun <T : Any> queueRetryableFailures(
        config: UploadConfig<T>,
        errors: List<UploadError>,
        preparedUploads: List<PreparedUpload<T>>
    ) {
        val uploadType = config.modelClass.simpleName ?: "Unknown"
        val retryableErrors = errors.filter { it.retryable }
        if (retryableErrors.isEmpty()) return

        val payloadMap = preparedUploads.associateBy { it.localId }

        retryableErrors.forEach { error ->
            val preparedUpload = payloadMap[error.itemId]
            if (preparedUpload != null) {
                retryQueue.queueFailedOperation(
                    uploadType = uploadType,
                    error = error,
                    payload = preparedUpload.serialized,
                    endpoint = config.endpoint,
                    httpMethod = if (preparedUpload.dbId.isNullOrEmpty()) "POST" else "PUT",
                    dbId = preparedUpload.dbId,
                    modelClassName = config.modelClass.java.simpleName
                )
            }
        }
    }
    private fun reconcileDbFailures(
        succeeded: List<UploadedItem>,
        dbFailed: List<UploadedItem>
    ): Pair<List<UploadedItem>, List<UploadError>> {
        val dbFailedErrors = dbFailed.map { failedItem ->
            UploadError(
                itemId = failedItem.localId,
                exception = Exception("Local DB update failed"),
                retryable = false
            )
        }

        val actuallySucceeded = if (dbFailed.isEmpty()) {
            succeeded
        } else {
            val dbFailedIds = dbFailed.map { it.localId }.toHashSet()
            succeeded.filter { it.localId !in dbFailedIds }
        }

        return actuallySucceeded to dbFailedErrors
    }


    private fun normalizeUploadResult(localId: String, responseBody: JsonObject, idField: String, revField: String): UploadedItem {
        return UploadedItem(
            localId = localId,
            remoteId = getString(idField, responseBody),
            remoteRev = getString(revField, responseBody),
            response = responseBody
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Room upload path (RoomUploadConfig). Mirrors the Realm path above but sources pending items
    // and persists results through DAO-backed callbacks instead of Realm. The batch/HTTP loop is
    // intentionally duplicated rather than shared, so the still-live Realm path carries zero risk;
    // the two converge once every uploadable model is on Room and the Realm path is deleted.
    // ---------------------------------------------------------------------------------------------

    suspend fun <T : Any> uploadRoom(config: RoomUploadConfig<T>): UploadResult<Int> = withContext(dispatcherProvider.io) {
        try {
            val itemsToUpload = queryItemsToUploadRoom(config)
            if (itemsToUpload.isEmpty()) {
                return@withContext UploadResult.Empty
            }

            Log.d(TAG, "Uploading ${itemsToUpload.size} ${config.modelClassName} items")

            val allSucceeded = mutableListOf<UploadedItem>()
            val allFailed = mutableListOf<UploadError>()

            itemsToUpload.chunked(config.batchSize).forEach { batch ->
                val (succeeded, failed) = uploadBatchRoom(batch, config)

                var dbFailedErrors = emptyList<UploadError>()
                if (succeeded.isNotEmpty()) {
                    val dbFailed = updateDatabaseBatchRoom(succeeded, config)
                    val (actuallySucceeded, dbErrors) = reconcileDbFailures(succeeded, dbFailed)
                    dbFailedErrors = dbErrors
                    allSucceeded.addAll(actuallySucceeded)
                }

                allFailed.addAll(failed)
                allFailed.addAll(dbFailedErrors)
            }

            if (allFailed.isNotEmpty()) {
                queueRetryableFailuresRoom(config, allFailed, itemsToUpload)
            }

            when {
                allFailed.isEmpty() -> UploadResult.Success(allSucceeded.size, allSucceeded)
                allSucceeded.isEmpty() -> UploadResult.Failure(allFailed)
                else -> UploadResult.PartialSuccess(allSucceeded, allFailed)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Critical error during Room upload", e)
            UploadResult.Failure(listOf(UploadError("", e, retryable = true)))
        }
    }

    private suspend fun <T : Any> queryItemsToUploadRoom(
        config: RoomUploadConfig<T>
    ): List<PreparedUpload<T>> {
        val items = config.fetchPendingItems.invoke()
        return items.mapNotNull { item ->
            val serialized = try {
                when (val serializer = config.serializer) {
                    is UploadSerializer.Simple -> serializer.serialize(item)
                    is UploadSerializer.Async -> serializer.serialize(item)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Serialization failed for item", e)
                return@mapNotNull null
            }
            PreparedUpload(
                item = item,
                localId = config.idExtractor(item) ?: "",
                dbId = config.dbIdExtractor?.invoke(item),
                serialized = serialized
            )
        }
    }

    private suspend fun <T : Any> uploadBatchRoom(
        batch: List<PreparedUpload<T>>,
        config: RoomUploadConfig<T>
    ): Pair<List<UploadedItem>, List<UploadError>> {
        val baseUrl = UrlUtils.getUrl()
        val semaphore = Semaphore(6)

        val batchResults = coroutineScope {
            batch.map { preparedItem ->
                async {
                    coroutineContext.ensureActive()
                    try {
                        config.beforeUpload?.invoke(preparedItem.item)

                        val requestUrl = if (preparedItem.dbId.isNullOrEmpty()) {
                            "$baseUrl/${config.endpoint}"
                        } else {
                            "$baseUrl/${config.endpoint}/${preparedItem.dbId}"
                        }

                        val response = semaphore.withPermit {
                            if (preparedItem.dbId.isNullOrEmpty()) {
                                uploadRepository.postUpload(requestUrl, preparedItem.serialized)
                            } else {
                                uploadRepository.putUpload(requestUrl, preparedItem.serialized)
                            }
                        }

                        val responseBody = response.body()
                        if (response.isSuccessful && responseBody != null) {
                            val (idField, revField) = when (config.responseHandler) {
                                is ResponseHandler.Standard -> "id" to "rev"
                                is ResponseHandler.Custom -> config.responseHandler.idField to config.responseHandler.revField
                            }
                            val uploadedItem = normalizeUploadResult(preparedItem.localId, responseBody, idField, revField)
                            config.afterUpload?.invoke(preparedItem.item, uploadedItem)
                            BatchItemResult.Success(uploadedItem)
                        } else if (response.code() == 409) {
                            try {
                                val docId = preparedItem.dbId ?: preparedItem.localId
                                val getResponse = semaphore.withPermit {
                                    uploadRepository.fetchExistingDoc("$baseUrl/${config.endpoint}/$docId")
                                }
                                val existingDoc = getResponse.body()
                                if (getResponse.isSuccessful && existingDoc != null) {
                                    val uploadedItem = normalizeUploadResult(preparedItem.localId, existingDoc, "_id", "_rev")
                                    config.afterUpload?.invoke(preparedItem.item, uploadedItem)
                                    BatchItemResult.Success(uploadedItem)
                                } else {
                                    BatchItemResult.Error(UploadError(preparedItem.localId, Exception("Document exists (409) but couldn't fetch revision"), retryable = false, httpCode = 409))
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: IOException) {
                                Log.w(TAG, "Network error fetching existing doc for 409 recovery on item ${preparedItem.localId}", e)
                                BatchItemResult.Error(UploadError(preparedItem.localId, e, retryable = true, httpCode = 409))
                            } catch (e: Exception) {
                                BatchItemResult.Error(UploadError(preparedItem.localId, Exception("Document exists (409) but fetch failed: ${e.message}"), retryable = false, httpCode = 409))
                            }
                        } else {
                            val errorMsg = "Upload failed: HTTP ${response.code()}"
                            Log.w(TAG, "$errorMsg for item ${preparedItem.localId}")
                            BatchItemResult.Error(UploadError(preparedItem.localId, Exception(errorMsg), retryable = response.code() >= 500, httpCode = response.code()))
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: IOException) {
                        Log.w(TAG, "Network error uploading item ${preparedItem.localId}", e)
                        BatchItemResult.Error(UploadError(preparedItem.localId, e, retryable = true))
                    } catch (e: Exception) {
                        Log.e(TAG, "Unexpected error uploading item ${preparedItem.localId}", e)
                        BatchItemResult.Error(UploadError(preparedItem.localId, e, retryable = false))
                    }
                }
            }.awaitAll()
        }

        val succeeded = mutableListOf<UploadedItem>()
        val failed = mutableListOf<UploadError>()

        batchResults.forEach { result ->
            when (result) {
                is BatchItemResult.Success -> succeeded.add(result.item)
                is BatchItemResult.Error -> failed.add(result.error)
            }
        }

        return succeeded to failed
    }

    private suspend fun <T : Any> updateDatabaseBatchRoom(
        succeeded: List<UploadedItem>,
        config: RoomUploadConfig<T>
    ): List<UploadedItem> {
        val itemResults = succeeded.map {
            UploadedItemResult(it.localId, it.remoteId, it.remoteRev, it.response)
        }
        val failedResults = config.markUploaded(itemResults)
        if (failedResults.isEmpty()) return emptyList()

        val succeededMap = succeeded.associateBy { it.localId }
        return failedResults.mapNotNull { failedResult ->
            succeededMap[failedResult.localId]
        }
    }

    private suspend fun <T : Any> queueRetryableFailuresRoom(
        config: RoomUploadConfig<T>,
        errors: List<UploadError>,
        preparedUploads: List<PreparedUpload<T>>
    ) {
        val retryableErrors = errors.filter { it.retryable }
        if (retryableErrors.isEmpty()) return

        val payloadMap = preparedUploads.associateBy { it.localId }
        retryableErrors.forEach { error ->
            val preparedUpload = payloadMap[error.itemId]
            if (preparedUpload != null) {
                retryQueue.queueFailedOperation(
                    uploadType = config.modelClassName,
                    error = error,
                    payload = preparedUpload.serialized,
                    endpoint = config.endpoint,
                    httpMethod = if (preparedUpload.dbId.isNullOrEmpty()) "POST" else "PUT",
                    dbId = preparedUpload.dbId,
                    modelClassName = config.modelClassName
                )
            }
        }
    }
}

private data class PreparedUpload<T : Any>(
    val item: T,
    val localId: String,
    val dbId: String?,
    val serialized: JsonObject
)

private sealed class BatchItemResult {
    data class Success(val item: UploadedItem) : BatchItemResult()
    data class Error(val error: UploadError) : BatchItemResult()
}
