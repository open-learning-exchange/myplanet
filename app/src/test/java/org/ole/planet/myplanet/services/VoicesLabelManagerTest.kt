package org.ole.planet.myplanet.services

import org.junit.Assert.assertEquals
import org.junit.Test

class VoicesLabelManagerTest {

    @Test
    fun testFormatLabelValue_singleWord() {
        assertEquals("Hello", VoicesLabelManager.formatLabelValue("hello"))
        assertEquals("Hello", VoicesLabelManager.formatLabelValue("HELLO"))
    }

    @Test
    fun testFormatLabelValue_normalizesCase() {
        assertEquals("Hello World", VoicesLabelManager.formatLabelValue("HELLO WORLD"))
        assertEquals("Hello World", VoicesLabelManager.formatLabelValue("hello world"))
        assertEquals("Hello World", VoicesLabelManager.formatLabelValue("hElLo wOrLd"))
    }

    @Test
    fun testFormatLabelValue_replacesUnderscoresAndDashes() {
        assertEquals("Hello World", VoicesLabelManager.formatLabelValue("hello_world"))
        assertEquals("Hello World", VoicesLabelManager.formatLabelValue("hello-world"))
        assertEquals("Hello World Test", VoicesLabelManager.formatLabelValue("hello_world-test"))
    }

    @Test
    fun testFormatLabelValue_handlesMultipleSpaces() {
        assertEquals("Hello World", VoicesLabelManager.formatLabelValue("hello   world"))
        assertEquals("Hello World", VoicesLabelManager.formatLabelValue("  hello    world  "))
        assertEquals("Hello World", VoicesLabelManager.formatLabelValue("hello_ _world"))
    }

    @Test
    fun testFormatLabelValue_handlesNonAlphaOnlyInputs() {
        assertEquals("", VoicesLabelManager.formatLabelValue(""))
        assertEquals("", VoicesLabelManager.formatLabelValue("   "))
        assertEquals("", VoicesLabelManager.formatLabelValue(" _ "))
        assertEquals("", VoicesLabelManager.formatLabelValue("-"))
        assertEquals("", VoicesLabelManager.formatLabelValue("_-"))
    }
}
