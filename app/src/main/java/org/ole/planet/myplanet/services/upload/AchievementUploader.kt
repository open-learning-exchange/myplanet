package org.ole.planet.myplanet.services.upload

import android.content.Context
import android.util.Log
import com.google.gson.JsonObject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import org.ole.planet.myplanet.model.Achievement
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
                    val docRev = putAchievement(url, achievement) ?: return@forEach
                    val resumeFileName = achievement.get("resumeFileName")?.asString ?: ""

                    val finalRev = if (resumeFileName.isNotEmpty()) {
                        uploadCvAttachment(id, docRev, resumeFileName) ?: docRev
                    } else {
                        docRev
                    }
                    userAchievementsRepository.markAchievementUploaded(id, finalRev)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Exception in AchievementUploader", e)
                }
            }
        }
    }
    
    private suspend fun putAchievement(url: String, achievement: JsonObject): String? {
        val response = uploadRepository.putUpload(url, achievement)
        if (response.isSuccessful) return revOf(response.body())
        if (response.code() != 409) {
            Log.w(TAG, "Achievement upload failed: HTTP ${response.code()}")
            return null
        }

        val serverRev = uploadRepository.fetchExistingDoc(url).body()?.get("_rev")?.asString
        if (serverRev.isNullOrEmpty()) {
            Log.w(TAG, "Achievement conflict: couldn't fetch server rev")
            return null
        }
        val rebased = achievement.deepCopy().apply { addProperty("_rev", serverRev) }
        val retry = uploadRepository.putUpload(url, rebased)
        if (!retry.isSuccessful) {
            Log.w(TAG, "Achievement conflict retry failed: HTTP ${retry.code()}")
            return null
        }
        return revOf(retry.body())
    }

    private fun revOf(body: JsonObject?): String? = body?.get("rev")?.asString?.takeIf { it.isNotEmpty() }

    private suspend fun uploadCvAttachment(docId: String, rev: String, resumeFileName: String): String? {
        val olePath = FileUtils.getOlePath(context)
        val cvFile = File(olePath + Achievement.resolveCvRelativePath(olePath, docId, resumeFileName))
        if (!cvFile.exists()) return null
        return try {
            val body = cvFile.asRequestBody("application/pdf".toMediaTypeOrNull())
            // CouchDB attachment key is always "resume.pdf"
            val url = "${UrlUtils.getUrl()}/achievements/$docId/resume.pdf"
            val response = uploadRepository.uploadResource(FileUploader.getHeaderMap("application/pdf", rev), url, body)
            if (response.isSuccessful) revOf(response.body()) else null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload CV attachment", e)
            null
        }
    }

    companion object {
        private const val TAG = "AchievementUploader"
    }
}
