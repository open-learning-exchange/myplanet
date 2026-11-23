package org.ole.planet.myplanet.services.upload

import android.util.Log
import com.google.gson.JsonObject
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.callback.OnSuccessListener
import org.ole.planet.myplanet.di.ApplicationScope
import org.ole.planet.myplanet.model.SubmitPhotos
import org.ole.planet.myplanet.repository.PhotoUpload
import org.ole.planet.myplanet.repository.SubmissionsRepository
import org.ole.planet.myplanet.repository.UploadRepository
import org.ole.planet.myplanet.services.FileUploader
import org.ole.planet.myplanet.services.upload.UploadConstants.BATCH_SIZE
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.GsonUtils
import org.ole.planet.myplanet.utils.GsonUtils.getString
import org.ole.planet.myplanet.utils.UrlUtils

class PhotoUploader @Inject constructor(
    private val submissionsRepository: SubmissionsRepository,
    private val dispatcherProvider: DispatcherProvider,
    @ApplicationScope scope: CoroutineScope,
    private val uploadRepository: UploadRepository
) : FileUploader(uploadRepository, scope) {

    suspend fun uploadSubmitPhotos(
        listener: OnSuccessListener?
    ): String? {
        val photosToUpload = submissionsRepository.getUnuploadedPhotos()

        if (photosToUpload.isEmpty()) {
            return "No photos to upload"
        }

        var uploadedCount = 0
        withContext(dispatcherProvider.io) {
            val baseUrl = UrlUtils.getUrl()

            val semaphore = Semaphore(MAX_CONCURRENT_UPLOADS)

            photosToUpload.chunked(BATCH_SIZE).forEach { batch ->
                val ids = batch.mapNotNull { it.first }.toTypedArray()
                val photosById = submissionsRepository.getPhotosByIds(ids).associateBy { it.id }

                val successfulUploads = coroutineScope {
                    batch.map { (photoId, serialized) ->
                        async {
                            if (photoId == null) return@async null
                            try {
                                uploadPhoto(photoId, serialized, photosById[photoId], baseUrl, semaphore)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Log.e(TAG, "Exception in PhotoUploader", e)
                                null
                            }
                        }
                    }.awaitAll().filterNotNull()
                }

                if (successfulUploads.isNotEmpty()) {
                    submissionsRepository.markPhotosUploadedBatch(successfulUploads)
                    uploadedCount += successfulUploads.size
                }
            }
        }
        return "Uploaded $uploadedCount of ${photosToUpload.size} photos"
    }

    private suspend fun uploadPhoto(
        photoId: String,
        serialized: JsonObject,
        photo: SubmitPhotos?,
        baseUrl: String,
        semaphore: Semaphore
    ): PhotoUpload? {
        val existingId = photo?._id
        val existingRev = photo?._rev
        val (remoteId, docRev) = if (!existingId.isNullOrEmpty() && !existingRev.isNullOrEmpty()) {
            existingId to existingRev
        } else {
            val response = semaphore.withPermit {
                uploadRepository.postUpload("$baseUrl/submissions", serialized)
            }
            val body = response.body()
            if (!response.isSuccessful || body == null) return null
            val id = getString("id", body)
            val rev = getString("rev", body)
            submissionsRepository.recordPhotoRemoteDoc(photoId, rev, id)
            id to rev
        }

        val file = photo?.photoLocation?.let { File(it) }
        if (file == null || !file.exists()) {
            Log.w(TAG, "Photo file missing for $photoId; uploading doc without image")
            return PhotoUpload(photoId, docRev, remoteId)
        }

        val response = semaphore.withPermit {
            uploadRepository.uploadAttachment(file, "%s/submissions/%s/%s", remoteId, docRev, file.name)
        }
        val body = response.body()
        if (!response.isSuccessful || body == null || !GsonUtils.getBoolean("ok", body)) {
            Log.w(TAG, "Image upload failed for $photoId: HTTP ${response.code()}")
            return null
        }
        return PhotoUpload(photoId, getString("rev", body).ifEmpty { docRev }, remoteId)
    }

    companion object {
        private const val TAG = "PhotoUploader"
        private const val MAX_CONCURRENT_UPLOADS = 6
    }
}
