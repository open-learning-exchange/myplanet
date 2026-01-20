package org.ole.planet.myplanet.service.upload

import android.content.Context
import android.util.Log
import com.google.gson.JsonObject
import dagger.hilt.android.qualifiers.ApplicationContext
import io.realm.RealmObject
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.data.ApiInterface
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.utilities.JsonUtils.getString
import org.ole.planet.myplanet.utilities.UrlUtils

@Singleton
class UploadCoordinator @Inject constructor(
    private val databaseService: DatabaseService,
    private val apiInterface: ApiInterface,
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "UploadCoordinator"
        private const val SYNC_DATA_TAG = "SyncData"
    }

    suspend fun <T : RealmObject> upload(
        config: UploadConfig<T>
    ): UploadResult<Int> = withContext(Dispatchers.IO) {
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

                if (succeeded.isNotEmpty()) {
                    updateDatabaseBatch(succeeded, config)
                }

                allSucceeded.addAll(succeeded)
                allFailed.addAll(failed)
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

    private suspend fun <T : RealmObject> queryItemsToUpload(
        config: UploadConfig<T>
    ): List<PreparedUpload<T>> = databaseService.withRealmAsync { realm ->
        val query = realm.where(config.modelClass.java)
        val filteredQuery = config.queryBuilder(query)
        val results = filteredQuery.findAll()

        results.mapNotNull { item ->
            val copiedItem = realm.copyFromRealm(item)

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
                    is UploadSerializer.WithRealm -> serializer.serialize(realm, copiedItem)
                    is UploadSerializer.WithContext -> serializer.serialize(copiedItem, context)
                    is UploadSerializer.Full -> serializer.serialize(realm, copiedItem, context)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Serialization failed for item", e)
                return@mapNotNull null
            }

            val localId = config.idExtractor(copiedItem) ?: ""
            val dbId = config.dbIdExtractor?.invoke(copiedItem)

            // Log the full serialized data for sync visibility
            Log.d(SYNC_DATA_TAG, "========== SYNC DATA START ==========")
            Log.d(SYNC_DATA_TAG, "Model: ${config.modelClass.simpleName}")
            Log.d(SYNC_DATA_TAG, "Endpoint: ${config.endpoint}")
            Log.d(SYNC_DATA_TAG, "Local ID: $localId")
            Log.d(SYNC_DATA_TAG, "DB ID (_id): $dbId")
            Log.d(SYNC_DATA_TAG, "Operation: ${if (dbId.isNullOrEmpty()) "POST (new)" else "PUT (update)"}")
            Log.d(SYNC_DATA_TAG, "--- Full Serialized JSON Data ---")
            // Log JSON in chunks to avoid logcat truncation
            val jsonString = serialized.toString()
            val chunkSize = 3000
            var offset = 0
            while (offset < jsonString.length) {
                val end = minOf(offset + chunkSize, jsonString.length)
                Log.d(SYNC_DATA_TAG, "JSON[$offset-$end]: ${jsonString.substring(offset, end)}")
                offset = end
            }
            Log.d(SYNC_DATA_TAG, "========== SYNC DATA END ==========")

            PreparedUpload(
                item = copiedItem,
                localId = localId,
                dbId = dbId,
                serialized = serialized
            )
        }
    }

    private suspend fun <T : RealmObject> uploadBatch(
        batch: List<PreparedUpload<T>>,
        config: UploadConfig<T>
    ): Pair<List<UploadedItem>, List<UploadError>> {
        val succeeded = mutableListOf<UploadedItem>()
        val failed = mutableListOf<UploadError>()

        batch.forEach { preparedItem ->
            try {
                config.beforeUpload?.invoke(preparedItem.item)

                val requestUrl = if (preparedItem.dbId.isNullOrEmpty()) {
                    "${UrlUtils.getUrl()}/${config.endpoint}"
                } else {
                    "${UrlUtils.getUrl()}/${config.endpoint}/${preparedItem.dbId}"
                }

                Log.d(SYNC_DATA_TAG, "========== API REQUEST ==========")
                Log.d(SYNC_DATA_TAG, "URL: $requestUrl")
                Log.d(SYNC_DATA_TAG, "Method: ${if (preparedItem.dbId.isNullOrEmpty()) "POST" else "PUT"}")
                Log.d(SYNC_DATA_TAG, "Item Local ID: ${preparedItem.localId}")

                val response = if (preparedItem.dbId.isNullOrEmpty()) {
                    apiInterface.postDocSuspend(UrlUtils.header, "application/json", requestUrl, preparedItem.serialized)
                } else {
                    apiInterface.putDocSuspend(UrlUtils.header, "application/json", requestUrl, preparedItem.serialized)
                }

                Log.d(SYNC_DATA_TAG, "Response Code: ${response.code()}")

                if (response.isSuccessful && response.body() != null) {
                    val responseBody = response.body()!!

                    Log.d(SYNC_DATA_TAG, "Response Body: $responseBody")
                    Log.d(SYNC_DATA_TAG, "========== API REQUEST SUCCESS ==========")

                    val (idField, revField) = when (config.responseHandler) {
                        is ResponseHandler.Standard -> "id" to "rev"
                        is ResponseHandler.Custom -> config.responseHandler.idField to config.responseHandler.revField
                    }

                    val uploadedItem = UploadedItem(
                        localId = preparedItem.localId,
                        remoteId = getString(idField, responseBody),
                        remoteRev = getString(revField, responseBody),
                        response = responseBody
                    )

                    Log.d(SYNC_DATA_TAG, "Uploaded Item - Remote ID: ${uploadedItem.remoteId}, Rev: ${uploadedItem.remoteRev}")

                    config.afterUpload?.invoke(preparedItem.item, uploadedItem)
                    succeeded.add(uploadedItem)
                } else {
                    val errorMsg = "Upload failed: HTTP ${response.code()}"
                    Log.w(TAG, "$errorMsg for item ${preparedItem.localId}")
                    Log.w(SYNC_DATA_TAG, "========== API REQUEST FAILED ==========")
                    Log.w(SYNC_DATA_TAG, "Error: $errorMsg")
                    Log.w(SYNC_DATA_TAG, "Response Error Body: ${response.errorBody()?.string()}")
                    failed.add(UploadError(
                        preparedItem.localId,
                        Exception(errorMsg),
                        retryable = response.code() >= 500,
                        httpCode = response.code()
                    ))
                }
            } catch (e: IOException) {
                Log.w(TAG, "Network error uploading item ${preparedItem.localId}", e)
                Log.w(SYNC_DATA_TAG, "========== API REQUEST NETWORK ERROR ==========")
                Log.w(SYNC_DATA_TAG, "Error: ${e.message}")
                failed.add(UploadError(preparedItem.localId, e, retryable = true))
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error uploading item ${preparedItem.localId}", e)
                Log.e(SYNC_DATA_TAG, "========== API REQUEST UNEXPECTED ERROR ==========")
                Log.e(SYNC_DATA_TAG, "Error: ${e.message}", e)
                failed.add(UploadError(preparedItem.localId, e, retryable = false))
            }
        }
        return succeeded to failed
    }

    private suspend fun <T : RealmObject> updateDatabaseBatch(
        succeeded: List<UploadedItem>,
        config: UploadConfig<T>
    ) {
        databaseService.executeTransactionAsync { realm ->
            succeeded.forEach { uploadedItem ->
                try {
                    val item = realm.where(config.modelClass.java).equalTo(
                        getIdFieldName(config.modelClass),
                        uploadedItem.localId).findFirst()

                    item?.let {
                        setRealmField(it, "_id", uploadedItem.remoteId)
                        setRealmField(it, "_rev", uploadedItem.remoteRev)
                        config.additionalUpdates?.invoke(realm, it, uploadedItem)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to update item ${uploadedItem.localId}", e)
                }
            }
        }
    }

    private fun getIdFieldName(modelClass: KClass<out RealmObject>): String {
        return "id"
    }

    private fun setRealmField(obj: RealmObject, fieldName: String, value: Any?) {
        try {
            val field = obj.javaClass.getDeclaredField(fieldName)
            field.isAccessible = true
            field.set(obj, value)
        } catch (e: NoSuchFieldException) {
            Log.w(TAG, "Field $fieldName not found on ${obj.javaClass.simpleName}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set field $fieldName: ${e.message}")
        }
    }
}

private data class PreparedUpload<T : RealmObject>(
    val item: T,
    val localId: String,
    val dbId: String?,
    val serialized: JsonObject
)
