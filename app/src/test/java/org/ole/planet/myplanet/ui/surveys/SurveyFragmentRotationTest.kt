package org.ole.planet.myplanet.ui.surveys

import android.os.Bundle
import android.os.Looper
import android.widget.RadioButton
import androidx.lifecycle.ViewModelProvider
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import javax.inject.Inject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.ui.dashboard.TestDashboardElementActivity
import org.ole.planet.myplanet.utils.UrlUtils
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class SurveyFragmentRotationTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var sharedPrefManager: SharedPrefManager

    @Before
    fun setUp() {
        hiltRule.inject()
        UrlUtils.init(sharedPrefManager)
    }

    @Test
    fun `adopt tab still loads adoptable surveys after rotation`() {
        val controller = Robolectric.buildActivity(TestDashboardElementActivity::class.java).setup()
        val fragment = SurveyFragment().apply {
            arguments = Bundle().apply {
                putBoolean("isTeam", true)
                putString("teamId", "team1")
            }
        }
        controller.get().openCallFragment(fragment, "survey")
        controller.get().supportFragmentManager.executePendingTransactions()
        idle()

        fragment.requireView().findViewById<RadioButton>(R.id.rbAdoptSurvey).performClick()
        idle()
        assertTrue(viewModelOf(controller.get()).isTeamShareAllowed.value)

        controller.recreate()
        idle()

        val restored = controller.get().supportFragmentManager.fragments.filterIsInstance<SurveyFragment>().single()
        val view = restored.requireView()
        assertTrue(view.findViewById<RadioButton>(R.id.rbAdoptSurvey).isChecked)
        assertFalse(view.findViewById<RadioButton>(R.id.rbTeamSurvey).isChecked)
        assertTrue(viewModelOf(controller.get()).isTeamShareAllowed.value)
    }

    private fun viewModelOf(activity: TestDashboardElementActivity): SurveysViewModel {
        val fragment = activity.supportFragmentManager.fragments.filterIsInstance<SurveyFragment>().single()
        return ViewModelProvider(fragment)[SurveysViewModel::class.java]
    }

    private fun idle() {
        shadowOf(Looper.getMainLooper()).idle()
    }
}
