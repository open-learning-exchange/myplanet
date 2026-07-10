package org.ole.planet.myplanet.repository

import com.google.gson.JsonObject
import io.realm.RealmObject
import org.ole.planet.myplanet.services.upload.UploadConfig
import org.ole.planet.myplanet.services.upload.UploadedItem
import retrofit2.Response

interface UploadRepository {
    suspend fun <T: RealmObject> queryPending(config: UploadConfig<T>): List<T>
    suspend fun <T: RealmObject> markUploaded(config: UploadConfig<T>, succeeded: List<UploadedItem>): List<UploadedItem>
    suspend fun postUpload(url: String, serializedData: JsonObject): Response<JsonObject>
    suspend fun putUpload(url: String, serializedData: JsonObject): Response<JsonObject>
    suspend fun fetchExistingDoc(url: String): Response<JsonObject>
}
