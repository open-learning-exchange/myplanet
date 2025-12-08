package org.ole.planet.myplanet.ui.submission

data class SubmissionDetail(
    val title: String,
    val status: String,
    val date: Long,
    val submittedBy: String,
    val questionAnswers: List<QuestionAnswer>
)
