package org.ole.planet.myplanet.ui.submission

data class QuestionAnswer(
    val questionId: String?,
    val questionHeader: String?,
    val questionBody: String?,
    val questionType: String?,
    val answer: String?,
    val answerChoices: List<String>?,
    val isCorrect: Boolean
)
