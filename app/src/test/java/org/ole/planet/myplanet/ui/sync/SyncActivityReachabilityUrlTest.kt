package org.ole.planet.myplanet.ui.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncActivityReachabilityUrlTest {

    @Test
    fun `alternative url keeps a host that contains db`() {
        assertEquals(
            "https://satellite:pin@dbhost.example.org/db/_all_dbs",
            SyncActivity.reachabilityUrl("https://satellite:pin@dbhost.example.org/db", isAlternativeUrl = true)
        )
    }

    @Test
    fun `alternative url without db gets db appended once`() {
        assertEquals(
            "https://planet.example.org/db/_all_dbs",
            SyncActivity.reachabilityUrl("https://planet.example.org/", isAlternativeUrl = true)
        )
    }

    @Test
    fun `primary url is used as is`() {
        assertEquals(
            "http://192.168.0.10:2200/_all_dbs",
            SyncActivity.reachabilityUrl("http://192.168.0.10:2200", isAlternativeUrl = false)
        )
    }
}
