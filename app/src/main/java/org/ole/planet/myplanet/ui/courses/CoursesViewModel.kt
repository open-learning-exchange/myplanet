package org.ole.planet.myplanet.ui.courses

import androidx.lifecycle.ViewModel
import com.google.gson.JsonObject
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import org.ole.planet.myplanet.model.RealmTag
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.repository.CoursesRepository
import org.ole.planet.myplanet.repository.ProgressRepository
import org.ole.planet.myplanet.repository.RatingsRepository
import org.ole.planet.myplanet.repository.TagsRepository

@HiltViewModel
class CoursesViewModel @Inject constructor(
    private val coursesRepository: CoursesRepository,
    private val tagsRepository: TagsRepository,
    private val ratingsRepository: RatingsRepository,
    private val progressRepository: ProgressRepository
) : ViewModel() {

    suspend fun getCourseTags(courseId: String): List<RealmTag> {
        return tagsRepository.getTagsForCourse(courseId)
    }

    suspend fun getCourseRatings(userId: String?): HashMap<String?, JsonObject> {
        return ratingsRepository.getCourseRatings(userId)
    }

    suspend fun getCourseProgressSummary(userId: String?): HashMap<String?, JsonObject> {
        return progressRepository.getCourseProgress(userId)
    }

    suspend fun getCourseOfflineResources(courseIds: List<String>): List<RealmMyLibrary> {
        return coursesRepository.getCourseOfflineResources(courseIds)
    }

    suspend fun filterCourses(
        searchText: String,
        gradeLevel: String,
        subjectLevel: String,
        tagNames: List<String>
    ): List<RealmMyCourse> {
        return coursesRepository.filterCourses(searchText, gradeLevel, subjectLevel, tagNames)
    }

    suspend fun saveSearchActivity(
        searchText: String,
        userName: String,
        planetCode: String,
        parentCode: String,
        tags: List<RealmTag>,
        grade: String,
        subject: String
    ) {
        coursesRepository.saveSearchActivity(
            searchText, userName, planetCode, parentCode, tags, grade, subject
        )
    }
}
