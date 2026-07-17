package org.ole.planet.myplanet.services.upload

import android.content.Context
import com.google.gson.JsonObject
import io.realm.Realm
import io.realm.RealmObject
import io.realm.RealmQuery
import kotlin.reflect.KClass

data class UploadConfig<T : RealmObject>(
    val modelClass: KClass<T>,
    val endpoint: String,

    val queryBuilder: ((RealmQuery<T>) -> RealmQuery<T>)? = null,

    val fetchPendingItems: (suspend () -> List<T>)? = null,

    val serializer: UploadSerializer<T>,

    val idExtractor: (T) -> String?,

    val dbIdExtractor: ((T) -> String?)? = null,

    val responseHandler: ResponseHandler = ResponseHandler.Standard,

    val filterGuests: Boolean = false,
    val guestUserIdExtractor: ((T) -> String?)? = null,

    val batchSize: Int = 50,

    val beforeUpload: (suspend (T) -> Unit)? = null,
    val afterUpload: (suspend (T, UploadedItem) -> Unit)? = null,

    val additionalUpdates: ((Realm, T, UploadedItem) -> Unit)? = null
) {
    init {
        require(queryBuilder != null || fetchPendingItems != null) {
            "UploadConfig must specify either queryBuilder or fetchPendingItems"
        }
    }
}

// Bound is `Any` (not `RealmObject`) so the same serializer types work for both the Realm
// UploadConfig and the Room RoomUploadConfig upload paths.
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
