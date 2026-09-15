package org.ole.planet.myplanet.ui.submissions

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyList
import org.mockito.ArgumentMatchers.anyMap
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.ole.planet.myplanet.model.StepExam
import org.ole.planet.myplanet.model.Submission
import org.ole.planet.myplanet.model.SubmissionRowProjection
import org.ole.planet.myplanet.repository.SubmissionsRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.utils.DispatcherProvider

@OptIn(ExperimentalCoroutinesApi::class)
class SubmissionViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val testDispatcherProvider = object : DispatcherProvider {
        override val io = testDispatcher
        override val main = testDispatcher
        override val mainImmediate = testDispatcher
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

        runBlocking {
            `when`(submissionsRepository.getSubmissionProjections(anyList(), anyString(), anyString(), anyString(), anyMap())).thenAnswer { invocation ->
                val subs = invocation.getArgument<List<Submission>>(0)
                val uid = invocation.getArgument<String>(1)
                val type = invocation.getArgument<String>(2)
                val query = invocation.getArgument<String>(3)
                val examMap = invocation.getArgument<Map<String?, StepExam>>(4)

                var filtered = when (type) {
                    "survey" -> subs.filter { it.userId == uid && it.type == "survey" }
                    "survey_submission" -> subs.filter {
                        it.userId == uid && it.type == "survey" && it.status != "pending"
                    }
                    else -> subs.filter { it.userId == uid && it.type != "survey" }
                }.sortedByDescending { it.lastUpdateTime }

                if (query.isNotEmpty()) {
                    val examIds = examMap.mapNotNullTo(HashSet()) { (id, exam) ->
                        if (exam.name?.contains(query, ignoreCase = true) == true) id else null
                    }
                    filtered = filtered.filter { examIds.contains(it.parentId) }
                }

                val uniqueRawSubmissions = mutableListOf<Submission>()
                val submissionCountMap = HashMap<String?, Int>()
                for (group in filtered.groupBy { it.parentId }.values) {
                    val newest = group.maxByOrNull { it.lastUpdateTime } ?: continue
                    uniqueRawSubmissions.add(newest)
                    submissionCountMap[newest.id] = group.size
                }

                val userIds = uniqueRawSubmissions.mapNotNull { it.userId }.distinct()
                val fallbackUsersMap = runBlocking { userRepository.getUsersByIds(userIds) }.associateBy { it.id }

                uniqueRawSubmissions.map { sub ->
                    val name = submissionsRepository.getNormalizedSubmitterName(sub)
                    val fallback = sub.userId?.let { fallbackUsersMap[it]?.name }
                    val submitterName = name ?: fallback ?: ""
                    val count = submissionCountMap[sub.id] ?: 1
                    SubmissionRowProjection(sub, submitterName, count)
                }.sortedByDescending { it.submission.lastUpdateTime }
            }
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createSubmission(id: String, parentId: String, type: String, status: String, lastUpdateTime: Long, userId: String = "user1"): Submission {
        return Submission().apply {
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
        `when`(userRepository.getUsersByIds(listOf("user1"))).thenReturn(emptyList())
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
    }

    @Test
    fun testQueryFilteringAndGrouping() = runTest(testDispatcher) {
        val s1 = createSubmission("1", "p1", "exam", "complete", 100L)
        val s2 = createSubmission("2", "p1", "exam", "complete", 300L) // Latest for p1
        val s3 = createSubmission("3", "p2", "exam", "complete", 200L)
        val subList = listOf(s1, s2, s3)

        val examMap = mapOf<String?, StepExam>(
            "p1" to StepExam().apply { name = "Math Exam" },
            "p2" to StepExam().apply { name = "Science Exam" }
        )

        `when`(userRepository.getActiveUserIdSuspending()).thenReturn("user1")
        `when`(userRepository.getUsersByIds(listOf("user1"))).thenReturn(emptyList())
        `when`(submissionsRepository.getSubmissionsFlow("user1")).thenReturn(flowOf(subList))
        `when`(submissionsRepository.getExamMap(subList)).thenReturn(examMap)
        `when`(submissionsRepository.getNormalizedSubmitterName(s1)).thenReturn("John Doe")
        `when`(submissionsRepository.getNormalizedSubmitterName(s2)).thenReturn("John Doe")
        `when`(submissionsRepository.getNormalizedSubmitterName(s3)).thenReturn("John Doe")

        viewModel = SubmissionViewModel(submissionsRepository, userRepository, testDispatcherProvider)
        val job = launch {
            viewModel.submissions.collect { }
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
        assertEquals("Math Exam", subs[0].examTitle)
        job.cancel()
    }

    @Test
    fun testSubmissionCountsMapTracksLatestSubmission() = runTest(testDispatcher) {
        val s1 = createSubmission("1", "p1", "exam", "complete", 100L)
        val s2 = createSubmission("2", "p1", "exam", "complete", 300L) // Latest for p1
        val s3 = createSubmission("3", "p2", "exam", "complete", 200L) // Only one for p2
        val subList = listOf(s1, s2, s3)

        `when`(userRepository.getActiveUserIdSuspending()).thenReturn("user1")
        `when`(userRepository.getUsersByIds(listOf("user1"))).thenReturn(emptyList())
        `when`(submissionsRepository.getSubmissionsFlow("user1")).thenReturn(flowOf(subList))
        `when`(submissionsRepository.getExamMap(subList)).thenReturn(emptyMap())
        `when`(submissionsRepository.getNormalizedSubmitterName(s1)).thenReturn("John Doe")
        `when`(submissionsRepository.getNormalizedSubmitterName(s2)).thenReturn("John Doe")
        `when`(submissionsRepository.getNormalizedSubmitterName(s3)).thenReturn("John Doe")

        viewModel = SubmissionViewModel(submissionsRepository, userRepository, testDispatcherProvider)
        val job = launch {
            viewModel.submissions.collect { }
        }
        advanceUntilIdle()

        viewModel.setFilter("exam", "")
        advanceUntilIdle()

        val subs = viewModel.submissions.value
        assertEquals(2, subs.size)
        assertEquals(2, subs.find { it.id == "2" }?.submissionCount)
        assertEquals(1, subs.find { it.id == "3" }?.submissionCount)

        job.cancel()
    }

    @Test
    fun testTieBreakingForEqualLastUpdateTime() = runTest(testDispatcher) {
        // Two submissions share the newest lastUpdateTime within the same parent;
        // the tie-breaker keeps the first one encountered in the (descending-sorted) list.
        val s1 = createSubmission("1", "p1", "exam", "complete", 300L)
        val s2 = createSubmission("2", "p1", "exam", "complete", 300L)
        val s3 = createSubmission("3", "p2", "exam", "complete", 200L)
        val subList = listOf(s1, s2, s3)

        `when`(userRepository.getActiveUserIdSuspending()).thenReturn("user1")
        `when`(userRepository.getUsersByIds(listOf("user1"))).thenReturn(emptyList())
        `when`(submissionsRepository.getSubmissionsFlow("user1")).thenReturn(flowOf(subList))
        `when`(submissionsRepository.getExamMap(subList)).thenReturn(emptyMap())
        `when`(submissionsRepository.getNormalizedSubmitterName(s1)).thenReturn("John Doe")
        `when`(submissionsRepository.getNormalizedSubmitterName(s2)).thenReturn("John Doe")
        `when`(submissionsRepository.getNormalizedSubmitterName(s3)).thenReturn("John Doe")

        viewModel = SubmissionViewModel(submissionsRepository, userRepository, testDispatcherProvider)
        val job = launch {
            viewModel.submissions.collect { }
        }
        advanceUntilIdle()

        viewModel.setFilter("exam", "")
        advanceUntilIdle()

        val subs = viewModel.submissions.value
        // p1 keeps the first-encountered newest submission ("1"), p2 keeps "3"
        assertEquals(2, subs.size)
        assertEquals("1", subs.find { it.id == "1" }?.id)
        assertEquals(2, subs.find { it.id == "1" }?.submissionCount)
        assertEquals("3", subs.find { it.id == "3" }?.id)
        assertEquals(1, subs.find { it.id == "3" }?.submissionCount)

        job.cancel()
    }

    @Test
    fun testDistinctEmissions() = runTest(testDispatcher) {
        val s1 = createSubmission("1", "p1", "exam", "complete", 100L)
        val s1_dup = createSubmission("1", "p1", "exam", "complete", 100L)
        val subList = listOf(s1)
        val subListDup = listOf(s1_dup)

        val flowEmitter = kotlinx.coroutines.flow.MutableStateFlow(subList)

        `when`(userRepository.getActiveUserIdSuspending()).thenReturn("user1")
        `when`(userRepository.getUsersByIds(listOf("user1"))).thenReturn(emptyList())
        `when`(submissionsRepository.getSubmissionsFlow("user1")).thenReturn(flowEmitter)
        `when`(submissionsRepository.getExamMap(subList)).thenReturn(emptyMap())
        `when`(submissionsRepository.getExamMap(subListDup)).thenReturn(emptyMap())
        `when`(submissionsRepository.getNormalizedSubmitterName(s1)).thenReturn("John Doe")
        `when`(submissionsRepository.getNormalizedSubmitterName(s1_dup)).thenReturn("John Doe")

        viewModel = SubmissionViewModel(submissionsRepository, userRepository, testDispatcherProvider)

        var emissions = 0
        val job = launch {
            viewModel.submissions.collect {
                emissions++
            }
        }
        advanceUntilIdle()

        assertEquals(2, emissions) // Initial empty state + real emission

        flowEmitter.value = subListDup
        advanceUntilIdle()

        // Equivalent list emission from repository is suppressed downstream
        assertEquals(2, emissions)

        job.cancel()
    }
}
