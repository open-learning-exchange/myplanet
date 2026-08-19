package org.ole.planet.myplanet.ui.viewer

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import org.ole.planet.myplanet.data.auth.AuthSessionUpdater
import org.ole.planet.myplanet.model.MyLibrary
import org.ole.planet.myplanet.repository.RatingsRepository
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.sync.ServerUrlMapper

@HiltViewModel
class ResourceViewerViewModel @Inject constructor(
    private val resourcesRepository: ResourcesRepository,
    private val authSessionUpdaterFactory: AuthSessionUpdater.Factory,
    private val serverUrlMapper: ServerUrlMapper,
    private val sharedPrefManager: SharedPrefManager,
    private val userRepository: UserRepository,
    private val ratingsRepository: RatingsRepository
) : ViewModel() {

    companion object {
        private const val RATING_PROMPT_PREFIX = "rating_prompted_"
    }

    suspend fun shouldShowResourceRatingDialog(resourceId: String): Boolean {
        val userId = userRepository.getUserModel()?.id?.takeIf { it.isNotBlank() } ?: return false
        if (isRatingPrompted(userId, resourceId)) {
            return false
        }

        val hasRated = if (!userId.isNullOrEmpty()) {
            try {
                val summary = ratingsRepository.getRatingSummary("resource", resourceId, userId)
                summary.userRating != null || summary.existingRating != null
            } catch (e: Exception) {
                false
            }
        } else {
            false
        }

        return !hasRated
    }

    fun isRatingPrompted(userId: String?, resourceId: String?): Boolean {
        val key = "${RATING_PROMPT_PREFIX}${userId}_$resourceId"
        return sharedPrefManager.getRawString(key, "false") == "true"
    }

    fun setRatingPrompted(userId: String?, resourceId: String?) {
        val key = "${RATING_PROMPT_PREFIX}${userId}_$resourceId"
        sharedPrefManager.setRawString(key, "true")
    }

    suspend fun ensureServerUrlUpdated() {
        val serverUrl = sharedPrefManager.getServerUrl()
        val mapping = serverUrlMapper.processUrl(serverUrl)
        if (mapping.alternativeUrl != null) {
            serverUrlMapper.updateServerIfNecessary(mapping, sharedPrefManager.rawPreferences) { url ->
                serverUrlMapper.isUrlDirectlyReachable(url)
            }
        }
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
}
