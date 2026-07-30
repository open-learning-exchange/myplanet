package org.ole.planet.myplanet.ui.courses

import javax.inject.Inject
import org.ole.planet.myplanet.repository.RatingSummaryModel
import org.ole.planet.myplanet.repository.RatingsRepository

class RatingSummaryProvider @Inject constructor(
    private val ratingsRepository: RatingsRepository
) {
    suspend operator fun invoke(courseId: String): RatingSummaryModel {
        return ratingsRepository.getCourseRatingSummary(courseId)
    }
}
