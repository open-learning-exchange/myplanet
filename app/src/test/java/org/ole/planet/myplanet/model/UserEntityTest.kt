package org.ole.planet.myplanet.model

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.utils.NetworkUtils
import org.ole.planet.myplanet.utils.Utilities
import org.ole.planet.myplanet.utils.VersionUtils

@OptIn(ExperimentalCoroutinesApi::class)
class UserEntityTest {

    private val mockContext: Context = mockk(relaxed = true)
    private var originalContext: Context? = null
    private var originalScope: CoroutineScope? = null

    @Before
    fun setup() {
        // applicationScope is lateinit — reading it before anything initialized it throws
        originalScope = try {
            MainApplication.applicationScope
        } catch (_: UninitializedPropertyAccessException) {
            null
        }
        Dispatchers.setMain(Dispatchers.Unconfined)
        MainApplication.applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        mockkObject(Utilities)
        every { Utilities.toast(any(), any()) } returns Unit
        try {
            originalContext = MainApplication.context
        } catch (_: Exception) {
        }
        MainApplication.testContext = mockContext
    }

    @After
    fun tearDown() {
        MainApplication.testContext = originalContext
        // Cancel + restore only when there was an original scope. If applicationScope was
        // uninitialized before this test, leave the live temp scope in place — replacing an
        // uninitialized lateinit with a cancelled scope would make later tests in the same
        // JVM silently skip coroutine work.
        originalScope?.let {
            MainApplication.applicationScope.cancel()
            MainApplication.applicationScope = it
        }
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun testIsManagerWithManagerRole() {
        val user = UserEntity()
        user.rolesList = mutableListOf("manager")
        user.userAdmin = false
        assertTrue(user.isManager())
    }

    @Test
    fun testIsManagerWithUserAdminTrue() {
        val user = UserEntity()
        user.rolesList = mutableListOf()
        user.userAdmin = true
        assertTrue(user.isManager())
    }

    @Test
    fun testIsManagerFalse() {
        val user = UserEntity()
        user.rolesList = mutableListOf()
        user.userAdmin = false
        assertFalse(user.isManager())
    }

    @Test
    fun testIsManagerNullRolesAndAdmin() {
        val user = UserEntity()
        user.rolesList = null
        user.userAdmin = null
        assertFalse(user.isManager())
    }

    @Test
    fun testIsManagerCaseInsensitive() {
        val user = UserEntity()
        user.rolesList = mutableListOf("MaNaGeR")
        user.userAdmin = false
        assertTrue(user.isManager())
    }

    @Test
    fun testIsManagerWithCompoundRole() {
        val user = UserEntity()
        user.rolesList = mutableListOf("project_manager")
        user.userAdmin = false
        assertFalse(user.isManager())
    }

    @Test
    fun testIsLeaderWithLeaderRole() {
        val user = UserEntity()
        user.rolesList = mutableListOf("leader")
        assertTrue(user.isLeader())
    }

    @Test
    fun testIsLeaderWithCompoundRole() {
        val user = UserEntity()
        user.rolesList = mutableListOf("team_leader")
        assertFalse(user.isLeader())
    }

    @Test
    fun testEffectiveIdWhenCouchIdPresent() {
        val user = UserEntity(id = "local_123", _id = "couch_456")
        assertEquals("couch_456", user.effectiveId)
    }

    @Test
    fun testEffectiveIdWhenCouchIdNull() {
        val user = UserEntity(id = "local_123", _id = null)
        assertEquals("local_123", user.effectiveId)
    }

    @Test
    fun testEffectiveIdWhenCouchIdEmpty() {
        val user = UserEntity(id = "local_123", _id = "")
        assertEquals("local_123", user.effectiveId)
    }

    @Test
    fun serialize_existingUser_includesCredentialFields_omitsNewUserFields() {
        val user = UserEntity(
            id = "local_123",
            _id = "couch_456",
            _rev = "1-abc",
            name = "Jane Doe",
            derived_key = "derived-key-value",
            salt = "salt-value",
            password_scheme = "pbkdf2",
            password = "should-not-be-serialized"
        )
        user.rolesList = mutableListOf("learner")

        val json = user.serialize()

        assertEquals("couch_456", json.get("_id").asString)
        assertEquals("1-abc", json.get("_rev").asString)
        assertEquals("derived-key-value", json.get("derived_key").asString)
        assertEquals("salt-value", json.get("salt").asString)
        assertEquals("pbkdf2", json.get("password_scheme").asString)
        assertEquals(1, json.getAsJsonArray("roles").size())
        assertEquals("learner", json.getAsJsonArray("roles")[0].asString)
        assertFalse(json.has("password"))
        assertFalse(json.has("androidId"))
        assertFalse(json.has("app"))
        assertFalse(json.has("uniqueAndroidId"))
    }

    @Test
    fun serialize_newUser_includesPasswordAndDeviceFields_omitsCredentialFields() {
        mockkObject(VersionUtils)
        mockkObject(NetworkUtils)
        every { VersionUtils.getAndroidId(any()) } returns "android-id-1"
        every { NetworkUtils.getUniqueIdentifier() } returns "unique-id-1"
        every { NetworkUtils.getCustomDeviceName(any()) } returns "custom-device-1"

        val user = UserEntity(id = "local_123", _id = "", name = "New User", password = "plaintext-password")
        user.rolesList = mutableListOf()

        val json = user.serialize()

        assertEquals("plaintext-password", json.get("password").asString)
        assertEquals("unique-id-1", json.get("androidId").asString)
        assertEquals("myplanet", json.get("app").asString)
        assertEquals("android-id-1", json.get("uniqueAndroidId").asString)
        assertEquals("custom-device-1", json.get("customDeviceName").asString)
        assertFalse(json.has("_id"))
        assertFalse(json.has("derived_key"))
        assertFalse(json.has("salt"))
        assertFalse(json.has("password_scheme"))
    }

    @Test
    fun serialize_invalidIterationsFallsBackToTen() {
        val user = UserEntity(id = "local_123", _id = "couch_456", iterations = "not-a-number")
        user.rolesList = mutableListOf()

        val json = user.serialize()

        assertEquals(10, json.get("iterations").asInt)
    }
}
