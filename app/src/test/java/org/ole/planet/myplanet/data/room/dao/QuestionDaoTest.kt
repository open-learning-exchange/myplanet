package org.ole.planet.myplanet.data.room.dao

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.data.room.AppDatabase
import org.ole.planet.myplanet.model.ExamQuestion
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class QuestionDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var questionDao: QuestionDao

    @Before
    fun initDb() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        questionDao = database.questionDao()
    }

    @After
    fun closeDb() {
        database.close()
    }

    @Test
    fun deleteStaleQuestions_removesQuestionsNoLongerInTheSyncedExam() = runBlocking {
        questionDao.upsertAll(
            listOf(
                ExamQuestion(id = "q1", examId = "exam1"),
                ExamQuestion(id = "q2", examId = "exam1"),
                ExamQuestion(id = "q3", examId = "exam1"),
                ExamQuestion(id = "other", examId = "exam2"),
            )
        )

        questionDao.deleteStaleQuestions(listOf(ExamQuestion(id = "q1", examId = "exam1"), ExamQuestion(id = "q3", examId = "exam1")))

        assertEquals(listOf("q1", "q3"), questionDao.getByExamId("exam1").map { it.id }.sorted())
        assertEquals(listOf("other"), questionDao.getByExamId("exam2").map { it.id })
    }

    @Test
    fun getByIds_handlesMoreThan1000IdsAndDuplicates() = runBlocking {
        val questions = (0 until 1200).map { i ->
            ExamQuestion(id = "question_$i", examId = "exam_$i")
        }
        questionDao.upsertAll(questions)

        val ids = (0 until 1200).map { "question_$it" }
        val idsWithDuplicates = ids + ids.take(100)

        val result = questionDao.getByIds(idsWithDuplicates)

        assertEquals(1200, result.size)
        assertEquals(1200, result.map { it.id }.distinct().size)

        val emptyResult = questionDao.getByIds(emptyList())
        assertEquals(0, emptyResult.size)
    }

    @Test
    fun getByExamIds_handlesMoreThan1000ExamIdsAndDuplicatesWithoutThrowing() = runBlocking {
        val questions = (0 until 1200).map { i ->
            ExamQuestion(id = "question_$i", examId = "exam_$i")
        }
        questionDao.upsertAll(questions)

        val examIds = (0 until 1200).map { "exam_$it" }
        val examIdsWithDuplicates = examIds + examIds.take(100)

        val result = questionDao.getByExamIds(examIdsWithDuplicates)

        assertEquals(1200, result.size)
        assertEquals(1200, result.map { it.id }.distinct().size)

        val emptyResult = questionDao.getByExamIds(emptyList())
        assertEquals(0, emptyResult.size)
    }

    @Test
    fun deleteStaleQuestions_handlesMoreThan1000RetainedIds() = runBlocking {
        // Seeding 1200 questions in exam1, plus 5 stale questions in exam1
        val keptQuestions = (0 until 1200).map { i ->
            ExamQuestion(id = "kept_$i", examId = "exam1")
        }
        val staleQuestions = (0 until 5).map { i ->
            ExamQuestion(id = "stale_$i", examId = "exam1")
        }
        questionDao.upsertAll(keptQuestions + staleQuestions)

        // Keep all 1200 keptQuestions
        questionDao.deleteStaleQuestions(keptQuestions)

        val remainingIds = questionDao.getByExamId("exam1").map { it.id }.toSet()
        val expectedKeptIds = keptQuestions.map { it.id }.toSet()
        assertEquals(expectedKeptIds, remainingIds)
    }

    @Test
    fun deleteStaleQuestions_handlesMoreThan900StaleRows() = runBlocking {
        // Seeding 1200 questions in exam1; keeping only 200 of them (>900 stale rows to delete)
        val questions = (0 until 1200).map { i ->
            ExamQuestion(id = "q_$i", examId = "exam1")
        }
        questionDao.upsertAll(questions)

        val keptQuestions = questions.take(200)
        questionDao.deleteStaleQuestions(keptQuestions)

        val remainingIds = questionDao.getByExamId("exam1").map { it.id }.toSet()
        val expectedKeptIds = keptQuestions.map { it.id }.toSet()
        assertEquals(expectedKeptIds, remainingIds)
        assertEquals(200, remainingIds.size)
    }

    @Test
    fun deleteStaleQuestions_handlesDuplicateIdsInKeepList() = runBlocking {
        val questions = listOf(
            ExamQuestion(id = "q1", examId = "exam1"),
            ExamQuestion(id = "q2", examId = "exam1"),
            ExamQuestion(id = "q3", examId = "exam1")
        )
        questionDao.upsertAll(questions)

        val duplicatesKeep = listOf(
            ExamQuestion(id = "q1", examId = "exam1"),
            ExamQuestion(id = "q1", examId = "exam1"),
            ExamQuestion(id = "q3", examId = "exam1")
        )
        questionDao.deleteStaleQuestions(duplicatesKeep)

        val remainingIds = questionDao.getByExamId("exam1").map { it.id }.toSet()
        assertEquals(setOf("q1", "q3"), remainingIds)
    }

    @Test
    fun deleteStaleQuestions_handlesTwoRepresentedExamsAndLeavesUnrepresentedExamUntouched() = runBlocking {
        val exam1Questions = listOf(
            ExamQuestion(id = "e1_q1", examId = "exam1"),
            ExamQuestion(id = "e1_q2", examId = "exam1")
        )
        val exam2Questions = listOf(
            ExamQuestion(id = "e2_q1", examId = "exam2"),
            ExamQuestion(id = "e2_q2", examId = "exam2")
        )
        val exam3Questions = listOf(
            ExamQuestion(id = "e3_q1", examId = "exam3"),
            ExamQuestion(id = "e3_q2", examId = "exam3")
        )
        questionDao.upsertAll(exam1Questions + exam2Questions + exam3Questions)

        // Sync exam1 (keeping only e1_q1) and exam2 (keeping only e2_q2). exam3 is unrepresented.
        questionDao.deleteStaleQuestions(
            listOf(
                ExamQuestion(id = "e1_q1", examId = "exam1"),
                ExamQuestion(id = "e2_q2", examId = "exam2")
            )
        )

        assertEquals(setOf("e1_q1"), questionDao.getByExamId("exam1").map { it.id }.toSet())
        assertEquals(setOf("e2_q2"), questionDao.getByExamId("exam2").map { it.id }.toSet())
        assertEquals(setOf("e3_q1", "e3_q2"), questionDao.getByExamId("exam3").map { it.id }.toSet())
    }

    @Test
    fun deleteStaleQuestions_emptyListIsNoOp() = runBlocking {
        val questions = listOf(
            ExamQuestion(id = "q1", examId = "exam1"),
            ExamQuestion(id = "q2", examId = "exam1")
        )
        questionDao.upsertAll(questions)

        questionDao.deleteStaleQuestions(emptyList())

        val remainingIds = questionDao.getByExamId("exam1").map { it.id }.toSet()
        assertEquals(setOf("q1", "q2"), remainingIds)
    }

    @Test
    fun deleteStaleQuestions_skipsNullExamId() = runBlocking {
        val questions = listOf(
            ExamQuestion(id = "q1", examId = "exam1"),
            ExamQuestion(id = "q2", examId = "exam1")
        )
        questionDao.upsertAll(questions)

        questionDao.deleteStaleQuestions(listOf(ExamQuestion(id = "q1", examId = null)))

        val remainingIds = questionDao.getByExamId("exam1").map { it.id }.toSet()
        assertEquals(setOf("q1", "q2"), remainingIds)
    }
}
