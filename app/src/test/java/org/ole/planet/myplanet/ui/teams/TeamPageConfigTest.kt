package org.ole.planet.myplanet.ui.teams

import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.callback.OnTeamPageListener
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TeamPageConfigTest {

    class TeamPageFragment : Fragment(), OnTeamPageListener {
        var addDocumentCalled = false
        var addCourseCalled = false

        override fun onAddDocument() {
            addDocumentCalled = true
        }

        override fun onAddCourse() {
            addCourseCalled = true
        }
    }

    class PlainFragment : Fragment()

    private fun teamPageFragment(fragmentType: String?) = TeamPageFragment().apply {
        if (fragmentType != null) {
            arguments = bundleOf(FRAGMENT_TYPE_KEY to fragmentType)
        }
    }

    @Test
    fun `resolves matching OnTeamPageListener fragment by fragmentType`() {
        val coursesFragment = teamPageFragment(TeamPageConfig.CoursesPage.id)
        val resourcesFragment = teamPageFragment(TeamPageConfig.ResourcesPage.id)
        val fragments = listOf(PlainFragment(), coursesFragment, resourcesFragment)

        val resolved = resolveTeamPageListener(fragments, TeamPageConfig.ResourcesPage.id)

        assertSame(resourcesFragment, resolved)
        resolved?.onAddDocument()
        assertTrue(resourcesFragment.addDocumentCalled)
    }

    @Test
    fun `returns null when no fragment carries the target fragmentType`() {
        val fragments = listOf(PlainFragment(), teamPageFragment(TeamPageConfig.CoursesPage.id))

        assertNull(resolveTeamPageListener(fragments, TeamPageConfig.DocumentsPage.id))
    }

    @Test
    fun `ignores team page fragments without arguments`() {
        val fragments = listOf(teamPageFragment(null))

        assertNull(resolveTeamPageListener(fragments, TeamPageConfig.CoursesPage.id))
    }

    @Test
    fun `returns null when fragment list is empty`() {
        assertNull(resolveTeamPageListener(emptyList(), TeamPageConfig.CoursesPage.id))
    }
}
