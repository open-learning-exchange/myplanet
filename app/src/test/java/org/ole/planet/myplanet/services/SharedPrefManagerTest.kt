package org.ole.planet.myplanet.services

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.di.NetworkModule
import org.ole.planet.myplanet.model.User
import org.ole.planet.myplanet.utils.FakeCredentialStore
import org.ole.planet.myplanet.utils.FakeKeyValueStore
import org.ole.planet.myplanet.utils.UrlUtils

class SharedPrefManagerTest {

    private lateinit var sharedPrefManager: SharedPrefManager
    private lateinit var store: FakeKeyValueStore
    private lateinit var defaultStore: FakeKeyValueStore
    private lateinit var credentialStore: FakeCredentialStore

    @Before
    fun setup() {
        store = FakeKeyValueStore()
        defaultStore = FakeKeyValueStore()
        credentialStore = FakeCredentialStore()
        sharedPrefManager = newManager()
    }

    private fun newManager(gson: com.google.gson.Gson = NetworkModule.provideGson()) =
        SharedPrefManager(store, defaultStore, credentialStore, gson)

    @Test
    fun testGetAndSetSavedUsers() {
        assertTrue(sharedPrefManager.getSavedUsers().isEmpty())

        val users = listOf(User(name = "Test User"))
        sharedPrefManager.setSavedUsers(users)

        assertEquals(NetworkModule.provideGson().toJson(users), store.values["savedUsers"])
        val retrievedUsers = newManager().getSavedUsers()
        assertEquals(1, retrievedUsers.size)
        assertEquals("Test User", retrievedUsers[0].name)
    }

    @Test
    fun testGetSavedUsersSuccessiveCallsCacheHit() {
        val spyGson = io.mockk.spyk(NetworkModule.provideGson())
        val json = spyGson.toJson(listOf(User(name = "User 1")))
        store.values["savedUsers"] = json
        val manager = newManager(spyGson)

        val res1 = manager.getSavedUsers()
        val res2 = manager.getSavedUsers()

        assertEquals(res1, res2)
        assertEquals("User 1", res1[0].name)
        verify(exactly = 1) { spyGson.fromJson<List<User>>(json, any<java.lang.reflect.Type>()) }
    }

    @Test
    fun testGetSavedUsersRawComparisonOutOfStringWrite() {
        store.values["savedUsers"] = NetworkModule.provideGson().toJson(listOf(User(name = "User 1")))
        assertEquals("User 1", sharedPrefManager.getSavedUsers()[0].name)

        store.values["savedUsers"] = NetworkModule.provideGson().toJson(listOf(User(name = "User 2")))
        assertEquals("User 2", sharedPrefManager.getSavedUsers()[0].name)
    }

