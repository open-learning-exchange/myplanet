package org.ole.planet.myplanet.service.upload

import android.content.Context
import com.google.gson.JsonObject
import io.realm.Realm
import io.realm.RealmObject
import io.realm.RealmQuery
import kotlin.reflect.KClass

data class UploadConfig<T : RealmObject>(
    val modelClass: KClass<T>,
    val endpoint: String,

    val queryBuilder: (RealmQuery<T>) -> RealmQuery<T>,

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
)

sealed class UploadSerializer<T : RealmObject> {
    data class Simple<T : RealmObject>(
        val serialize: (T) -> JsonObject
    ) : UploadSerializer<T>()

    data class WithRealm<T : RealmObject>(
        val serialize: (Realm, T) -> JsonObject
    ) : UploadSerializer<T>()

    data class WithContext<T : RealmObject>(
        val serialize: (T, Context) -> JsonObject
    ) : UploadSerializer<T>()

    data class Full<T : RealmObject>(
        val serialize: (Realm, T, Context) -> JsonObject
    ) : UploadSerializer<T>()
}

sealed class ResponseHandler {
    object Standard : ResponseHandler()

    data class Custom(
        val idField: String,
        val revField: String
    ) : ResponseHandler()
}
