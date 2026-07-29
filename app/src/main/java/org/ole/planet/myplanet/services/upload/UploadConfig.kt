package org.ole.planet.myplanet.services.upload

import android.content.Context
import com.google.gson.JsonObject
import kotlin.reflect.KClass

data class UploadConfig<T : Any>(
    val modelClass: KClass<T>,
    val endpoint: String,

    val fetchPendingItems: suspend () -> List<T>,

    val serializer: UploadSerializer<T>,

    val idExtractor: (T) -> String?,

    val dbIdExtractor: ((T) -> String?)? = null,

    val responseHandler: ResponseHandler = ResponseHandler.Standard,

    val filterGuests: Boolean = false,
    val guestUserIdExtractor: ((T) -> String?)? = null,

    val batchSize: Int = 50,

    val beforeUpload: (suspend (T) -> Unit)? = null,
    val afterUpload: (suspend (T, UploadedItem) -> Unit)? = null,

    val additionalUpdates: ((T, UploadedItem) -> Unit)? = null
)

sealed class UploadSerializer<T : Any> {
    data class Simple<T : Any>(
        val serialize: (T) -> JsonObject
    ) : UploadSerializer<T>()

    data class WithContext<T : Any>(
        val serialize: (T, Context) -> JsonObject
    ) : UploadSerializer<T>()

    data class Async<T : Any>(
        val serialize: suspend (T) -> JsonObject
    ) : UploadSerializer<T>()

    data class AsyncContext<T : Any>(
        val serialize: suspend (T, Context) -> JsonObject
    ) : UploadSerializer<T>()
}

sealed class ResponseHandler {
    object Standard : ResponseHandler()

    data class Custom(
        val idField: String,
        val revField: String
    ) : ResponseHandler()
}
