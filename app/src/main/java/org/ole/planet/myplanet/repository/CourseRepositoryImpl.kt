package org.ole.planet.myplanet.repository

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmCourseStep
import org.ole.planet.myplanet.model.RealmMyCourse
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.ole.planet.myplanet.model.CourseProgress
import org.ole.planet.myplanet.model.RealmAnswer
import org.ole.planet.myplanet.model.RealmCourseProgress
import org.ole.planet.myplanet.model.RealmExamQuestion
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmRemovedLog
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission
import io.realm.Realm

class CourseRepositoryImpl @Inject constructor(
    databaseService: DatabaseService
) : RealmRepository(databaseService), CourseRepository {

    override suspend fun getMyCoursesFlow(userId: String): Flow<List<RealmMyCourse>> {
        return queryListFlow(RealmMyCourse::class.java) {
            equalTo("userId", userId)
        }
    }

    override suspend fun getCourseByCourseId(courseId: String?): RealmMyCourse? {
        if (courseId.isNullOrBlank()) {
            return null
        }
        return findByField(RealmMyCourse::class.java, "courseId", courseId)
    }

    override suspend fun getCourseOnlineResources(courseId: String?): List<RealmMyLibrary> {
        return getCourseResources(courseId, isOffline = false)
    }

    override suspend fun getCourseOfflineResources(courseId: String?): List<RealmMyLibrary> {
        return getCourseResources(courseId, isOffline = true)
    }

    override suspend fun getCourseExamCount(courseId: String?): Int {
        if (courseId.isNullOrEmpty()) {
            return 0
        }
        return count(RealmStepExam::class.java) {
            equalTo("courseId", courseId)
        }.toInt()
    }

    override suspend fun getCourseSteps(courseId: String?): List<RealmCourseStep> {
        if (courseId.isNullOrEmpty()) {
            return emptyList()
        }
        return queryList(RealmCourseStep::class.java) {
            equalTo("courseId", courseId)
        }
    }

    override suspend fun markCourseAdded(courseId: String, userId: String?): Boolean {
        if (courseId.isBlank()) {
            return false
        }

        var courseFound = false
        executeTransaction { realm ->
            realm.where(RealmMyCourse::class.java)
                .equalTo("courseId", courseId)
                .findFirst()
                ?.let { course ->
                    course.setUserId(userId)
                    if (!userId.isNullOrBlank()) {
                        realm.where(RealmRemovedLog::class.java)
                            .equalTo("type", "courses")
                            .equalTo("userId", userId)
                            .equalTo("docId", course.courseId)
                            .findAll()
                            .deleteAllFromRealm()
                    }
                    courseFound = true
                }
        }

        return courseFound
    }

    private suspend fun getCourseResources(courseId: String?, isOffline: Boolean): List<RealmMyLibrary> {
        if (courseId.isNullOrEmpty()) {
            return emptyList()
        }
        return queryList(RealmMyLibrary::class.java) {
            equalTo("courseId", courseId)
            equalTo("resourceOffline", isOffline)
            isNotNull("resourceLocalAddress")
        }
    }

    override suspend fun getCourseProgress(courseId: String, userId: String?): CourseProgress {
        return withRealm { realm ->
            val course = realm.where(RealmMyCourse::class.java).equalTo("courseId", courseId).findFirst()
            val courseProgress = RealmCourseProgress.getCourseProgress(realm, userId)
            val progressObject = courseProgress.get(courseId)
            val currentProgress = progressObject?.get("current")?.asInt ?: 0
            val maxProgress = progressObject?.get("max")?.asInt ?: 0
            val progress = if (maxProgress > 0) {
                (currentProgress.toDouble() / maxProgress.toDouble() * 100).toInt()
            } else {
                0
            }
            val steps = realm.where(RealmCourseStep::class.java).contains("courseId", courseId).findAll()
            val array = JsonArray()
            steps.map {
                val ob = JsonObject()
                ob.addProperty("stepId", it.id)
                val exams = realm.where(RealmStepExam::class.java).equalTo("stepId", it.id).findAll()
                getExamObject(realm, exams, ob, userId)
                array.add(ob)
            }
            CourseProgress(course?.courseTitle, progress, array, currentProgress, maxProgress)
        }
    }

    private fun getExamObject(
        realm: Realm,
        exams: List<RealmStepExam>,
        ob: JsonObject,
        userId: String?
    ) {
        exams.forEach { exam ->
            if (exam.id != null && userId != null) {
                realm.where(RealmSubmission::class.java).equalTo("userId", userId)
                    .contains("parentId", exam.id!!).equalTo("type", "exam").findAll()
                    .map { submission ->
                        val answers = realm.where(RealmAnswer::class.java).equalTo("submissionId", submission.id).findAll()
                        var examId = submission.parentId
                        if (submission.parentId?.contains("@") == true) {
                            examId = submission.parentId!!.split("@")[0]
                        }
                        val questions = realm.where(RealmExamQuestion::class.java).equalTo("examId", examId).findAll()
                        val questionCount = questions.size
                        if (questionCount == 0) {
                            ob.addProperty("completed", false)
                            ob.addProperty("percentage", 0)
                        } else {
                            ob.addProperty("completed", answers.size == questionCount)
                            val percentage = (answers.size.toDouble() / questionCount) * 100
                            ob.addProperty("percentage", percentage)
                        }
                        ob.addProperty("status", submission.status)
                    }
            }
        }
    }
}
