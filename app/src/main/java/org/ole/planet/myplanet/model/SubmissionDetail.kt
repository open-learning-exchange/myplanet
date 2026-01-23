package org.ole.planet.myplanet.model

data class SubmissionDetail(
    val title: String,
    val status: String,
    val date: Long,
    val submittedBy: String,
    val questionAnswers: List<QuestionAnswer>
)
