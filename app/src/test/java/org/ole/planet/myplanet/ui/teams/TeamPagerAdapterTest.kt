package org.ole.planet.myplanet.ui.teams

import android.app.Application
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.widget.ViewPager2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.callback.OnChangedListener
import org.ole.planet.myplanet.callback.OnTeamPageListener
import org.ole.planet.myplanet.ui.teams.TeamPageConfig.CoursesPage
import org.ole.planet.myplanet.ui.teams.TeamPageConfig.DocumentsPage
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class TeamPagerAdapterTest {

    class HostFragment : Fragment() {
        lateinit var viewPager: ViewPager2

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View {
            viewPager = ViewPager2(requireContext()).apply {
                id = View.generateViewId()
            }
            return viewPager
        }
    }

    @Test
    fun resolvingFragmentFromViewPagerReturnsCurrentPageFragmentAndDoesNotSetStatic() {
        val controller = Robolectric.buildActivity(FragmentActivity::class.java)
        try {
            val activity = controller.setup().get()
            val container = FrameLayout(activity).apply { id = View.generateViewId() }
            activity.setContentView(container)

            val hostFragment = HostFragment()
            activity.supportFragmentManager.beginTransaction()
                .add(container.id, hostFragment)
                .commitNow()

            val pages = listOf(CoursesPage, DocumentsPage)
            val adapter = TeamPagerAdapter(
                parentFragment = hostFragment,
                pages = pages,
                teamId = "team_123",
                onMemberChangeListener = OnChangedListener { },
                teamUpdateListener = OnChangedListener { }
            )

            hostFragment.viewPager.adapter = adapter
            ShadowLooper.idleMainLooper()

            // Page 0: CoursesPage
            hostFragment.viewPager.currentItem = 0
            ShadowLooper.idleMainLooper()

            val itemId0 = adapter.getItemId(0)
            val fragment0 = hostFragment.childFragmentManager.findFragmentByTag("f$itemId0")

            assertNotNull(fragment0)
            assertTrue(fragment0 is OnTeamPageListener)

            // Page 1: DocumentsPage
            hostFragment.viewPager.currentItem = 1
            ShadowLooper.idleMainLooper()

            val itemId1 = adapter.getItemId(1)
            val fragment1 = hostFragment.childFragmentManager.findFragmentByTag("f$itemId1")

            assertNotNull(fragment1)
            assertTrue(fragment1 is OnTeamPageListener)

            // Assert they are distinct fragments corresponding to active pages
            assertNotEquals(fragment0, fragment1)

            // Assert global static is NOT set by adapter or page selection
            assertNull(MainApplication.listener)
        } finally {
            controller.destroy()
        }
    }
}
