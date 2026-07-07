package org.ole.planet.myplanet.domain.usecase

import javax.inject.Inject
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.repository.RatingsRepository
import org.ole.planet.myplanet.repository.RatingSummary
import org.ole.planet.myplanet.services.UserSessionManager
import org.ole.planet.myplanet.utils.DispatcherProvider

data class RatingSummaryModel(
    val user: RealmUser?,
    val ratingSummary: RatingSummary?
)

class GetRatingSummaryUseCase @Inject constructor(
    private val ratingsRepository: RatingsRepository,
    private val userSessionManager: UserSessionManager,
    private val dispatcherProvider: DispatcherProvider
) {
    suspend operator fun invoke(courseId: String): RatingSummaryModel {
        return withContext(dispatcherProvider.io) {
            val user = userSessionManager.getUserModel()
            val userId = user?.id
            val ratingSummary = if (userId != null) {
                ratingsRepository.getRatingSummary("course", courseId, userId)
            } else {
                null
            }
            RatingSummaryModel(user, ratingSummary)
        }
    }
}
