package org.ole.planet.myplanet.service.upload

import com.google.gson.JsonObject

sealed class UploadResult<out T> {
    data class Success<T>(
        val data: T,
        val items: List<UploadedItem>
    ) : UploadResult<T>()

    data class PartialSuccess<T>(
        val succeeded: List<UploadedItem>,
        val failed: List<UploadError>
    ) : UploadResult<T>()

    data class Failure(
        val errors: List<UploadError>
    ) : UploadResult<Nothing>()

    object Empty : UploadResult<Nothing>()
}

data class UploadedItem(
    val localId: String,
    val remoteId: String,
    val remoteRev: String,
    val response: JsonObject
)

data class UploadError(
    val itemId: String,
    val exception: Exception,
    val retryable: Boolean,
    val httpCode: Int? = null
) {
    val message: String get() = exception.message ?: "Unknown error"
}
