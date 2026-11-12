package org.ole.planet.myplanet.ui.chat

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.services.SharedPrefManager

class ChatDetailFragmentTest {

    private lateinit var fragment: ChatDetailFragment
    private lateinit var mockSharedPrefManager: SharedPrefManager

    @Before
    fun setUp() {
        fragment = ChatDetailFragment()
        mockSharedPrefManager = mockk(relaxed = true)
        fragment.sharedPrefManager = mockSharedPrefManager
    }

    private fun invokeGetModelsMap(fragmentInstance: ChatDetailFragment): Map<String, String> {
        val method = ChatDetailFragment::class.java.getDeclaredMethod("getModelsMap")
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(fragmentInstance) as Map<String, String>
    }

    private fun invokeGetCachedProviderAvailability(fragmentInstance: ChatDetailFragment): Map<String, Boolean>? {
        val method = ChatDetailFragment::class.java.getDeclaredMethod("getCachedProviderAvailability")
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(fragmentInstance) as Map<String, Boolean>?
    }

    @Test
    fun getModelsMap_cachesParsedResultForSamePreference() {
        val json = """{"openai":"gpt-4o","ollama":"llama3"}"""
        every { mockSharedPrefManager.getRawString("ai_models") } returns json

        val map1 = invokeGetModelsMap(fragment)
        val map2 = invokeGetModelsMap(fragment)

        assertEquals(2, map1.size)
        assertEquals("gpt-4o", map1["openai"])
        assertEquals("llama3", map1["ollama"])
        assertEquals(map1, map2)

        verify(exactly = 2) { mockSharedPrefManager.getRawString("ai_models") }
    }

    @Test
    fun getModelsMap_invalidatesCacheWhenPreferenceChangesMidSession() {
        val jsonInitial = """{"openai":"gpt-4o"}"""
        val jsonUpdated = """{"openai":"gpt-4o","claude":"claude-3-5-sonnet"}"""

        every { mockSharedPrefManager.getRawString("ai_models") } returns jsonInitial
        val mapInitial = invokeGetModelsMap(fragment)
        assertEquals(1, mapInitial.size)
        assertEquals("gpt-4o", mapInitial["openai"])

        every { mockSharedPrefManager.getRawString("ai_models") } returns jsonUpdated
        val mapUpdated = invokeGetModelsMap(fragment)
        assertEquals(2, mapUpdated.size)
        assertEquals("gpt-4o", mapUpdated["openai"])
        assertEquals("claude-3-5-sonnet", mapUpdated["claude"])
    }

    @Test
    fun getCachedProviderAvailability_usesModelsMapAndReflectsUpdates() {
        val jsonInitial = """{"openai":"gpt-4o","ollama":"llama3"}"""
        every { mockSharedPrefManager.getRawString("ai_models") } returns jsonInitial

        val availabilityInitial = invokeGetCachedProviderAvailability(fragment)
        assertEquals(mapOf("openai" to true, "ollama" to true), availabilityInitial)

        val jsonUpdated = """{"openai":"gpt-4o","google":"gemini-1.5-pro"}"""
        every { mockSharedPrefManager.getRawString("ai_models") } returns jsonUpdated

        val availabilityUpdated = invokeGetCachedProviderAvailability(fragment)
        assertEquals(mapOf("openai" to true, "google" to true), availabilityUpdated)
    }

    @Test
    fun getCachedProviderAvailability_returnsNullWhenEmpty() {
        every { mockSharedPrefManager.getRawString("ai_models") } returns ""

        val availability = invokeGetCachedProviderAvailability(fragment)
        assertNull(availability)
    }
}
