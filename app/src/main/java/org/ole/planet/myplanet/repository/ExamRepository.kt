package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.RealmCourseProgress
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.RealmSubmitPhotos

interface ExamRepository {
    suspend fun getSubmission(id: String): RealmSubmission?
    suspend fun getExamByStepId(stepId: String): RealmStepExam?
    suspend fun getExamById(id: String): RealmStepExam?
    suspend fun getCourseProgress(courseId: String, stepNum: Int): RealmCourseProgress?
    suspend fun updateCourseProgress(courseId: String, stepNum: Int, passed: Boolean)
    suspend fun updateSubmissionStatus(submissionId: String, status: String)
    suspend fun insertPhotoSubmission(
        submissionId: String,
        examId: String?,
        courseId: String?,
        userId: String?,
        date: String,
        uniqueId: String,
        photoPath: String?
    )
    suspend fun getExamQuestions(examId: String?): List<RealmExamQuestion>
    suspend fun getPendingSubmission(userId: String?, parentId: String, type: String?): RealmSubmission?
    suspend fun isCourseCertified(courseId: String?): Boolean
    suspend fun createSubmission(
        exam: RealmStepExam?,
        user: RealmUserModel?,
        id: String?,
        type: String?,
        isTeam: Boolean,
        teamId: String?
    ): RealmSubmission?

    suspend fun clearAllExistingAnswers(examId: String?, courseId: String?, userId: String?, id: String?)
    suspend fun saveAnswer(
        submissionId: String?,
        question: RealmExamQuestion?,
        ans: String,
        listAns: HashMap<String, String>?,
        otherText: String?,
        isEtVisible: Boolean,
        type: String?,
        questionsSize: Int
    ): Boolean
}
