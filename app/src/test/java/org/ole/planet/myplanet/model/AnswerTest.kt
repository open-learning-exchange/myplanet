package org.ole.planet.myplanet.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AnswerTest {

    @Test
    fun serializeAnswer_writesGradeOnlyWhenItIsSet() {
        val json = Answer.serializeAnswer(listOf(
            Answer(value = "A", questionId = "q1", grade = 1),
            Answer(value = "A written answer", questionId = "q2", grade = null)
        ))

        assertEquals(1, json[0].asJsonObject.get("grade").asInt)
        assertFalse(json[1].asJsonObject.has("grade"))
    }
}
