package org.ole.planet.myplanet.repository

import com.google.gson.JsonObject
import io.realm.Realm
import io.realm.RealmObject
import io.realm.RealmQuery
import kotlin.reflect.KClass
import org.ole.planet.myplanet.services.upload.UploadConfig
import org.ole.planet.myplanet.services.upload.UploadedItem
import retrofit2.Response

interface UploadRepository {
    suspend fun <T: RealmObject> queryPending(config: UploadQueryContract<T>): List<T>
    suspend fun <T: RealmObject> markUploaded(config: UploadUpdateContract<T>, succeeded: List<UploadedItemResult>): List<UploadedItemResult>
    suspend fun postUpload(url: String, serializedData: JsonObject): Response<JsonObject>
    suspend fun putUpload(url: String, serializedData: JsonObject): Response<JsonObject>
    suspend fun fetchExistingDoc(url: String): Response<JsonObject>
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
