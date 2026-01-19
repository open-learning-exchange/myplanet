package org.ole.planet.myplanet.ui.submissions

import org.ole.planet.myplanet.model.QuestionAnswer

data class SubmissionDetail(
    val title: String,
    val status: String,
    val date: Long,
    val submittedBy: String,
    val questionAnswers: List<QuestionAnswer>
)
