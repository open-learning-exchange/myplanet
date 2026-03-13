package org.ole.planet.myplanet.utils

import io.realm.RealmList
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.ole.planet.myplanet.model.RealmExamQuestion

class ExamAnswerUtilsTest {

    private fun createQuestion(type: String, choices: List<String>): RealmExamQuestion {
        val question = RealmExamQuestion()
        question.type = type

        val correctChoiceField = RealmExamQuestion::class.java.getDeclaredField("correctChoice")
        correctChoiceField.isAccessible = true
        val list = RealmList<String>()
        list.addAll(choices)
        correctChoiceField.set(question, list)

        return question
    }

    @Test
    fun testCheckCorrectAnswer_Select() {
        val question = createQuestion("select", listOf("correct answer"))

        // Correct answer
        assertTrue(ExamAnswerUtils.checkCorrectAnswer("correct answer", null, question))

        // Wrong answer
        assertFalse(ExamAnswerUtils.checkCorrectAnswer("wrong answer", null, question))

        // Case-insensitive match
        assertTrue(ExamAnswerUtils.checkCorrectAnswer("CoRrEcT aNsWeR", null, question))
    }

    @Test
    fun testCheckCorrectAnswer_SelectMultiple() {
        val question = createQuestion("selectMultiple", listOf("A", "B"))

        // Matching set
        val matchingSet = mapOf("0" to "A", "1" to "B")
        assertTrue(ExamAnswerUtils.checkCorrectAnswer("", matchingSet, question))

        // Subset
        val subset = mapOf("0" to "A")
        assertFalse(ExamAnswerUtils.checkCorrectAnswer("", subset, question))

        // Superset
        val superset = mapOf("0" to "A", "1" to "B", "2" to "C")
        assertFalse(ExamAnswerUtils.checkCorrectAnswer("", superset, question))

        // Empty set
        val emptySet = emptyMap<String, String>()
        assertFalse(ExamAnswerUtils.checkCorrectAnswer("", emptySet, question))
    }

    @Test
    fun testCheckCorrectAnswer_InputText() {
        val question = createQuestion("input", listOf("expected word"))

        // Partial match
        assertTrue(ExamAnswerUtils.checkCorrectAnswer("the expected word is here", null, question))

        // No match
        assertFalse(ExamAnswerUtils.checkCorrectAnswer("something else entirely", null, question))

        // Case-insensitive match
        assertTrue(ExamAnswerUtils.checkCorrectAnswer("the EXPECTED WORD is here", null, question))
    }

    @Test
    fun testCheckCorrectAnswer_EdgeCases() {
        val question = createQuestion("select", listOf("A"))

        // Null question
        assertFalse(ExamAnswerUtils.checkCorrectAnswer("A", null, null))

        // Null answer for select
        assertFalse(ExamAnswerUtils.checkCorrectAnswer("", null, question))

        // Empty answer for input
        val inputQuestion = createQuestion("input", listOf("A"))
        assertFalse(ExamAnswerUtils.checkCorrectAnswer("", null, inputQuestion))
    }
}
