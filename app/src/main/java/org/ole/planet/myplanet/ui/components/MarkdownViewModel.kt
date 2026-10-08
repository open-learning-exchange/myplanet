package org.ole.planet.myplanet.ui.components

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import org.ole.planet.myplanet.repository.UserRepository

enum class ChallengeAction { START, CONTINUE, NEXT, SYNC }

data class ChallengeDialogState(val progress: Int, val action: ChallengeAction)

@HiltViewModel
class MarkdownViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {
    suspend fun hasActiveUserSyncAction(): Boolean {
        return userRepository.hasActiveUserSyncAction()
    }

    fun challengeDialogState(
        courseStatus: String,
        voiceCount: Int,
        allVoiceCount: Int,
        hasUnfinishedSurvey: Boolean
    ): ChallengeDialogState {
        val earnedDollarsVoice = allVoiceCount * 2
        val earnedDollarsSurvey = if (!hasUnfinishedSurvey) 1 else 0
        val total = earnedDollarsVoice + earnedDollarsSurvey
        val progressValue = ((total.toDouble() / 500) * 100).toInt().coerceAtMost(100)

        val action = when {
            courseStatus.contains("no iniciado") -> ChallengeAction.START
            courseStatus.contains("terminado") && voiceCount < 5 -> ChallengeAction.NEXT
            courseStatus.contains("terminado") && voiceCount >= 5 -> ChallengeAction.SYNC
            else -> ChallengeAction.CONTINUE
        }

        return ChallengeDialogState(progress = progressValue, action = action)
    }

    fun isChallengeCompleted(courseStatus: String, voiceCount: Int, hasSyncAction: Boolean): Boolean {
        return courseStatus.contains("terminado") && voiceCount >= 5 && hasSyncAction
    }
}
