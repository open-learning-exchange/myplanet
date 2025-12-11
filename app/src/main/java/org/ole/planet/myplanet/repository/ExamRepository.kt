package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.RealmExamQuestion
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.RealmSubmitPhotos

interface ExamRepository {
    suspend fun getSubmissionById(id: String): RealmSubmission?
    suspend fun getExamByStepId(stepId: String): RealmStepExam?
    suspend fun getExamById(id: String): RealmStepExam?
    suspend fun getQuestions(exam: RealmStepExam?): List<RealmExamQuestion>?
    suspend fun saveCourseProgress(exam: RealmStepExam?, stepNumber: Int, sub: RealmSubmission?)
    suspend fun insertIntoSubmitPhotos(
        submitId: String?,
        exam: RealmStepExam?,
        userId: String?,
        date: String,
        uniqueId: String,
        photoPath: String?
    )
    suspend fun updateSubmissionStatus(subId: String, status: String)
    suspend fun clearAllSubmissions(userId: String?, parentId: String?)
    suspend fun saveAnswer(
        submission: org.ole.planet.myplanet.model.RealmSubmission?,
        question: org.ole.planet.myplanet.model.RealmExamQuestion,
        ans: String,
        listAns: Map<String, String>?,
        otherText: String?,
        otherVisible: Boolean,
        type: String,
        index: Int,
        total: Int
    ): Boolean
    suspend fun createSubmission(
        submission: org.ole.planet.myplanet.model.RealmSubmission?,
        user: org.ole.planet.myplanet.model.RealmUserModel?,
        exam: org.ole.planet.myplanet.model.RealmStepExam?,
        id: String?,
        isTeam: Boolean,
        teamId: String?
    )
    suspend fun getSubmission(userId: String?, parentId: String?, type: String?): org.ole.planet.myplanet.model.RealmSubmission?
    suspend fun isCourseCertified(courseId: String?): Boolean
}
