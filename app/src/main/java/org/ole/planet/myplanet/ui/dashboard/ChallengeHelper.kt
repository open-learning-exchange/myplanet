package org.ole.planet.myplanet.ui.dashboard

import android.content.SharedPreferences
import androidx.fragment.app.FragmentManager
import com.google.gson.JsonObject
import io.realm.Realm
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.RealmUserChallengeActions
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.utilities.MarkdownDialog
import org.ole.planet.myplanet.domain.ChallengeEvaluator

class ChallengeHelper(
    private val activity: DashboardActivity,
    private val realm: Realm,
    private val user: RealmUserModel?,
    private val settings: SharedPreferences,
    private val editor: SharedPreferences.Editor,
    private val evaluator: ChallengeEvaluator,
    private val viewModel: DashboardViewModel
) {
    private val fragmentManager: FragmentManager
        get() = activity.supportFragmentManager

    fun evaluateChallengeDialog() {
        val result = evaluator.evaluate() ?: return
        val courseStatus = getCourseStatus(result.courseProgress, result.courseName)
        challengeDialog(result.voiceCount, courseStatus, result.allVoiceCount, result.hasUnfinishedSurvey)
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

    private fun challengeDialog(
        voiceCount: Int,
        courseStatus: String,
        allVoiceCount: Int,
        hasUnfinishedSurvey: Boolean
    ) {
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

        if (isCompleted && hasShownCongrats) return

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
