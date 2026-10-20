package org.ole.planet.myplanet.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.ole.planet.myplanet.data.room.dao.QuestionDao
import org.ole.planet.myplanet.model.ExamQuestion
import org.ole.planet.myplanet.model.Submission
import org.ole.planet.myplanet.utils.TestTimeProvider

class SubmissionsRepositoryExporterTest {

    @Test
    fun `dateFormatter formats Instant correctly in fixed timezone`() {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.getDefault())
            .withZone(ZoneId.of("UTC"))

        // 2023-10-15T14:30:00Z -> 1697380200L
        val instant = Instant.ofEpochSecond(1697380200L)
        val formatted = formatter.format(instant)

        assertEquals("2023-10-15 14:30", formatted)
    }

    @Test
    fun `generatedLine uses injected timeProvider`() {
        val testTimeProvider = TestTimeProvider(1697380200000L)
        val exporter = SubmissionsRepositoryExporter(
            mockk(relaxed = true),
            mockk(),
            mockk(),
            mockk(),
            mockk(),
            testTimeProvider
        )

        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.getDefault())
            .withZone(ZoneId.systemDefault())

        val expectedInitial = "Generated: " + formatter.format(Instant.ofEpochMilli(1697380200000L))
        assertEquals(expectedInitial, exporter.generatedLine())

        testTimeProvider.advanceBy(60_000)
        val expectedAdvanced = "Generated: " + formatter.format(Instant.ofEpochMilli(1697380260000L))
        assertEquals(expectedAdvanced, exporter.generatedLine())
    }

    @Test
    fun `getQuestionsByExam handles mixed exams and maps questions to respective exam`() = runTest {
        val questionDao = mockk<QuestionDao>()
        val exporter = SubmissionsRepositoryExporter(
            mockk(relaxed = true),
            mockk(),
            mockk(),
            mockk(),
            questionDao,
            TestTimeProvider()
        )

        val sub1 = Submission().apply { id = "sub1"; parentId = "exam1" }
        val sub2 = Submission().apply { id = "sub2"; parentId = "exam2" }

        val q1 = ExamQuestion(id = "q1", examId = "exam1")
        val q2 = ExamQuestion(id = "q2", examId = "exam2")

        coEvery { questionDao.getByExamIds(listOf("exam1", "exam2")) } returns listOf(q1, q2)

        val result = exporter.getQuestionsByExam(listOf(sub1, sub2))

        assertEquals(2, result.size)
        assertEquals(listOf(q1), result["exam1"])
        assertEquals(listOf(q2), result["exam2"])
        coVerify(exactly = 1) { questionDao.getByExamIds(listOf("exam1", "exam2")) }
    }

    @Test
    fun `getQuestionsByExam queries repeated exam only once`() = runTest {
        val questionDao = mockk<QuestionDao>()
        val exporter = SubmissionsRepositoryExporter(
            mockk(relaxed = true),
            mockk(),
            mockk(),
            mockk(),
            questionDao,
            TestTimeProvider()
        )

        val sub1 = Submission().apply { id = "sub1"; parentId = "exam1" }
        val sub2 = Submission().apply { id = "sub2"; parentId = "exam1" }

        val q1 = ExamQuestion(id = "q1", examId = "exam1")

        coEvery { questionDao.getByExamIds(listOf("exam1")) } returns listOf(q1)

        val result = exporter.getQuestionsByExam(listOf(sub1, sub2))

        assertEquals(1, result.size)
        assertEquals(listOf(q1), result["exam1"])
        coVerify(exactly = 1) { questionDao.getByExamIds(listOf("exam1")) }
    }

    @Test
    fun `getQuestionsByExam returns empty map when parentId is missing or null`() = runTest {
        val questionDao = mockk<QuestionDao>()
        val exporter = SubmissionsRepositoryExporter(
            mockk(relaxed = true),
            mockk(),
            mockk(),
            mockk(),
            questionDao,
            TestTimeProvider()
        )

        val sub1 = Submission().apply { id = "sub1"; parentId = null }

        val result = exporter.getQuestionsByExam(listOf(sub1))

        assertTrue(result.isEmpty())
        coVerify(exactly = 0) { questionDao.getByExamIds(any()) }
    }

    @Test
    fun `getQuestionsByExam parses parentId with at-symbol format`() = runTest {
        val questionDao = mockk<QuestionDao>()
        val exporter = SubmissionsRepositoryExporter(
            mockk(relaxed = true),
            mockk(),
            mockk(),
            mockk(),
            questionDao,
            TestTimeProvider()
        )

        val sub1 = Submission().apply { id = "sub1"; parentId = "exam1@course1" }
        val q1 = ExamQuestion(id = "q1", examId = "exam1")

        coEvery { questionDao.getByExamIds(listOf("exam1")) } returns listOf(q1)

        val result = exporter.getQuestionsByExam(listOf(sub1))

        assertEquals(1, result.size)
        assertEquals(listOf(q1), result["exam1"])
        coVerify(exactly = 1) { questionDao.getByExamIds(listOf("exam1")) }
    }
}
