package org.ole.planet.myplanet.domain.usecase

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.model.StepItem
import org.ole.planet.myplanet.repository.CoursesRepository
import org.ole.planet.myplanet.repository.RatingsRepository
import org.ole.planet.myplanet.repository.RatingSummary
import org.ole.planet.myplanet.repository.SubmissionsRepository
import org.ole.planet.myplanet.services.UserSessionManager
import org.ole.planet.myplanet.utils.DispatcherProvider
import kotlinx.coroutines.withContext

data class CourseDetailModel(
    val course: RealmMyCourse,
    val user: RealmUser?,
    val ratingSummary: RatingSummary?,
    val examCount: Int,
    val resources: List<RealmMyLibrary>,
    val downloadedResources: List<RealmMyLibrary>,
    val steps: List<StepItem>
)

class GetCourseDetailUseCase @Inject constructor(
    private val coursesRepository: CoursesRepository,
    private val submissionsRepository: SubmissionsRepository,
    private val ratingsRepository: RatingsRepository,
    private val userSessionManager: UserSessionManager,
    private val dispatcherProvider: DispatcherProvider
) {
    operator fun invoke(courseId: String): Flow<CourseDetailModel?> {
        return coursesRepository.getCourseByCourseIdFlow(courseId).map { course ->
            if (course == null) return@map null

            withContext(dispatcherProvider.io) {
                val user = userSessionManager.getUserModel()
                val examCount = coursesRepository.getCourseExamCount(courseId)
                val resources = coursesRepository.getCourseOnlineResources(courseId)
                val downloadedResources = coursesRepository.getCourseOfflineResources(courseId)
                val rawSteps = coursesRepository.getCourseSteps(courseId)

                val steps = rawSteps.map { step ->
                    val count = step.id?.let { submissionsRepository.getExamQuestionCount(it) } ?: 0
                    StepItem(
                        id = step.id,
                        stepTitle = step.stepTitle,
                        questionCount = count
                    )
                }

                val userId = user?.id
                val ratingSummary = if (userId != null) {
                    ratingsRepository.getRatingSummary("course", courseId, userId)
                } else {
                    null
                }

                CourseDetailModel(
                    course = course,
                    user = user,
                    ratingSummary = ratingSummary,
                    examCount = examCount,
                    resources = resources,
                    downloadedResources = downloadedResources,
                    steps = steps
                )
            }
        }
    }
}
