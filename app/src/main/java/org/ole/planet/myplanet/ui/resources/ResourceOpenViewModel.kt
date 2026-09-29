package org.ole.planet.myplanet.ui.resources

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import org.ole.planet.myplanet.model.MyLibrary
import org.ole.planet.myplanet.repository.ResourceUrlsResponse
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.repository.UserRepository

sealed interface HtmlOpenOutcome {
    data class DownloadNeeded(val urls: List<String>) : HtmlOpenOutcome
    object ResourceNotFound : HtmlOpenOutcome
    object NoAttachments : HtmlOpenOutcome
    object Error : HtmlOpenOutcome
}

@HiltViewModel
class ResourceOpenViewModel @Inject constructor(
    private val resourcesRepository: ResourcesRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    suspend fun trackOpen(item: MyLibrary) {
        resourcesRepository.trackResourceOpen(item)
    }

    suspend fun reconcileHtmlOffline(resourceId: String) {
        resourcesRepository.reconcileHtmlResourceOffline(resourceId)
    }

    suspend fun resolveHtmlDownloadUrls(resourceId: String?): HtmlOpenOutcome {
        if (resourceId == null) {
            return HtmlOpenOutcome.ResourceNotFound
        }
        return when (val result = resourcesRepository.getHtmlResourceDownloadUrls(resourceId)) {
            is ResourceUrlsResponse.Success -> HtmlOpenOutcome.DownloadNeeded(result.urls)
            is ResourceUrlsResponse.ResourceNotFound -> HtmlOpenOutcome.ResourceNotFound
            is ResourceUrlsResponse.NoAttachments -> HtmlOpenOutcome.NoAttachments
            is ResourceUrlsResponse.Error -> HtmlOpenOutcome.Error
        }
    }

    suspend fun findByLocalAddress(localAddress: String): List<MyLibrary> {
        return resourcesRepository.getLibraryItemsByLocalAddress(localAddress)
    }

    suspend fun isGuestUser(): Boolean {
        return userRepository.getUserModel()?.isGuest() ?: true
    }
}
