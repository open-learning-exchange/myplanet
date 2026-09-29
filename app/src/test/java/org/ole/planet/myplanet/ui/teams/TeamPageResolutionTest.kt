package org.ole.planet.myplanet.ui.teams

import android.os.Bundle
import androidx.fragment.app.Fragment
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.ole.planet.myplanet.callback.OnTeamPageListener

class TeamPageResolutionTest {

    private class DummyTeamPageFragment(
        val fragmentType: String?
    ) : Fragment(), OnTeamPageListener {
        var addDocumentCalled = false
        var addCourseCalled = false

        override fun getArguments(): Bundle? {
            if (fragmentType == null) return null
            val bundle = mockk<Bundle>()
            every { bundle.getString("fragmentType") } returns fragmentType
            return bundle
        }

        override fun onAddDocument() {
            addDocumentCalled = true
        }

        override fun onAddCourse() {
            addCourseCalled = true
        }
    }

    private class NonTeamPageFragment : Fragment()

    private fun resolvePageListener(fragments: List<Fragment>, targetPageId: String): OnTeamPageListener? {
        return fragments.firstOrNull {
            it is OnTeamPageListener && it.arguments?.getString("fragmentType") == targetPageId
        } as? OnTeamPageListener
    }

    @Test
    fun `resolves matching OnTeamPageListener fragment by fragmentType`() {
        val courseFragment = DummyTeamPageFragment("courses")
        val resourceFragment = DummyTeamPageFragment("resources")
        val otherFragment = NonTeamPageFragment()

        val fragments = listOf(otherFragment, courseFragment, resourceFragment)

        val resolved = resolvePageListener(fragments, "resources")
        assertEquals(resourceFragment, resolved)

        resolved?.onAddDocument()
        assertTrue(resourceFragment.addDocumentCalled)
    }

    @Test
    fun `returns null when no matching OnTeamPageListener fragment exists`() {
        val courseFragment = DummyTeamPageFragment("courses")
        val nonTeamFragment = NonTeamPageFragment()

        val fragments = listOf(nonTeamFragment, courseFragment)

        val resolved = resolvePageListener(fragments, "documents")
        assertNull(resolved)
    }

    @Test
    fun `returns null and does not throw when fragment list is empty`() {
        val fragments = emptyList<Fragment>()

        val resolved = resolvePageListener(fragments, "courses")
        assertNull(resolved)
    }
}
