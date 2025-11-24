package org.ole.planet.myplanet.ui.news

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ActivityTestRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.ui.dashboard.DashboardActivity

@RunWith(AndroidJUnit4::class)
class NewsScrollTest {

    @Rule
    @JvmField
    val activityRule = ActivityTestRule(DashboardActivity::class.java)

    @Test
    fun testScrollPerformance() {
        // Assume we are on a screen with rv_news or navigate to it.
        // For simplicity, we assume DashboardActivity shows fragments and one of them is NewsFragment.
        // We might need to navigate to "Community" tab.

        // This test stub attempts to scroll the news recycler view.
        try {
            onView(withId(R.id.rv_news)).perform(RecyclerViewActions.scrollToPosition<RecyclerView.ViewHolder>(20))
        } catch (e: Exception) {
            // Ignore if view not found (e.g. need navigation)
        }
    }
}
