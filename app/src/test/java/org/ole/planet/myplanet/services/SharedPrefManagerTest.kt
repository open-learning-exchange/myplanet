package org.ole.planet.myplanet.services

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
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
        val jsonSlot = slot<String>()
        sharedPrefManager.setSavedUsers(users)
        verify { mockEditor.putString("savedUsers", capture(jsonSlot)) }
        verify { mockEditor.apply() }

        val expectedJson = Gson().toJson(users)
        assertEquals(expectedJson, jsonSlot.captured)

        // Retrieve mocked using the generated JSON
        every { mockSharedPreferences.getString("savedUsers", null) } returns expectedJson
        val retrievedUsers = sharedPrefManager.getSavedUsers()
        assertEquals(1, retrievedUsers.size)
        assertEquals("Test User", retrievedUsers[0].name)
    }

    @Test
    fun testGetSelectedTeamId() {
        // Test non-empty string
        every { mockSharedPreferences.getString("selectedTeamId", "") } returns "team123"
        assertEquals("team123", sharedPrefManager.getSelectedTeamId())

        // Test null return from SharedPreferences
        every { mockSharedPreferences.getString("selectedTeamId", "") } returns null
        assertEquals("", sharedPrefManager.getSelectedTeamId())

        // Test empty string return from SharedPreferences
        every { mockSharedPreferences.getString("selectedTeamId", "") } returns ""
        assertEquals("", sharedPrefManager.getSelectedTeamId())
    }

    @Test
    fun testGetTeamName() {
        // Test non-empty string
        every { mockSharedPreferences.getString("teamName", "") } returns "My Team"
        assertEquals("My Team", sharedPrefManager.getTeamName())

        // Test null return from SharedPreferences
        every { mockSharedPreferences.getString("teamName", "") } returns null
        assertEquals("", sharedPrefManager.getTeamName())

        // Test empty string return from SharedPreferences
        every { mockSharedPreferences.getString("teamName", "") } returns ""
        assertEquals("", sharedPrefManager.getTeamName())
    }

    @Test
    fun testSetPendingLanguageChange() {
        // Test with non-null value
        sharedPrefManager.setPendingLanguageChange("fr")
        verify { mockEditor.putString("pendingLanguageChange", "fr") }
        verify(exactly = 0) { mockEditor.remove("pendingLanguageChange") }
        verify { mockEditor.apply() }

        // Test with null value
        sharedPrefManager.setPendingLanguageChange(null)
        verify { mockEditor.remove("pendingLanguageChange") }
        verify { mockEditor.apply() }
    }

    @Test
    fun testSetSynced() {
        // Since setSynced is private, we test it via the public wrapper setChatHistorySynced.
        // Test false synced
        sharedPrefManager.setChatHistorySynced(false)
        verify { mockEditor.putBoolean("chat_history_synced", false) }
        verify(exactly = 0) { mockEditor.putLong(eq("chat_history_synced_time"), any()) }
        verify { mockEditor.apply() }

        // Test true synced
        sharedPrefManager.setChatHistorySynced(true)
        verify { mockEditor.putBoolean("chat_history_synced", true) }
        verify { mockEditor.putLong(eq("chat_history_synced_time"), any()) }
        verify { mockEditor.apply() }
    }

    @Test
    fun testGetSyncTime() {
        val testTime = 1634567890L
        every { mockSharedPreferences.getLong("chat_history_synced_time", 0L) } returns testTime
        assertEquals(testTime, sharedPrefManager.getSyncTime(SharedPrefManager.SyncKey.CHAT_HISTORY))
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
