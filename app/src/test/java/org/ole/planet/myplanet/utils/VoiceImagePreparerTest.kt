package org.ole.planet.myplanet.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceImagePreparerTest {

    @Test
    fun `sampling never decodes below the target size`() {
        assertEquals(1, VoiceImagePreparer.sampleSize(1280))
        assertEquals(1, VoiceImagePreparer.sampleSize(2559))
        assertEquals(2, VoiceImagePreparer.sampleSize(2560))
        assertEquals(2, VoiceImagePreparer.sampleSize(4032))
        assertEquals(4, VoiceImagePreparer.sampleSize(5120))
    }

    @Test
    fun `scaling brings the longest side to 1280 and never enlarges`() {
        assertEquals(1280f, 3024 * VoiceImagePreparer.scaleFor(3024, 2016), 0.5f)
        assertEquals(1280f, 2016 * VoiceImagePreparer.scaleFor(1512, 2016), 0.5f)
        assertEquals(1f, VoiceImagePreparer.scaleFor(800, 600))
    }
}
