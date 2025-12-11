package org.ole.planet.myplanet.ui.exam

import kotlinx.coroutines.runBlocking
import org.ole.planet.myplanet.model.RealmExamQuestion
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.repository.ExamRepository

object ExamSubmissionUtils {
    fun saveAnswer(
        examRepository: ExamRepository,
        submission: RealmSubmission?,
        question: RealmExamQuestion,
        ans: String,
        listAns: Map<String, String>?,
        otherText: String?,
        otherVisible: Boolean,
        type: String,
        index: Int,
        total: Int
    ): Boolean {
        return runBlocking {
            examRepository.saveAnswer(
                submission,
                question,
                ans,
                listAns,
                otherText,
                otherVisible,
                type,
                index,
                total
            )
        }
    }
}
