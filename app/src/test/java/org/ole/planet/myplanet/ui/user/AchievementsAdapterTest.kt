package org.ole.planet.myplanet.ui.user

import android.os.Build
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.utils.JsonUtils
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.P], application = android.app.Application::class)
class AchievementsAdapterTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var adapter: AchievementsAdapter

    @Before
    fun setUp() {

        val ref1 = JsonObject()
        ref1.addProperty("name", "John Doe")
        ref1.addProperty("relationship", "Friend")
        ref1.addProperty("phone", "123-456-7890")
        ref1.addProperty("email", "john@example.com")

        val ref2 = JsonObject()
        ref2.addProperty("name", "Jane Smith")
        ref2.addProperty("relationship", "Colleague")

        val jsonList = listOf(JsonUtils.gson.toJson(ref1), JsonUtils.gson.toJson(ref2))

        adapter = AchievementsAdapter(jsonList)
    }

    @Test
    fun testSubmitJsonListParsesCorrectly() {
        assertEquals(2, adapter.itemCount)

        val row1 = adapter.currentList[0]
        assertEquals("John Doe", row1.name)
        assertEquals("Friend", row1.relationship)
        assertEquals("123-456-7890", row1.phone)
        assertEquals("john@example.com", row1.email)

        val row2 = adapter.currentList[1]
        assertEquals("Jane Smith", row2.name)
        assertEquals("Colleague", row2.relationship)
        assertEquals("—", row2.phone)
        assertEquals("—", row2.email)
    }

    @Test
    fun testUpdateList() {
        val ref3 = JsonObject()
        ref3.addProperty("name", "Bob")
        ref3.addProperty("relationship", "Brother")
        ref3.addProperty("phone", "555")
        ref3.addProperty("email", "bob@ex.com")

        val jsonList = listOf(JsonUtils.gson.toJson(ref3))

        adapter.submitJsonList(jsonList)

        // Wait for DiffUtil to compute on the background thread and submit to main
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        var attempts = 0
        while (adapter.currentList.size != 1 && attempts < 50) {
            Thread.sleep(10)
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            attempts++
        }

        // Assert directly
        val list = adapter.currentList
        assertEquals(1, list.size)
        val row1 = list[0]
        assertEquals("Bob", row1.name)
        assertEquals("Brother", row1.relationship)
        assertEquals("555", row1.phone)
        assertEquals("bob@ex.com", row1.email)
    }

    @Test
    fun testSubmitInvalidJson() {
        // We create a fresh adapter here, avoiding issues with lists merging unpredictably
        adapter = AchievementsAdapter(listOf())

        val invalidJsonList = listOf("invalid json", "{\"name\": \"Bob\"}")
        adapter.submitJsonList(invalidJsonList)

        // Wait for DiffUtil
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        var attempts = 0
        while (adapter.currentList.size != 2 && attempts < 50) {
            Thread.sleep(10)
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            attempts++
        }

        // Assert directly
        val list = adapter.currentList
        assertEquals(2, list.size)
        val row1 = list[0]
        assertEquals("—", row1.name)
        val row2 = list[1]
        assertEquals("Bob", row2.name)
    }
}
