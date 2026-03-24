package org.ole.planet.myplanet.services

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.model.User
import org.ole.planet.myplanet.utils.Constants.PREFS_NAME

class SharedPrefManagerTest {

    private lateinit var sharedPrefManager: SharedPrefManager
    private lateinit var mockContext: Context
    private lateinit var mockSharedPreferences: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor

    @Before
    fun setup() {
        mockContext = mockk()
        mockSharedPreferences = mockk()
        mockEditor = mockk(relaxed = true)

        every { mockContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) } returns mockSharedPreferences
        every { mockSharedPreferences.edit() } returns mockEditor

        // Chainable editor mock
        every { mockEditor.putString(any(), any()) } returns mockEditor
        every { mockEditor.putBoolean(any(), any()) } returns mockEditor
        every { mockEditor.putInt(any(), any()) } returns mockEditor
        every { mockEditor.putLong(any(), any()) } returns mockEditor
        every { mockEditor.remove(any()) } returns mockEditor

        sharedPrefManager = SharedPrefManager(mockContext)
    }

    @Test
    fun testGetAndSetSavedUsers() {
        // Testing with empty state first
        every { mockSharedPreferences.getString("savedUsers", null) } returns null
        assertTrue(sharedPrefManager.getSavedUsers().isEmpty())

        // Set users
        val users = listOf(User(name = "Test User"))
        sharedPrefManager.setSavedUsers(users)
        verify { mockEditor.putString("savedUsers", any()) }
        verify { mockEditor.apply() }

        // Retrieve mocked
        val usersJson = "[{\"name\":\"Test User\"}]"
        every { mockSharedPreferences.getString("savedUsers", null) } returns usersJson
        val retrievedUsers = sharedPrefManager.getSavedUsers()
        assertEquals(1, retrievedUsers.size)
        assertEquals("Test User", retrievedUsers[0].name)
    }

    @Test
    fun testGetAndSetRepliedNewsId() {
        every { mockSharedPreferences.getString("repliedNewsId", null) } returns "123"
        assertEquals("123", sharedPrefManager.getRepliedNewsId())

        sharedPrefManager.setRepliedNewsId("456")
        verify { mockEditor.putString("repliedNewsId", "456") }
        verify { mockEditor.apply() }
    }

    @Test
    fun testGetAndSetManualConfig() {
        every { mockSharedPreferences.getBoolean("manualConfig", false) } returns true
        assertTrue(sharedPrefManager.getManualConfig())

        sharedPrefManager.setManualConfig(true)
        verify { mockEditor.putBoolean("manualConfig", true) }
        verify { mockEditor.apply() }
    }

    @Test
    fun testGetAndSetUrlHost() {
        every { mockSharedPreferences.getString("url_Host", "") } returns "example.com"
        assertEquals("example.com", sharedPrefManager.getUrlHost())

        sharedPrefManager.setUrlHost("new.example.com")
        verify { mockEditor.putString("url_Host", "new.example.com") }
        verify { mockEditor.apply() }
    }

    @Test
    fun testGetAndSetUrlPort() {
        every { mockSharedPreferences.getInt("url_Port", 443) } returns 8080
        assertEquals(8080, sharedPrefManager.getUrlPort())

        sharedPrefManager.setUrlPort(8081)
        verify { mockEditor.putInt("url_Port", 8081) }
        verify { mockEditor.apply() }
    }

    @Test
    fun testRawString() {
        every { mockSharedPreferences.getString("test_key", "") } returns "test_val"
        assertEquals("test_val", sharedPrefManager.getRawString("test_key", ""))

        sharedPrefManager.setRawString("test_key", "new_val")
        verify { mockEditor.putString("test_key", "new_val") }
        verify { mockEditor.apply() }
    }
}
