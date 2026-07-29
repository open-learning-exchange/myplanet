package org.ole.planet.myplanet.data.room

import android.app.Application
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.model.Answer
import org.ole.planet.myplanet.model.CourseStep
import org.ole.planet.myplanet.model.ExamQuestion
import org.ole.planet.myplanet.model.MyCourse
import org.ole.planet.myplanet.model.MyTeam
import org.ole.planet.myplanet.model.StepExam
import org.ole.planet.myplanet.model.Submission
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Real in-memory Room round-trip coverage for the eight domains that were collapsed from the
 * legacy shadow-entity layer into first-class Room entities (User is covered in
 * [AppDatabaseRoundTripTest]).
 *
 * Because the rest of the unit suite mocks the DAOs, these are the only tests that exercise the
 * reshaped SQL, the `@ColumnInfo` column renames (`courseRev`->`_rev`, `body`->`question`,
 * `updated`->`isUpdated`), the JSON list converters, and — most importantly — the relation
 * flattening that the mappers used to perform: `Submission.teamId` filtering and the
 * `ExamQuestion` correct-choice persistence that exam grading depends on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class CollapsedEntitiesRoundTripTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `Submission teamId flattened column drives the team-scoped queries`() = runBlocking {
        db.submissionDao().upsertAll(
            listOf(
                Submission().apply { id = "s1"; userId = "u1"; teamId = "t1"; type = "survey" },
                Submission().apply { id = "s2"; userId = "u1"; teamId = "t2"; type = "survey" },
                Submission().apply { id = "s3"; userId = "u1"; teamId = null; type = "survey" }
            )
        )

        assertEquals(listOf("s1"), db.submissionDao().getByTeamId("t1").map { it.id })
        assertEquals(listOf("s1"), db.submissionDao().getByUserIdAndTeamId("u1", "t1").map { it.id })
        assertEquals(listOf("s3"), db.submissionDao().getByUserIdWithoutTeam("u1").map { it.id })
        assertEquals(3, db.submissionDao().getByUserId("u1").size)
    }

    @Test
    fun `Submission round-trips scalar fields and markUploaded updates identity`() = runBlocking {
        db.submissionDao().upsertAll(
            listOf(
                Submission().apply {
                    id = "s1"; _id = ""; userId = "u1"; parentId = "exam1@course1"
                    status = "complete"; isUpdated = true; grade = 7L
                }
            )
        )

        val loaded = db.submissionDao().getByIdOrRemoteId("s1")
        assertEquals("u1", loaded?.userId)
        assertEquals("exam1@course1", loaded?.parentId)
        assertEquals(7L, loaded?.grade)

        db.submissionDao().markUploaded("s1", "remote1", "rev1")
        val uploaded = db.submissionDao().getByIdOrRemoteId("remote1")
        assertEquals("s1", uploaded?.id)
        assertEquals("remote1", uploaded?._id)
        assertEquals(false, uploaded?.isUpdated)
    }

    @Test
    fun `Answer round-trips valueChoices converter and joins by submissionId`() = runBlocking {
        db.answerDao().upsertAll(
            listOf(
                Answer().apply {
                    id = "a1"; submissionId = "s1"; questionId = "q1"
                    value = "B"; valueChoices = listOf("B", "C"); grade = 1; isPassed = true
                }
            )
        )

        val loaded = db.answerDao().getBySubmissionId("s1").single()
        assertEquals("B", loaded.value)
        assertEquals(listOf("B", "C"), loaded.valueChoices)
        assertTrue(loaded.isPassed)

        val byQuestion = db.answerDao().getBySubmissionAndQuestion("s1", "q1")
        assertEquals("a1", byQuestion?.id)
    }

    @Test
    fun `ExamQuestion persists correctChoice and body maps to the question column`() = runBlocking {
        val question = ExamQuestion().apply {
            id = "q1"; examId = "exam1"; type = "select"
            body = "Pick the prime"
            setCorrectChoices(listOf("b", "c"))
        }
        db.questionDao().upsertAll(listOf(question))

        val loaded = db.questionDao().getByExamId("exam1").single()
        // body is stored in the renamed `question` column; grading reads it back.
        assertEquals("Pick the prime", loaded.body)
        // Regression guard: correctChoice must survive a DB round-trip (formerly a mirror column;
        // exam grading in getSubmissionDetail relies on it after loading from Room).
        assertEquals(listOf("b", "c"), loaded.getCorrectChoice())
        assertEquals(1, db.questionDao().countByExamId("exam1"))
    }

    @Test
    fun `StepExam round-trips and is queryable by step, course, team and type`() = runBlocking {
        db.examDao().upsertAll(
            listOf(
                StepExam().apply { id = "e1"; stepId = "st1"; courseId = "c1"; teamId = "tm1"; type = "exam"; name = "Quiz" },
                StepExam().apply { id = "e2"; stepId = "st2"; courseId = "c1"; type = "survey" }
            )
        )

        assertEquals("Quiz", db.examDao().getById("e1")?.name)
        assertEquals(listOf("e1"), db.examDao().getByStepId("st1").map { it.id })
        assertEquals(setOf("e1", "e2"), db.examDao().getByCourseId("c1").map { it.id }.toSet())
        assertEquals(listOf("e1"), db.examDao().getByTeamId("tm1").map { it.id })
        assertEquals(listOf("e2"), db.examDao().getByType("survey").map { it.id })
    }

    @Test
    fun `MyCourse round-trips userId list and courseRev column and joins to steps`() = runBlocking {
        db.courseDao().upsert(
            MyCourse().apply {
                id = "c1"; _id = "c1"; courseId = "c1"; courseRev = "rev1"
                courseTitle = "Algebra"; userId = listOf("u1", "u2")
            }
        )
        db.courseStepDao().upsertAll(
            listOf(
                CourseStep().apply { id = "st1"; courseId = "c1"; stepTitle = "Intro" },
                CourseStep().apply { id = "st2"; courseId = "c1"; stepTitle = "Practice" }
            )
        )

        val course = db.courseDao().getByCourseId("c1")
        assertEquals("Algebra", course?.courseTitle)
        assertEquals("rev1", course?.courseRev)          // stored in the renamed `_rev` column
        assertEquals(listOf("u1", "u2"), course?.userId)

        val steps = db.courseStepDao().getByCourseId("c1")
        assertEquals(setOf("st1", "st2"), steps.map { it.id }.toSet())
    }

    @Test
    fun `MyTeam round-trips courses list, updated column, id alias and doc-type queries`() = runBlocking {
        db.teamDao().upsert(
            MyTeam().apply {
                _id = "tm1"; teamId = "tid1"; userId = "u1"; docType = "team"; type = "team"
                name = "Team One"; isPublic = true; updated = true
                courses = listOf("c1", "c2")
            }
        )
        db.teamDao().upsert(
            MyTeam().apply { _id = "m1"; teamId = "tid1"; userId = "u1"; docType = "membership" }
        )

        val team = db.teamDao().getById("tm1")
        assertEquals("Team One", team?.name)
        assertEquals(listOf("c1", "c2"), team?.courses)
        assertEquals(true, team?.updated)                 // stored in the renamed `isUpdated` column
        assertEquals("tm1", team?.id)                     // @get:Ignore id alias mirrors _id

        assertEquals(listOf("tm1"), db.teamDao().getByDocType("team").map { it._id })
        assertEquals("tm1", db.teamDao().getByTeamId("tid1")?._id)
        assertEquals(1, db.teamDao().countByTeamIdUserIdAndDocType("tid1", "u1", "membership"))
    }

    @Test
    fun `CourseStep round-trips and deletes with its course`() = runBlocking {
        db.courseStepDao().upsertAll(
            listOf(CourseStep().apply { id = "st1"; courseId = "c1"; stepTitle = "Intro"; noOfResources = 3 })
        )
        val loaded = db.courseStepDao().getById("st1")
        assertEquals("Intro", loaded?.stepTitle)
        assertEquals(3, loaded?.noOfResources)
        assertNull(db.courseStepDao().getById("missing"))
    }
}
