git reset --hard HEAD
cat << 'INNER_EOF' > app/src/main/java/org/ole/planet/myplanet/services/upload/PhotoUploader.kt
package org.ole.planet.myplanet.services.upload

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.callback.OnSuccessListener
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.repository.SubmissionsRepository
import org.ole.planet.myplanet.services.FileUploader
import org.ole.planet.myplanet.services.upload.UploadConstants.BATCH_SIZE
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.JsonUtils.getString
import org.ole.planet.myplanet.utils.UrlUtils
import javax.inject.Inject
import org.ole.planet.myplanet.di.ApplicationScope

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
                        val \`object\` = apiInterface.postDoc(
                            UrlUtils.header, "application/json",
                            "\${UrlUtils.getUrl()}/submissions", serialized
                        ).body()

                        if (\`object\` != null) {
                            val rev = getString("rev", \`object\`)
                            val id = getString("id", \`object\`)

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
INNER_EOF
sed -i 's/\\`object\\`/`object`/g' app/src/main/java/org/ole/planet/myplanet/services/upload/PhotoUploader.kt
sed -i 's/\\\${UrlUtils/\${UrlUtils/g' app/src/main/java/org/ole/planet/myplanet/services/upload/PhotoUploader.kt

sed -i 's/photoUploader.uploadSubmitPhotos(this, listener, ::notifyListener)/val resultMessage = photoUploader.uploadSubmitPhotos(listener)\n        resultMessage?.let {\n            notifyListener(listener, it)\n        }/g' app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt
sed -i 's/import org.ole.planet.myplanet.services.upload.PhotoUploader/import org.ole.planet.myplanet.services.upload.PhotoUploader\nimport org.ole.planet.myplanet.services.upload.UploadConstants.BATCH_SIZE/g' app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt
sed -i '/private const val BATCH_SIZE = 50/d' app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt

sed -i 's/photoUploader = PhotoUploader(submissionsRepository, apiInterface, TestDispatcherProvider(testDispatcher))/photoUploader = PhotoUploader(submissionsRepository, apiInterface, TestDispatcherProvider(testDispatcher), testScope)/g' app/src/test/java/org/ole/planet/myplanet/services/UploadManagerTest.kt

git add app/src/main/java/org/ole/planet/myplanet/services/upload/PhotoUploader.kt app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt app/src/test/java/org/ole/planet/myplanet/services/UploadManagerTest.kt app/src/main/java/org/ole/planet/myplanet/di/ServiceModule.kt app/src/main/java/org/ole/planet/myplanet/services/upload/UploadConstants.kt
git commit --amend -m "Refactor: Extract PhotoUploader and fix leaky abstractions

- Extracted uploadSubmitPhotos from UploadManager into a new dedicated PhotoUploader class.
- Fixed leaky abstraction: PhotoUploader now extends FileUploader directly and manages its own scope.
- Fixed leaky UI callback: PhotoUploader returns a result string instead of executing a UI callback from its parent.
- Consolidated BATCH_SIZE constant into a shared UploadConstants object.
- Modified UploadManager to delegate to PhotoUploader, reducing its responsibilities.
- Updated ServiceModule to inject the new dependency and pass it to UploadManager.
- Updated UploadManagerTest to maintain full test coverage for the delegated behavior."
