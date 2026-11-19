package org.ole.planet.myplanet.ui.community

import org.junit.Assert.assertEquals
import org.junit.Test
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.MyTeam

class CommunityServicesLinksTest {

    private fun link(title: String, teamType: String? = null, icon: String? = null) =
        MyTeam(_id = title, title = title, docType = "link", teamType = teamType, icon = icon)

    @Test
    fun `toServicesLinks splits social links from local links and keeps order`() {
        val team = link("team", "sync", "groups")
        val insta = link("insta", "social", "instagram")
        val enterprise = link("enterprise", "local", "work")
        val legacy = link("legacy")

        val sections = listOf(team, insta, enterprise, legacy).toServicesLinks()

        assertEquals(listOf(team, enterprise, legacy), sections.local)
        assertEquals(listOf(insta), sections.social)
    }

    @Test
    fun `linkIconRes maps Planet icon names`() {
        assertEquals(R.drawable.ic_link_team, link("a", icon = "groups").linkIconRes())
        assertEquals(R.drawable.ic_link_enterprise, link("a", icon = "work").linkIconRes())
        assertEquals(R.drawable.ic_link_instagram, link("a", "social", "instagram").linkIconRes())
        assertEquals(R.drawable.ic_link_x, link("a", "social", "x").linkIconRes())
        assertEquals(R.drawable.ic_link_web, link("a", "social", "web").linkIconRes())
    }

    @Test
    fun `linkIconRes falls back like Planet when icon is missing`() {
        assertEquals(R.drawable.ic_link, link("a").linkIconRes())
        assertEquals(R.drawable.ic_link_web, link("a", "social").linkIconRes())
    }
}
