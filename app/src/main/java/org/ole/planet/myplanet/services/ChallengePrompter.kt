package org.ole.planet.myplanet.services

import androidx.fragment.app.FragmentManager
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.ui.components.MarkdownDialogFragment
import org.ole.planet.myplanet.ui.dashboard.ChallengeDialogData
import org.ole.planet.myplanet.ui.dashboard.DashboardActivity
import org.ole.planet.myplanet.ui.dashboard.DashboardViewModel

class ChallengePrompter(
    private val activity: DashboardActivity,
    private val sharedPrefManager: SharedPrefManager,
    private val viewModel: DashboardViewModel
) {
    private val fragmentManager: FragmentManager
        get() = activity.supportFragmentManager

    fun showChallengeDialog(data: ChallengeDialogData) {
        val voiceCount = data.voiceCount
        val courseStatus = data.courseStatus
        val allVoiceCount = data.allVoiceCount
        val hasUnfinishedSurvey = data.hasUnfinishedSurvey
        val hasValidSync = data.hasValidSync

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
