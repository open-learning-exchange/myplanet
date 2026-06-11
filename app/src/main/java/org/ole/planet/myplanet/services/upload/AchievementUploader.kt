package org.ole.planet.myplanet.services.upload

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.services.FileUploader
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.FileUtils
import org.ole.planet.myplanet.utils.UrlUtils

class AchievementUploader @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val userRepository: UserRepository,
    private val apiInterface: ApiInterface,
    private val dispatcherProvider: DispatcherProvider
) {

    suspend fun uploadAchievement() {
        val list = userRepository.getAchievementsForUpload()
        if (list.isEmpty()) return
        withContext(dispatcherProvider.io) {
            list.forEach { achievement ->
                val id = achievement.get("_id")?.asString ?: return@forEach
                val url = "${UrlUtils.getUrl()}/achievements/$id"
                try {
                    val response = apiInterface.putDoc(UrlUtils.header, "application/json", url, achievement)
                    if (response.isSuccessful) {
                        val rev = response.body()?.get("rev")?.asString
                        userRepository.markAchievementUploaded(id, rev)
                        val resumeFileName = achievement.get("resumeFileName")?.asString ?: ""
                        if (resumeFileName.isNotEmpty() && !rev.isNullOrEmpty()) {
                            uploadCvAttachment(id, rev, resumeFileName)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Exception in AchievementUploader", e)
                }
            }
        }
    }

    private suspend fun uploadCvAttachment(docId: String, rev: String, resumeFileName: String) {
        val cvFile = File(FileUtils.getOlePath(context) + "cv/$resumeFileName")
        if (!cvFile.exists()) return
        try {
            val body = cvFile.readBytes().toRequestBody("application/pdf".toMediaTypeOrNull())
            // CouchDB attachment key is always "resume.pdf"
            val url = "${UrlUtils.getUrl()}/achievements/$docId/resume.pdf"
            apiInterface.uploadResource(FileUploader.getHeaderMap("application/pdf", rev), url, body)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload CV attachment", e)
        }
    }

    companion object {
        private const val TAG = "AchievementUploader"
    }
}
