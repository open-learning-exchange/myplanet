package org.ole.planet.myplanet.datasource

import android.content.SharedPreferences
import com.google.gson.Gson
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.model.MyLife

class MyLifeCacheDataSourceTest {

    private lateinit var mockSharedPreferences: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor
    private lateinit var gson: Gson
    private lateinit var dataSource: MyLifeCacheDataSource

    @Before
    fun setUp() {
        mockSharedPreferences = mockk(relaxed = true)
        mockEditor = mockk(relaxed = true)
        gson = Gson()

        every { mockSharedPreferences.edit() } returns mockEditor
        every { mockEditor.putString(any(), any()) } returns mockEditor

        dataSource = MyLifeCacheDataSource(mockSharedPreferences, gson)
    }

    @Test
    fun read_returnsNull_whenNoJsonFound() {
        every { mockSharedPreferences.getString("myLifeCache_user1", null) } returns null

        val result = dataSource.read("user1")

        assertNull(result)
    }

    @Test
    fun read_returnsParsedList_whenValidJson() {
        val items = listOf(
            CachedMyLifeItem("img1", "Title 1", true, 1),
            CachedMyLifeItem("img2", "Title 2", false, 2)
        )
        val json = gson.toJson(items)
        every { mockSharedPreferences.getString("myLifeCache_user1", null) } returns json

        val result = dataSource.read("user1")

        assertNotNull(result)
        assertEquals(2, result!!.size)
        assertEquals("img1", result[0].imageId)
        assertEquals("Title 1", result[0].title)
        assertTrue(result[0].isVisible)
        assertEquals(1, result[0].weight)
    }

    @Test
    fun read_returnsNull_whenMalformedJson() {
        every { mockSharedPreferences.getString("myLifeCache_user1", null) } returns "corrupt_json_string"

        val result = dataSource.read("user1")

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

        dataSource.write("user1", listOf(item1))

        verify(exactly = 1) { mockEditor.putString(any(), any()) }
        assertEquals("myLifeCache_user1", keySlot.captured)

        val readBack = gson.fromJson(jsonSlot.captured, Array<CachedMyLifeItem>::class.java).toList()
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

        dataSource.write("--", emptyList())

        assertEquals("myLifeCache_--", keySlot.captured)
    }
}
