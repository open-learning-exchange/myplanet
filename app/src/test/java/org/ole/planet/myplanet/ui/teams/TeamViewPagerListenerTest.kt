package org.ole.planet.myplanet.ui.teams

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.callback.OnTeamPageListener
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TeamViewPagerListenerTest {

    class DummyTeamPageFragment : Fragment(), OnTeamPageListener {
        var addCourseCount = 0
        var addDocumentCount = 0

        override fun onAddCourse() {
            addCourseCount++
        }

        override fun onAddDocument() {
            addDocumentCount++
        }

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View {
            return View(requireContext())
        }
    }

    class TestPagerAdapter(
        fragment: Fragment,
        private val fragment0: Fragment,
        private val fragment1: Fragment
    ) : FragmentStateAdapter(fragment) {

        override fun getItemCount(): Int = 2

        override fun createFragment(position: Int): Fragment {
            return if (position == 0) fragment0 else fragment1
        }
    }

    class TestHostFragment : Fragment() {
        lateinit var viewPager2: ViewPager2

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View {
            val context = requireContext()
            val layout = FrameLayout(context)
            viewPager2 = ViewPager2(context).apply { id = View.generateViewId() }
            layout.addView(viewPager2)
            return layout
        }
    }

    @Test
    fun `resolving listener from ViewPager returns active fragment for displayed page`() {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        val hostFragment = TestHostFragment()

        activity.supportFragmentManager.beginTransaction()
            .add(android.R.id.content, hostFragment)
            .commitNow()

        val fragment0 = DummyTeamPageFragment()
        val fragment1 = DummyTeamPageFragment()

        val adapter = TestPagerAdapter(hostFragment, fragment0, fragment1)
        hostFragment.viewPager2.adapter = adapter
        ShadowLooper.idleMainLooper()

        // Position 0 displayed initially
        hostFragment.viewPager2.currentItem = 0
        ShadowLooper.idleMainLooper()

        val itemId0 = adapter.getItemId(0)
        val activeFragment0 = hostFragment.childFragmentManager.findFragmentByTag("f$itemId0")
        assertNotNull(activeFragment0)
        assertTrue(activeFragment0 is OnTeamPageListener)

        (activeFragment0 as OnTeamPageListener).onAddCourse()
        assertEquals(1, fragment0.addCourseCount)
        assertEquals(0, fragment1.addCourseCount)

        // Switch to Position 1
        hostFragment.viewPager2.setCurrentItem(1, false)
        ShadowLooper.idleMainLooper()

        val activePosition = hostFragment.viewPager2.currentItem
        val activeItemId1 = adapter.getItemId(activePosition)
        val activeFragment1 = hostFragment.childFragmentManager.findFragmentByTag("f$activeItemId1")
        assertNotNull(activeFragment1)
        assertTrue(activeFragment1 is OnTeamPageListener)

        (activeFragment1 as OnTeamPageListener).onAddDocument()
        assertEquals(1, fragment0.addCourseCount)
        assertEquals(0, fragment0.addDocumentCount)
        assertEquals(1, fragment1.addDocumentCount)
    }
}
