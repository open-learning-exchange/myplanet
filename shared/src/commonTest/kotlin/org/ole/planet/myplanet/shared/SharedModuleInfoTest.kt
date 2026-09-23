package org.ole.planet.myplanet.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class SharedModuleInfoTest {
    @Test
    fun versionIsSet() {
        assertEquals("0.1.0", SharedModuleInfo.VERSION)
    }
}
