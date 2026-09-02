package org.ole.planet.myplanet.ui.dashboard

import android.os.Bundle
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.ui.courses.CoursesFragment
import org.ole.planet.myplanet.ui.resources.ResourcesFragment
import org.ole.planet.myplanet.ui.teams.TeamFragment
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@AndroidEntryPoint
class TestDashboardElementActivity : DashboardElementActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.AppTheme)
        super.onCreate(savedInstanceState)
        val container = FrameLayout(this).apply { id = R.id.fragment_container }
        setContentView(container)
        navigationView = BottomNavigationView(this)
    }
}

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class DashboardElementActivityNavigationTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @javax.inject.Inject
    lateinit var sharedPrefManager: org.ole.planet.myplanet.services.SharedPrefManager

    @Before
    fun setUp() {
        hiltRule.inject()
        org.ole.planet.myplanet.utils.UrlUtils.init(sharedPrefManager)
    }

    @Test
    fun testOpenCallFragment_initialNavigationAddsToBackStack() {
        val activity = Robolectric.buildActivity(TestDashboardElementActivity::class.java).setup().get()
        val fm = activity.supportFragmentManager

        val fragment1 = ResourcesFragment()
        activity.openCallFragment(fragment1, "library")
        fm.executePendingTransactions()

        assertEquals(1, fm.backStackEntryCount)
        assertSame(fragment1, fm.findFragmentByTag("library"))
    }

    @Test
    fun testOpenCallFragment_reopeningCurrentlyVisibleDoesNotDuplicate() {
        val activity = Robolectric.buildActivity(TestDashboardElementActivity::class.java).setup().get()
        val fm = activity.supportFragmentManager

        val fragment1 = ResourcesFragment()
        activity.openCallFragment(fragment1, "library")
        fm.executePendingTransactions()

        assertEquals(1, fm.backStackEntryCount)

        // Calling openCallFragment on currently visible fragment
        activity.openCallFragment(ResourcesFragment(), "library")
        fm.executePendingTransactions()

        assertEquals(1, fm.backStackEntryCount)
        assertSame(fragment1, fm.findFragmentByTag("library"))
    }

    @Test
    fun testOpenCallFragment_switchingTabsAndReturningPopsBackStackInsteadOfRestacking() {
        val activity = Robolectric.buildActivity(TestDashboardElementActivity::class.java).setup().get()
        val fm = activity.supportFragmentManager

        // 1. Open Library tab
        val libraryFragment = ResourcesFragment()
        activity.openCallFragment(libraryFragment, "library")
        fm.executePendingTransactions()
        assertEquals(1, fm.backStackEntryCount)

        // 2. Open Courses tab
        val coursesFragment = CoursesFragment()
        activity.openCallFragment(coursesFragment, "course")
        fm.executePendingTransactions()
        assertEquals(2, fm.backStackEntryCount)

        // 3. Open Library tab again -> should pop back to existing Library instance
        activity.openCallFragment(ResourcesFragment(), "library")
        fm.executePendingTransactions()

        assertEquals(1, fm.backStackEntryCount)
        val currentFragment = fm.findFragmentById(R.id.fragment_container)
        assertSame(libraryFragment, currentFragment)
    }

    @Test
    fun testOnClickTabItems_usesTeamsTagForTeamFragment() {
        val activity = Robolectric.buildActivity(TestDashboardElementActivity::class.java).setup().get()
        val fm = activity.supportFragmentManager

        // Position 3 is TeamFragment with "teams" tag
        activity.onClickTabItems(3)
        fm.executePendingTransactions()

        val teamFragment = fm.findFragmentByTag("teams")
        assertNotNull(teamFragment)
        assertEquals(true, teamFragment is TeamFragment)
    }
}
