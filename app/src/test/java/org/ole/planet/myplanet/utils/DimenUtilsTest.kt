package org.ole.planet.myplanet.utils

import android.content.res.Resources
import android.util.DisplayMetrics
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class DimenUtilsTest {

    @Before
    fun setUp() {
        mockkStatic(Resources::class)
    }

    @After
    fun tearDown() {
        unmockkStatic(Resources::class)
    }

    @Test
    fun testDpToPx() {
        val mockResources = mockk<Resources>()
        val mockDisplayMetrics = DisplayMetrics()
        mockDisplayMetrics.xdpi = 320f // DisplayMetrics.DENSITY_DEFAULT is 160

        every { Resources.getSystem() } returns mockResources
        every { mockResources.displayMetrics } returns mockDisplayMetrics

        val result = DimenUtils.dpToPx(10)

        assertEquals(20, result)
    }

    @Test
    fun testDpToPx_withZero() {
        val mockResources = mockk<Resources>()
        val mockDisplayMetrics = DisplayMetrics()
        mockDisplayMetrics.xdpi = 320f

        every { Resources.getSystem() } returns mockResources
        every { mockResources.displayMetrics } returns mockDisplayMetrics

        val result = DimenUtils.dpToPx(0)

        assertEquals(0, result)
    }

    @Test
    fun testDpToPx_withDifferentDensity() {
        val mockResources = mockk<Resources>()
        val mockDisplayMetrics = DisplayMetrics()
        mockDisplayMetrics.xdpi = 240f // 240 / 160 = 1.5

        every { Resources.getSystem() } returns mockResources
        every { mockResources.displayMetrics } returns mockDisplayMetrics

        val result = DimenUtils.dpToPx(10) // 10 * 1.5 = 15

        assertEquals(15, result)
    }
}
