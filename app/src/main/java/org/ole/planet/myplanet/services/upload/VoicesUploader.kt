package org.ole.planet.myplanet.services.upload

import android.text.TextUtils
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.withContext
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
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.FileUtils
import org.ole.planet.myplanet.utils.JsonUtils.getString
import org.ole.planet.myplanet.utils.NetworkUtils
import org.ole.planet.myplanet.utils.TimeProvider
import org.ole.planet.myplanet.utils.UrlUtils
import org.ole.planet.myplanet.utils.addDocumentOrigin

class VoicesUploader @Inject constructor(
    private val gson: Gson,
    private val uploadRepository: UploadRepository,
    private val voicesRepository: VoicesRepository,
    private val userRepository: UserRepository,
    private val uploadCoordinator: UploadCoordinator,
    private val uploadConfigs: UploadConfigs,
    private val retryQueue: RetryQueue,
    private val dispatcherProvider: DispatcherProvider,
    private val timeProvider: TimeProvider
) {

    suspend fun uploadNews() {
        // Note: uploadNews has unique logic that requires uploading images BEFORE the news document,
        // then modifying the serialized JSON based on image upload responses. This doesn't fit the
        // standard UploadCoordinator pattern (a single serialize-then-POST/PUT per item), so the
        // per-item image handling stays custom here — but the bulk_docs POST and response walk
        // go through the same BulkDocsUploader used by TeamsUploader, instead of a separate copy.
        val user = userRepository.getUserModel()
        val newsItems = voicesRepository.getNewsForUpload()

        withContext(dispatcherProvider.io) {
            newsItems.processInBatches { batch ->
                val successfulUpdates = mutableListOf<NewsUpdateData>()
                val processedNews = mutableListOf<Pair<NewsUploadData, JsonArray>>()

                batch.forEach { news ->
                    try {
                        // Upload images first and collect metadata
                        val imagesArray = JsonArray()
                        val messageWithImages = StringBuilder(news.message ?: "")

                        news.imageUrls.forEach { imageUrl ->
                            val imgObject = gson.fromJson(imageUrl, JsonObject::class.java)

                            // Create image resource document
                            val imageDoc = createImage(user, imgObject)
                            val imageResponse = uploadRepository.postUpload(
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

                            uploadRepository.uploadResource(
                                FileUploader.getHeaderMap(mimeType, resourceRev),
                                "${UrlUtils.getUrl()}/resources/$resourceId/$fileName",
                                fileBody
                            )

                            val resourceObject = JsonObject()
                            resourceObject.addProperty("resourceId", resourceId)
                            resourceObject.addProperty("filename", fileName)
                            val markdown = "![](resources/$resourceId/$fileName)"
                            resourceObject.addProperty("markdown", markdown)
                            imagesArray.add(resourceObject)

                            messageWithImages.append("\n").append(markdown)
                        }

                        val newsJson = news.newsJson
                        newsJson.addProperty("message", messageWithImages.toString())
                        newsJson.add("images", imagesArray)

                        processedNews.add(Pair(news, imagesArray))
                    } catch (e: Exception) {
                        Log.e(TAG, "Exception processing images for news", e)
                        val isCreate = TextUtils.isEmpty(news._id)
                        queueNewsRetry(news, news.newsJson, null, if (isCreate) "POST" else "PUT", e)
                    }
                }

                BulkDocsUploader.upload(
                    uploadRepository,
                    "${UrlUtils.getUrl()}/news/_bulk_docs",
                    processedNews.map { (news, imagesArray) -> (news to imagesArray) to news.newsJson }
                ) { (news, imagesArray), outcome ->
                    val isCreate = TextUtils.isEmpty(news._id)
                    when (outcome) {
                        is BulkDocsUploader.Outcome.Accepted -> {
                            successfulUpdates.add(NewsUpdateData(
                                id = news.id,
                                _id = getString("id", outcome.element),
                                _rev = getString("rev", outcome.element),
                                imagesArray = imagesArray
                            ))
                        }
                        is BulkDocsUploader.Outcome.Rejected -> {
                            val errorReason = outcome.element.get("error").asString
                            queueNewsRetry(news, news.newsJson, null, if (isCreate) "POST" else "PUT", Exception("Bulk upload error: $errorReason"))
                        }
                        is BulkDocsUploader.Outcome.RequestFailed -> {
                            queueNewsRetry(news, news.newsJson, outcome.httpCode, if (isCreate) "POST" else "PUT", outcome.exception)
                        }
                    }
                }

                if (successfulUpdates.isNotEmpty()) {
                    voicesRepository.markNewsUploaded(successfulUpdates)
                }
            }
        }
        uploadNewsActivities()
    }

    private suspend fun uploadNewsActivities() {
        uploadCoordinator.uploadRoom(uploadConfigs.NewsActivities)
    }

    private fun createImage(user: UserEntity?, imgObject: JsonObject?): JsonObject {
        val `object` = JsonObject()
        `object`.addProperty("title", getString("fileName", imgObject))
        `object`.addProperty("createdDate", timeProvider.now())
        `object`.addProperty("filename", getString("fileName", imgObject))
        `object`.addProperty("private", true)
        user?.id?.let { `object`.addProperty("addedBy", it) }
        user?.parentCode?.let { `object`.addProperty("resideOn", it) }
        user?.planetCode?.let { `object`.addProperty("sourcePlanet", it) }
        val object1 = JsonObject()
        `object`.addDocumentOrigin()
        `object`.addProperty("deviceName", NetworkUtils.getDeviceName())
        `object`.addProperty("customDeviceName", NetworkUtils.getCustomDeviceName(MainApplication.context))
        `object`.add("privateFor", object1)
        `object`.addProperty("mediaType", "image")
        return `object`
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
            payload = payload,
            endpoint = "news",
            httpMethod = httpMethod,
            dbId = news._id,
            modelClassName = "News"
        )
    }

    companion object {
        private const val TAG = "VoicesUploader"
    }
}
