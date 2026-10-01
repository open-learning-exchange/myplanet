package org.ole.planet.myplanet.services.upload

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.data.NetworkResult
import org.ole.planet.myplanet.repository.UploadRepository
import org.ole.planet.myplanet.repository.UserAchievementsRepository
import org.ole.planet.myplanet.services.FileUploader
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.FileUtils
import org.ole.planet.myplanet.utils.UrlUtils

class AchievementUploader @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val userAchievementsRepository: UserAchievementsRepository,
    private val uploadRepository: UploadRepository,
    private val dispatcherProvider: DispatcherProvider
) {

    suspend fun uploadAchievement() {
        val list = userAchievementsRepository.getAchievementsForUpload()
        if (list.isEmpty()) return
        withContext(dispatcherProvider.io) {
            list.forEach { achievement ->
                val id = achievement.get("_id")?.asString ?: return@forEach
                val url = "${UrlUtils.getUrl()}/achievements/$id"
                try {
                    val result = uploadRepository.putUpload(url, achievement)
                    if (result is NetworkResult.Success) {
                        val rev = result.data.get("rev")?.asString
                        userAchievementsRepository.markAchievementUploaded(id, rev)
                        val resumeFileName = achievement.get("resumeFileName")?.asString ?: ""
                        if (resumeFileName.isNotEmpty() && !rev.isNullOrEmpty()) {
                            uploadCvAttachment(id, rev, resumeFileName)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
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
            // CouchDB attachment key is always "resume.pdf"
            val url = "${UrlUtils.getUrl()}/achievements/$docId/resume.pdf"
            uploadRepository.uploadResource(FileUploader.getHeaderMap("application/pdf", rev), url, cvFile, "application/pdf")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload CV attachment", e)
        }
    }

    companion object {
        private const val TAG = "AchievementUploader"
    }
}
