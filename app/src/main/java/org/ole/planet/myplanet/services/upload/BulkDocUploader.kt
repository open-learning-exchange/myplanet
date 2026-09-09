package org.ole.planet.myplanet.services.upload

import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.ole.planet.myplanet.repository.UploadRepository

object BulkDocUploader {
    private const val TAG = "BulkDocUploader"

    sealed class Outcome {
        data class Accepted(val element: JsonObject) : Outcome()
        data class Rejected(val element: JsonObject, val httpCode: Int) : Outcome()
        data class RequestFailed(val httpCode: Int?, val exception: Exception?) : Outcome()
    }

    suspend fun <T> upload(
        uploadRepository: UploadRepository,
        url: String,
        items: List<Pair<T, JsonObject>>,
        onResult: suspend (item: T, outcome: Outcome) -> Unit
    ) {
        if (items.isEmpty()) return

        val bulkDocs = JsonArray()
        items.forEach { (_, doc) -> bulkDocs.add(doc) }
        val payload = JsonObject().apply { add("docs", bulkDocs) }

        try {
            val response = uploadRepository.postUploadArray(url, payload)
            val responseBody = response.body()

            if (response.isSuccessful && responseBody != null) {
                if (responseBody.size() < items.size) {
                    Log.w(TAG, "Bulk upload to $url returned ${responseBody.size()} result(s) for a batch of ${items.size}; ${items.size - responseBody.size()} item(s) were not processed and will retry next sync")
                }
                for (i in 0 until responseBody.size()) {
                    val element = responseBody.get(i).asJsonObject
                    val (item, _) = items.getOrNull(i) ?: continue
                    val outcome = if (element.has("error")) {
                        Outcome.Rejected(element, response.code())
                    } else {
                        Outcome.Accepted(element)
                    }
                    onResult(item, outcome)
                }
            } else {
                items.forEach { (item, _) -> onResult(item, Outcome.RequestFailed(response.code(), null)) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during bulk upload to $url", e)
            items.forEach { (item, _) -> onResult(item, Outcome.RequestFailed(null, e)) }
        }
    }
}
