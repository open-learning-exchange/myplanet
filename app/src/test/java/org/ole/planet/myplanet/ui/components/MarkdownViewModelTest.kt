package org.ole.planet.myplanet.ui.components

import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.repository.UserRepository

class MarkdownViewModelTest {
    private lateinit var userRepository: UserRepository
    private lateinit var viewModel: MarkdownViewModel

    @Before
    fun setUp() {
        userRepository = mockk()
        viewModel = MarkdownViewModel(userRepository)
    }

    @Test
    fun `challengeDialogState calculates progress correctly`() {
        // (allVoiceCount, hasUnfinishedSurvey) = (0,true) -> 0, (0,false) -> 0, (2,false) -> 1, (125,true) -> 50, (250,false) -> 100, (1000,false) -> 100
        assertEquals(0, viewModel.challengeDialogState("status", voiceCount = 0, allVoiceCount = 0, hasUnfinishedSurvey = true).progress)
        assertEquals(0, viewModel.challengeDialogState("status", voiceCount = 0, allVoiceCount = 0, hasUnfinishedSurvey = false).progress)
        assertEquals(1, viewModel.challengeDialogState("status", voiceCount = 0, allVoiceCount = 2, hasUnfinishedSurvey = false).progress)
        assertEquals(50, viewModel.challengeDialogState("status", voiceCount = 0, allVoiceCount = 125, hasUnfinishedSurvey = true).progress)
        assertEquals(100, viewModel.challengeDialogState("status", voiceCount = 0, allVoiceCount = 250, hasUnfinishedSurvey = false).progress)
        assertEquals(100, viewModel.challengeDialogState("status", voiceCount = 0, allVoiceCount = 1000, hasUnfinishedSurvey = false).progress)
    }

    @Test
    fun `challengeDialogState selects action correctly`() {
        // "Course no iniciado" -> START
        assertEquals(ChallengeAction.START, viewModel.challengeDialogState("Course no iniciado", voiceCount = 0, allVoiceCount = 0, hasUnfinishedSurvey = false).action)

        // "X terminado!" with voiceCount 4 -> NEXT
        assertEquals(ChallengeAction.NEXT, viewModel.challengeDialogState("X terminado!", voiceCount = 4, allVoiceCount = 0, hasUnfinishedSurvey = false).action)

        // "X terminado!" with voiceCount 5 -> SYNC
        assertEquals(ChallengeAction.SYNC, viewModel.challengeDialogState("X terminado!", voiceCount = 5, allVoiceCount = 0, hasUnfinishedSurvey = false).action)

        // "en progreso" -> CONTINUE
        assertEquals(ChallengeAction.CONTINUE, viewModel.challengeDialogState("en progreso", voiceCount = 0, allVoiceCount = 0, hasUnfinishedSurvey = false).action)

        // "X Terminado" (capital T) -> CONTINUE
        assertEquals(ChallengeAction.CONTINUE, viewModel.challengeDialogState("X Terminado", voiceCount = 0, allVoiceCount = 0, hasUnfinishedSurvey = false).action)
    }

    @Test
    fun `isChallengeCompleted returns true only when status contains terminado, voiceCount is at least 5, and hasSyncAction is true`() {
        assertTrue(viewModel.isChallengeCompleted("terminado", voiceCount = 5, hasSyncAction = true))
        assertTrue(viewModel.isChallengeCompleted("X terminado!", voiceCount = 6, hasSyncAction = true))

        assertFalse(viewModel.isChallengeCompleted("terminado", voiceCount = 4, hasSyncAction = true))
        assertFalse(viewModel.isChallengeCompleted("terminado", voiceCount = 5, hasSyncAction = false))
        assertFalse(viewModel.isChallengeCompleted("no iniciado", voiceCount = 5, hasSyncAction = true))
        assertFalse(viewModel.isChallengeCompleted("Terminado", voiceCount = 5, hasSyncAction = true))
    }
}
