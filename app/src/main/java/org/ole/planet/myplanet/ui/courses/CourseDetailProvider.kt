package org.ole.planet.myplanet.ui.courses

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.model.MyCourse
import org.ole.planet.myplanet.model.MyLibrary
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.model.StepItem
import org.ole.planet.myplanet.repository.CoursesRepository
import org.ole.planet.myplanet.repository.RatingSummary
import org.ole.planet.myplanet.repository.RatingsRepository
import org.ole.planet.myplanet.repository.SubmissionsRepository
import org.ole.planet.myplanet.services.UserSessionManager
import org.ole.planet.myplanet.utils.DispatcherProvider

data class CourseDetailModel(
    val course: MyCourse,
    val user: UserEntity?,
    val ratingSummary: RatingSummary?,
    val examCount: Int,
    val resources: List<MyLibrary>,
    val downloadedResources: List<MyLibrary>,
    val steps: List<StepItem>
)

class CourseDetailProvider @Inject constructor(
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
