package org.ole.planet.myplanet.repository

import android.text.TextUtils
import io.realm.Realm
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmCourseProgress
import org.ole.planet.myplanet.model.RealmExamQuestion
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmitPhotos
import org.ole.planet.myplanet.model.RealmSubmission
import java.util.UUID
import javax.inject.Inject

class ExamRepositoryImpl @Inject constructor(
    databaseService: DatabaseService
) : RealmRepository(databaseService), ExamRepository {
    override suspend fun getExam(stepId: String?, id: String?): RealmStepExam? {
        return withRealm { realm ->
            val query = realm.where(RealmStepExam::class.java)
            if (!TextUtils.isEmpty(stepId)) {
                query.equalTo("stepId", stepId)
            } else {
                query.equalTo("id", id)
            }
            val exam = query.findFirst()
            exam?.let { realm.copyFromRealm(it) }
        }
    }

    override suspend fun getQuestions(examId: String?): List<RealmExamQuestion> {
        return queryList(RealmExamQuestion::class.java) {
            equalTo("examId", examId)
        }
    }

    override suspend fun getCourseProgress(courseId: String?, stepNumber: Int): RealmCourseProgress? {
        return withRealm { realm ->
            val progress = realm.where(RealmCourseProgress::class.java)
                .equalTo("courseId", courseId)
                .equalTo("stepNum", stepNumber)
                .findFirst()
            progress?.let { realm.copyFromRealm(it) }
        }
    }

    override suspend fun updateCourseProgress(courseId: String?, stepNumber: Int, passed: Boolean) {
        executeTransaction { realm ->
            val progress = realm.where(RealmCourseProgress::class.java)
                .equalTo("courseId", courseId)
                .equalTo("stepNum", stepNumber)
                .findFirst()
            progress?.passed = passed
        }
    }

    override suspend fun insertPhotoSubmission(
        submitId: String,
        exam: RealmStepExam?,
        userId: String?,
        date: String,
        uniqueId: String,
        photoPath: String?
    ) {
        executeTransaction { realm ->
            val submit = realm.createObject(RealmSubmitPhotos::class.java, UUID.randomUUID().toString())
            submit.submissionId = submitId
            submit.examId = exam?.id
            submit.courseId = exam?.courseId
            submit.memberId = userId
            submit.date = date
            submit.uniqueId = uniqueId
            submit.photoLocation = photoPath
            submit.uploaded = false
        }
    }

    override suspend fun getSubmission(id: String?): RealmSubmission? {
        return withRealm { realm ->
            val submission = realm.where(RealmSubmission::class.java)
                .equalTo("id", id)
                .findFirst()
            submission?.let { realm.copyFromRealm(it) }
        }
    }

    override suspend fun isCourseCertified(courseId: String?): Boolean {
        if (courseId == null) return false
        return withRealm { realm ->
            realm.where(org.ole.planet.myplanet.model.RealmCertification::class.java)
                .contains("courseIds", courseId)
                .count() > 0
        }
    }
}
