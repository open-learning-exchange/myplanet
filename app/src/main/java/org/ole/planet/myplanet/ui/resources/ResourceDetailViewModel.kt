package org.ole.planet.myplanet.ui.resources

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import org.ole.planet.myplanet.model.MyLibrary
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.repository.RatingSummary
import org.ole.planet.myplanet.repository.RatingsRepository
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.repository.UserRepository

@HiltViewModel
class ResourceDetailViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val resourcesRepository: ResourcesRepository,
    private val ratingsRepository: RatingsRepository
) : ViewModel() {

    suspend fun getUserModel(): UserEntity? = userRepository.getUserModel()

    suspend fun resolveLibraryItem(id: String): MyLibrary? = resourcesRepository.resolveLibraryItem(id)

    suspend fun setUserLibrary(id: String, isAdd: Boolean): MyLibrary? = resourcesRepository.setUserLibrary(id, isAdd)

    suspend fun getRatingSummary(type: String, resourceId: String, userId: String?): RatingSummary =
        ratingsRepository.getRatingSummary(type, resourceId, userId)
}
