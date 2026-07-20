package org.ole.planet.myplanet.utils

import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.ole.planet.myplanet.model.RealmExamQuestion

class ExamAnswerUtilsTest {

    private fun createQuestion(questionType: String?, choices: List<String>): RealmExamQuestion {
        val mockQuestion = mockk<RealmExamQuestion>()
        every { mockQuestion.type } returns questionType
        every { mockQuestion.getCorrectChoice() } returns choices.toMutableList()
        return mockQuestion
    }

    @Test
    fun testCheckCorrectAnswer_Select() {
        val question = createQuestion("select", listOf("correct answer"))

        assertTrue(ExamAnswerUtils.checkCorrectAnswer("correct answer", null, question))
        assertFalse(ExamAnswerUtils.checkCorrectAnswer("wrong answer", null, question))
        assertTrue(ExamAnswerUtils.checkCorrectAnswer("CoRrEcT aNsWeR", null, question))
    }

    @Test
    fun testCheckCorrectAnswer_SelectMultiple() {
        val question = createQuestion("selectMultiple", listOf("A", "B"))

        val matchingSet = mapOf("0" to "A", "1" to "B")
        assertTrue(ExamAnswerUtils.checkCorrectAnswer("", matchingSet, question))

        val subset = mapOf("0" to "A")
        assertFalse(ExamAnswerUtils.checkCorrectAnswer("", subset, question))

        val superset = mapOf("0" to "A", "1" to "B", "2" to "C")
        assertFalse(ExamAnswerUtils.checkCorrectAnswer("", superset, question))

        val emptySet = emptyMap<String, String>()
        assertFalse(ExamAnswerUtils.checkCorrectAnswer("", emptySet, question))
    }

    @Test
    fun testCheckCorrectAnswer_InputText() {
        val question = createQuestion("input", listOf("expected word"))

        assertTrue(ExamAnswerUtils.checkCorrectAnswer("the expected word is here", null, question))
        assertFalse(ExamAnswerUtils.checkCorrectAnswer("something else entirely", null, question))
        assertTrue(ExamAnswerUtils.checkCorrectAnswer("the EXPECTED WORD is here", null, question))
    }

    @Test
    fun testCheckCorrectAnswer_EdgeCases() {
        val question = createQuestion("select", listOf("A"))

        assertFalse(ExamAnswerUtils.checkCorrectAnswer("A", null, null))
        assertFalse(ExamAnswerUtils.checkCorrectAnswer("", null, question))

        val inputQuestion = createQuestion("input", listOf("A"))
        assertFalse(ExamAnswerUtils.checkCorrectAnswer("", null, inputQuestion))
    }
}
