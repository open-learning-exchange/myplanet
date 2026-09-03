package org.ole.planet.myplanet.services.upload

import org.ole.planet.myplanet.repository.UploadRepository
import org.ole.planet.myplanet.repository.UploadedItemResult

/**
 * Database-agnostic upload configuration for models that have been migrated to Room.
 *
 * It mirrors [UploadConfig] but replaces the Realm-bound persistence hook: results are persisted
 * through [markUploaded] (a DAO-backed suspend lambda) instead of [UploadRepository.markUploaded].
 * Both configs implement [UploadPipelineConfig], so [UploadCoordinator] runs them through the same
 * batch/HTTP/retry pipeline.
 */
data class RoomUploadConfig<T : Any>(
    override val endpoint: String,
    val modelClassName: String,

    override val fetchPendingItems: suspend () -> List<T>,

    override val serializer: UploadSerializer<T>,

    override val idExtractor: (T) -> String?,
    override val dbIdExtractor: ((T) -> String?)? = null,

    override val responseHandler: ResponseHandler = ResponseHandler.Standard,

    override val batchSize: Int = 50,

    override val beforeUpload: (suspend (T) -> Unit)? = null,
    override val afterUpload: (suspend (T, UploadedItem) -> Unit)? = null,

    /**
     * Persists the results of successful uploads (typically setting `_id`/`_rev` via a DAO) and
     * returns the subset that could not be persisted locally (treated as failures).
     */
    val markUploaded: suspend (List<UploadedItemResult>) -> List<UploadedItemResult>,
) : UploadPipelineConfig<T> {

    override val modelLabel: String
        get() = modelClassName

    override suspend fun persistUploaded(
        uploadRepository: UploadRepository,
        results: List<UploadedItemResult>
    ): List<UploadedItemResult> = markUploaded(results)
}
