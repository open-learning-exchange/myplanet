package org.ole.planet.myplanet.ui.dashboard

import android.content.SharedPreferences
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.google.gson.JsonObject
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.BuildConfig
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.repository.CourseRepository
import org.ole.planet.myplanet.repository.NewsRepository
import org.ole.planet.myplanet.repository.ProgressRepository
import org.ole.planet.myplanet.repository.SurveyRepository
import org.ole.planet.myplanet.repository.UserChallengeRepository
import org.ole.planet.myplanet.ui.courses.MyProgressFragment
import org.ole.planet.myplanet.utilities.MarkdownDialog

class ChallengeHelper(
    private val activity: DashboardActivity,
    private val user: RealmUserModel?,
    private val settings: SharedPreferences,
    private val editor: SharedPreferences.Editor,
    private val viewModel: DashboardViewModel,
    private val progressRepository: ProgressRepository,
    private val courseRepository: CourseRepository,
    private val newsRepository: NewsRepository,
    private val surveyRepository: SurveyRepository,
    private val userChallengeRepository: UserChallengeRepository
) {
    private val fragmentManager: FragmentManager
        get() = activity.supportFragmentManager

    fun evaluateChallengeDialog() {
        val startTime = 1730419200000
        val endTime = 1734307200000

        val courseId = "4e6b78800b6ad18b4e8b0e1e38a98cac"
        activity.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val courseName = courseRepository.getCourseByCourseId(courseId)?.courseTitle
                val uniqueDates = newsRepository.getVoiceDates(startTime, endTime, user?.id)
                val allUniqueDates = newsRepository.getVoiceDates(startTime, endTime, null)
                val hasUnfinishedSurvey = surveyRepository.hasPendingSurvey(courseId)
                val courseData = progressRepository.fetchCourseData(user?.id)
                val progress = MyProgressFragment.getCourseProgress(courseData, courseId)

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

                    withContext(Dispatchers.Main) {
                        challengeDialog(uniqueDates.size, courseStatus, allUniqueDates.size, hasUnfinishedSurvey)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
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
                settings.getString("serverURL", "") in validUrls
    }

    private fun challengeDialog(voiceCount: Int, courseStatus: String, allVoiceCount: Int, hasUnfinishedSurvey: Boolean) {
        activity.lifecycleScope.launch {
            val voiceTaskDone = if (voiceCount >= 5) "✅" else "[ ]"
            val prereqsMet = courseStatus.contains("terminado", ignoreCase = true) && voiceCount >= 5
            val hasValidSync = if (prereqsMet) userChallengeRepository.hasValidSync(user?.id) else false
            val syncTaskDone = if (prereqsMet) {
                if (hasValidSync) "✅" else "[ ]"
            } else "[ ]"
            val courseTaskDone = if (courseStatus.contains("terminado", ignoreCase = true)) "✅ $courseStatus" else "[ ] $courseStatus"

            val isCompleted = syncTaskDone.startsWith("✅") && voiceTaskDone.startsWith("✅") && courseTaskDone.startsWith("✅")

            val hasShownCongrats = settings.getBoolean("has_shown_congrats", false)

            if (isCompleted && hasShownCongrats) return@launch

            if (isCompleted && !hasShownCongrats) {
                editor.putBoolean("has_shown_congrats", true).apply()
                val markdownContent = """
            ${activity.getString(R.string.community_earnings, viewModel.calculateCommunityProgress(allVoiceCount, hasUnfinishedSurvey))}
            ${activity.getString(R.string.your_earnings, viewModel.calculateIndividualProgress(voiceCount, hasUnfinishedSurvey))}
            ### ${activity.getString(R.string.congratulations)} <br/>
        """.trimIndent()
                MarkdownDialog.newInstance(markdownContent, courseStatus, voiceCount, allVoiceCount, hasUnfinishedSurvey)
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
                MarkdownDialog.newInstance(markdownContent, courseStatus, voiceCount, allVoiceCount, hasUnfinishedSurvey)
                    .show(fragmentManager, "markdown_dialog")
            }
        }
    }
}
