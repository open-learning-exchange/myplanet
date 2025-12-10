package org.ole.planet.myplanet.ui.submission

import org.ole.planet.myplanet.model.RealmExamQuestion
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.RealmAnswer
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmUserModel

data class SubmissionDetail(
    val submission: RealmSubmission,
    val exam: RealmStepExam?,
    val user: RealmUserModel?,
    val questionAnswerPairs: List<QuestionAnswerPair>
)

data class QuestionAnswerPair(
    val question: RealmExamQuestion,
    val answer: RealmAnswer?
)
