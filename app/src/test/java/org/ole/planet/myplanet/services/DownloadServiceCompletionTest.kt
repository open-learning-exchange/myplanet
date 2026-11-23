package org.ole.planet.myplanet.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadServiceCompletionTest {

    @Test
    fun `no notification when nothing was attempted`() {
        assertNull(DownloadService.completionHadErrors(completed = 0, total = 0))
    }

    @Test
    fun `all succeeded reports no errors`() {
        assertEquals(false, DownloadService.completionHadErrors(completed = 5, total = 5))
    }

    @Test
    fun `some failed reports errors`() {
        assertEquals(true, DownloadService.completionHadErrors(completed = 3, total = 5))
    }

    @Test
    fun `all failed still notifies with errors`() {
        assertEquals(true, DownloadService.completionHadErrors(completed = 0, total = 3))
    }
}
