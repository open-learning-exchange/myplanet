package org.ole.planet.myplanet.services.upload

import android.util.Log
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.callback.OnSuccessListener
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.di.ApplicationScope
import org.ole.planet.myplanet.repository.SubmissionsRepository
import org.ole.planet.myplanet.services.FileUploader
import org.ole.planet.myplanet.services.upload.UploadConstants.BATCH_SIZE
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.JsonUtils.getString
import org.ole.planet.myplanet.utils.UrlUtils

class PhotoUploader @Inject constructor(
    private val submissionsRepository: SubmissionsRepository,
    private val apiInterface: ApiInterface,
    private val dispatcherProvider: DispatcherProvider,
    @ApplicationScope scope: CoroutineScope
) : FileUploader(apiInterface, scope) {

    suspend fun uploadSubmitPhotos(
        listener: OnSuccessListener?
    ): String? {
        val photosToUpload = submissionsRepository.getUnuploadedPhotos()

        if (photosToUpload.isEmpty()) {
            return "No photos to upload"
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
                        Log.e(TAG, "Exception in PhotoUploader", e)
                    }
                }

                if (listener != null && successfulUploads.isNotEmpty()) {
                    val photoIds = successfulUploads.map { it.photoId }.toTypedArray()
                    val photos = submissionsRepository.getPhotosByIds(photoIds)

                    photos.forEach { photo ->
                        val uploadInfo = successfulUploads.find { it.photoId == photo.id }
                        if (uploadInfo != null) {
                            uploadAttachment(uploadInfo.id, uploadInfo.rev, photo, listener)
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
