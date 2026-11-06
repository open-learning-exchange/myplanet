package org.ole.planet.myplanet.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceImagesTest {

    @Test
    fun `sampling never decodes below the target size`() {
        assertEquals(1, VoiceImages.sampleSize(1280))
        assertEquals(1, VoiceImages.sampleSize(2559))
        assertEquals(2, VoiceImages.sampleSize(2560))
        assertEquals(2, VoiceImages.sampleSize(4032))
        assertEquals(4, VoiceImages.sampleSize(5120))
    }

    @Test
    fun `scaling brings the longest side to 1280 and never enlarges`() {
        assertEquals(1280f, 3024 * VoiceImages.scaleFor(3024, 2016), 0.5f)
        assertEquals(1280f, 2016 * VoiceImages.scaleFor(1512, 2016), 0.5f)
        assertEquals(1f, VoiceImages.scaleFor(800, 600))
    }
}
