package org.ole.planet.myplanet.utils

import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class AmountUtilsTest {
    private lateinit var defaultLocale: Locale

    @Before
    fun setup() {
        defaultLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun tearDown() {
        Locale.setDefault(defaultLocale)
    }

    @Test
    fun `parse reads decimals with a dot or a comma`() {
        assertEquals(12.5, AmountUtils.parse("12.50")!!, 0.0)
        assertEquals(12.5, AmountUtils.parse(" 12,50 ")!!, 0.0)
        assertEquals(1500.0, AmountUtils.parse("1500")!!, 0.0)
        assertNull(AmountUtils.parse(""))
    }

    @Test
    fun `toPlainString drops trailing zeros and float noise`() {
        assertEquals("12.5", AmountUtils.toPlainString(12.5))
        assertEquals("1500", AmountUtils.toPlainString(1500.0))
        assertEquals("0.3", AmountUtils.toPlainString(0.1 + 0.2))
    }

    @Test
    fun `format shows whole amounts plainly and cents with two places`() {
        assertEquals("1500", AmountUtils.format(1500.0))
        assertEquals("12.50", AmountUtils.format(12.5))
        assertEquals("-0.75", AmountUtils.format(-0.75))
    }
}
