package org.ole.planet.myplanet.model.dto

import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.RealmUserModel

data class SubmissionDetail(
    val submission: RealmSubmission,
    val exam: RealmStepExam?,
    val user: RealmUserModel?,
    val questionAnswers: List<QuestionAnswerPair>
)
