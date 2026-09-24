package org.ole.planet.myplanet.services.upload

import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import org.ole.planet.myplanet.data.NetworkResult
import org.ole.planet.myplanet.repository.UploadRepository
import org.ole.planet.myplanet.utils.UrlUtils

object BulkDocsUploader {
    private const val TAG = "BulkDocsUploader"

    sealed class Outcome {
        data class Accepted(val element: JsonObject) : Outcome()
        data class Rejected(val element: JsonObject, val httpCode: Int?) : Outcome()
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
            when (val result = uploadRepository.postUploadArray(url, payload)) {
                is NetworkResult.Success -> {
                    val responseBody = result.data
                    if (responseBody.size() < items.size) {
                        Log.w(TAG, "Bulk upload to ${UrlUtils.redactForLog(url)} returned ${responseBody.size()} result(s) for a batch of ${items.size}; ${items.size - responseBody.size()} item(s) were not processed and will retry next sync")
                    }
                    for (i in 0 until responseBody.size()) {
                        val element = responseBody.get(i).asJsonObject
                        val (item, _) = items.getOrNull(i) ?: continue
                        val outcome = if (element.has("error")) {
                            Outcome.Rejected(element, null)
                        } else {
                            Outcome.Accepted(element)
                        }
                        onResult(item, outcome)
                    }
                }
                is NetworkResult.Error -> {
                    items.forEach { (item, _) -> onResult(item, Outcome.RequestFailed(result.code, null)) }
                }
                is NetworkResult.Exception -> {
                    Log.e(TAG, "Exception during bulk upload to ${UrlUtils.redactForLog(url)}", result.exception)
                    val e = result.exception as? Exception ?: Exception(result.exception)
                    items.forEach { (item, _) -> onResult(item, Outcome.RequestFailed(null, e)) }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Exception during bulk upload to ${UrlUtils.redactForLog(url)}", e)
            items.forEach { (item, _) -> onResult(item, Outcome.RequestFailed(null, e)) }
        }
    }
}
