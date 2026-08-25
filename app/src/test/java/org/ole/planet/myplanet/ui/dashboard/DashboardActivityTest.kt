package org.ole.planet.myplanet.ui.dashboard

import android.os.Bundle
import android.view.View
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import javax.inject.Inject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.utils.UrlUtils
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = HiltTestApplication::class)
@LooperMode(LooperMode.Mode.PAUSED)
class DashboardActivityTest {

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
    fun testSyncBannerDismissalSurvivesRecreation() {
        sharedPrefManager.setLastSync(1000L)
        val controller = Robolectric.buildActivity(DashboardActivity::class.java).setup()
        val activity = controller.get()
        assertNotNull(activity)

        val syncBanner = activity.findViewById<View>(R.id.dashboard_sync_banner)
        val dismissButton = activity.findViewById<View>(R.id.btn_dismiss_sync)
        assertNotNull(syncBanner)
        assertNotNull(dismissButton)

        assertEquals(View.VISIBLE, syncBanner.visibility)

        dismissButton.performClick()
        assertEquals(View.GONE, syncBanner.visibility)

        val bundle = Bundle()
        controller.saveInstanceState(bundle).pause().stop().destroy()

        val recreatedController = Robolectric.buildActivity(DashboardActivity::class.java).setup(bundle)
        val recreatedActivity = recreatedController.get()
        val recreatedSyncBanner = recreatedActivity.findViewById<View>(R.id.dashboard_sync_banner)

        assertEquals(View.GONE, recreatedSyncBanner.visibility)
        recreatedController.pause().stop().destroy()
    }

    @Test
    fun testSyncBannerShowsAgainAfterNewSync() {
        sharedPrefManager.setLastSync(1000L)
        val controller = Robolectric.buildActivity(DashboardActivity::class.java).setup()
        val activity = controller.get()

        val syncBanner = activity.findViewById<View>(R.id.dashboard_sync_banner)
        val dismissButton = activity.findViewById<View>(R.id.btn_dismiss_sync)

        assertEquals(View.VISIBLE, syncBanner.visibility)

        dismissButton.performClick()
        assertEquals(View.GONE, syncBanner.visibility)

        val bundle = Bundle()
        controller.saveInstanceState(bundle).pause().stop().destroy()

        // Newer sync timestamp arrives
        sharedPrefManager.setLastSync(2000L)

        val recreatedController = Robolectric.buildActivity(DashboardActivity::class.java).setup(bundle)
        val recreatedActivity = recreatedController.get()
        val recreatedSyncBanner = recreatedActivity.findViewById<View>(R.id.dashboard_sync_banner)

        assertEquals(View.VISIBLE, recreatedSyncBanner.visibility)
        recreatedController.pause().stop().destroy()
    }
}
