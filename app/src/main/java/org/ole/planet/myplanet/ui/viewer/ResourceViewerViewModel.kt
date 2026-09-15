package org.ole.planet.myplanet.ui.viewer

import android.content.Context
import androidx.lifecycle.ViewModel
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.data.auth.AuthSessionUpdater
import org.ole.planet.myplanet.model.MyLibrary
import org.ole.planet.myplanet.repository.ConfigurationsRepository
import org.ole.planet.myplanet.repository.RatingsRepository
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.DownloadUtils
import org.ole.planet.myplanet.utils.FileUtils

@HiltViewModel
class ResourceViewerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val resourcesRepository: ResourcesRepository,
    private val authSessionUpdaterFactory: AuthSessionUpdater.Factory,
    private val ratingsRepository: RatingsRepository,
    private val configurationsRepository: ConfigurationsRepository,
    private val sharedPrefManager: SharedPrefManager,
    private val dispatcherProvider: DispatcherProvider
) : ViewModel() {

    suspend fun shouldShowResourceRatingDialog(userId: String, resourceId: String): Boolean {
        if (isRatingPrompted(userId, resourceId)) {
            return false
        }

        val hasRated = try {
            val summary = ratingsRepository.getRatingSummary("resource", resourceId, userId)
            summary.userRating != null || summary.existingRating != null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }

        return !hasRated
    }

    suspend fun isRatingPrompted(userId: String, resourceId: String): Boolean {
        return ratingsRepository.isRatingPrompted(userId, resourceId)
    }

    suspend fun setRatingPrompted(userId: String, resourceId: String) {
        ratingsRepository.setRatingPrompted(userId, resourceId)
    }

    fun getPlaybackProgress(resourceKey: String): Long =
        sharedPrefManager.getMediaPlaybackPosition(resourceKey)

    fun savePlaybackProgress(resourceKey: String, positionMs: Long) {
        sharedPrefManager.setMediaPlaybackPosition(resourceKey, positionMs)
    }

    fun savePlaybackProgress(resourceKey: String, currentPos: Long, duration: Long) {
        val effectivePosition = calculateEffectivePlaybackPosition(currentPos, duration)
        sharedPrefManager.setMediaPlaybackPosition(resourceKey, effectivePosition)
    }

    fun getPlaybackSpeed(): Float =
        sharedPrefManager.getMediaPlaybackSpeed()

    fun savePlaybackSpeed(speed: Float) {
        sharedPrefManager.setMediaPlaybackSpeed(speed)
    }

    companion object {
        const val NEAR_END_THRESHOLD_MS = 2000L

        fun calculateEffectivePlaybackPosition(currentPos: Long, duration: Long): Long {
            if (currentPos <= 0L) return 0L
            if (duration > 0L && duration - currentPos < NEAR_END_THRESHOLD_MS) {
                return 0L
            }
            return currentPos
        }
    }

    suspend fun ensureServerUrlUpdated() {
        configurationsRepository.ensureServerUrlUpdated()
    }

    fun getAuthSessionUpdater(callback: AuthSessionUpdater.AuthCallback): AuthSessionUpdater {
        return authSessionUpdaterFactory.create(callback)
    }

    suspend fun getLibraryItemById(id: String): MyLibrary? {
        return resourcesRepository.getLibraryItemById(id)
    }

    suspend fun updateLibraryItemTranslationAudioPath(id: String, outputFile: String?) {
        resourcesRepository.updateLibraryItem(id) { it.translationAudioPath = outputFile }
    }

    suspend fun getExternalFilesDir(): File? = withContext(dispatcherProvider.io) {
        context.getExternalFilesDir(null)
    }

    suspend fun downloadResource(url: String) = withContext(dispatcherProvider.io) {
        if (!FileUtils.checkFileExist(context, url)) {
            DownloadUtils.openDownloadService(context, arrayListOf(url), false)
        }
    }

    suspend fun extractPdfText(file: File): String = withContext(dispatcherProvider.io) {
        try {
            PDFBoxResourceLoader.init(context)
            PDDocument.load(file).use {
                PDFTextStripper().getText(it).trim()
            }
        } catch (e: Exception) { "" }
    }
}
