package org.ole.planet.myplanet.model.dto

import org.ole.planet.myplanet.model.RealmAnswer
import org.ole.planet.myplanet.model.RealmExamQuestion

data class QuestionChoice(
    val id: String?,
    val text: String?
)

data class QuestionAnswerViewModel(
    val question: RealmExamQuestion,
    val answer: RealmAnswer?,
    val choices: List<QuestionChoice>
)
