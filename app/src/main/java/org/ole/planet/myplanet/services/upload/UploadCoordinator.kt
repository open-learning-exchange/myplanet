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
import org.ole.planet.myplanet.data.NetworkResult
import org.ole.planet.myplanet.repository.UploadRepository
import org.ole.planet.myplanet.repository.UploadedItemResult
import org.ole.planet.myplanet.services.retry.RetryQueue
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.GsonUtils.getString
import org.ole.planet.myplanet.utils.UrlUtils

@Singleton
class UploadCoordinator @Inject constructor(
    private val uploadRepository: UploadRepository,
    private val retryQueue: RetryQueue,
    private val dispatcherProvider: DispatcherProvider
) {

    companion object {
        private const val TAG = "UploadCoordinator"
        private const val MAX_CONCURRENT_UPLOADS = 6
    }

    suspend fun <T : Any> upload(config: UploadConfig<T>): UploadResult<Int> = runPipeline(config)

    suspend fun <T : Any> uploadRoom(config: RoomUploadConfig<T>): UploadResult<Int> = runPipeline(config)

    private suspend fun <T : Any> runPipeline(
        config: UploadPipelineConfig<T>
    ): UploadResult<Int> = withContext(dispatcherProvider.io) {
        try {
            val pendingItems = config.fetchPendingItems.invoke()

            if (pendingItems.isEmpty()) {
                return@withContext UploadResult.Empty
            }

            Log.d(TAG, "Uploading ${pendingItems.size} ${config.modelLabel} items")

            val allSucceeded = mutableListOf<UploadedItem>()
            val allFailed = mutableListOf<UploadError>()

            pendingItems.chunked(config.batchSize).forEachIndexed { batchIndex, rawBatch ->
                Log.d(TAG, "Processing batch ${batchIndex + 1} with ${rawBatch.size} items")

                val batch = queryItemsToUpload(rawBatch, config)

                val (succeeded, failed) = uploadBatch(batch, config)

                var dbFailedErrors = emptyList<UploadError>()
                if (succeeded.isNotEmpty()) {
                    val dbFailed = updateDatabaseBatch(succeeded, config)
                    val (actuallySucceeded, dbErrors) = reconcileDbFailures(succeeded, dbFailed)
                    dbFailedErrors = dbErrors
                    allSucceeded.addAll(actuallySucceeded)
                }

                val batchFailed = failed + dbFailedErrors
                allFailed.addAll(batchFailed)

                if (batchFailed.isNotEmpty()) {
                    queueRetryableFailures(config, batchFailed, batch)
                }
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Critical error during upload", e)
            UploadResult.Failure(
                listOf(UploadError("", e, retryable = true))
            )
        }
    }

    private suspend fun <T : Any> queryItemsToUpload(
        items: List<T>,
        config: UploadPipelineConfig<T>
    ): List<PreparedUpload<T>> {
        return items.mapNotNull { item ->
            if (config.shouldFilter(item)) {
                Log.d(TAG, "Filtering out item from upload")
                return@mapNotNull null
            }

            val serialized = try {
                when (val serializer = config.serializer) {
                    is UploadSerializer.Simple -> serializer.serialize(item)
                    is UploadSerializer.Async -> serializer.serialize(item)
                }
            } catch (e: CancellationException) {
                throw e
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

    private suspend fun <T : Any> uploadBatch(
        batch: List<PreparedUpload<T>>,
        config: UploadPipelineConfig<T>
    ): Pair<List<UploadedItem>, List<UploadError>> {
        val baseUrl = UrlUtils.getUrl()
        val semaphore = Semaphore(MAX_CONCURRENT_UPLOADS)

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

                        val result = semaphore.withPermit {
                            if (preparedItem.dbId.isNullOrEmpty()) {
                                uploadRepository.postUpload(requestUrl, preparedItem.serialized)
                            } else {
                                uploadRepository.putUpload(requestUrl, preparedItem.serialized)
                            }
                        }

                        when (result) {
                            is NetworkResult.Success -> {
                                val responseHandler = config.responseHandler
                                val (idField, revField) = when (responseHandler) {
                                    is ResponseHandler.Standard -> "id" to "rev"
                                    is ResponseHandler.Custom -> responseHandler.idField to responseHandler.revField
                                }

                                val uploadedItem = normalizeUploadResult(
                                    preparedItem.localId,
                                    result.data,
                                    idField,
                                    revField
                                )

                                config.afterUpload?.invoke(preparedItem.item, uploadedItem)
                                BatchItemResult.Success(uploadedItem)
                            }
                            is NetworkResult.Error -> if (result.code == 409) {
                                try {
                                    val docId = preparedItem.dbId ?: preparedItem.localId
                                    val getResult = semaphore.withPermit {
                                        uploadRepository.fetchExistingDoc("$baseUrl/${config.endpoint}/$docId")
                                    }
                                    when (getResult) {
                                        is NetworkResult.Success -> {
                                            val uploadedItem = normalizeUploadResult(
                                                preparedItem.localId,
                                                getResult.data,
                                                "_id",
                                                "_rev"
                                            )
                                            config.afterUpload?.invoke(preparedItem.item, uploadedItem)
                                            BatchItemResult.Success(uploadedItem)
                                        }
                                        is NetworkResult.Error -> BatchItemResult.Error(UploadError(
                                            preparedItem.localId,
                                            Exception("Document exists (409) but couldn't fetch revision"),
                                            retryable = false,
                                            httpCode = 409
                                        ))
                                        is NetworkResult.Exception -> {
                                            val e = getResult.exception
                                            if (e is IOException) {
                                                Log.w(TAG, "Network error fetching existing doc for 409 recovery on item ${preparedItem.localId}", e)
                                                BatchItemResult.Error(UploadError(preparedItem.localId, e, retryable = true, httpCode = 409))
                                            } else {
                                                BatchItemResult.Error(UploadError(
                                                    preparedItem.localId,
                                                    Exception("Document exists (409) but fetch failed: ${e.message}"),
                                                    retryable = false, httpCode = 409
                                                ))
                                            }
                                        }
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
                                val errorMsg = "Upload failed: HTTP ${result.code}"
                                Log.w(TAG, "$errorMsg for item ${preparedItem.localId}")
                                BatchItemResult.Error(UploadError(
                                    preparedItem.localId,
                                    Exception(errorMsg),
                                    retryable = (result.code ?: 0) >= 500,
                                    httpCode = result.code
                                ))
                            }
                            is NetworkResult.Exception -> {
                                val e = result.exception as? Exception ?: Exception(result.exception)
                                if (e is IOException) {
                                    Log.w(TAG, "Network error uploading item ${preparedItem.localId}", e)
                                    BatchItemResult.Error(UploadError(preparedItem.localId, e, retryable = true))
                                } else {
                                    Log.e(TAG, "Unexpected error uploading item ${preparedItem.localId}", e)
                                    BatchItemResult.Error(UploadError(preparedItem.localId, e, retryable = false))
                                }
                            }
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
        config: UploadPipelineConfig<T>
    ): List<UploadedItem> {
        val itemResults = succeeded.map {
            UploadedItemResult(it.localId, it.remoteId, it.remoteRev, it.response)
        }

        val failedResults = config.persistUploaded(uploadRepository, itemResults)

        if (failedResults.isEmpty()) return emptyList()

        val succeededMap = succeeded.associateBy { it.localId }
        return failedResults.mapNotNull { failedResult ->
            succeededMap[failedResult.localId]
        }
    }

    private suspend fun <T : Any> queueRetryableFailures(
        config: UploadPipelineConfig<T>,
        errors: List<UploadError>,
        preparedUploads: List<PreparedUpload<T>>
    ) {
        val retryableErrors = errors.filter { it.retryable }
        if (retryableErrors.isEmpty()) return

        val payloadMap = preparedUploads.associateBy { it.localId }

        retryableErrors.forEach { error ->
            val preparedUpload = payloadMap[error.itemId] ?: return@forEach
            retryQueue.queueFailedOperation(
                uploadType = config.modelLabel,
                error = error,
                payload = preparedUpload.serialized,
                endpoint = config.endpoint,
                httpMethod = if (preparedUpload.dbId.isNullOrEmpty()) "POST" else "PUT",
                dbId = preparedUpload.dbId,
                modelClassName = config.modelLabel
            )
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
