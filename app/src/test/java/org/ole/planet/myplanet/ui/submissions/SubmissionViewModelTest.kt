package org.ole.planet.myplanet.ui.submissions

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.repository.SubmissionsRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.utils.DispatcherProvider

import org.mockito.Mockito.`when`

@OptIn(ExperimentalCoroutinesApi::class)
class SubmissionViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val testDispatcherProvider = object : DispatcherProvider {
        override val io = testDispatcher
        override val main = testDispatcher
        override val default = testDispatcher
        override val unconfined = testDispatcher
    }

    private lateinit var submissionsRepository: SubmissionsRepository
    private lateinit var userRepository: UserRepository
    private lateinit var viewModel: SubmissionViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        submissionsRepository = mock(SubmissionsRepository::class.java)
        userRepository = mock(UserRepository::class.java)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createSubmission(id: String, parentId: String, type: String, status: String, lastUpdateTime: Long, userId: String = "user1"): RealmSubmission {
        return RealmSubmission().apply {
            this.id = id
            this.parentId = parentId
            this.type = type
            this.status = status
            this.lastUpdateTime = lastUpdateTime
            this.userId = userId
        }
    }

    @Test
    fun testFilterModes() = runTest(testDispatcher) {
        val s1 = createSubmission("1", "p1", "survey", "pending", 100L)
        val s2 = createSubmission("2", "p2", "survey", "complete", 200L)
        val s3 = createSubmission("3", "p3", "exam", "complete", 300L)
        val subList = listOf(s1, s2, s3)

        `when`(userRepository.getActiveUserIdSuspending()).thenReturn("user1")
        `when`(submissionsRepository.getSubmissionsFlow("user1")).thenReturn(flowOf(subList))
        `when`(submissionsRepository.getExamMap(subList)).thenReturn(emptyMap())
        `when`(submissionsRepository.getNormalizedSubmitterName(s1)).thenReturn("John Doe")
        `when`(submissionsRepository.getNormalizedSubmitterName(s2)).thenReturn("John Doe")
        `when`(submissionsRepository.getNormalizedSubmitterName(s3)).thenReturn("John Doe")

        viewModel = SubmissionViewModel(submissionsRepository, userRepository, testDispatcherProvider)

        // Setup observers for StateFlow to be active
        val job = launch {
            viewModel.submissions.collect { }
        }
        val job2 = launch {
            viewModel.submissionCounts.collect { }
        }

        advanceUntilIdle()

        // Test "survey" type
        viewModel.setFilter("survey", "")
        advanceUntilIdle()
        var subs = viewModel.submissions.value
        assertEquals("survey mode: ${subs.map{it.id}}", 2, subs.size) // s1 and s2

        // Test "survey_submission" type
        viewModel.setFilter("survey_submission", "")
        advanceUntilIdle()
        subs = viewModel.submissions.value
        assertEquals("survey_submission mode: ${subs.map{it.id}}", 1, subs.size) // s2
        assertEquals("2", subs[0].id)

        // Test default non-survey type
        viewModel.setFilter("exam", "")
        advanceUntilIdle()
        subs = viewModel.submissions.value
        assertEquals("exam mode: ${subs.map{it.id}}", 1, subs.size) // s3
        assertEquals("3", subs[0].id)

        job.cancel()
        job2.cancel()
    }

    @Test
    fun testQueryFilteringAndGrouping() = runTest(testDispatcher) {
        val s1 = createSubmission("1", "p1", "exam", "complete", 100L)
        val s2 = createSubmission("2", "p1", "exam", "complete", 300L) // Latest for p1
        val s3 = createSubmission("3", "p2", "exam", "complete", 200L)
        val subList = listOf(s1, s2, s3)

        val examMap = mapOf<String?, RealmStepExam>(
            "p1" to RealmStepExam().apply { name = "Math Exam" },
            "p2" to RealmStepExam().apply { name = "Science Exam" }
        )

        `when`(userRepository.getActiveUserIdSuspending()).thenReturn("user1")
        `when`(submissionsRepository.getSubmissionsFlow("user1")).thenReturn(flowOf(subList))
        `when`(submissionsRepository.getExamMap(subList)).thenReturn(examMap)
        `when`(submissionsRepository.getNormalizedSubmitterName(s1)).thenReturn("John Doe")
        `when`(submissionsRepository.getNormalizedSubmitterName(s2)).thenReturn("John Doe")
        `when`(submissionsRepository.getNormalizedSubmitterName(s3)).thenReturn("John Doe")

        viewModel = SubmissionViewModel(submissionsRepository, userRepository, testDispatcherProvider)
        val job = launch {
            viewModel.submissions.collect { }
        }
        val job2 = launch {
            viewModel.submissionCounts.collect { }
        }
        advanceUntilIdle()

        // Grouping by parentId, returning latest
        viewModel.setFilter("exam", "")
        advanceUntilIdle()
        var subs = viewModel.submissions.value
        assertEquals("size mismatch: ${subs.map{it.id}}", 2, subs.size)
        // Order is by descending lastUpdateTime: s2 (300L) then s3 (200L)
        assertEquals("2", subs[0].id)
        assertEquals("3", subs[1].id)

        // Filtering by exam title
        viewModel.setFilter("exam", "Math")
        advanceUntilIdle()
        subs = viewModel.submissions.value
        assertEquals(1, subs.size)
        assertEquals("2", subs[0].id) // s2 is the latest for p1 ("Math Exam")
        job.cancel()
        job2.cancel()
    }

    @Test
    fun testSubmissionCountsMapTracksLatestSubmission() = runTest(testDispatcher) {
        val s1 = createSubmission("1", "p1", "exam", "complete", 100L)
        val s2 = createSubmission("2", "p1", "exam", "complete", 300L) // Latest for p1
        val s3 = createSubmission("3", "p2", "exam", "complete", 200L) // Only one for p2
        val subList = listOf(s1, s2, s3)

        `when`(userRepository.getActiveUserIdSuspending()).thenReturn("user1")
        `when`(submissionsRepository.getSubmissionsFlow("user1")).thenReturn(flowOf(subList))
        `when`(submissionsRepository.getExamMap(subList)).thenReturn(emptyMap())
        `when`(submissionsRepository.getNormalizedSubmitterName(s1)).thenReturn("John Doe")
        `when`(submissionsRepository.getNormalizedSubmitterName(s2)).thenReturn("John Doe")
        `when`(submissionsRepository.getNormalizedSubmitterName(s3)).thenReturn("John Doe")

        viewModel = SubmissionViewModel(submissionsRepository, userRepository, testDispatcherProvider)
        val job = launch {
            viewModel.submissions.collect { }
        }
        val job2 = launch {
            viewModel.submissionCounts.collect { }
        }
        advanceUntilIdle()

        viewModel.setFilter("exam", "")
        advanceUntilIdle()

        val counts = viewModel.submissionCounts.value
        assertEquals(2, counts.size)
        // Key is the latest submission ID, Value is the count of submissions for that parentId
        assertEquals(2, counts["2"])
        assertEquals(1, counts["3"])

        job.cancel()
        job2.cancel()
    }
}
