package org.ole.planet.myplanet.repository

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.data.room.dao.CourseProgressDao
import org.ole.planet.myplanet.data.room.dao.NewsDao
import org.ole.planet.myplanet.data.room.dao.OfflineActivityDao
import org.ole.planet.myplanet.data.room.dao.SubmissionDao
import org.ole.planet.myplanet.data.room.dao.TeamTaskDao
import org.ole.planet.myplanet.data.room.dao.UserDao
import org.ole.planet.myplanet.model.CourseCompletion
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.model.gamification.BadgeCategory
import org.ole.planet.myplanet.services.UserSessionManager
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.TestDispatcherProvider
import org.ole.planet.myplanet.utils.TestTimeProvider

@OptIn(ExperimentalCoroutinesApi::class)
class GamificationRepositoryImplTest {

    private val offlineActivityDao: OfflineActivityDao = mockk(relaxed = true)
    private val progressRepository: ProgressRepository = mockk(relaxed = true)
    private val courseProgressDao: CourseProgressDao = mockk(relaxed = true)
    private val teamTaskDao: TeamTaskDao = mockk(relaxed = true)
    private val newsDao: NewsDao = mockk(relaxed = true)
    private val activitiesRepository: ActivitiesRepository = mockk(relaxed = true)
    private val submissionDao: SubmissionDao = mockk(relaxed = true)
    private val userDao: UserDao = mockk(relaxed = true)

    private val testDispatcher = StandardTestDispatcher()
    private val dispatcherProvider: DispatcherProvider = TestDispatcherProvider(testDispatcher)
    private val testTimeProvider = TestTimeProvider()

    private lateinit var repository: GamificationRepositoryImpl

    private val zone = ZoneId.systemDefault()
    private val testDate = LocalDate.of(2026, 8, 19)

