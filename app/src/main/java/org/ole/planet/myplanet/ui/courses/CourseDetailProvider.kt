package org.ole.planet.myplanet.ui.courses

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.model.MyCourse
import org.ole.planet.myplanet.model.MyLibrary
import org.ole.planet.myplanet.model.StepItem
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.repository.CoursesRepository
import org.ole.planet.myplanet.repository.RatingSummary

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
    private val coursesRepository: CoursesRepository
) {
    operator fun invoke(courseId: String): Flow<CourseDetailModel?> {
        return coursesRepository.getCourseDetailModel(courseId)
    }
}
