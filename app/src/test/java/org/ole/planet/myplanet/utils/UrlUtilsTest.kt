package org.ole.planet.myplanet.utils

import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import org.ole.planet.myplanet.services.SharedPrefManager

class UrlUtilsTest {

    @Test
    fun `getUpdateUrl should append versions to base url when not alternative`() {
        val spm = mockk<SharedPrefManager>()
        every { spm.isAlternativeUrl() } returns false
        every { spm.getCouchdbUrl() } returns "http://example.com"

        val result = UrlUtils.getUpdateUrl(spm)

        assertEquals("http://example.com/versions", result)
    }

    @Test
    fun `getUpdateUrl should append versions to base url when alternative`() {
        val spm = mockk<SharedPrefManager>()
        every { spm.isAlternativeUrl() } returns true
        every { spm.getProcessedAlternativeUrl() } returns "http://alt.example.com"

        val result = UrlUtils.getUpdateUrl(spm)

        assertEquals("http://alt.example.com/versions", result)
    }

    @Test
    fun `getUpdateUrl should remove trailing db before appending versions`() {
        val spm = mockk<SharedPrefManager>()
        every { spm.isAlternativeUrl() } returns false
        every { spm.getCouchdbUrl() } returns "http://example.com/db"

        val result = UrlUtils.getUpdateUrl(spm)

        assertEquals("http://example.com/versions", result)
    }

    @Test
    fun `getUpdateUrl should remove trailing db from alternative url before appending versions`() {
        val spm = mockk<SharedPrefManager>()
        every { spm.isAlternativeUrl() } returns true
        every { spm.getProcessedAlternativeUrl() } returns "http://alt.example.com/db"

        val result = UrlUtils.getUpdateUrl(spm)

        assertEquals("http://alt.example.com/versions", result)
    }
}