    @Test
    fun testSetSavedUsersFollowedByGetSavedUsersNoReparse() {
        val spyGson = io.mockk.spyk(NetworkModule.provideGson())
        val manager = newManager(spyGson)

        manager.setSavedUsers(listOf(User(name = "User 1")))

        val retrieved = manager.getSavedUsers()
        assertEquals(1, retrieved.size)
        assertEquals("User 1", retrieved[0].name)
        verify(exactly = 0) { spyGson.fromJson<List<User>>(any<String>(), any<java.lang.reflect.Type>()) }
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun testGetSavedUsersReturnsDefensiveCopyOfList() {
        store.values["savedUsers"] = NetworkModule.provideGson().toJson(listOf(User(name = "User 1")))

        val mutable = sharedPrefManager.getSavedUsers() as MutableList<User>
        mutable.clear()

        val retrieved = sharedPrefManager.getSavedUsers()
        assertEquals(1, retrieved.size)
        assertEquals("User 1", retrieved[0].name)
    }

    @Test
    fun testGetSavedUsersReturnsDefensiveCopyOfElements() {
        store.values["savedUsers"] = NetworkModule.provideGson().toJson(listOf(User(name = "User 1")))

        sharedPrefManager.getSavedUsers()[0].name = "mutated"

        assertEquals("User 1", sharedPrefManager.getSavedUsers()[0].name)
    }

    @Test
    fun testSetSavedUsersDoesNotCacheCallerElements() {
        val user = User(name = "User 1")
        val users = mutableListOf(user)

        sharedPrefManager.setSavedUsers(users)
        user.name = "mutated"
        users.clear()

        val retrieved = sharedPrefManager.getSavedUsers()
        assertEquals(1, retrieved.size)
        assertEquals("User 1", retrieved[0].name)
    }

    @Test
    fun testGetSavedUsersMalformedJson() {
        store.values["savedUsers"] = "invalid json {"
        try {
            sharedPrefManager.getSavedUsers()
            org.junit.Assert.fail("Expected JsonSyntaxException or similar error on malformed json")
        } catch (e: Exception) {
            assertTrue(e is com.google.gson.JsonSyntaxException || e is com.google.gson.JsonParseException)
        }
    }

    @Test
    fun testGetSelectedTeamIdAndTeamNameDefaultToEmpty() {
        assertEquals("", sharedPrefManager.getSelectedTeamId())
        assertEquals("", sharedPrefManager.getTeamName())

        sharedPrefManager.setSelectedTeamId("team123")
        sharedPrefManager.setTeamName("My Team")
        assertEquals("team123", store.values["selectedTeamId"])
        assertEquals("My Team", store.values["teamName"])
        assertEquals("team123", sharedPrefManager.getSelectedTeamId())
        assertEquals("My Team", sharedPrefManager.getTeamName())
    }

    @Test
    fun testSetPendingLanguageChange() {
        sharedPrefManager.setPendingLanguageChange("fr")
        assertEquals("fr", store.values["pendingLanguageChange"])

        sharedPrefManager.setPendingLanguageChange(null)
        assertFalse(store.contains("pendingLanguageChange"))
        assertNull(sharedPrefManager.getPendingLanguageChange())
    }

    @Test
    fun testGetAndSetRepliedNewsIdManualConfigUrlHostAndLogin() {
        sharedPrefManager.setRepliedNewsId("456")
        sharedPrefManager.setManualConfig(true)
        sharedPrefManager.setUrlHost("new.example.com")
        sharedPrefManager.setLoggedIn(true)

        assertEquals("456", store.values["repliedNewsId"])
        assertEquals(true, store.values["manualConfig"])
        assertEquals("new.example.com", store.values["url_Host"])
        assertEquals(true, store.values[SharedPrefManager.KEY_LOGIN])
        assertEquals("456", sharedPrefManager.getRepliedNewsId())
        assertTrue(sharedPrefManager.getManualConfig())
        assertEquals("new.example.com", sharedPrefManager.getUrlHost())
        assertTrue(sharedPrefManager.isLoggedIn())
    }

    @Test
    fun testDefaultsWhenAbsent() {
        assertTrue(sharedPrefManager.getAutoSync())
        assertEquals(60 * 60, sharedPrefManager.getAutoSyncInterval())
        assertTrue(sharedPrefManager.getFirstRun())
        assertEquals(1.0f, sharedPrefManager.getMediaPlaybackSpeed())
        assertEquals(-1, sharedPrefManager.getCachedApkVersion())
        assertEquals(0L, sharedPrefManager.getLastVersionCheckTimestamp())
        assertEquals(0, sharedPrefManager.getHeavySyncSkip("ratings"))
        assertFalse(sharedPrefManager.isLoggedIn())
    }

    @Test
    fun testRawString() {
        store.values["test_key"] = "test_val"
        assertEquals("test_val", sharedPrefManager.getRawString("test_key", ""))

        sharedPrefManager.setRawString("test_key", "new_val")
        assertEquals("new_val", store.values["test_key"])
    }

    @Test
    fun testClearPreferencesKeepsFirstLaunchAndManualConfigAndClearsDefaults() {
        store.values[SharedPrefManager.FIRST_LAUNCH] = true
        store.values["serverURL"] = "http://host"
        defaultStore.values["beta_function"] = true

        sharedPrefManager.clearPreferences()

        assertEquals(mapOf<String, Any>(SharedPrefManager.FIRST_LAUNCH to true, SharedPrefManager.MANUAL_CONFIG to false), store.values)
        assertEquals(1, store.editCount)
        assertTrue(defaultStore.values.isEmpty())
    }

    @Test
    fun testRemoveKey() {
        store.values["some_key"] = "x"
        sharedPrefManager.removeKey("some_key")
        assertFalse(store.contains("some_key"))
    }

    @Test
    fun testGetAndSetNewLoginUsernameAndPasswordAreEncrypted() {
        sharedPrefManager.setNewLoginUsername("test_user")
        sharedPrefManager.setNewLoginPassword("test_pass")
        assertEquals("enc:test_user", store.values["new_login_username"])
        assertEquals("enc:test_pass", store.values["new_login_password"])
        assertEquals("test_user", sharedPrefManager.getNewLoginUsername())
        assertEquals("test_pass", sharedPrefManager.getNewLoginPassword())

        sharedPrefManager.setNewLoginUsername(null)
        sharedPrefManager.setNewLoginPassword(null)
        assertFalse(store.contains("new_login_username"))
        assertFalse(store.contains("new_login_password"))
        assertNull(sharedPrefManager.getNewLoginUsername())
    }

    @Test
    fun testBetaFlagsReadTheDefaultStore() {
        assertFalse(sharedPrefManager.isBetaFeatureEnabled())
        defaultStore.values["beta_function"] = true
        assertTrue(sharedPrefManager.isBetaFeatureEnabled())

        sharedPrefManager.setBetaAutoDownload(true)
        assertEquals(true, defaultStore.values["beta_auto_download"])
        assertTrue(sharedPrefManager.getBetaAutoDownload())
        assertFalse(store.contains("beta_auto_download"))
    }

    @Test
    fun testUrlSettersInvalidateCache() {
        mockkObject(UrlUtils)
        every { UrlUtils.invalidateCaches() } just Runs

        sharedPrefManager.setCouchdbUrl("http://new-couch.com")
        verify(exactly = 1) { UrlUtils.invalidateCaches() }

        sharedPrefManager.setProcessedAlternativeUrl("http://new-alt.com")
        verify(exactly = 2) { UrlUtils.invalidateCaches() }

        sharedPrefManager.setIsAlternativeUrl(true)
        verify(exactly = 3) { UrlUtils.invalidateCaches() }

        unmockkObject(UrlUtils)
    }

    @Test
    fun testSaveServerConfig() {
        mockkObject(UrlUtils)
        every { UrlUtils.invalidateCaches() } just Runs

        sharedPrefManager.saveServerConfig(
            serverPin = "1234",
            urlScheme = "http",
            urlHost = "host.com",
            serverUrl = "http://host.com",
            couchdbUrl = "http://satellite:1234@host.com:80",
            urlUser = "satellite",
            urlPwd = "1234"
        )

        assertEquals(1, store.editCount)
        assertEquals(
            mapOf<String, Any>(
                "serverPin" to "1234",
                "url_Scheme" to "http",
                "url_Host" to "host.com",
                "serverURL" to "http://host.com",
                "couchdbURL" to "http://satellite:1234@host.com:80",
                "url_user" to "satellite",
                "url_pwd" to "1234"
            ),
            store.values
        )
        verify(exactly = 1) { UrlUtils.invalidateCaches() }

        unmockkObject(UrlUtils)
    }

    @Test
    fun testSaveAlternativeServerConfig() {
        mockkObject(UrlUtils)
        every { UrlUtils.invalidateCaches() } just Runs

        sharedPrefManager.saveAlternativeServerConfig(
            serverPin = "5678",
            urlUser = "admin",
            urlPwd = "pass",
            urlScheme = "https",
            urlHost = "alt.com",
            alternativeUrl = "https://alt.com",
            processedAlternativeUrl = "https://admin:pass@alt.com:443",
            isAlternativeUrl = true
        )

        assertEquals(1, store.editCount)
        assertEquals(
            mapOf<String, Any>(
                "serverPin" to "5678",
                "url_user" to "admin",
                "url_pwd" to "pass",
                "url_Scheme" to "https",
                "url_Host" to "alt.com",
                "alternativeUrl" to "https://alt.com",
                "processedAlternativeUrl" to "https://admin:pass@alt.com:443",
                "isAlternativeUrl" to true
            ),
            store.values
        )
        verify(exactly = 1) { UrlUtils.invalidateCaches() }

        unmockkObject(UrlUtils)
    }

    @Test
    fun testSaveAlternativeUrlConfigLeavesServerPinAlone() {
        mockkObject(UrlUtils)
        every { UrlUtils.invalidateCaches() } just Runs
        store.values["serverPin"] = "1111"

        sharedPrefManager.saveAlternativeUrlConfig(
            urlUser = "satellite",
            urlPwd = "1111",
            urlScheme = "http",
            urlHost = "primary.com",
            alternativeUrl = "http://primary.com",
            processedAlternativeUrl = "https://satellite:1111@alt.com:443"
        )

        assertEquals(1, store.editCount)
        assertEquals(
            mapOf<String, Any>(
                "serverPin" to "1111",
                "url_user" to "satellite",
                "url_pwd" to "1111",
                "url_Scheme" to "http",
                "url_Host" to "primary.com",
                "alternativeUrl" to "http://primary.com",
                "processedAlternativeUrl" to "https://satellite:1111@alt.com:443",
                "isAlternativeUrl" to true
            ),
            store.values
        )
        verify(exactly = 1) { UrlUtils.invalidateCaches() }

        unmockkObject(UrlUtils)
    }

    @Test
    fun testSaveUserInfo() {
        store.values["password"] = "legacy"

        sharedPrefManager.saveUserInfo(
            userId = "usr123",
            userName = "john_doe",
            firstName = "John",
            lastName = "Doe",
            middleName = "M",
            isUserAdmin = true,
            lastLogin = 1000L
        )

        assertEquals(1, store.editCount)
        assertEquals(
            mapOf<String, Any>(
                "userId" to "usr123",
                "name" to "john_doe",
                "firstName" to "John",
                "lastName" to "Doe",
                "middleName" to "M",
                "isUserAdmin" to true,
                "lastLogin" to 1000L
            ),
            store.values
        )
    }

    @Test
    fun testSyncBookkeepingKeys() {
        sharedPrefManager.setHeavySyncSkip("ratings", 40)
        assertEquals(40, store.values["heavy_sync_skip_ratings"])
        assertEquals(40, sharedPrefManager.getHeavySyncSkip("ratings"))
        sharedPrefManager.clearHeavySyncSkip("ratings")
        assertFalse(store.contains("heavy_sync_skip_ratings"))

        sharedPrefManager.setResourceSyncProgress(123L, 7)
        assertEquals(123L, store.values["ResourceLastSyncTime"])
        assertEquals(7, store.values["ResourceSyncPosition"])

        sharedPrefManager.setPlanetCode("planet")
        sharedPrefManager.setCachedApkVersion(7064)
        sharedPrefManager.setLastVersionCheckTimestamp(99L)
        sharedPrefManager.setAiModels("{}")
        sharedPrefManager.setPlanetType("community")
        assertEquals("planet", sharedPrefManager.getPlanetCode())
        assertEquals(7064, store.values["cachedApkVersion"])
        assertEquals(99L, store.values["last_version_check_timestamp"])
        assertEquals("{}", store.values["ai_models"])
        assertEquals("community", store.values["planetType"])
    }
}
