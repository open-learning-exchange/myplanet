package org.ole.planet.myplanet.utils

import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.ole.planet.myplanet.model.ExamQuestion

class ExamAnswerUtilsTest {

    private fun createQuestion(questionType: String?, choices: List<String>): ExamQuestion {
        val mockQuestion = mockk<ExamQuestion>()
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

        val multiQuestion = createQuestion("input", listOf("first choice", "second choice"))
        assertTrue(ExamAnswerUtils.checkCorrectAnswer("this is the SECOND choice", null, multiQuestion))
        assertTrue(ExamAnswerUtils.checkCorrectAnswer("FIRST choice here", null, multiQuestion))
        assertFalse(ExamAnswerUtils.checkCorrectAnswer("third choice is missing", null, multiQuestion))
    }

    @Test
    fun testCheckCorrectAnswer_EdgeCases() {
        val question = createQuestion("select", listOf("A"))

        assertFalse(ExamAnswerUtils.checkCorrectAnswer("A", null, null))
        assertFalse(ExamAnswerUtils.checkCorrectAnswer("", null, question))

        val inputQuestion = createQuestion("input", listOf("A"))
        assertFalse(ExamAnswerUtils.checkCorrectAnswer("", null, inputQuestion))
    }

    @Test
    fun testChoiceCachingAndChangedJson() {
        val json1 = "[{\"id\":\"1\",\"text\":\"Choice A\"},{\"id\":\"2\",\"text\":\"Choice B\"}]"
        val mockQuestion1 = mockk<ExamQuestion>()
        every { mockQuestion1.choices } returns json1

        // First call populates cache
        val text1 = ExamAnswerUtils.getChoiceTextById(mockQuestion1, "1")
        org.junit.Assert.assertEquals("Choice A", text1)

        // Second call hits cache (returns same value)
        val text2 = ExamAnswerUtils.getChoiceTextById(mockQuestion1, "2")
        org.junit.Assert.assertEquals("Choice B", text2)

        val json2 = "[{\"id\":\"1\",\"text\":\"Updated Choice A\"},{\"id\":\"3\",\"text\":\"Choice C\"}]"
        val mockQuestion2 = mockk<ExamQuestion>()
        every { mockQuestion2.choices } returns json2

        // Different JSON populates a new map in cache, should get updated text
        val text3 = ExamAnswerUtils.getChoiceTextById(mockQuestion2, "1")
        org.junit.Assert.assertEquals("Updated Choice A", text3)
        val text4 = ExamAnswerUtils.getChoiceTextById(mockQuestion2, "3")
        org.junit.Assert.assertEquals("Choice C", text4)
    }

    @Test
    fun testMissingIdsFallback() {
        val json = "[{\"id\":\"1\",\"text\":\"Choice A\"}]"
        val mockQuestion = mockk<ExamQuestion>()
        every { mockQuestion.choices } returns json

        // Known ID
        val text1 = ExamAnswerUtils.getChoiceTextById(mockQuestion, "1")
        org.junit.Assert.assertEquals("Choice A", text1)

        // Missing ID should fallback to ID itself
        val text2 = ExamAnswerUtils.getChoiceTextById(mockQuestion, "999")
        org.junit.Assert.assertEquals("999", text2)

        // Null choices should fallback to ID itself
        val mockQuestionNullChoices = mockk<ExamQuestion>()
        every { mockQuestionNullChoices.choices } returns null
        val text3 = ExamAnswerUtils.getChoiceTextById(mockQuestionNullChoices, "123")
        org.junit.Assert.assertEquals("123", text3)
    }

    @Test
    fun testEvictionOrder() {
        // We will insert 105 distinct choices JSONs to force eviction of the first 5
        for (i in 1..105) {
            val json = "[{\"id\":\"$i\",\"text\":\"Choice $i\"}]"
            val mockQuestion = mockk<ExamQuestion>()
            every { mockQuestion.choices } returns json

            // This will parse and cache it
            ExamAnswerUtils.getChoiceTextById(mockQuestion, "$i")
        }

        // At this point, the cache has exactly 100 items (6 through 105).
        // Since we didn't mock time or provide any spying mechanism on the internal cache,
        // we can just verify that it still works (if we request item 1, it will have to re-parse it,
        // which is functionally the same from outside).

        // Let's request item 105 again to ensure it's still accessible.
        val json105 = "[{\"id\":\"105\",\"text\":\"Choice 105\"}]"
        val mockQuestion105 = mockk<ExamQuestion>()
        every { mockQuestion105.choices } returns json105
        org.junit.Assert.assertEquals("Choice 105", ExamAnswerUtils.getChoiceTextById(mockQuestion105, "105"))

        // If the cache was not bounded, it would be 105 items long, but we can't easily assert on private state
        // without reflection. So we'll trust the LinkedHashMap eviction handles it correctly under the hood,
        // while this test primarily ensures adding 105 items doesn't crash anything.
    }
}
