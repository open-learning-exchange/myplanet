package org.ole.planet.myplanet.ui.exam

import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.RealmExamQuestion
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.repository.SubmissionRepository

class ExamSubmissionUtils @Inject constructor(
    private val submissionRepository: SubmissionRepository
) {
    fun saveAnswer(
        coroutineScope: CoroutineScope,
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
        val submissionId = try {
            submission?.id
        } catch (e: IllegalStateException) {
            null
        }
        coroutineScope.launch {
            submissionRepository.saveAnswer(
                submissionId,
                question.id,
                ans,
                listAns,
                otherText,
                otherVisible,
                type,
                index,
                total
            )
        }

        return if (type == "exam") {
            ExamAnswerUtils.checkCorrectAnswer(ans, listAns, question)
        } else {
            true
        }
    }
}
