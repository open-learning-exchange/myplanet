package org.ole.planet.myplanet.model.dto

import org.ole.planet.myplanet.model.RealmAnswer
import org.ole.planet.myplanet.model.RealmExamQuestion

data class QuestionAnswerPair(
    val question: RealmExamQuestion,
    val answer: RealmAnswer?
)
