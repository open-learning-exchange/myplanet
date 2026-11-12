package org.ole.planet.myplanet.ui.dashboard

import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.ui.components.FragmentNavigator
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DashboardActivityBackPressTest {

    private lateinit var activity: AppCompatActivity
    private var containerId: Int = 1001

    @Before
    fun setUp() {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java).setup()
        activity = controller.get()
        val container = FrameLayout(activity).apply { id = containerId }
        activity.setContentView(container)
    }

    @Test
    fun isRootDashboardFragment_identifiesRootFragmentsCorrectly() {
        assertTrue(DashboardActivity.isRootDashboardFragment(null))
        assertTrue(DashboardActivity.isRootDashboardFragment(mockk<BellDashboardFragment>()))
        assertTrue(DashboardActivity.isRootDashboardFragment(InactiveDashboardFragment()))

        assertFalse(DashboardActivity.isRootDashboardFragment(TestFeatureFragment("resources")))
        assertFalse(DashboardActivity.isRootDashboardFragment(TestFeatureFragment("courses")))
    }

    @Test
    fun backPressNavigation_fromChildFragmentWithOneEntry_popsBackStackInsteadOfLogout() {
        val rootFragment = InactiveDashboardFragment()
        FragmentNavigator.replaceFragment(
            fragmentManager = activity.supportFragmentManager,
            containerId = containerId,
            fragment = rootFragment,
            addToBackStack = false,
            tag = "ROOT"
        )
        activity.supportFragmentManager.executePendingTransactions()

        assertEquals(0, activity.supportFragmentManager.backStackEntryCount)
        assertTrue(DashboardActivity.isRootDashboardFragment(activity.supportFragmentManager.findFragmentById(containerId)))

        val childFragment = TestFeatureFragment("child")
        FragmentNavigator.replaceFragment(
            fragmentManager = activity.supportFragmentManager,
            containerId = containerId,
            fragment = childFragment,
            addToBackStack = true,
            tag = "CHILD"
        )
        activity.supportFragmentManager.executePendingTransactions()

        assertEquals(1, activity.supportFragmentManager.backStackEntryCount)
        val currentFrag = activity.supportFragmentManager.findFragmentById(containerId)
        assertFalse(DashboardActivity.isRootDashboardFragment(currentFrag))

        val shouldPop = activity.supportFragmentManager.backStackEntryCount > 0 && !DashboardActivity.isRootDashboardFragment(currentFrag)
        assertTrue(shouldPop)

        FragmentNavigator.popBackStack(activity.supportFragmentManager)
        activity.supportFragmentManager.executePendingTransactions()

        assertEquals(0, activity.supportFragmentManager.backStackEntryCount)
        val restoredFrag = activity.supportFragmentManager.findFragmentById(containerId)
        assertTrue(DashboardActivity.isRootDashboardFragment(restoredFrag))

        val shouldPopAtRoot = activity.supportFragmentManager.backStackEntryCount > 0 && !DashboardActivity.isRootDashboardFragment(restoredFrag)
        assertFalse(shouldPopAtRoot)
    }

    @Test
    fun backPressNavigation_nestedChildFragments_popsSequentiallyToRoot() {
        val rootFragment = InactiveDashboardFragment()
        FragmentNavigator.replaceFragment(
            fragmentManager = activity.supportFragmentManager,
            containerId = containerId,
            fragment = rootFragment,
            addToBackStack = false,
            tag = "ROOT"
        )
        activity.supportFragmentManager.executePendingTransactions()

        val featureA = TestFeatureFragment("FeatureA")
        FragmentNavigator.replaceFragment(
            fragmentManager = activity.supportFragmentManager,
            containerId = containerId,
            fragment = featureA,
            addToBackStack = true,
            tag = "FEATURE_A"
        )
        activity.supportFragmentManager.executePendingTransactions()

        val featureB = TestFeatureFragment("FeatureB")
        FragmentNavigator.replaceFragment(
            fragmentManager = activity.supportFragmentManager,
            containerId = containerId,
            fragment = featureB,
            addToBackStack = true,
            tag = "FEATURE_B"
        )
        activity.supportFragmentManager.executePendingTransactions()

        assertEquals(2, activity.supportFragmentManager.backStackEntryCount)
        assertFalse(DashboardActivity.isRootDashboardFragment(activity.supportFragmentManager.findFragmentById(containerId)))

        // First back press: pops FeatureB -> FeatureA
        FragmentNavigator.popBackStack(activity.supportFragmentManager)
        activity.supportFragmentManager.executePendingTransactions()

        assertEquals(1, activity.supportFragmentManager.backStackEntryCount)
        assertFalse(DashboardActivity.isRootDashboardFragment(activity.supportFragmentManager.findFragmentById(containerId)))

        // Second back press: pops FeatureA -> Root
        FragmentNavigator.popBackStack(activity.supportFragmentManager)
        activity.supportFragmentManager.executePendingTransactions()

        assertEquals(0, activity.supportFragmentManager.backStackEntryCount)
        assertTrue(DashboardActivity.isRootDashboardFragment(activity.supportFragmentManager.findFragmentById(containerId)))
    }

    class TestFeatureFragment(val name: String) : Fragment()
}
