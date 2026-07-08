package org.ole.planet.myplanet.repository

import io.realm.RealmObject
import org.ole.planet.myplanet.services.upload.UploadConfig
import org.ole.planet.myplanet.services.upload.UploadedItem

import com.google.gson.JsonObject
import retrofit2.Response

interface UploadRepository {
    suspend fun <T: RealmObject> queryPending(config: UploadConfig<T>): List<T>
    suspend fun <T: RealmObject> markUploaded(config: UploadConfig<T>, succeeded: List<UploadedItem>): List<UploadedItem>
    suspend fun executeUploadRequest(url: String, isPut: Boolean, serializedData: JsonObject): Response<JsonObject>
    suspend fun handleConflictResolution(url: String): Response<JsonObject>
    fun normalizeUploadResult(localId: String, responseBody: JsonObject, idField: String, revField: String): UploadedItem
}
