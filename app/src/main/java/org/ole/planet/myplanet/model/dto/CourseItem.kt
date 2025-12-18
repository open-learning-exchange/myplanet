package org.ole.planet.myplanet.model.dto

import com.google.gson.JsonObject
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.utilities.JsonUtils

data class CourseItem(
    val id: String?,
    val courseId: String?,
    val courseTitle: String?,
    val description: String?,
    val gradeLevel: String?,
    val subjectLevel: String?,
    val createdDate: Long?,
    val isMyCourse: Boolean,
    val numberOfSteps: Int,
    val averageRating: Float?,
    val totalRatings: Int?,
    val currentProgress: Int?,
    val maxProgress: Int?
) {
    companion object {
        fun from(
            course: RealmMyCourse,
            ratings: HashMap<String?, JsonObject>,
            progress: HashMap<String?, JsonObject>
        ): CourseItem {
            val ratingObject = ratings[course.courseId]
            val progressObject = progress[course.courseId]
            return CourseItem(
                id = course.id,
                courseId = course.courseId,
                courseTitle = course.courseTitle,
                description = course.description,
                gradeLevel = course.gradeLevel,
                subjectLevel = course.subjectLevel,
                createdDate = course.createdDate,
                isMyCourse = course.isMyCourse,
                numberOfSteps = course.getNumberOfSteps(),
                averageRating = ratingObject?.get("average")?.asFloat,
                totalRatings = ratingObject?.get("count")?.asInt,
                currentProgress = progressObject?.let { JsonUtils.getInt("current", it) },
                maxProgress = progressObject?.let { JsonUtils.getInt("max", it) }
            )
        }
    }
}
