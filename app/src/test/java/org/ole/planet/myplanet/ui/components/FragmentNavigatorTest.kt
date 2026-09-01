package org.ole.planet.myplanet.ui.components

import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FragmentNavigatorTest {

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
    fun replaceFragment_withoutBackStack_replacesFragment() {
        val fragment = TestFragment("one")
        FragmentNavigator.replaceFragment(
            fragmentManager = activity.supportFragmentManager,
            containerId = containerId,
            fragment = fragment,
            addToBackStack = false,
            tag = "TAG_ONE"
        )
        activity.supportFragmentManager.executePendingTransactions()

        assertEquals(0, activity.supportFragmentManager.backStackEntryCount)
        assertNotNull(activity.supportFragmentManager.findFragmentByTag("TAG_ONE"))
    }

    @Test
    fun replaceFragment_withBackStack_addsToBackStack() {
        val fragment = TestFragment("one")
        FragmentNavigator.replaceFragment(
            fragmentManager = activity.supportFragmentManager,
            containerId = containerId,
            fragment = fragment,
            addToBackStack = true,
            tag = "TAG_ONE"
        )
        activity.supportFragmentManager.executePendingTransactions()

        assertEquals(1, activity.supportFragmentManager.backStackEntryCount)
        assertNotNull(activity.supportFragmentManager.findFragmentByTag("TAG_ONE"))
    }

    @Test
    fun popBackStack_withEntries_popsTopEntry() {
        val fragment1 = TestFragment("one")
        val fragment2 = TestFragment("two")

        FragmentNavigator.replaceFragment(
            fragmentManager = activity.supportFragmentManager,
            containerId = containerId,
            fragment = fragment1,
            addToBackStack = true,
            tag = "TAG_ONE"
        )
        activity.supportFragmentManager.executePendingTransactions()

        FragmentNavigator.replaceFragment(
            fragmentManager = activity.supportFragmentManager,
            containerId = containerId,
            fragment = fragment2,
            addToBackStack = true,
            tag = "TAG_TWO"
        )
        activity.supportFragmentManager.executePendingTransactions()

        assertEquals(2, activity.supportFragmentManager.backStackEntryCount)

        FragmentNavigator.popBackStack(activity.supportFragmentManager)
        activity.supportFragmentManager.executePendingTransactions()

        assertEquals(1, activity.supportFragmentManager.backStackEntryCount)
        assertNotNull(activity.supportFragmentManager.findFragmentByTag("TAG_ONE"))
    }

    @Test
    fun popBackStack_withZeroEntries_doesNotCrash() {
        assertEquals(0, activity.supportFragmentManager.backStackEntryCount)
        FragmentNavigator.popBackStack(activity.supportFragmentManager)
        assertEquals(0, activity.supportFragmentManager.backStackEntryCount)
    }

    class TestFragment(val name: String) : Fragment()
}
