package org.ole.planet.myplanet.model

data class ExamAnswerData(
    val submission: RealmSubmission?,
    val question: RealmExamQuestion,
    val ans: String,
    val listAns: Map<String, String>?,
    val otherText: String?,
    val otherVisible: Boolean,
    val type: String,
    val index: Int,
    val total: Int,
    val isExplicitSubmission: Boolean
)
