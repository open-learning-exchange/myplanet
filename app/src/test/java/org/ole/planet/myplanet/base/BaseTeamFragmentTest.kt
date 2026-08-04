package org.ole.planet.myplanet.base

import android.os.Bundle
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.ole.planet.myplanet.model.News

class BaseTeamFragmentTest {

    class TestTeamFragment : BaseTeamFragment() {
        public override fun shouldQueryTeamFromRealm(): Boolean {
            return super.shouldQueryTeamFromRealm()
        }
        override fun onNewsItemClick(news: News?) {}
        override fun clearImages() {}
    }

    @Test
    fun `shouldQueryTeamFromRealm returns false when arguments are complete`() {
        val fragment = spyk<TestTeamFragment>()
        val mockBundle = mockk<Bundle>()

        every { fragment.requireArguments() } returns mockBundle
        every { mockBundle.containsKey("teamName") } returns true
        every { mockBundle.containsKey("teamType") } returns true
        every { mockBundle.containsKey("teamId") } returns true

        assertFalse(fragment.shouldQueryTeamFromRealm())
    }

    @Test
    fun `shouldQueryTeamFromRealm returns true when arguments are incomplete`() {
        val fragment = spyk<TestTeamFragment>()
        val mockBundle = mockk<Bundle>()

        every { fragment.requireArguments() } returns mockBundle

        // Missing teamId
        every { mockBundle.containsKey("teamName") } returns true
        every { mockBundle.containsKey("teamType") } returns true
        every { mockBundle.containsKey("teamId") } returns false
        assertTrue(fragment.shouldQueryTeamFromRealm())

        // Missing teamType
        every { mockBundle.containsKey("teamName") } returns true
        every { mockBundle.containsKey("teamType") } returns false
        every { mockBundle.containsKey("teamId") } returns true
        assertTrue(fragment.shouldQueryTeamFromRealm())

        // Missing teamName
        every { mockBundle.containsKey("teamName") } returns false
        every { mockBundle.containsKey("teamType") } returns true
        every { mockBundle.containsKey("teamId") } returns true
        assertTrue(fragment.shouldQueryTeamFromRealm())

        // Empty bundle
        every { mockBundle.containsKey("teamName") } returns false
        every { mockBundle.containsKey("teamType") } returns false
        every { mockBundle.containsKey("teamId") } returns false
        assertTrue(fragment.shouldQueryTeamFromRealm())
    }
}
