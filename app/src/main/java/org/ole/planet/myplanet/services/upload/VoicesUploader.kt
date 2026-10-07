package org.ole.planet.myplanet.services.upload

import android.text.TextUtils
import android.util.Log
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.repository.NewsUpdateData
import org.ole.planet.myplanet.repository.NewsUploadData
import org.ole.planet.myplanet.repository.UploadRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.repository.VoicesRepository
import org.ole.planet.myplanet.services.FileUploader
import org.ole.planet.myplanet.services.retry.RetryQueue
import org.ole.planet.myplanet.services.upload.UploadConstants.BATCH_SIZE
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.FileUtils
import org.ole.planet.myplanet.utils.KJson
import org.ole.planet.myplanet.utils.KJson.getString
import org.ole.planet.myplanet.utils.NetworkUtils
import org.ole.planet.myplanet.utils.TimeProvider
import org.ole.planet.myplanet.utils.UrlUtils
import org.ole.planet.myplanet.utils.addDocumentOrigin

private inline fun <T> Iterable<T>.processInBatches(action: (List<T>) -> Unit) {
    chunked(BATCH_SIZE).forEach(action)
}

class VoicesUploader @Inject constructor(
    private val userRepository: UserRepository,
    private val voicesRepository: VoicesRepository,
    private val uploadRepository: UploadRepository,
    private val retryQueue: RetryQueue,
    private val timeProvider: TimeProvider,
    private val dispatcherProvider: DispatcherProvider
) {

    private fun createImage(user: UserEntity?, imgObject: JsonObject?): JsonObject = buildJsonObject {
        put("title", getString("fileName", imgObject))
        put("createdDate", timeProvider.now())
        put("filename", getString("fileName", imgObject))
        put("private", true)
        user?.id?.let { put("addedBy", it) }
        user?.parentCode?.let { put("resideOn", it) }
        user?.planetCode?.let { put("sourcePlanet", it) }
        addDocumentOrigin()
        put("deviceName", NetworkUtils.getDeviceName())
        put("customDeviceName", NetworkUtils.getCustomDeviceName(MainApplication.context))
        put("privateFor", JsonObject(emptyMap()))
        put("mediaType", "image")
    }

    suspend fun uploadNews() {
        val user = userRepository.getUserModel()
        val newsItems = voicesRepository.getNewsForUpload()

        withContext(dispatcherProvider.io) {
            newsItems.processInBatches { batch ->
                val successfulUpdates = mutableListOf<NewsUpdateData>()
                val processedNews = mutableListOf<Triple<NewsUploadData, JsonArray, JsonObject>>()

                batch.forEach { news ->
                    try {
                        // Upload images first and collect metadata
                        val images = mutableListOf<JsonObject>()
                        val messageWithImages = StringBuilder(news.message ?: "")

                        news.imageUrls.forEach { imageUrl ->
                            val imgObject = KJson.parseObjectOrNull(imageUrl)

                            // Create image resource document
                            val imageDoc = createImage(user, imgObject)
                            val imageResponse = uploadRepository.postJson(
                                "${UrlUtils.getUrl()}/resources",
                                imageDoc
                            ).body()

                            val resourceId = getString("id", imageResponse)
                            val resourceRev = getString("rev", imageResponse)

                            // Upload image file as attachment
                            val imageFile = File(getString("imageUrl", imgObject))
                            val fileName = FileUtils.getFileNameFromUrl(getString("imageUrl", imgObject))
                            val mimeType = FileUtils.getMimeType(fileName) ?: "application/octet-stream"
                            val fileBody = imageFile.asRequestBody("application/octet-stream".toMediaTypeOrNull())

                            uploadRepository.uploadResourceJson(
                                FileUploader.getHeaderMap(mimeType, resourceRev),
                                "${UrlUtils.getUrl()}/resources/$resourceId/$fileName",
                                fileBody
                            )

                            val markdown = "![](resources/$resourceId/$fileName)"
                            images.add(
                                buildJsonObject {
                                    put("resourceId", resourceId)
                                    put("filename", fileName)
                                    put("markdown", markdown)
                                }
                            )

                            messageWithImages.append("\n").append(markdown)
                        }

                        val imagesArray = JsonArray(images)
                        // Replaces the two fields in place, keeping the key order the Gson edit kept.
                        val newsJson = JsonObject(
                            news.newsJson + mapOf("message" to JsonPrimitive(messageWithImages.toString()), "images" to imagesArray)
                        )

                        processedNews.add(Triple(news, imagesArray, newsJson))
                    } catch (e: Exception) {
                        Log.e(TAG, "Exception in VoicesUploader processing images for news", e)
                        val isCreate = TextUtils.isEmpty(news._id)
                        queueNewsRetry(news, news.newsJson, null, if (isCreate) "POST" else "PUT", e)
                    }
                }

                BulkDocsUploader.upload(
                    uploadRepository,
                    "${UrlUtils.getUrl()}/news/_bulk_docs",
                    processedNews.map { (news, imagesArray, newsJson) -> Triple(news, imagesArray, newsJson) to newsJson }
                ) { (news, imagesArray, newsJson), outcome ->
                    val isCreate = TextUtils.isEmpty(news._id)
                    when (outcome) {
                        is BulkDocsUploader.Outcome.Accepted -> {
                            successfulUpdates.add(
                                NewsUpdateData(
                                    id = news.id,
                                    _id = KJson.getString("id", outcome.element),
                                    _rev = KJson.getString("rev", outcome.element),
                                    imagesArray = imagesArray
                                )
                            )
                        }
                        is BulkDocsUploader.Outcome.Rejected -> {
                            val errorReason = KJson.gsonAsString(outcome.element["error"])
                            queueNewsRetry(news, newsJson, null, if (isCreate) "POST" else "PUT", Exception("Bulk upload error: $errorReason"))
                        }
                        is BulkDocsUploader.Outcome.RequestFailed -> {
                            queueNewsRetry(news, newsJson, outcome.httpCode, if (isCreate) "POST" else "PUT", outcome.exception)
                        }
                    }
                }

                if (successfulUpdates.isNotEmpty()) {
                    voicesRepository.markNewsUploaded(successfulUpdates)
                }
            }
        }
    }

    private suspend fun queueNewsRetry(
        news: NewsUploadData,
        payload: JsonObject,
        httpCode: Int?,
        httpMethod: String,
        exception: Exception? = null
    ) {
        val retryable = exception != null || (httpCode != null && httpCode >= 500)
        if (!retryable) return
        retryQueue.queueFailedOperation(
            uploadType = "News",
            error = UploadError(
                itemId = news.id ?: "",
                exception = exception ?: Exception("Upload failed: HTTP $httpCode"),
                retryable = true,
                httpCode = httpCode
            ),
            // newsJson keeps the number literals of the stored columns it was built from, as the Gson tree did.
            payload = payload,
            endpoint = "news",
            httpMethod = httpMethod,
            dbId = news._id,
            modelClassName = "News",
            numbers = KJson.GsonNumbers.LITERAL
        )
    }

    companion object {
        private const val TAG = "VoicesUploader"
    }
}
