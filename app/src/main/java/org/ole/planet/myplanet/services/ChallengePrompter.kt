package org.ole.planet.myplanet.services

import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.google.gson.JsonObject
import java.time.LocalDate
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.BuildConfig
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.ui.components.MarkdownDialogFragment
import org.ole.planet.myplanet.ui.courses.CoursesProgressFragment
import org.ole.planet.myplanet.ui.dashboard.DashboardActivity
import org.ole.planet.myplanet.ui.dashboard.DashboardViewModel

class ChallengePrompter(
    private val activity: DashboardActivity,
    private val user: RealmUser?,
    private val sharedPrefManager: SharedPrefManager,
    private val viewModel: DashboardViewModel
) {
    private val fragmentManager: FragmentManager
        get() = activity.supportFragmentManager

    fun evaluateChallengeDialog() {
        val startTime = 1730419200000
        val endTime = 1734307200000

        val courseId = "4e6b78800b6ad18b4e8b0e1e38a98cac"
        activity.lifecycleScope.launch {
            try {
                val courseData = viewModel.fetchCourseData(user?.id)

                val uniqueDates = viewModel.getCommunityVoiceDates(startTime, endTime, user?.id)
                val allUniqueDates = viewModel.getCommunityVoiceDates(startTime, endTime, null)
                val courseName = viewModel.getCourseTitleById(courseId)
                val hasUnfinishedSurvey = hasPendingSurvey(courseId)

                val progress = CoursesProgressFragment.getCourseProgress(courseData, courseId)
                val validUrls = listOf(
                    "https://${BuildConfig.PLANET_GUATEMALA_URL}",
                    "http://${BuildConfig.PLANET_XELA_URL}",
                    "http://${BuildConfig.PLANET_URIUR_URL}",
                    "http://${BuildConfig.PLANET_SANPABLO_URL}",
                    "http://${BuildConfig.PLANET_EMBAKASI_URL}",
                    "https://${BuildConfig.PLANET_VI_URL}"
                )

                val today = LocalDate.now()
                if (user?.id?.startsWith("guest") == false && shouldPromptChallenge(today, validUrls)) {
                    val courseStatus = getCourseStatus(progress, courseName)
                    val voiceCount = uniqueDates.size
                    val prereqsMet = courseStatus.contains("terminado", ignoreCase = true) && voiceCount >= 5
                    var hasValidSync = false
                    if (prereqsMet) {
                        hasValidSync = viewModel.hasUserCompletedSync(user.id ?: "")
                    }
                    challengeDialog(uniqueDates.size, courseStatus, allUniqueDates.size, hasUnfinishedSurvey, hasValidSync)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun hasPendingSurvey(courseId: String): Boolean {
        val surveys = viewModel.getSurveysByCourseId(courseId)
        for (survey in surveys) {
            if (!viewModel.hasSubmission(survey.id, survey.courseId, user?.id, "survey")) {
                return true
            }
        }
        return false
    }

    private fun getCourseStatus(progress: JsonObject?, courseName: String?): String {
        return if (progress != null) {
            val max = progress.get("max").asInt
            val current = progress.get("current").asInt
            if (current == max) {
                activity.getString(R.string.course_completed, courseName)
            } else {
                activity.getString(R.string.course_in_progress, courseName, current, max)
            }
        } else {
            activity.getString(R.string.course_not_started, courseName)
        }
    }

    private fun shouldPromptChallenge(today: LocalDate, validUrls: List<String>): Boolean {
        val endDate = LocalDate.of(2025, 1, 16)
        return today.isAfter(LocalDate.of(2024, 11, 30)) &&
                today.isBefore(endDate) &&
                sharedPrefManager.getServerUrl() in validUrls
    }

    private fun challengeDialog(voiceCount: Int, courseStatus: String, allVoiceCount: Int, hasUnfinishedSurvey: Boolean, hasValidSync: Boolean) {
        val voiceTaskDone = if (voiceCount >= 5) "✅" else "[ ]"
        val prereqsMet = courseStatus.contains("terminado", ignoreCase = true) && voiceCount >= 5
        val syncTaskDone = if (prereqsMet) {
            if (hasValidSync) "✅" else "[ ]"
        } else "[ ]"
        val courseTaskDone = if (courseStatus.contains("terminado", ignoreCase = true)) "✅ $courseStatus" else "[ ] $courseStatus"

        val isCompleted = syncTaskDone.startsWith("✅") && voiceTaskDone.startsWith("✅") && courseTaskDone.startsWith("✅")

        val hasShownCongrats = sharedPrefManager.getHasShownCongrats()

        if (isCompleted && hasShownCongrats) return

        if (isCompleted && !hasShownCongrats) {
            sharedPrefManager.setHasShownCongrats(true)
            val markdownContent = """
            ${activity.getString(R.string.community_earnings, viewModel.calculateCommunityProgress(allVoiceCount, hasUnfinishedSurvey))}
            ${activity.getString(R.string.your_earnings, viewModel.calculateIndividualProgress(voiceCount, hasUnfinishedSurvey))}
            ### ${activity.getString(R.string.congratulations)} <br/>
        """.trimIndent()
            MarkdownDialogFragment.newInstance(markdownContent, courseStatus, voiceCount, allVoiceCount, hasUnfinishedSurvey)
                .show(fragmentManager, "markdown_dialog")
        } else {
            val cappedVoiceCount = minOf(voiceCount, 5)
            val voicesText = if (cappedVoiceCount > 0) {
                "$cappedVoiceCount ${activity.getString(R.string.daily_voices)}"
            } else {
                ""
            }
            val markdownContent = """
            ${activity.getString(R.string.community_earnings, viewModel.calculateCommunityProgress(allVoiceCount, hasUnfinishedSurvey))}
            ${activity.getString(R.string.your_earnings, viewModel.calculateIndividualProgress(voiceCount, hasUnfinishedSurvey))}
            ### ${activity.getString(R.string.per_survey, courseTaskDone)} <br/>
            ### ${activity.getString(R.string.share_opinion)} $voicesText <br/>
            ### ${activity.getString(R.string.remember_sync)} <br/>
        """.trimIndent()
            MarkdownDialogFragment.newInstance(markdownContent, courseStatus, voiceCount, allVoiceCount, hasUnfinishedSurvey)
                .show(fragmentManager, "markdown_dialog")
        }
    }
}
