package org.ole.planet.myplanet.services

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.model.User
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33], application = Application::class)
class SharedPrefManagerTest {
    
    private lateinit var spm: SharedPrefManager

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("OLE_PLANET", Context.MODE_PRIVATE).edit().clear().commit()
        spm = SharedPrefManager(context)
    }

    // -------------------------------------------------------------------------
    // Saved users
    // -------------------------------------------------------------------------

    @Test
    fun `getSavedUsers returns empty list by default`() {
        assertTrue(spm.getSavedUsers().isEmpty())
    }

    @Test
    fun `setSavedUsers and getSavedUsers round-trip`() {
        val users = listOf(User("Alice Full", "alice"))
        spm.setSavedUsers(users)
        val result = spm.getSavedUsers()
        assertEquals(1, result.size)
        assertEquals("alice", result[0].name)
        assertEquals("Alice Full", result[0].fullName)
    }

    @Test
    fun `setSavedUsers overwrites previous list`() {
        spm.setSavedUsers(listOf(User("Alice", "alice")))
        spm.setSavedUsers(listOf(User("Bob", "bob"), User("Carol", "carol")))
        assertEquals(2, spm.getSavedUsers().size)
    }

    @Test
    fun `setSavedUsers with empty list results in empty getSavedUsers`() {
        spm.setSavedUsers(listOf(User("Alice", "alice")))
        spm.setSavedUsers(emptyList())
        assertTrue(spm.getSavedUsers().isEmpty())
    }

    // -------------------------------------------------------------------------
    // RepliedNewsId
    // -------------------------------------------------------------------------

    @Test
    fun `getRepliedNewsId returns null by default`() {
        assertNull(spm.getRepliedNewsId())
    }

    @Test
    fun `setRepliedNewsId persists value`() {
        spm.setRepliedNewsId("news123")
        assertEquals("news123", spm.getRepliedNewsId())
    }

    @Test
    fun `setRepliedNewsId null persists null`() {
        spm.setRepliedNewsId("news123")
        spm.setRepliedNewsId(null)
        assertNull(spm.getRepliedNewsId())
    }

    // -------------------------------------------------------------------------
    // ManualConfig
    // -------------------------------------------------------------------------

    @Test
    fun `getManualConfig returns false by default`() {
        assertFalse(spm.getManualConfig())
    }

    @Test
    fun `setManualConfig true persists`() {
        spm.setManualConfig(true)
        assertTrue(spm.getManualConfig())
    }

    @Test
    fun `setManualConfig false persists`() {
        spm.setManualConfig(true)
        spm.setManualConfig(false)
        assertFalse(spm.getManualConfig())
    }

    // -------------------------------------------------------------------------
    // SelectedTeamId
    // -------------------------------------------------------------------------

    @Test
    fun `getSelectedTeamId returns empty string by default`() {
        assertEquals("", spm.getSelectedTeamId())
    }

    @Test
    fun `setSelectedTeamId persists value`() {
        spm.setSelectedTeamId("team-abc")
        assertEquals("team-abc", spm.getSelectedTeamId())
    }

    // -------------------------------------------------------------------------
    // ServerUrl / ServerPin / ServerProtocol
    // -------------------------------------------------------------------------

    @Test
    fun `getServerUrl returns empty string by default`() {
        assertEquals("", spm.getServerUrl())
    }

    @Test
    fun `setServerUrl persists value`() {
        spm.setServerUrl("https://example.com")
        assertEquals("https://example.com", spm.getServerUrl())
    }

    @Test
    fun `getServerPin returns empty string by default`() {
        assertEquals("", spm.getServerPin())
    }

    @Test
    fun `setServerPin persists value`() {
        spm.setServerPin("1234")
        assertEquals("1234", spm.getServerPin())
    }

    @Test
    fun `getServerProtocol returns empty string by default`() {
        assertEquals("", spm.getServerProtocol())
    }

    @Test
    fun `setServerProtocol persists value`() {
        spm.setServerProtocol("https")
        assertEquals("https", spm.getServerProtocol())
    }

    // -------------------------------------------------------------------------
    // UrlPort (default is 443)
    // -------------------------------------------------------------------------

    @Test
    fun `getUrlPort returns 443 by default`() {
        assertEquals(443, spm.getUrlPort())
    }

    @Test
    fun `setUrlPort persists value`() {
        spm.setUrlPort(8080)
        assertEquals(8080, spm.getUrlPort())
    }

    // -------------------------------------------------------------------------
    // Sync flags — all 8 SyncKey entries
    // -------------------------------------------------------------------------

    @Test
    fun `all sync flags return false by default`() {
        assertFalse(spm.isChatHistorySynced())
        assertFalse(spm.isTeamsSynced())
        assertFalse(spm.isFeedbackSynced())
        assertFalse(spm.isAchievementsSynced())
        assertFalse(spm.isHealthSynced())
        assertFalse(spm.isCoursesSynced())
        assertFalse(spm.isResourcesSynced())
        assertFalse(spm.isExamsSynced())
    }

    @Test
    fun `setChatHistorySynced true persists flag`() {
        spm.setChatHistorySynced(true)
        assertTrue(spm.isChatHistorySynced())
    }

    @Test
    fun `setChatHistorySynced true records timestamp`() {
        val before = System.currentTimeMillis()
        spm.setChatHistorySynced(true)
        assertTrue(spm.getSyncTime(SharedPrefManager.SyncKey.CHAT_HISTORY) >= before)
    }

    @Test
    fun `setChatHistorySynced false clears flag but preserves timestamp`() {
        spm.setChatHistorySynced(true)
        val timestampAfterTrue = spm.getSyncTime(SharedPrefManager.SyncKey.CHAT_HISTORY)
        spm.setChatHistorySynced(false)
        assertFalse(spm.isChatHistorySynced())
        assertEquals(timestampAfterTrue, spm.getSyncTime(SharedPrefManager.SyncKey.CHAT_HISTORY))
    }

    @Test
    fun `setTeamsSynced persists correctly`() {
        spm.setTeamsSynced(true)
        assertTrue(spm.isTeamsSynced())
    }

    @Test
    fun `setFeedbackSynced persists correctly`() {
        spm.setFeedbackSynced(true)
        assertTrue(spm.isFeedbackSynced())
    }

    @Test
    fun `setAchievementsSynced persists correctly`() {
        spm.setAchievementsSynced(true)
        assertTrue(spm.isAchievementsSynced())
    }

    @Test
    fun `setHealthSynced persists correctly`() {
        spm.setHealthSynced(true)
        assertTrue(spm.isHealthSynced())
    }

    @Test
    fun `setCoursesSynced persists correctly`() {
        spm.setCoursesSynced(true)
        assertTrue(spm.isCoursesSynced())
    }

    @Test
    fun `setResourcesSynced persists correctly`() {
        spm.setResourcesSynced(true)
        assertTrue(spm.isResourcesSynced())
    }

    @Test
    fun `setExamsSynced persists correctly`() {
        spm.setExamsSynced(true)
        assertTrue(spm.isExamsSynced())
    }

    @Test
    fun `setIsExamsSynced alias matches isExamsSynced`() {
        spm.setIsExamsSynced(true)
        assertTrue(spm.getIsExamsSynced())
        assertTrue(spm.isExamsSynced())
    }

    // -------------------------------------------------------------------------
    // getSyncTime
    // -------------------------------------------------------------------------

    @Test
    fun `getSyncTime returns 0 before any sync`() {
        assertEquals(0L, spm.getSyncTime(SharedPrefManager.SyncKey.COURSES))
    }

    @Test
    fun `getSyncTime returns positive value after sync set to true`() {
        spm.setCoursesSynced(true)
        assertTrue(spm.getSyncTime(SharedPrefManager.SyncKey.COURSES) > 0L)
    }

    @Test
    fun `each SyncKey stores its own timestamp independently`() {
        spm.setCoursesSynced(true)
        assertEquals(0L, spm.getSyncTime(SharedPrefManager.SyncKey.TEAMS))
        assertTrue(spm.getSyncTime(SharedPrefManager.SyncKey.COURSES) > 0L)
    }

    // -------------------------------------------------------------------------
    // AutoSync / FastSync / UseImprovedSync
    // -------------------------------------------------------------------------

    @Test
    fun `getAutoSync returns true by default`() {
        assertTrue(spm.getAutoSync())
    }

    @Test
    fun `setAutoSync false persists`() {
        spm.setAutoSync(false)
        assertFalse(spm.getAutoSync())
    }

    @Test
    fun `getFastSync returns false by default`() {
        assertFalse(spm.getFastSync())
    }

    @Test
    fun `setFastSync true persists`() {
        spm.setFastSync(true)
        assertTrue(spm.getFastSync())
    }

    @Test
    fun `getUseImprovedSync returns false by default`() {
        assertFalse(spm.getUseImprovedSync())
    }

    @Test
    fun `setUseImprovedSync true persists`() {
        spm.setUseImprovedSync(true)
        assertTrue(spm.getUseImprovedSync())
    }

    // -------------------------------------------------------------------------
    // AutoSyncInterval / AutoSyncPosition
    // -------------------------------------------------------------------------

    @Test
    fun `getAutoSyncInterval returns 3600 by default`() {
        assertEquals(60 * 60, spm.getAutoSyncInterval())
    }

    @Test
    fun `setAutoSyncInterval persists value`() {
        spm.setAutoSyncInterval(1800)
        assertEquals(1800, spm.getAutoSyncInterval())
    }

    @Test
    fun `getAutoSyncPosition returns 0 by default`() {
        assertEquals(0, spm.getAutoSyncPosition())
    }

    @Test
    fun `setAutoSyncPosition persists value`() {
        spm.setAutoSyncPosition(3)
        assertEquals(3, spm.getAutoSyncPosition())
    }

    // -------------------------------------------------------------------------
    // FirstRun / FirstLaunch
    // -------------------------------------------------------------------------

    @Test
    fun `getFirstRun returns true by default`() {
        assertTrue(spm.getFirstRun())
    }

    @Test
    fun `setFirstRun false persists`() {
        spm.setFirstRun(false)
        assertFalse(spm.getFirstRun())
    }

    @Test
    fun `getFirstLaunch returns false by default`() {
        assertFalse(spm.getFirstLaunch())
    }

    @Test
    fun `setFirstLaunch true persists`() {
        spm.setFirstLaunch(true)
        assertTrue(spm.getFirstLaunch())
    }

    // -------------------------------------------------------------------------
    // LastSync / LastUsageUploaded
    // -------------------------------------------------------------------------

    @Test
    fun `getLastSync returns 0 by default`() {
        assertEquals(0L, spm.getLastSync())
    }

    @Test
    fun `setLastSync persists value`() {
        spm.setLastSync(9_999_999L)
        assertEquals(9_999_999L, spm.getLastSync())
    }

    @Test
    fun `getLastUsageUploaded returns 0 by default`() {
        assertEquals(0L, spm.getLastUsageUploaded())
    }

    @Test
    fun `setLastUsageUploaded persists value`() {
        spm.setLastUsageUploaded(1_234_567L)
        assertEquals(1_234_567L, spm.getLastUsageUploaded())
    }

    // -------------------------------------------------------------------------
    // LastWifiId / LastWifiSsid
    // -------------------------------------------------------------------------

    @Test
    fun `getLastWifiId returns -1 by default`() {
        assertEquals(-1, spm.getLastWifiId())
    }

    @Test
    fun `setLastWifiId persists value`() {
        spm.setLastWifiId(42)
        assertEquals(42, spm.getLastWifiId())
    }

    @Test
    fun `getLastWifiSsid returns null by default`() {
        assertNull(spm.getLastWifiSsid())
    }

    @Test
    fun `setLastWifiSsid persists value`() {
        spm.setLastWifiSsid("MyNetwork")
        assertEquals("MyNetwork", spm.getLastWifiSsid())
    }

    // -------------------------------------------------------------------------
    // PendingLanguageChange
    // -------------------------------------------------------------------------

    @Test
    fun `getPendingLanguageChange returns null by default`() {
        assertNull(spm.getPendingLanguageChange())
    }

    @Test
    fun `setPendingLanguageChange persists value`() {
        spm.setPendingLanguageChange("fr")
        assertEquals("fr", spm.getPendingLanguageChange())
    }

    @Test
    fun `setPendingLanguageChange null removes the key`() {
        spm.setPendingLanguageChange("fr")
        spm.setPendingLanguageChange(null)
        assertNull(spm.getPendingLanguageChange())
    }

    // -------------------------------------------------------------------------
    // PinnedServerUrl / SwitchCloudUrl / IsAlternativeUrl
    // -------------------------------------------------------------------------

    @Test
    fun `getPinnedServerUrl returns null by default`() {
        assertNull(spm.getPinnedServerUrl())
    }

    @Test
    fun `setPinnedServerUrl persists value`() {
        spm.setPinnedServerUrl("https://pinned.example.com")
        assertEquals("https://pinned.example.com", spm.getPinnedServerUrl())
    }

    @Test
    fun `getSwitchCloudUrl returns false by default`() {
        assertFalse(spm.getSwitchCloudUrl())
    }

    @Test
    fun `setSwitchCloudUrl true persists`() {
        spm.setSwitchCloudUrl(true)
        assertTrue(spm.getSwitchCloudUrl())
    }

    @Test
    fun `isAlternativeUrl returns false by default`() {
        assertFalse(spm.isAlternativeUrl())
    }

    @Test
    fun `setIsAlternativeUrl true persists`() {
        spm.setIsAlternativeUrl(true)
        assertTrue(spm.isAlternativeUrl())
    }

    // -------------------------------------------------------------------------
    // LoggedIn / NotificationShown / HasShownCongrats
    // -------------------------------------------------------------------------

    @Test
    fun `isLoggedIn returns false by default`() {
        assertFalse(spm.isLoggedIn())
    }

    @Test
    fun `setLoggedIn true persists`() {
        spm.setLoggedIn(true)
        assertTrue(spm.isLoggedIn())
    }

    @Test
    fun `isNotificationShown returns false by default`() {
        assertFalse(spm.isNotificationShown())
    }

    @Test
    fun `setNotificationShown true persists`() {
        spm.setNotificationShown(true)
        assertTrue(spm.isNotificationShown())
    }

    @Test
    fun `getHasShownCongrats returns false by default`() {
        assertFalse(spm.getHasShownCongrats())
    }

    @Test
    fun `setHasShownCongrats true persists`() {
        spm.setHasShownCongrats(true)
        assertTrue(spm.getHasShownCongrats())
    }

    // -------------------------------------------------------------------------
    // UserId / UserName / PlanetCode / ParentCode / CommunityName
    // -------------------------------------------------------------------------

    @Test
    fun `getUserId returns empty string by default`() {
        assertEquals("", spm.getUserId())
    }

    @Test
    fun `setUserId persists value`() {
        spm.setUserId("user-123")
        assertEquals("user-123", spm.getUserId())
    }

    @Test
    fun `getUserName returns empty string by default`() {
        assertEquals("", spm.getUserName())
    }

    @Test
    fun `setUserName persists value`() {
        spm.setUserName("jdoe")
        assertEquals("jdoe", spm.getUserName())
    }

    @Test
    fun `getPlanetCode returns empty string by default`() {
        assertEquals("", spm.getPlanetCode())
    }

    @Test
    fun `setPlanetCode persists value`() {
        spm.setPlanetCode("learning")
        assertEquals("learning", spm.getPlanetCode())
    }

    @Test
    fun `getParentCode returns empty string by default`() {
        assertEquals("", spm.getParentCode())
    }

    @Test
    fun `getCommunityName returns empty string by default`() {
        assertEquals("", spm.getCommunityName())
    }

    @Test
    fun `setCommunityName persists value`() {
        spm.setCommunityName("OLE-Nepal")
        assertEquals("OLE-Nepal", spm.getCommunityName())
    }

    // -------------------------------------------------------------------------
    // VersionDetail / ConcatenatedLinks / ConfigurationId
    // -------------------------------------------------------------------------

    @Test
    fun `getVersionDetail returns null by default`() {
        assertNull(spm.getVersionDetail())
    }

    @Test
    fun `setVersionDetail persists value`() {
        spm.setVersionDetail("""{"version":"1.0"}""")
        assertEquals("""{"version":"1.0"}""", spm.getVersionDetail())
    }

    @Test
    fun `getConcatenatedLinks returns null by default`() {
        assertNull(spm.getConcatenatedLinks())
    }

    @Test
    fun `setConcatenatedLinks persists value`() {
        spm.setConcatenatedLinks("link1,link2")
        assertEquals("link1,link2", spm.getConcatenatedLinks())
    }

    @Test
    fun `getConfigurationId returns null by default`() {
        assertNull(spm.getConfigurationId())
    }

    @Test
    fun `setConfigurationId persists value`() {
        spm.setConfigurationId("cfg-001")
        assertEquals("cfg-001", spm.getConfigurationId())
    }

    // -------------------------------------------------------------------------
    // Raw accessors
    // -------------------------------------------------------------------------

    @Test
    fun `getRawString returns default when key missing`() {
        assertEquals("fallback", spm.getRawString("nonexistent", "fallback"))
    }

    @Test
    fun `getRawString returns empty string default when unspecified`() {
        assertEquals("", spm.getRawString("nonexistent"))
    }

    @Test
    fun `setRawString and getRawString round-trip`() {
        spm.setRawString("custom_key", "custom_value")
        assertEquals("custom_value", spm.getRawString("custom_key"))
    }

    @Test
    fun `getRawLong returns default when key missing`() {
        assertEquals(42L, spm.getRawLong("nonexistent", 42L))
    }

    @Test
    fun `setRawLong and getRawLong round-trip`() {
        spm.setRawLong("ts_key", 123_456_789L)
        assertEquals(123_456_789L, spm.getRawLong("ts_key"))
    }

    @Test
    fun `removeKey removes previously set value`() {
        spm.setRawString("temp", "value")
        spm.removeKey("temp")
        assertEquals("", spm.getRawString("temp"))
    }

    @Test
    fun `removeKey on non-existent key is a no-op`() {
        spm.removeKey("ghost_key")
        assertEquals("", spm.getRawString("ghost_key"))
    }
}
