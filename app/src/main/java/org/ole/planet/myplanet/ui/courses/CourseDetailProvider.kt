package org.ole.planet.myplanet.ui.courses

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.model.CourseDetailModel
import org.ole.planet.myplanet.repository.CoursesRepository

class CourseDetailProvider @Inject constructor(
    private val coursesRepository: CoursesRepository
) {
    operator fun invoke(courseId: String): Flow<CourseDetailModel?> {
        return coursesRepository.getCourseDetailModel(courseId)
    }
}
