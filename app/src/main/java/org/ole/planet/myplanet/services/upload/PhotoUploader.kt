package org.ole.planet.myplanet.services.upload

import android.util.Log
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.callback.OnSuccessListener
import org.ole.planet.myplanet.data.room.dao.SubmitPhotosDao.UploadedPhoto
import org.ole.planet.myplanet.di.ApplicationScope
import org.ole.planet.myplanet.repository.SubmissionsRepository
import org.ole.planet.myplanet.repository.UploadRepository
import org.ole.planet.myplanet.services.FileUploader
import org.ole.planet.myplanet.services.upload.UploadConstants.BATCH_SIZE
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.JsonUtils.getString
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

        withContext(dispatcherProvider.io) {
            data class UploadedPhotoInfo(val photoId: String, val rev: String, val id: String)

            val baseUrl = UrlUtils.getUrl()

            photosToUpload.chunked(BATCH_SIZE).forEach { batch ->
                val successfulUploads = mutableListOf<UploadedPhotoInfo>()

                batch.forEach { (photoId, serialized) ->
                    try {
                        val `object` = uploadRepository.postUpload(
                            "$baseUrl/submissions", serialized
                        ).body()

                        if (`object` != null) {
                            val rev = getString("rev", `object`)
                            val id = getString("id", `object`)

                            if (photoId != null) {
                                successfulUploads.add(UploadedPhotoInfo(photoId, rev, id))
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Exception in PhotoUploader", e)
                    }
                }

                if (successfulUploads.isNotEmpty()) {
                    val batchedMarks = successfulUploads.map { UploadedPhoto(it.photoId, it.rev, it.id) }
                    submissionsRepository.markPhotosUploadedBatch(batchedMarks)
                }

                if (listener != null && successfulUploads.isNotEmpty()) {
                    val photoIds = successfulUploads.map { it.photoId }.toTypedArray()
                    val photosMap = submissionsRepository.getPhotosByIds(photoIds).associateBy { it.id }

                    successfulUploads.forEach { uploadInfo ->
                        val photo = photosMap[uploadInfo.photoId]
                        if (photo != null) {
                            uploadAttachment(photo.photoLocation, "%s/submissions/%s/%s", uploadInfo.id, uploadInfo.rev, listener)
                        }
                    }
                }
            }
        }
        return null
    }

    companion object {
        private const val TAG = "PhotoUploader"
    }
}
