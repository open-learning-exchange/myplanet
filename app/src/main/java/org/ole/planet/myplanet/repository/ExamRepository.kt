package org.ole.planet.myplanet.repository

import io.realm.RealmResults
import org.ole.planet.myplanet.model.RealmCourseProgress
import org.ole.planet.myplanet.model.RealmExamQuestion
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmitPhotos
import org.ole.planet.myplanet.model.RealmSubmission

interface ExamRepository {
    suspend fun getExam(stepId: String?, id: String?): RealmStepExam?
    suspend fun getQuestions(examId: String?): List<RealmExamQuestion>
    suspend fun getCourseProgress(courseId: String?, stepNumber: Int): RealmCourseProgress?
    suspend fun updateCourseProgress(courseId: String?, stepNumber: Int, passed: Boolean)
    suspend fun insertPhotoSubmission(
        submitId: String,
        exam: RealmStepExam?,
        userId: String?,
        date: String,
        uniqueId: String,
        photoPath: String?,
    )
    suspend fun getSubmission(id: String?): RealmSubmission?
    suspend fun isCourseCertified(courseId: String?): Boolean
}
