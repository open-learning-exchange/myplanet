package org.ole.planet.myplanet.services

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.di.NetworkModule
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

        sharedPrefManager = SharedPrefManager(mockContext, NetworkModule.provideGson())
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

        val expectedJson = NetworkModule.provideGson().toJson(users)
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
    fun testGetAndSetLoggedIn() {
        every { mockSharedPreferences.getBoolean(SharedPrefManager.KEY_LOGIN, false) } returns true
        assertTrue(sharedPrefManager.isLoggedIn())

        every { mockSharedPreferences.getBoolean(SharedPrefManager.KEY_LOGIN, false) } returns false
        assertEquals(false, sharedPrefManager.isLoggedIn())

        sharedPrefManager.setLoggedIn(true)
        verify { mockEditor.putBoolean(SharedPrefManager.KEY_LOGIN, true) }
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

    @Test
    fun testClearPreferences() {
        mockkStatic(PreferenceManager::class)
        val mockDefaultSharedPreferences: SharedPreferences = mockk()
        val mockDefaultEditor: SharedPreferences.Editor = mockk(relaxed = true)

        every { PreferenceManager.getDefaultSharedPreferences(mockContext) } returns mockDefaultSharedPreferences
        every { mockDefaultSharedPreferences.edit() } returns mockDefaultEditor
        every { mockDefaultEditor.clear() } returns mockDefaultEditor

        // First launch and manual config boolean mocks
        every { mockSharedPreferences.getBoolean(SharedPrefManager.FIRST_LAUNCH, false) } returns true
        every { mockSharedPreferences.getBoolean(SharedPrefManager.MANUAL_CONFIG, false) } returns false

        every { mockEditor.clear() } returns mockEditor
        every { mockEditor.commit() } returns true

        sharedPrefManager.clearPreferences()

        verify { mockEditor.clear() }
        verify { mockEditor.putBoolean(SharedPrefManager.FIRST_LAUNCH, true) }
        verify { mockEditor.putBoolean(SharedPrefManager.MANUAL_CONFIG, false) }
        verify { mockEditor.commit() }

        verify { mockDefaultEditor.clear() }
    }

    @Test
    fun testRemoveKey() {
        sharedPrefManager.removeKey("some_key")
        verify { mockEditor.remove("some_key") }
        verify { mockEditor.apply() }
    }

    @Test
    fun testGetAndSetNewLoginUsername() {
        mockkObject(org.ole.planet.myplanet.utils.SecurePrefs)
        every { org.ole.planet.myplanet.utils.SecurePrefs.encryptString(any(), "test_user") } returns "encrypted_user"
        every { org.ole.planet.myplanet.utils.SecurePrefs.decryptString(any(), "encrypted_user") } returns "test_user"

        // Set
        sharedPrefManager.setNewLoginUsername("test_user")
        verify { mockEditor.putString("new_login_username", "encrypted_user") }

        // Get
        every { mockSharedPreferences.getString("new_login_username", null) } returns "encrypted_user"
        assertEquals("test_user", sharedPrefManager.getNewLoginUsername())

        // Set null
        sharedPrefManager.setNewLoginUsername(null)
        verify { mockEditor.remove("new_login_username") }

        unmockkObject(org.ole.planet.myplanet.utils.SecurePrefs)
    }

    @Test
    fun testGetAndSetNewLoginPassword() {
        mockkObject(org.ole.planet.myplanet.utils.SecurePrefs)
        every { org.ole.planet.myplanet.utils.SecurePrefs.encryptString(any(), "test_pass") } returns "encrypted_pass"
        every { org.ole.planet.myplanet.utils.SecurePrefs.decryptString(any(), "encrypted_pass") } returns "test_pass"

        // Set
        sharedPrefManager.setNewLoginPassword("test_pass")
        verify { mockEditor.putString("new_login_password", "encrypted_pass") }

        // Get
        every { mockSharedPreferences.getString("new_login_password", null) } returns "encrypted_pass"
        assertEquals("test_pass", sharedPrefManager.getNewLoginPassword())

        // Set null
        sharedPrefManager.setNewLoginPassword(null)
        verify { mockEditor.remove("new_login_password") }

        unmockkObject(org.ole.planet.myplanet.utils.SecurePrefs)
    }

}
