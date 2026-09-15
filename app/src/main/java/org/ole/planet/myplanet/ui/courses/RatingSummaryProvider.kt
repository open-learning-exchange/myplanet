package org.ole.planet.myplanet.ui.courses

import javax.inject.Inject
import org.ole.planet.myplanet.repository.RatingSummary
import org.ole.planet.myplanet.repository.RatingsRepository

class RatingSummaryProvider @Inject constructor(
    private val ratingsRepository: RatingsRepository
) {
    suspend operator fun invoke(courseId: String, userId: String): RatingSummary {
        return ratingsRepository.getRatingSummary("course", courseId, userId)
    }
}
