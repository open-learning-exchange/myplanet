package org.ole.planet.myplanet.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PdfRenderSizeTest {

    @Test
    fun testA4AtTarget1080() {
        val (w, h) = computePdfRenderSize(pageW = 595, pageH = 842, targetW = 1080)
        assertEquals(1080, w)
        assertEquals(1528, h)
    }

    @Test
    fun testA0AtTarget3000() {
        val (w, h) = computePdfRenderSize(pageW = 2384, pageH = 3370, targetW = 3000)
        assertTrue("Width $w must be <= 2048", w <= 2048)
        assertTrue("Height $h must be <= 2048", h <= 2048)

        val expectedH = w.toDouble() * 3370 / 2384
        val diff = abs(h - expectedH)
        assertTrue("Aspect ratio deviation $diff must be <= 1 px", diff <= 1.0)
    }

    @Test
    fun testTargetWZero() {
        val (w, h) = computePdfRenderSize(pageW = 595, pageH = 842, targetW = 0)
        assertEquals(595, w)
        assertEquals(842, h)

        val (wLarge, hLarge) = computePdfRenderSize(pageW = 3000, pageH = 4000, targetW = 0)
        assertTrue("Width $wLarge must be <= 2048", wLarge <= 2048)
        assertTrue("Height $hLarge must be <= 2048", hLarge <= 2048)
    }

    @Test
    fun testExtremePage() {
        val (w, h) = computePdfRenderSize(pageW = 1, pageH = 10000, targetW = 1080)
        assertTrue("Width $w must be >= 1", w >= 1)
        assertTrue("Width $w must be <= 2048", w <= 2048)
        assertTrue("Height $h must be >= 1", h >= 1)
        assertTrue("Height $h must be <= 2048", h <= 2048)

        val (wZero, hZero) = computePdfRenderSize(pageW = 1, pageH = 10000, targetW = 0)
        assertTrue("Width $wZero must be >= 1", wZero >= 1)
        assertTrue("Width $wZero must be <= 2048", wZero <= 2048)
        assertTrue("Height $hZero must be >= 1", hZero >= 1)
        assertTrue("Height $hZero must be <= 2048", hZero <= 2048)
    }
}
