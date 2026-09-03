package org.ole.planet.myplanet.data.room.dao

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.data.room.AppDatabase
import org.ole.planet.myplanet.model.StepExam
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [32], application = Application::class)
class ExamDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var examDao: ExamDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        examDao = database.examDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun getByTypeAndName_returnsMatch() = runBlocking {
        val exam1 = StepExam().apply {
            id = "1"
            type = "surveys"
            name = "Survey 1"
        }
        val exam2 = StepExam().apply {
            id = "2"
            type = "surveys"
            name = "Survey 2"
        }

        examDao.upsertAll(listOf(exam1, exam2))

        val result = examDao.getByTypeAndName("surveys", "Survey 2")
        assertEquals("2", result?.id)
    }

    @Test
    fun getByTypeAndName_returnsNullForNoMatch() = runBlocking {
        val exam1 = StepExam().apply {
            id = "1"
            type = "surveys"
            name = "Survey 1"
        }

        examDao.upsertAll(listOf(exam1))

        val result = examDao.getByTypeAndName("surveys", "Survey 2")
        assertNull(result)
    }

    @Test
    fun getByTypeAndName_filtersByType() = runBlocking {
        val exam1 = StepExam().apply {
            id = "1"
            type = "otherType"
            name = "Survey 1"
        }

        examDao.upsertAll(listOf(exam1))

        val result = examDao.getByTypeAndName("surveys", "Survey 1")
        assertNull(result)
    }

    @Test
    fun getTeamOwnedSurveys_returnsMatchingTeamOrSubmissionSurveys() = runBlocking {
        val exam1 = StepExam().apply {
            id = "1"
            type = "surveys"
            teamId = "teamA"
        }
        val exam2 = StepExam().apply {
            id = "2"
            type = "surveys"
            teamId = "teamB"
        }
        val exam3 = StepExam().apply {
            id = "3"
            type = "surveys"
            teamId = "teamB"
        }
        val exam4 = StepExam().apply {
            id = "4"
            type = "other"
            teamId = "teamA"
        }

        examDao.upsertAll(listOf(exam1, exam2, exam3, exam4))

        val result = examDao.getTeamOwnedSurveys(teamId = "teamA", submissionIds = listOf("2"))
        assertEquals(listOf("1", "2"), result.map { it.id })
    }

    @Test
    fun getAdoptableTeamSurveys_withExcludedIds_filtersCorrectly() = runBlocking {
        val exam1 = StepExam().apply {
            id = "1"
            type = "surveys"
            isTeamShareAllowed = true
        }
        val exam2 = StepExam().apply {
            id = "2"
            type = "surveys"
            isTeamShareAllowed = true
        }
        val exam3 = StepExam().apply {
            id = "3"
            type = "surveys"
            isTeamShareAllowed = false
        }

        examDao.upsertAll(listOf(exam1, exam2, exam3))

        val resultWithExclusion = examDao.getAdoptableTeamSurveys(excludedIds = setOf("1"))
        assertEquals(listOf("2"), resultWithExclusion.map { it.id })

        val resultWithoutExclusion = examDao.getAdoptableTeamSurveys()
        assertEquals(listOf("1", "2"), resultWithoutExclusion.map { it.id })
    }
}
