package org.ole.planet.myplanet.services.upload

import android.text.TextUtils
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.File
import java.io.IOException
import java.util.Base64
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.repository.NewsUpdateData
import org.ole.planet.myplanet.repository.NewsUploadData
import org.ole.planet.myplanet.repository.UploadRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.repository.VoicesRepository
import org.ole.planet.myplanet.services.retry.RetryQueue
import org.ole.planet.myplanet.services.upload.UploadConstants.BATCH_SIZE
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.FileUtils
import org.ole.planet.myplanet.utils.GsonUtils.getString
import org.ole.planet.myplanet.utils.NetworkUtils
import org.ole.planet.myplanet.utils.TimeProvider
import org.ole.planet.myplanet.utils.UrlUtils
import org.ole.planet.myplanet.utils.VoiceImagePreparer
import org.ole.planet.myplanet.utils.addDocumentOrigin

private inline fun <T> Iterable<T>.processInBatches(action: (List<T>) -> Unit) {
    chunked(BATCH_SIZE).forEach(action)
}

class VoicesUploader @Inject constructor(
    private val gson: Gson,
    private val userRepository: UserRepository,
    private val voicesRepository: VoicesRepository,
    private val uploadRepository: UploadRepository,
    private val retryQueue: RetryQueue,
    private val timeProvider: TimeProvider,
    private val dispatcherProvider: DispatcherProvider
) {

    private fun createMedia(user: UserEntity?, fileName: String, mediaType: String): JsonObject {
        val `object` = JsonObject()
        `object`.addProperty("title", fileName)
        `object`.addProperty("createdDate", timeProvider.now())
        `object`.addProperty("filename", fileName)
        `object`.addProperty("private", true)
        user?.id?.let { `object`.addProperty("addedBy", it) }
        user?.parentCode?.let { `object`.addProperty("resideOn", it) }
        user?.planetCode?.let { `object`.addProperty("sourcePlanet", it) }
        val object1 = JsonObject()
        `object`.addDocumentOrigin()
        `object`.addProperty("deviceName", NetworkUtils.getDeviceName())
        `object`.addProperty("customDeviceName", NetworkUtils.getCustomDeviceName(MainApplication.context))
        `object`.add("privateFor", object1)
        `object`.addProperty("mediaType", mediaType)
        return `object`
    }

    private suspend fun putImage(user: UserEntity?, resourceId: String, fileName: String, imageFile: File) {
        val mimeType = FileUtils.getMimeType(fileName) ?: "application/octet-stream"
        val doc = createMedia(user, fileName, "image")
        val attachment = JsonObject().apply {
            addProperty("content_type", mimeType)
            addProperty("data", Base64.getEncoder().encodeToString(imageFile.readBytes()))
        }
        doc.add("_attachments", JsonObject().apply { add(fileName, attachment) })
        val code = uploadRepository.putUpload("${UrlUtils.getUrl()}/resources/$resourceId", doc).code()
        if (code != 201 && code != 202 && code != 409) {
            throw IOException("Image upload for $resourceId failed: HTTP $code")
        }
    }

    // Videos are too large to inline as base64, so the resource doc is PUT first and the file is
    // streamed as a separate attachment. A 409 means an earlier attempt created the doc; reuse its
    // rev, and skip the attachment entirely if that attempt already got it up.
    private suspend fun putVideo(user: UserEntity?, resourceId: String, fileName: String, videoFile: File) {
        val url = "${UrlUtils.getUrl()}/resources/$resourceId"
        val created = uploadRepository.putUpload(url, createMedia(user, fileName, "video"))
        val rev = when (created.code()) {
            201, 202 -> getString("rev", created.body())
            409 -> {
                val existing = uploadRepository.fetchExistingDoc(url)
                val body = existing.body()?.takeIf { existing.isSuccessful }
                    ?: throw IOException("Video lookup for $resourceId failed: HTTP ${existing.code()}")
                if ((body.get("_attachments") as? JsonObject)?.has(fileName) == true) return
                getString("_rev", body)
            }
            else -> throw IOException("Video upload for $resourceId failed: HTTP ${created.code()}")
        }
        val code = uploadRepository.uploadAttachment(videoFile, "%s/resources/%s/%s", resourceId, rev, fileName).code()
        if (code != 201 && code != 202) {
            throw IOException("Video attachment for $resourceId failed: HTTP $code")
        }
    }

    private class PreparedNews(
        val news: NewsUploadData,
        val imagesArray: JsonArray,
        val videosArray: JsonArray,
        val files: List<File>
    )

    suspend fun uploadNews() {
        val user = userRepository.getUserModel()
        val newsItems = voicesRepository.getNewsForUpload()

        withContext(dispatcherProvider.io) {
            newsItems.processInBatches { batch ->
                val successfulUpdates = mutableListOf<NewsUpdateData>()
                val processedNews = mutableListOf<PreparedNews>()
                val uploadedFiles = mutableListOf<File>()

                batch.forEach { news ->
                    try {
                        val imagesArray = (news.newsJson.get("images") as? JsonArray)?.deepCopy() ?: JsonArray()
                        val videosArray = (news.newsJson.get("videos") as? JsonArray)?.deepCopy() ?: JsonArray()
                        val messageWithMedia = StringBuilder(news.message ?: "")

                        val files = mutableListOf<File>()
                        news.imageUrls.forEach { imageUrl ->
                            val imgObject = gson.fromJson(imageUrl, JsonObject::class.java)
                            val imageFile = File(getString("imageUrl", imgObject))
                            if (!imageFile.isFile) {
                                Log.w(TAG, "Dropping image ${imageFile.name} from voice ${news.id}: file is gone")
                                return@forEach
                            }
                            val resourceId = getString("resourceId", imgObject).ifEmpty { legacyResourceId(news, imageUrl) }
                            val fileName = safeFileName(imageFile.name)
                            putImage(user, resourceId, fileName, imageFile)
                            files.add(imageFile)

                            val resourceObject = JsonObject()
                            resourceObject.addProperty("resourceId", resourceId)
                            resourceObject.addProperty("filename", fileName)
                            val markdown = "![](resources/$resourceId/$fileName)"
                            resourceObject.addProperty("markdown", markdown)
                            imagesArray.add(resourceObject)

                            messageWithMedia.append("\n").append(markdown)
                        }

                        news.videoUrls.forEach { videoUrl ->
                            val vidObject = gson.fromJson(videoUrl, JsonObject::class.java)
                            val videoFile = File(getString("videoUrl", vidObject))
                            if (!videoFile.isFile) {
                                Log.w(TAG, "Dropping video ${videoFile.name} from voice ${news.id}: file is gone")
                                return@forEach
                            }
                            val resourceId = getString("resourceId", vidObject).ifEmpty { legacyResourceId(news, videoUrl) }
                            val fileName = safeFileName(videoFile.name, "video.mp4")
                            putVideo(user, resourceId, fileName, videoFile)

                            val resourceObject = JsonObject()
                            resourceObject.addProperty("resourceId", resourceId)
                            resourceObject.addProperty("filename", fileName)
                            val markdown = "![](resources/$resourceId/$fileName)"
                            resourceObject.addProperty("markdown", markdown)
                            videosArray.add(resourceObject)

                            messageWithMedia.append("\n").append(markdown)
                        }

                        val newsJson = news.newsJson
                        newsJson.addProperty("message", messageWithMedia.toString())
                        newsJson.add("images", imagesArray)
                        newsJson.add("videos", videosArray)

                        processedNews.add(PreparedNews(news, imagesArray, videosArray, files))
                    } catch (e: Exception) {
                        Log.e(TAG, "Exception in VoicesUploader processing media for news", e)
                        val isCreate = TextUtils.isEmpty(news._id)
                        queueNewsRetry(news, news.newsJson, null, if (isCreate) "POST" else "PUT", e)
                    }
                }

                BulkDocsUploader.upload(
                    uploadRepository,
                    "${UrlUtils.getUrl()}/news/_bulk_docs",
                    processedNews.map { it to it.news.newsJson }
                ) { prepared, outcome ->
                    val news = prepared.news
                    val isCreate = TextUtils.isEmpty(news._id)
                    when (outcome) {
                        is BulkDocsUploader.Outcome.Accepted -> {
                            successfulUpdates.add(
                                NewsUpdateData(
                                    id = news.id,
                                    _id = getString("id", outcome.element),
                                    _rev = getString("rev", outcome.element),
                                    imagesArray = prepared.imagesArray,
                                    videosArray = prepared.videosArray,
                                    pendingReactions = news.pendingReactions,
                                    message = getString("message", news.newsJson)
                                )
                            )
                            uploadedFiles.addAll(prepared.files)
                        }
                        is BulkDocsUploader.Outcome.Rejected -> {
                            val errorReason = outcome.element.get("error").asString
                            if (errorReason == "conflict") return@upload
                            queueNewsRetry(news, news.newsJson, null, if (isCreate) "POST" else "PUT", Exception("Bulk upload error: $errorReason"))
                        }
                        is BulkDocsUploader.Outcome.RequestFailed -> {
                            queueNewsRetry(news, news.newsJson, outcome.httpCode, if (isCreate) "POST" else "PUT", outcome.exception)
                        }
                    }
                }

                if (successfulUpdates.isNotEmpty()) {
                    voicesRepository.markNewsUploaded(successfulUpdates)
                    deleteLocalCopies(uploadedFiles)
                }
            }
            uploadNewsDeletions()
        }
    }

    private fun legacyResourceId(news: NewsUploadData, imageEntry: String): String =
        UUID.nameUUIDFromBytes("${news.id}:$imageEntry".toByteArray()).toString()

    private fun safeFileName(name: String, fallback: String = "image.jpg"): String =
        name.replace(UNSAFE_FILE_NAME_CHARS, "_").trim('_', '.').ifEmpty { fallback }

    private fun deleteLocalCopies(files: List<File>) {
        val context = MainApplication.context
        files.forEach { file ->
            try {
                if (VoiceImagePreparer.isOwnedFile(context, file)) file.delete()
            } catch (e: Exception) {
                Log.w(TAG, "Could not delete uploaded image ${file.name}", e)
            }
        }
    }

    private suspend fun uploadNewsDeletions() {
        val deleted = voicesRepository.getPendingNewsDeletions().filter { remoteId ->
            try {
                val url = "${UrlUtils.getUrl()}/news/$remoteId"
                val current = uploadRepository.fetchExistingDoc(url)
                if (current.code() == 404) return@filter true
                val rev = getString("_rev", current.body()?.takeIf { current.isSuccessful } ?: return@filter false)
                val tombstone = JsonObject().apply {
                    addProperty("_id", remoteId)
                    addProperty("_rev", rev)
                    addProperty("_deleted", true)
                }
                uploadRepository.putUpload(url, tombstone).isSuccessful
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete news $remoteId on the server", e)
                false
            }
        }
        voicesRepository.markNewsDeletionsUploaded(deleted)
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
        private val UNSAFE_FILE_NAME_CHARS = Regex("[^A-Za-z0-9._-]+")
    }
}
