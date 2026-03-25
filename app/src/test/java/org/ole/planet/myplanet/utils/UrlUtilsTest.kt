package org.ole.planet.myplanet.utils

import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import org.ole.planet.myplanet.services.SharedPrefManager

class UrlUtilsTest {
    @Test
    fun `getApkVersionUrl uses alternative url`() {
        val mockSpm = mockk<SharedPrefManager>()
        every { mockSpm.isAlternativeUrl() } returns true
        every { mockSpm.getProcessedAlternativeUrl() } returns "http://192.168.1.2:5000"

        val result = UrlUtils.getApkVersionUrl(mockSpm)

        assertEquals("http://192.168.1.2:5000/apkversion", result)
    }

    @Test
    fun `getApkVersionUrl removes suffix db and appends apkversion`() {
        val mockSpm = mockk<SharedPrefManager>()
        every { mockSpm.isAlternativeUrl() } returns false
        every { mockSpm.getCouchdbUrl() } returns "http://192.168.1.1:5000/db"

        val result = UrlUtils.getApkVersionUrl(mockSpm)

        assertEquals("http://192.168.1.1:5000/apkversion", result)
    }

    @Test
    fun `getApkVersionUrl appends apkversion`() {
        val mockSpm = mockk<SharedPrefManager>()
        every { mockSpm.isAlternativeUrl() } returns false
        every { mockSpm.getCouchdbUrl() } returns "http://192.168.1.1:5000"

        val result = UrlUtils.getApkVersionUrl(mockSpm)

        assertEquals("http://192.168.1.1:5000/apkversion", result)
    }

    @Test
    fun `getApkVersionUrl with empty couchdb url returns apkversion`() {
        val mockSpm = mockk<SharedPrefManager>()
        every { mockSpm.isAlternativeUrl() } returns false
        every { mockSpm.getCouchdbUrl() } returns ""

        val result = UrlUtils.getApkVersionUrl(mockSpm)

        assertEquals("/apkversion", result)
    }

    @Test
    fun `getApkVersionUrl with alternative url empty returns apkversion`() {
        val mockSpm = mockk<SharedPrefManager>()
        every { mockSpm.isAlternativeUrl() } returns true
        every { mockSpm.getProcessedAlternativeUrl() } returns ""

        val result = UrlUtils.getApkVersionUrl(mockSpm)

        assertEquals("/apkversion", result)
    }
}
