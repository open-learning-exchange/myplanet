package org.ole.planet.myplanet.ui.dashboard

import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardPluginFragmentTest {

    @Test
    fun `myLifeRouteFor maps known imageIds to expected MyLifeRoute`() {
        assertEquals(MyLifeRoute.SUBMISSIONS, myLifeRouteFor("ic_submissions"))
        assertEquals(MyLifeRoute.REFERENCES, myLifeRouteFor("ic_references"))
        assertEquals(MyLifeRoute.CALENDAR, myLifeRouteFor("ic_calendar"))
        assertEquals(MyLifeRoute.SURVEYS, myLifeRouteFor("ic_my_survey"))
        assertEquals(MyLifeRoute.ACHIEVEMENTS, myLifeRouteFor("my_achievement"))
        assertEquals(MyLifeRoute.PERSONALS, myLifeRouteFor("ic_mypersonals"))
        assertEquals(MyLifeRoute.HEALTH, myLifeRouteFor("ic_myhealth"))
    }

    @Test
    fun `myLifeRouteFor maps null, empty, and unknown imageIds to UNKNOWN`() {
        assertEquals(MyLifeRoute.UNKNOWN, myLifeRouteFor(null))
        assertEquals(MyLifeRoute.UNKNOWN, myLifeRouteFor(""))
        assertEquals(MyLifeRoute.UNKNOWN, myLifeRouteFor("unknown_id"))
    }
}
