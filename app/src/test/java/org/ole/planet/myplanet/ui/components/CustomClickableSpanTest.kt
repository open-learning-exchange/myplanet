package org.ole.planet.myplanet.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CustomClickableSpanTest {
    @Test
    fun `https and http links are kept as is`() {
        assertEquals("https://www.youtube.com/watch?v=abc", CustomClickableSpan.normalizeWebLink("https://www.youtube.com/watch?v=abc"))
        assertEquals("http://youtu.be/abc", CustomClickableSpan.normalizeWebLink("http://youtu.be/abc"))
    }

    @Test
    fun `scheme-less domain links get https`() {
        assertEquals("https://youtu.be/abc", CustomClickableSpan.normalizeWebLink("youtu.be/abc"))
        assertEquals("https://www.youtube.com/watch?v=abc", CustomClickableSpan.normalizeWebLink("www.youtube.com/watch?v=abc"))
    }

    @Test
    fun `non-web links return null`() {
        assertNull(CustomClickableSpan.normalizeWebLink("mailto:someone@example.org"))
        assertNull(CustomClickableSpan.normalizeWebLink("/resources/file.pdf"))
        assertNull(CustomClickableSpan.normalizeWebLink("#section"))
        assertNull(CustomClickableSpan.normalizeWebLink("javascript:alert(1)"))
    }
}
