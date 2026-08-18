package org.ole.planet.myplanet.base

import android.os.Bundle
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.ole.planet.myplanet.model.MyTeam
import org.ole.planet.myplanet.model.News

class BaseTeamFragmentTest {

    class TestTeamFragment : BaseTeamFragment() {
        public override fun shouldQueryTeamLocally(): Boolean {
            return super.shouldQueryTeamLocally()
        }
        public override fun getEffectiveTeamName(): String {
            return super.getEffectiveTeamName()
        }
        public override fun getEffectiveTeamType(): String {
            return super.getEffectiveTeamType()
        }
        override fun onNewsItemClick(news: News?) {}
        override fun clearImages() {}
    }

    @Test
    fun `shouldQueryTeamLocally returns false when arguments are complete`() {
        val fragment = spyk<TestTeamFragment>()
        val mockBundle = mockk<Bundle>()

        every { fragment.requireArguments() } returns mockBundle
        every { mockBundle.containsKey("teamName") } returns true
        every { mockBundle.containsKey("teamType") } returns true
        every { mockBundle.containsKey("teamId") } returns true

        assertFalse(fragment.shouldQueryTeamLocally())
    }

    @Test
    fun `shouldQueryTeamLocally returns true when arguments are incomplete`() {
        val fragment = spyk<TestTeamFragment>()
        val mockBundle = mockk<Bundle>()

        every { fragment.requireArguments() } returns mockBundle

        // Missing teamId
        every { mockBundle.containsKey("teamName") } returns true
        every { mockBundle.containsKey("teamType") } returns true
        every { mockBundle.containsKey("teamId") } returns false
        assertTrue(fragment.shouldQueryTeamLocally())

        // Missing teamType
        every { mockBundle.containsKey("teamName") } returns true
        every { mockBundle.containsKey("teamType") } returns false
        every { mockBundle.containsKey("teamId") } returns true
        assertTrue(fragment.shouldQueryTeamLocally())

        // Missing teamName
        every { mockBundle.containsKey("teamName") } returns false
        every { mockBundle.containsKey("teamType") } returns true
        every { mockBundle.containsKey("teamId") } returns true
        assertTrue(fragment.shouldQueryTeamLocally())

        // Empty bundle
        every { mockBundle.containsKey("teamName") } returns false
        every { mockBundle.containsKey("teamType") } returns false
        every { mockBundle.containsKey("teamId") } returns false
        assertTrue(fragment.shouldQueryTeamLocally())
    }

    @Test
    fun `getEffectiveTeamName falls back to loaded team when argument is blank`() {
        val fragment = spyk<TestTeamFragment>()
        val mockBundle = mockk<Bundle>()

        every { fragment.requireArguments() } returns mockBundle
        every { mockBundle.getString("teamName") } returns ""
        fragment.team = MyTeam(name = "Loaded Team")

        assertEquals("Loaded Team", fragment.getEffectiveTeamName())
    }

    @Test
    fun `getEffectiveTeamType falls back to loaded team when argument is blank`() {
        val fragment = spyk<TestTeamFragment>()
        val mockBundle = mockk<Bundle>()

        every { fragment.requireArguments() } returns mockBundle
        every { mockBundle.getString("teamType") } returns ""
        fragment.team = MyTeam(type = "enterprise")

        assertEquals("enterprise", fragment.getEffectiveTeamType())
    }

    @Test
    fun `getEffectiveTeamName prefers non-blank argument over loaded team`() {
        val fragment = spyk<TestTeamFragment>()
        val mockBundle = mockk<Bundle>()

        every { fragment.requireArguments() } returns mockBundle
        every { mockBundle.getString("teamName") } returns "Argument Team"
        fragment.team = MyTeam(name = "Loaded Team")

        assertEquals("Argument Team", fragment.getEffectiveTeamName())
    }
}
