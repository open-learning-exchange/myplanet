package org.ole.planet.myplanet.services.upload

import org.ole.planet.myplanet.repository.UploadRepository
import org.ole.planet.myplanet.repository.UploadedItemResult
import org.ole.planet.myplanet.utils.KJson.GsonNumbers

interface UploadPipelineConfig<T : Any> {
    val endpoint: String
    val modelLabel: String

    val fetchPendingItems: suspend () -> List<T>
    val serializer: UploadSerializer<T>

    val idExtractor: (T) -> String?
    val dbIdExtractor: ((T) -> String?)?

    val responseHandler: ResponseHandler

    /**
     * How the retry queue prints a failed item's payload, so the stored text is the bytes Gson
     * wrote before the migration: [GsonNumbers.BRIDGED] for documents the models build (already
     * normalised with `withGsonBridgedNumbers`), [GsonNumbers.LITERAL] for documents a repository
     * builds from typed values, whose number text is already what Gson printed.
     */
    val payloadNumbers: GsonNumbers
    val batchSize: Int

    val beforeUpload: (suspend (T) -> Unit)?
    val afterUpload: (suspend (T, UploadedItem) -> Unit)?

    fun shouldFilter(item: T): Boolean = false

    suspend fun persistUploaded(
        uploadRepository: UploadRepository,
        results: List<UploadedItemResult>
    ): List<UploadedItemResult>
}
