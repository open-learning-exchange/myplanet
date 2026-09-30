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
}
