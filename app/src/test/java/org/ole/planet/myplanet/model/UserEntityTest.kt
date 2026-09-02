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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.utils.Utilities

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
}
