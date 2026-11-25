package org.ole.planet.myplanet.ui.exam

import org.junit.Assert.assertEquals
import org.junit.Test
import org.ole.planet.myplanet.model.Submission
import org.ole.planet.myplanet.ui.exam.ExamTakingFragment.SurveyStart

class ExamTakingSurveyStartTest {

    private val restored = Submission(id = "in-progress")
    private val existing = Submission(id = "older-pending")

    @Test
    fun teamSurveyAfterRotation_continuesTheSameSubmission() {
        assertEquals(SurveyStart.CONTINUE_RESTORED, ExamTakingFragment.surveyStart(restored, existing, isTeam = true))
    }

    @Test
    fun personalSurveyAfterRotation_continuesWithoutAskingAgain() {
        assertEquals(SurveyStart.CONTINUE_RESTORED, ExamTakingFragment.surveyStart(restored, existing, isTeam = false))
    }

    @Test
    fun teamSurveyFirstOpen_startsItsOwnSession() {
        assertEquals(SurveyStart.START_SESSION, ExamTakingFragment.surveyStart(null, existing, isTeam = true))
    }

    @Test
    fun personalSurveyFirstOpen_withPendingAnswers_asksToResume() {
        assertEquals(SurveyStart.ASK_RESUME, ExamTakingFragment.surveyStart(null, existing, isTeam = false))
    }

    @Test
    fun personalSurveyFirstOpen_withNothingPending_startsASession() {
        assertEquals(SurveyStart.START_SESSION, ExamTakingFragment.surveyStart(null, null, isTeam = false))
    }
}
