package org.ole.planet.myplanet.ui.dashboard

import android.content.SharedPreferences
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.google.gson.JsonObject
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.BuildConfig
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmUserChallengeActions
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.repository.ProgressRepository
import org.ole.planet.myplanet.repository.SubmissionsRepository
import org.ole.planet.myplanet.repository.VoicesRepository
import org.ole.planet.myplanet.ui.components.MarkdownDialogFragment
import org.ole.planet.myplanet.ui.courses.CoursesProgressFragment

class ChallengeHelper(
    private val activity: DashboardActivity,
    private val user: RealmUserModel?,
    private val settings: SharedPreferences,
    private val editor: SharedPreferences.Editor,
    private val viewModel: DashboardViewModel,
    private val progressRepository: ProgressRepository,
    private val databaseService: DatabaseService,
    private val voicesRepository: VoicesRepository,
    private val submissionsRepository: SubmissionsRepository
) {
    private val fragmentManager: FragmentManager
        get() = activity.supportFragmentManager

    fun evaluateChallengeDialog() {
        val startTime = 1730419200000
        val endTime = 1734307200000

        val courseId = "4e6b78800b6ad18b4e8b0e1e38a98cac"
        activity.lifecycleScope.launch(Dispatchers.Main) {
            try {
                val courseData = withContext(Dispatchers.IO) {
                    progressRepository.fetchCourseData(user?.id)
                }

                val uniqueDates = voicesRepository.getCommunityVoiceDates(startTime, endTime, user?.id)
                val allUniqueDates = voicesRepository.getCommunityVoiceDates(startTime, endTime, null)
                val courseName = databaseService.withRealmAsync { realm ->
                    realm.where(RealmMyCourse::class.java)
                        .equalTo("courseId", courseId)
                        .findFirst()?.courseTitle
                }
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
                    challengeDialog(uniqueDates.size, courseStatus, allUniqueDates.size, hasUnfinishedSurvey)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun hasPendingSurvey(courseId: String): Boolean {
        val surveys = databaseService.withRealmAsync { realm ->
            realm.copyFromRealm(
                realm.where(RealmStepExam::class.java)
                    .equalTo("courseId", courseId)
                    .equalTo("type", "survey")
                    .findAll()
            )
        }
        for (survey in surveys) {
            if (!submissionsRepository.hasSubmission(survey.id, survey.courseId, user?.id, "survey")) {
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
                settings.getString("serverURL", "") in validUrls
    }

    private fun challengeDialog(voiceCount: Int, courseStatus: String, allVoiceCount: Int, hasUnfinishedSurvey: Boolean) {
        databaseService.withRealm { realm ->
            val voiceTaskDone = if (voiceCount >= 5) "✅" else "[ ]"
            val prereqsMet = courseStatus.contains("terminado", ignoreCase = true) && voiceCount >= 5
            var hasValidSync = false
            val syncTaskDone = if (prereqsMet) {
                hasValidSync = realm.where(RealmUserChallengeActions::class.java)
                    .equalTo("userId", user?.id)
                    .equalTo("actionType", "sync")
                    .count() > 0

                if (hasValidSync) "✅" else "[ ]"
            } else "[ ]"
            val courseTaskDone = if (courseStatus.contains("terminado", ignoreCase = true)) "✅ $courseStatus" else "[ ] $courseStatus"

            val isCompleted = syncTaskDone.startsWith("✅") && voiceTaskDone.startsWith("✅") && courseTaskDone.startsWith("✅")

            val hasShownCongrats = settings.getBoolean("has_shown_congrats", false)

            if (isCompleted && hasShownCongrats) return@withRealm

            if (isCompleted && !hasShownCongrats) {
                editor.putBoolean("has_shown_congrats", true).apply()
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
}
