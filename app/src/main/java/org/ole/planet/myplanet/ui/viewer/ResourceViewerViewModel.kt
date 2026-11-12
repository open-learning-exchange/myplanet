package org.ole.planet.myplanet.ui.viewer

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.data.auth.AuthSessionUpdater
import org.ole.planet.myplanet.di.ApplicationScope
import org.ole.planet.myplanet.model.MyLibrary
import org.ole.planet.myplanet.repository.ConfigurationsRepository
import org.ole.planet.myplanet.repository.RatingsRepository
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.services.ResourceDownloadCoordinator
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.PdfTextExtractor
import org.ole.planet.myplanet.utils.StoragePathResolver

@HiltViewModel
class ResourceViewerViewModel @Inject constructor(
    private val resourcesRepository: ResourcesRepository,
    private val authSessionUpdaterFactory: AuthSessionUpdater.Factory,
    private val ratingsRepository: RatingsRepository,
    private val configurationsRepository: ConfigurationsRepository,
    private val userRepository: UserRepository,
    private val sharedPrefManager: SharedPrefManager,
    private val storagePathResolver: StoragePathResolver,
    private val resourceDownloadCoordinator: ResourceDownloadCoordinator,
    private val pdfTextExtractor: PdfTextExtractor,
    private val dispatcherProvider: DispatcherProvider,
    @ApplicationScope private val appScope: CoroutineScope
) : ViewModel() {

    suspend fun shouldShowResourceRatingDialog(resourceId: String): Boolean {
        val userId = userRepository.getUserModel()?.id?.takeIf { it.isNotBlank() } ?: return false

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

    suspend fun setRatingPrompted(resourceId: String) {
        val userId = userRepository.getUserModel()?.id?.takeIf { it.isNotBlank() } ?: return
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
    
    fun saveTranslationAudioPath(id: String, outputFile: String?) {
        appScope.launch { updateLibraryItemTranslationAudioPath(id, outputFile) }
    }

    suspend fun getExternalFilesDir(): File? = withContext(dispatcherProvider.io) {
        storagePathResolver.resolveExternalFilesDir()
    }

    suspend fun downloadResource(url: String) {
        resourceDownloadCoordinator.downloadIfMissing(url)
    }

    suspend fun extractPdfText(file: File): String = pdfTextExtractor.extractText(file)
}
