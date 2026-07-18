package org.ole.planet.myplanet.model

import org.junit.Assert.assertEquals
import org.junit.Test

class RemovedLogTest {
    @Test
    fun `removed log stores resource removal data`() {
        val log = RemovedLog().apply {
            id = "log1"
            type = "resources"
            userId = "user1"
            docId = "resource1"
        }

        assertEquals("log1", log.id)
        assertEquals("resources", log.type)
        assertEquals("user1", log.userId)
        assertEquals("resource1", log.docId)
    }
}
