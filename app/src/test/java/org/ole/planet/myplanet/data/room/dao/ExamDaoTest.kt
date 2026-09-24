package org.ole.planet.myplanet.data.room.dao

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
@Config(sdk = [32])
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

    @Test
    fun getIndividualSurveys_filtersCorrectly() = runBlocking {
        val exam1 = StepExam().apply {
            id = "1"
            type = "surveys"
            isTeamShareAllowed = false
            teamId = null
        }
        val exam2 = StepExam().apply {
            id = "2"
            type = "surveys"
            isTeamShareAllowed = true
            teamId = null
        }
        val exam3 = StepExam().apply {
            id = "3"
            type = "surveys"
            isTeamShareAllowed = false
            teamId = "team1"
        }
        val exam4 = StepExam().apply {
            id = "4"
            type = "surveys"
            isTeamShareAllowed = false
            teamId = ""
        }
        val exam5 = StepExam().apply {
            id = "5"
            type = "other"
            isTeamShareAllowed = false
            teamId = null
        }

        examDao.upsertAll(listOf(exam1, exam2, exam3, exam4, exam5))

        val result = examDao.getIndividualSurveys()
        assertEquals(listOf("1", "4"), result.map { it.id })
    }

    @Test
    fun getByIds_with1200IdsAndDuplicates_returnsAllMatchesWithoutDuplicates() = runBlocking {
        val exams = (1..1200).map { i ->
            StepExam().apply {
                id = "exam_$i"
            }
        }
        examDao.upsertAll(exams)

        val queryIds = (1..1200).map { "exam_$i" } + listOf("exam_1", "exam_500", "exam_1200")
        val results = examDao.getByIds(queryIds)

        assertEquals(1200, results.size)
        assertEquals((1..1200).map { "exam_$i" }.toSet(), results.map { it.id }.toSet())
    }

    @Test
    fun getByStepIds_with1200Ids_returnsAllMatches() = runBlocking {
        val exams = (1..1200).map { i ->
            StepExam().apply {
                id = "exam_$i"
                stepId = "step_$i"
            }
        }
        examDao.upsertAll(exams)

        val queryStepIds = (1..1200).map { "step_$i" } + listOf("step_10")
        val results = examDao.getByStepIds(queryStepIds)

        assertEquals(1200, results.size)
    }

    @Test
    fun getByCourseIds_with1200Ids_returnsAllMatches() = runBlocking {
        val exams = (1..1200).map { i ->
            StepExam().apply {
                id = "exam_$i"
                courseId = "course_$i"
            }
        }
        examDao.upsertAll(exams)

        val queryCourseIds = (1..1200).map { "course_$i" } + listOf("course_5")
        val results = examDao.getByCourseIds(queryCourseIds)

        assertEquals(1200, results.size)
    }

    @Test
    fun getTeamOwnedSurveys_with1200SubmissionIds_returnsTeamOwnedAndSubmissionSurveysWithoutDuplicates() = runBlocking {
        val teamExam = StepExam().apply {
            id = "team_exam"
            type = "surveys"
            teamId = "team1"
        }
        val submissionExams = (1..1200).map { i ->
            StepExam().apply {
                id = "sub_exam_$i"
                type = "surveys"
                teamId = "other_team"
            }
        }
        examDao.upsertAll(listOf(teamExam) + submissionExams)

        val querySubmissionIds = (1..1200).map { "sub_exam_$i" } + listOf("sub_exam_1", "sub_exam_100")
        val results = examDao.getTeamOwnedSurveys("team1", querySubmissionIds)

        assertEquals(1201, results.size)
        assertEquals((setOf("team_exam") + (1..1200).map { "sub_exam_$i" }), results.map { it.id }.toSet())
    }

    @Test
    fun getTeamOwnedSurveys_withEmptySubmissionIds_returnsTeamSurveys() = runBlocking {
        val teamExam = StepExam().apply {
            id = "team_exam"
            type = "surveys"
            teamId = "team1"
        }
        val otherExam = StepExam().apply {
            id = "other_exam"
            type = "surveys"
            teamId = "team2"
        }
        examDao.upsertAll(listOf(teamExam, otherExam))

        val results = examDao.getTeamOwnedSurveys("team1", emptyList())

        assertEquals(1, results.size)
        assertEquals("team_exam", results[0].id)
    }
}
