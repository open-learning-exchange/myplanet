package org.ole.planet.myplanet.repository

import io.realm.Realm
import io.realm.RealmObject
import io.realm.RealmQuery
import kotlin.reflect.KClass

interface UploadRepository {
    suspend fun <T: RealmObject> queryPending(config: UploadQueryContract<T>): List<T>
    suspend fun <T: RealmObject> markUploaded(config: UploadUpdateContract<T>, succeeded: List<UploadedItemResult>): List<UploadedItemResult>
}

data class UploadQueryContract<T : RealmObject>(
    val modelClass: KClass<T>,
    val queryBuilder: (RealmQuery<T>) -> RealmQuery<T>
)

data class UploadUpdateContract<T : RealmObject>(
    val modelClass: KClass<T>,
    val idExtractor: (T) -> String?,
    val additionalUpdates: ((Realm, T, UploadedItemResult) -> Unit)? = null
)

data class UploadedItemResult(
    val localId: String,
    val remoteId: String,
    val remoteRev: String,
    val response: com.google.gson.JsonObject
)
