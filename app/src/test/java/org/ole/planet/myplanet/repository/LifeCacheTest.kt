package org.ole.planet.myplanet.repository

import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.model.MyLife

class LifeCacheTest {

    private lateinit var mockSharedPreferences: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor
    private lateinit var json: Json
    private lateinit var lifeCache: LifeCache

    @Before
    fun setUp() {
        mockSharedPreferences = mockk(relaxed = true)
        mockEditor = mockk(relaxed = true)
        json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }

        every { mockSharedPreferences.edit() } returns mockEditor
        every { mockEditor.putString(any(), any()) } returns mockEditor

        lifeCache = LifeCache(mockSharedPreferences, json)
    }

    @Test
    fun read_returnsNull_whenNoJsonFound() {
        every { mockSharedPreferences.getString("myLifeCache_user1", null) } returns null

        val result = lifeCache.read("user1")

        assertNull(result)
    }

    @Test
    fun read_returnsParsedList_whenValidJson() {
        val items = listOf(
            CachedMyLifeItem("img1", "Title 1", true, 1),
            CachedMyLifeItem("img2", "Title 2", false, 2)
        )
        val jsonString = json.encodeToString(items)
        every { mockSharedPreferences.getString("myLifeCache_user1", null) } returns jsonString

        val result = lifeCache.read("user1")

        assertNotNull(result)
        assertEquals(2, result!!.size)
        assertEquals("img1", result[0].imageId)
        assertEquals("Title 1", result[0].title)
        assertTrue(result[0].isVisible)
        assertEquals(1, result[0].weight)
    }

    @Test
    fun read_decodesLegacyGsonFormatWithExplicitNulls() {
        val gsonJson = """[{"imageId":null,"title":"t","isVisible":true,"weight":2}]"""
        every { mockSharedPreferences.getString("myLifeCache_user1", null) } returns gsonJson

        val result = lifeCache.read("user1")

        assertNotNull(result)
        assertEquals(1, result!!.size)
        assertNull(result[0].imageId)
        assertEquals("t", result[0].title)
        assertTrue(result[0].isVisible)
        assertEquals(2, result[0].weight)
    }

    @Test
    fun write_andRead_roundTrip() {
        val item1 = MyLife().apply {
            imageId = null
            title = "RoundTrip Title"
            isVisible = false
            weight = 3
        }

        val jsonSlot = slot<String>()
        every { mockEditor.putString(any(), capture(jsonSlot)) } returns mockEditor

        lifeCache.write("user_round_trip", listOf(item1))

        val writtenJson = jsonSlot.captured
        val sharedPrefs = mockk<SharedPreferences>(relaxed = true)
        val cacheForRead = LifeCache(sharedPrefs, json)
        every { sharedPrefs.getString("myLifeCache_user_round_trip", null) } returns writtenJson

        val readBack = cacheForRead.read("user_round_trip")

        assertNotNull(readBack)
        assertEquals(1, readBack!!.size)
        assertNull(readBack[0].imageId)
        assertEquals("RoundTrip Title", readBack[0].title)
        assertFalse(readBack[0].isVisible)
        assertEquals(3, readBack[0].weight)
    }

    @Test
    fun read_returnsNull_whenMalformedJson() {
        every { mockSharedPreferences.getString("myLifeCache_user1", null) } returns "corrupt_json_string"

        val result = lifeCache.read("user1")

        assertNull(result)
    }

    @Test
    fun write_serializesItemsToSharedPreferences() {
        val item1 = MyLife().apply {
            imageId = "img1"
            title = "Title 1"
            isVisible = true
            weight = 10
        }
        val keySlot = slot<String>()
        val jsonSlot = slot<String>()

        every { mockEditor.putString(capture(keySlot), capture(jsonSlot)) } returns mockEditor

        lifeCache.write("user1", listOf(item1))

        verify(exactly = 1) { mockEditor.putString(any(), any()) }
        assertEquals("myLifeCache_user1", keySlot.captured)

        val readBack = json.decodeFromString<List<CachedMyLifeItem>>(jsonSlot.captured)
        assertEquals(1, readBack.size)
        assertEquals("img1", readBack[0].imageId)
        assertEquals("Title 1", readBack[0].title)
        assertTrue(readBack[0].isVisible)
        assertEquals(10, readBack[0].weight)
    }

    @Test
    fun write_handlesFallbackCacheKey() {
        val keySlot = slot<String>()
        every { mockEditor.putString(capture(keySlot), any()) } returns mockEditor

        lifeCache.write("--", emptyList())

        assertEquals("myLifeCache_--", keySlot.captured)
    }

    @Test
    fun read_hitsSharedPreferencesOnce_onMultipleReads() {
        val items = listOf(
            CachedMyLifeItem("img1", "Title 1", true, 1)
        )
        val jsonString = json.encodeToString(items)
        every { mockSharedPreferences.getString("myLifeCache_user1", null) } returns jsonString

        val firstRead = lifeCache.read("user1")
        val secondRead = lifeCache.read("user1")

        assertNotNull(firstRead)
        assertNotNull(secondRead)
        assertEquals(firstRead, secondRead)
        verify(exactly = 1) { mockSharedPreferences.getString("myLifeCache_user1", null) }
    }

    @Test
    fun write_followedByRead_returnsNewValue_withoutSharedPreferencesRead() {
        val item = MyLife().apply {
            imageId = "img_new"
            title = "New Title"
            isVisible = true
            weight = 5
        }

        lifeCache.write("user1", listOf(item))

        val readBack = lifeCache.read("user1")

        assertNotNull(readBack)
        assertEquals(1, readBack!!.size)
        assertEquals("img_new", readBack[0].imageId)
        assertEquals("New Title", readBack[0].title)
        verify(exactly = 0) { mockSharedPreferences.getString("myLifeCache_user1", null) }
    }

    @Test
    fun read_returnsDefensiveCopy_mutationDoesNotAffectSubsequentRead() {
        val items = listOf(
            CachedMyLifeItem("img1", "Original Title", true, 1)
        )
        val jsonString = json.encodeToString(items)
        every { mockSharedPreferences.getString("myLifeCache_user1", null) } returns jsonString

        val firstRead = lifeCache.read("user1")!!
        firstRead[0].title = "Mutated Title"

        val secondRead = lifeCache.read("user1")!!
        assertEquals("Original Title", secondRead[0].title)
    }
}
