package org.ole.planet.myplanet.services.upload.delegate

import android.util.Log
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.callback.OnSuccessListener
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.repository.SubmissionsRepository
import org.ole.planet.myplanet.services.FileUploader
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.JsonUtils.getString
import org.ole.planet.myplanet.utils.UrlUtils
import javax.inject.Inject

class PhotoUploadDelegate @Inject constructor(
    private val submissionsRepository: SubmissionsRepository,
    private val apiInterface: ApiInterface,
    private val dispatcherProvider: DispatcherProvider
) {

    suspend fun uploadSubmitPhotos(
        fileUploader: FileUploader,
        listener: OnSuccessListener?,
        onNotify: suspend (OnSuccessListener?, String) -> Unit
    ) {
        val photosToUpload = submissionsRepository.getUnuploadedPhotos()

        if (photosToUpload.isEmpty()) {
            onNotify(listener, "No photos to upload")
            return
        }

        withContext(dispatcherProvider.io) {
            data class UploadedPhotoInfo(val photoId: String, val rev: String, val id: String)

            photosToUpload.chunked(BATCH_SIZE).forEach { batch ->
                val successfulUploads = mutableListOf<UploadedPhotoInfo>()

                batch.forEach { (photoId, serialized) ->
                    try {
                        val `object` = apiInterface.postDoc(
                            UrlUtils.header, "application/json",
                            "${UrlUtils.getUrl()}/submissions", serialized
                        ).body()

                        if (`object` != null) {
                            val rev = getString("rev", `object`)
                            val id = getString("id", `object`)

                            submissionsRepository.markPhotoUploaded(photoId, rev, id)

                            if (listener != null && photoId != null) {
                                successfulUploads.add(UploadedPhotoInfo(photoId, rev, id))
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Exception in UploadManager", e)
                    }
                }

                if (listener != null && successfulUploads.isNotEmpty()) {
                    val photoIds = successfulUploads.map { it.photoId }.toTypedArray()
                    val photos = submissionsRepository.getPhotosByIds(photoIds)

                    photos.forEach { photo ->
                        val uploadInfo = successfulUploads.find { it.photoId == photo.id }
                        if (uploadInfo != null) {
                            fileUploader.uploadAttachment(uploadInfo.id, uploadInfo.rev, photo, listener)
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "UploadManager"
        private const val BATCH_SIZE = 50
    }
}