    @Before
    fun setUp() {
        val epochMillis = testDate.atStartOfDay(zone).toInstant().toEpochMilli() + 3600000L // 1am
        testTimeProvider.currentTime = epochMillis

        repository = GamificationRepositoryImpl(
            offlineActivityDao = offlineActivityDao,
            progressRepository = progressRepository,
            courseProgressDao = courseProgressDao,
            teamTaskDao = teamTaskDao,
            newsDao = newsDao,
            activitiesRepository = activitiesRepository,
            submissionDao = submissionDao,
            userDao = userDao,
            timeProvider = testTimeProvider,
            dispatcherProvider = dispatcherProvider
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun calculateStudyStreaks_emptyLogins_returnsZeroStreak() = runTest(testDispatcher) {
        coEvery { offlineActivityDao.getLoginTimesForUser("user1", "Alex") } returns emptyList()

        val streakInfo = repository.calculateStudyStreaks("user1", "Alex")

        assertEquals(0, streakInfo.currentStreak)
        assertEquals(0, streakInfo.longestStreak)
        assertFalse(streakInfo.isActiveToday)
        assertEquals(0, streakInfo.totalActiveDays)
    }

    @Test
    fun calculateStudyStreaks_contiguousDaysWithToday_returnsCorrectStreak() = runTest(testDispatcher) {
        val todayMillis = testDate.atStartOfDay(zone).toInstant().toEpochMilli()
        val yesterdayMillis = testDate.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val twoDaysAgoMillis = testDate.minusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()

        coEvery { offlineActivityDao.getLoginTimesForUser("user1", "Alex") } returns listOf(
            todayMillis,
            yesterdayMillis,
            twoDaysAgoMillis
        )

        val streakInfo = repository.calculateStudyStreaks("user1", "Alex")

        assertEquals(3, streakInfo.currentStreak)
        assertEquals(3, streakInfo.longestStreak)
        assertTrue(streakInfo.isActiveToday)
        assertEquals(3, streakInfo.totalActiveDays)
        assertTrue(streakInfo.recentActiveDays.last()) // today
    }

    @Test
    fun calculateStudyStreaks_activeYesterdayNotToday_maintainsCurrentStreak() = runTest(testDispatcher) {
        val yesterdayMillis = testDate.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val twoDaysAgoMillis = testDate.minusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()

        coEvery { offlineActivityDao.getLoginTimesForUser("user1", "Alex") } returns listOf(
            yesterdayMillis,
            twoDaysAgoMillis
        )

        val streakInfo = repository.calculateStudyStreaks("user1", "Alex")

        assertEquals(2, streakInfo.currentStreak)
        assertEquals(2, streakInfo.longestStreak)
        assertFalse(streakInfo.isActiveToday)
        assertEquals(2, streakInfo.totalActiveDays)
    }

    @Test
    fun calculateStudyStreaks_brokenStreak_resetsCurrentStreak() = runTest(testDispatcher) {
        val todayMillis = testDate.atStartOfDay(zone).toInstant().toEpochMilli()
        val fiveDaysAgoMillis = testDate.minusDays(5).atStartOfDay(zone).toInstant().toEpochMilli()
        val sixDaysAgoMillis = testDate.minusDays(6).atStartOfDay(zone).toInstant().toEpochMilli()
        val sevenDaysAgoMillis = testDate.minusDays(7).atStartOfDay(zone).toInstant().toEpochMilli()

        coEvery { offlineActivityDao.getLoginTimesForUser("user1", "Alex") } returns listOf(
            todayMillis,
            fiveDaysAgoMillis,
            sixDaysAgoMillis,
            sevenDaysAgoMillis
        )

        val streakInfo = repository.calculateStudyStreaks("user1", "Alex")

        assertEquals(1, streakInfo.currentStreak)
        assertEquals(3, streakInfo.longestStreak) // from 5-7 days ago
        assertTrue(streakInfo.isActiveToday)
    }

    @Test
    fun getBadges_unlocksBasedOnProgressThresholds() = runTest(testDispatcher) {
        coEvery { offlineActivityDao.getLoginTimesForUser("user1", "Alex") } returns listOf(
            testDate.atStartOfDay(zone).toInstant().toEpochMilli(),
            testDate.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
            testDate.minusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()
        )
        coEvery { progressRepository.getCompletedCourses("user1") } returns listOf(
            CourseCompletion("c1", "Course 1"),
            CourseCompletion("c2", "Course 2"),
            CourseCompletion("c3", "Course 3")
        )
        coEvery { courseProgressDao.countPassedStepsByUser("user1") } returns 8
        coEvery { teamTaskDao.countCompletedTasksForUser("user1") } returns 5
        coEvery { newsDao.countCommentsByUser("user1") } returns 4
        coEvery { activitiesRepository.getResourceOpenCount("Alex", UserSessionManager.KEY_RESOURCE_OPEN) } returns 26L
        coEvery { submissionDao.countCompletedSubmissions("user1") } returns 5

        val badges = repository.getBadges("user1", "Alex")

        // First step badge
        val firstStep = badges.first { it.id == "course_step_1" }
        assertTrue(firstStep.isUnlocked)

        // Scholar (3 courses) badge
        val scholar = badges.first { it.id == "course_comp_3" }
        assertTrue(scholar.isUnlocked)

        // Master Learner (5 courses) badge - locked
        val masterLearner = badges.first { it.id == "course_comp_5" }
        assertFalse(masterLearner.isUnlocked)
        assertEquals(3, masterLearner.currentProgress)

        // Spark (3-day streak) badge
        val sparkStreak = badges.first { it.id == "streak_3" }
        assertTrue(sparkStreak.isUnlocked)

        // Top Contributor (5 tasks) badge
        val topContributor = badges.first { it.id == "team_task_5" }
        assertTrue(topContributor.isUnlocked)

        // Active Collaborator (3 comments) badge
        val activeCollaborator = badges.first { it.id == "team_comm_3" }
        assertTrue(activeCollaborator.isUnlocked)

        // Bookworm (25 resources) badge
        val bookworm = badges.first { it.id == "resource_25" }
        assertTrue(bookworm.isUnlocked)

        // Quiz Ace (5 exams) badge
        val quizAce = badges.first { it.id == "exam_sub_5" }
        assertTrue(quizAce.isUnlocked)
    }

    @Test
    fun getCertificates_mapsCompletedCoursesToCertificates() = runTest(testDispatcher) {
        val user = UserEntity().apply {
            id = "user1"
            firstName = "Jane"
            lastName = "Doe"
        }
        coEvery { userDao.getById("user1") } returns user
        coEvery { progressRepository.getCompletedCourses("user1") } returns listOf(
            CourseCompletion("course-12345", "Digital Literacy")
        )

        val certs = repository.getCertificates("user1", "Jane Doe")

        assertEquals(1, certs.size)
        val cert = certs.first()
        assertEquals("course-12345", cert.courseId)
        assertEquals("Digital Literacy", cert.courseTitle)
        assertEquals("Jane Doe", cert.learnerName)
        assertTrue(cert.certificateId.startsWith("OLE-CERT-"))
    }

    @Test
    fun getGamificationSummary_aggregatesAllData() = runTest(testDispatcher) {
        coEvery { offlineActivityDao.getLoginTimesForUser("user1", "Alex") } returns emptyList()
        coEvery { progressRepository.getCompletedCourses("user1") } returns emptyList()
        coEvery { courseProgressDao.countPassedStepsByUser("user1") } returns 0
        coEvery { teamTaskDao.countCompletedTasksForUser("user1") } returns 0
        coEvery { newsDao.countCommentsByUser("user1") } returns 0
        coEvery { activitiesRepository.getResourceOpenCount("Alex", UserSessionManager.KEY_RESOURCE_OPEN) } returns 0L
        coEvery { submissionDao.countCompletedSubmissions("user1") } returns 0
        coEvery { userDao.getById("user1") } returns null

        val summary = repository.getGamificationSummary("user1", "Alex")

        assertEquals(0, summary.streakInfo.currentStreak)
        assertEquals(0, summary.completedCoursesCount)
        assertEquals(0L, summary.resourcesReadCount)
        assertTrue(summary.badges.isNotEmpty())
    }
}
