package org.ole.planet.myplanet.ui.community

import org.junit.Assert.assertEquals
import org.junit.Test

class CommunityServicesRouteTest {

    @Test
    fun `resolve returns ExternalLink for https URL`() {
        val route = "https://example.com/test"
        val result = CommunityServicesRoute.resolve(route)
        assertEquals(CommunityServicesRoute.ExternalLink("https://example.com/test"), result)
    }

    @Test
    fun `resolve returns ExternalLink for http URL`() {
        val route = "http://example.com/test"
        val result = CommunityServicesRoute.resolve(route)
        assertEquals(CommunityServicesRoute.ExternalLink("http://example.com/test"), result)
    }

    @Test
    fun `resolve returns TeamLink with id for teams view route`() {
        val route = "/teams/view/team123"
        val result = CommunityServicesRoute.resolve(route)
        assertEquals(CommunityServicesRoute.TeamLink("team123"), result)
    }

    @Test
    fun `resolve returns Unhandled for route with fewer than 4 segments`() {
        val route = "/teams/view"
        val result = CommunityServicesRoute.resolve(route)
        assertEquals(CommunityServicesRoute.Unhandled, result)
    }

    @Test
    fun `resolve returns Unhandled for empty string`() {
        val route = ""
        val result = CommunityServicesRoute.resolve(route)
        assertEquals(CommunityServicesRoute.Unhandled, result)
    }
}
