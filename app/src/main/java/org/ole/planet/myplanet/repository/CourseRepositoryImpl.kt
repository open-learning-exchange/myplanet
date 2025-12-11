package org.ole.planet.myplanet.repository

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmCourseStep
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.CourseProgressData
import org.ole.planet.myplanet.model.RealmAnswer
import org.ole.planet.myplanet.model.RealmCourseProgress
import org.ole.planet.myplanet.model.RealmExamQuestion
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmRemovedLog
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.StepProgress

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

    override suspend fun filterCourses(
        searchText: String,
        gradeLevel: String,
        subjectLevel: String,
        tagNames: List<String>
    ): List<RealmMyCourse> {
        return withRealm { realm ->
            val courseIdsWithTags = if (tagNames.isNotEmpty()) {
                val tagIds = realm.where(org.ole.planet.myplanet.model.RealmTag::class.java)
                    .`in`("name", tagNames.toTypedArray())
                    .findAll()
                    .map { it.id }

                realm.where(org.ole.planet.myplanet.model.RealmTag::class.java)
                    .equalTo("db", "courses")
                    .`in`("tagId", tagIds.toTypedArray())
                    .findAll()
                    .map { it.linkId }
            } else {
                null
            }

            var query = realm.where(RealmMyCourse::class.java)
            if (searchText.isNotEmpty()) {
                query = query.contains("courseTitle", searchText, io.realm.Case.INSENSITIVE)
            }
            if (gradeLevel.isNotEmpty()) {
                query = query.equalTo("gradeLevel", gradeLevel)
            }
            if (subjectLevel.isNotEmpty()) {
                query = query.equalTo("subjectLevel", subjectLevel)
            }
            courseIdsWithTags?.let {
                query = query.`in`("courseId", it.toTypedArray())
            }

            val results = query.findAll()
            val sortedList = results
                .filter { !it.courseTitle.isNullOrBlank() }
                .sortedWith(compareBy({ it.isMyCourse }, { it.courseTitle }))
            realm.copyFromRealm(sortedList)
        }
    }

    override suspend fun getCourseProgress(courseId: String, userId: String?): CourseProgressData? {
        return withRealm { realm ->
            val stepsList = RealmMyCourse.getCourseSteps(realm, courseId)
            val max = stepsList.size
            val current = RealmCourseProgress.getCurrentProgress(stepsList, realm, userId, courseId)

            val course = realm.where(RealmMyCourse::class.java).equalTo("courseId", courseId).findFirst()
            val title = course?.courseTitle

            val steps = stepsList.map { step ->
                val stepProgress = StepProgress(step.id)
                val exams = realm.where(RealmStepExam::class.java).equalTo("stepId", step.id).findAll()
                getExamObject(realm, exams, stepProgress, userId)
                stepProgress
            }
            CourseProgressData(title, current, max, steps)
        }
    }

    private fun getExamObject(
        realm: io.realm.Realm,
        exams: io.realm.RealmResults<RealmStepExam>,
        stepProgress: StepProgress,
        userId: String?
    ) {
        exams.forEach { it ->
            it.id?.let { it1 ->
                realm.where(RealmSubmission::class.java).equalTo("userId", userId)
                    .contains("parentId", it1).equalTo("type", "exam").findAll()
            }?.forEach {
                val answers = realm.where(RealmAnswer::class.java).equalTo("submissionId", it.id).findAll()
                var examId = it.parentId
                if (it.parentId?.contains("@") == true) {
                    examId = it.parentId!!.split("@")[0]
                }
                val questions = realm.where(RealmExamQuestion::class.java).equalTo("examId", examId).findAll()
                val questionCount = questions.size
                if (questionCount == 0) {
                    stepProgress.completed = false
                    stepProgress.percentage = 0.0
                } else {
                    stepProgress.completed = answers.size == questionCount
                    val percentage = (answers.size.toDouble() / questionCount) * 100
                    stepProgress.percentage = percentage
                }
                stepProgress.status = it.status
            }
        }
    }
}
