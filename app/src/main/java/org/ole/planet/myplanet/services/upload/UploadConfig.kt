package org.ole.planet.myplanet.services.upload

import com.google.gson.JsonObject
import kotlin.reflect.KClass
import org.ole.planet.myplanet.model.StepExam
import org.ole.planet.myplanet.model.Submission
import org.ole.planet.myplanet.repository.UploadRepository
import org.ole.planet.myplanet.repository.UploadUpdateContract
import org.ole.planet.myplanet.repository.UploadUpdateType
import org.ole.planet.myplanet.repository.UploadedItemResult

data class UploadConfig<T : Any>(
    val modelClass: KClass<T>,
    override val endpoint: String,

    override val fetchPendingItems: suspend () -> List<T>,

    override val serializer: UploadSerializer<T>,

    override val idExtractor: (T) -> String?,

    override val dbIdExtractor: ((T) -> String?)? = null,

    override val responseHandler: ResponseHandler = ResponseHandler.Standard,

    val filterGuests: Boolean = false,
    val guestUserIdExtractor: ((T) -> String?)? = null,

    override val batchSize: Int = 50,

    override val beforeUpload: (suspend (T) -> Unit)? = null,
    override val afterUpload: (suspend (T, UploadedItem) -> Unit)? = null,

    val additionalUpdates: ((T, UploadedItem) -> Unit)? = null
) : UploadPipelineConfig<T> {

    override val modelLabel: String
        get() = modelClass.simpleName ?: "Unknown"

    override fun shouldFilter(item: T): Boolean {
        if (!filterGuests) return false
        val userId = guestUserIdExtractor?.invoke(item) ?: return false
        return userId.startsWith("guest")
    }

    override suspend fun persistUploaded(
        uploadRepository: UploadRepository,
        results: List<UploadedItemResult>
    ): List<UploadedItemResult> {
        val updateType = when (modelClass) {
            StepExam::class -> UploadUpdateType.Exams
            Submission::class -> UploadUpdateType.Submissions
            else -> error("Unsupported upload update config: ${modelClass.qualifiedName}")
        }
        return uploadRepository.markUploaded(UploadUpdateContract(updateType), results)
    }
}

sealed class UploadSerializer<T : Any> {
    data class Simple<T : Any>(
        val serialize: (T) -> JsonObject
    ) : UploadSerializer<T>()

    data class Async<T : Any>(
        val serialize: suspend (T) -> JsonObject
    ) : UploadSerializer<T>()
}

sealed class ResponseHandler {
    object Standard : ResponseHandler()

    data class Custom(
        val idField: String,
        val revField: String
    ) : ResponseHandler()
}
