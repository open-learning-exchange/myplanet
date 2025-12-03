package org.ole.planet.myplanet.ui.submission

data class SubmissionInfo(
    val title: String,
    val status: String,
    val date: String,
    val submittedBy: String
)

data class AnswerInfo(
    val value: String?,
    val isPassed: Boolean
)

data class QuestionAnswerInfo(
    val questionHeader: String?,
    val questionBody: String?,
    val answer: AnswerInfo?,
    val type: String?
)
