package org.ole.planet.myplanet.services.upload

import org.ole.planet.myplanet.repository.UploadRepository
import org.ole.planet.myplanet.repository.UploadedItemResult

/**
 * Common shape shared by [UploadConfig] (Realm-era models, dispatched through
 * [UploadRepository.markUploaded]) and [RoomUploadConfig] (Room-backed models, persisted through
 * their own DAO-backed [RoomUploadConfig.markUploaded] lambda). Lets [UploadCoordinator] run both
 * through a single batch/HTTP/retry pipeline instead of two duplicated copies.
 */
interface UploadPipelineConfig<T : Any> {
    val endpoint: String
    val modelLabel: String

    val fetchPendingItems: suspend () -> List<T>
    val serializer: UploadSerializer<T>

    val idExtractor: (T) -> String?
    val dbIdExtractor: ((T) -> String?)?

    val responseHandler: ResponseHandler
    val batchSize: Int

    val beforeUpload: (suspend (T) -> Unit)?
    val afterUpload: (suspend (T, UploadedItem) -> Unit)?

    fun shouldFilter(item: T): Boolean = false

    suspend fun persistUploaded(
        uploadRepository: UploadRepository,
        results: List<UploadedItemResult>
    ): List<UploadedItemResult>
}
