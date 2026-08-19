package org.ole.planet.myplanet.repository

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.data.room.dao.CourseProgressDao
import org.ole.planet.myplanet.data.room.dao.NewsDao
import org.ole.planet.myplanet.data.room.dao.OfflineActivityDao
import org.ole.planet.myplanet.data.room.dao.SubmissionDao
import org.ole.planet.myplanet.data.room.dao.TeamTaskDao
import org.ole.planet.myplanet.data.room.dao.UserDao
import org.ole.planet.myplanet.model.gamification.BadgeCategory
import org.ole.planet.myplanet.model.gamification.CourseCertificate
import org.ole.planet.myplanet.model.gamification.GamificationBadge
import org.ole.planet.myplanet.model.gamification.GamificationSummary
import org.ole.planet.myplanet.model.gamification.StudyStreakInfo
import org.ole.planet.myplanet.services.UserSessionManager
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.TimeProvider

@Singleton
class GamificationRepositoryImpl @Inject constructor(
    private val offlineActivityDao: OfflineActivityDao,
    private val progressRepository: ProgressRepository,
    private val courseProgressDao: CourseProgressDao,
    private val teamTaskDao: TeamTaskDao,
    private val newsDao: NewsDao,
    private val activitiesRepository: ActivitiesRepository,
    private val submissionDao: SubmissionDao,
    private val userDao: UserDao,
    private val timeProvider: TimeProvider,
    private val dispatcherProvider: DispatcherProvider
) : GamificationRepository {

    override fun getGamificationSummaryFlow(userId: String, userName: String): Flow<GamificationSummary> = flow {
        emit(getGamificationSummary(userId, userName))
    }.flowOn(dispatcherProvider.io)

    override suspend fun getGamificationSummary(userId: String, userName: String): GamificationSummary = withContext(dispatcherProvider.io) {
        val streakInfo = calculateStudyStreaks(userId, userName)
        val badges = getBadges(userId, userName, streakInfo)
        val certificates = getCertificates(userId, userName)

        val totalBadges = badges.size
        val unlockedBadges = badges.count { it.isUnlocked }
        val completedCourses = certificates.size
        val resourcesRead = activitiesRepository.getResourceOpenCount(userName, UserSessionManager.KEY_RESOURCE_OPEN)
        val tasksCompleted = teamTaskDao.countCompletedTasksForUser(userId)

        GamificationSummary(
            streakInfo = streakInfo,
            badges = badges,
            certificates = certificates,
            totalBadgesCount = totalBadges,
            unlockedBadgesCount = unlockedBadges,
            completedCoursesCount = completedCourses,
            resourcesReadCount = resourcesRead,
            tasksCompletedCount = tasksCompleted
        )
    }

    override suspend fun calculateStudyStreaks(userId: String, userName: String): StudyStreakInfo = withContext(dispatcherProvider.io) {
        val loginTimes = offlineActivityDao.getLoginTimesForUser(userId, userName)
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(timeProvider.now()).atZone(zone).toLocalDate()

        if (loginTimes.isEmpty()) {
            return@withContext StudyStreakInfo(
                currentStreak = 0,
                longestStreak = 0,
                isActiveToday = false,
                recentActiveDays = List(7) { false },
                totalActiveDays = 0
            )
        }

        val activeDatesSet = loginTimes.mapNotNull { epochMillis ->
            runCatching {
                Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
            }.getOrNull()
        }.toSet()

        val isActiveToday = activeDatesSet.contains(today)

        // Calculate Current Streak
        val currentAnchor = when {
            activeDatesSet.contains(today) -> today
            activeDatesSet.contains(today.minusDays(1)) -> today.minusDays(1)
            else -> null
        }

        var currentStreak = 0
        if (currentAnchor != null) {
            var checkDate: LocalDate = currentAnchor
            while (activeDatesSet.contains(checkDate)) {
                currentStreak++
                checkDate = checkDate.minusDays(1)
            }
        }

        // Calculate Longest Streak
        val sortedDates = activeDatesSet.sorted()
        var longestStreak = 0
        var runningStreak = 0
        var previousDate: LocalDate? = null

        for (date in sortedDates) {
            val prev = previousDate
            if (prev == null || date == prev.plusDays(1)) {
                runningStreak++
            } else {
                runningStreak = 1
            }
            if (runningStreak > longestStreak) {
                longestStreak = runningStreak
            }
            previousDate = date
        }

        // Last 7 days status (from 6 days ago to today)
        val recentActiveDays = (6 downTo 0).map { offset ->
            activeDatesSet.contains(today.minusDays(offset.toLong()))
        }

        StudyStreakInfo(
            currentStreak = currentStreak,
            longestStreak = longestStreak,
            isActiveToday = isActiveToday,
            recentActiveDays = recentActiveDays,
            totalActiveDays = activeDatesSet.size
        )
    }

    override suspend fun getBadges(userId: String, userName: String): List<GamificationBadge> {
        val streakInfo = calculateStudyStreaks(userId, userName)
        return getBadges(userId, userName, streakInfo)
    }

    private suspend fun getBadges(userId: String, userName: String, streakInfo: StudyStreakInfo): List<GamificationBadge> = withContext(dispatcherProvider.io) {
        val completedCourses = progressRepository.getCompletedCourses(userId)
        val completedCoursesCount = completedCourses.size
        val passedStepsCount = courseProgressDao.countPassedStepsByUser(userId)

        val bestStreak = maxOf(streakInfo.currentStreak, streakInfo.longestStreak)
        val completedTasks = teamTaskDao.countCompletedTasksForUser(userId)
        val commentsCount = newsDao.countCommentsByUser(userId)

        val resourcesRead = activitiesRepository.getResourceOpenCount(userName, UserSessionManager.KEY_RESOURCE_OPEN).toInt()
        val submissionsCount = submissionDao.countCompletedSubmissions(userId)

        listOf(
            // Courses Category
            createBadge("course_step_1", "First Step", "Complete your first course step", BadgeCategory.COURSES, "👟", passedStepsCount, 1),
            createBadge("course_comp_1", "Course Graduate", "Complete 1 full course", BadgeCategory.COURSES, "🎓", completedCoursesCount, 1),
            createBadge("course_comp_3", "Scholar", "Complete 3 full courses", BadgeCategory.COURSES, "🥈", completedCoursesCount, 3),
            createBadge("course_comp_5", "Master Learner", "Complete 5 full courses", BadgeCategory.COURSES, "🥇", completedCoursesCount, 5),
            createBadge("course_comp_10", "Course Champion", "Complete 10 full courses", BadgeCategory.COURSES, "🏆", completedCoursesCount, 10),

            // Streaks Category
            createBadge("streak_3", "Spark", "Reach a 3-day study streak", BadgeCategory.STREAKS, "🔥", bestStreak, 3),
            createBadge("streak_7", "Momentum", "Reach a 7-day study streak", BadgeCategory.STREAKS, "⚡", bestStreak, 7),
            createBadge("streak_14", "Unstoppable", "Reach a 14-day study streak", BadgeCategory.STREAKS, "🌟", bestStreak, 14),
            createBadge("streak_30", "Consistency Master", "Reach a 30-day study streak", BadgeCategory.STREAKS, "👑", bestStreak, 30),

            // Teams Category
            createBadge("team_task_1", "Team Player", "Complete 1 team task", BadgeCategory.TEAMS, "🤝", completedTasks, 1),
            createBadge("team_task_5", "Top Contributor", "Complete 5 team tasks", BadgeCategory.TEAMS, "🚀", completedTasks, 5),
            createBadge("team_task_10", "Team Hero", "Complete 10 team tasks", BadgeCategory.TEAMS, "🎖️", completedTasks, 10),
            createBadge("team_comm_3", "Active Collaborator", "Post 3 comments on team tasks or meetups", BadgeCategory.TEAMS, "💬", commentsCount, 3),

            // Resources Category
            createBadge("resource_5", "Curious Reader", "Open 5 library resources", BadgeCategory.RESOURCES, "📖", resourcesRead, 5),
            createBadge("resource_25", "Bookworm", "Open 25 library resources", BadgeCategory.RESOURCES, "📚", resourcesRead, 25),
            createBadge("resource_50", "Library Master", "Open 50 library resources", BadgeCategory.RESOURCES, "🏛️", resourcesRead, 50),

            // Exams Category
            createBadge("exam_sub_1", "Exam Taker", "Submit 1 exam or assessment", BadgeCategory.EXAMS, "📝", submissionsCount, 1),
            createBadge("exam_sub_5", "Quiz Ace", "Submit 5 exams or assessments", BadgeCategory.EXAMS, "🎯", submissionsCount, 5)
        )
    }

    override suspend fun getCertificates(userId: String, userName: String): List<CourseCertificate> = withContext(dispatcherProvider.io) {
        val userEntity = userDao.getById(userId)
        val fullName = userEntity?.getFullName()
        val uName = userEntity?.name
        val learnerName = when {
            !fullName.isNullOrBlank() -> fullName
            !uName.isNullOrBlank() -> uName
            userName.isNotBlank() -> userName
            else -> "Learner"
        }

        val completedCourses = progressRepository.getCompletedCourses(userId)
        val formatter = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.getDefault())
        val todayStr = Instant.ofEpochMilli(timeProvider.now()).atZone(ZoneId.systemDefault()).toLocalDate().format(formatter)

        completedCourses.mapNotNull { course ->
            val courseId = course.courseId ?: return@mapNotNull null
            val courseTitle = course.courseTitle ?: "Course"
            val courseIdSanitized = courseId.replace("-", "").take(6).uppercase()
            val userIdSanitized = userId.replace("-", "").take(4).uppercase()
            val certId = "OLE-CERT-$courseIdSanitized-$userIdSanitized"
            CourseCertificate(
                courseId = courseId,
                courseTitle = courseTitle,
                learnerName = learnerName,
                completionDate = todayStr,
                certificateId = certId,
                organization = "Open Learning Exchange"
            )
        }
    }

    private fun createBadge(
        id: String,
        title: String,
        description: String,
        category: BadgeCategory,
        iconEmoji: String,
        currentProgress: Int,
        maxProgress: Int
    ): GamificationBadge {
        val isUnlocked = currentProgress >= maxProgress
        return GamificationBadge(
            id = id,
            title = title,
            description = description,
            category = category,
            iconEmoji = iconEmoji,
            currentProgress = currentProgress,
            maxProgress = maxProgress,
            isUnlocked = isUnlocked,
            unlockedDate = if (isUnlocked) timeProvider.now() else null
        )
    }
}
