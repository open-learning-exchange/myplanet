package org.ole.planet.myplanet.model

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class RealmUserTest {

    private val mockContext: Context = mockk(relaxed = true)
    private var originalContext: Context? = null

    @Before
    fun setup() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        MainApplication.applicationScope = CoroutineScope(Dispatchers.Unconfined)
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
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun testIsManagerWithManagerRole() {
        val user = RealmUser()
        user.rolesList = mutableListOf("manager")
        user.userAdmin = false
        assertTrue(user.isManager())
    }

    @Test
    fun testIsManagerWithUserAdminTrue() {
        val user = RealmUser()
        user.rolesList = mutableListOf()
        user.userAdmin = true
        assertTrue(user.isManager())
    }

    @Test
    fun testIsManagerFalse() {
        val user = RealmUser()
        user.rolesList = mutableListOf()
        user.userAdmin = false
        assertFalse(user.isManager())
    }

    @Test
    fun testIsManagerNullRolesAndAdmin() {
        val user = RealmUser()
        user.rolesList = null
        user.userAdmin = null
        assertFalse(user.isManager())
    }

    @Test
    fun testIsManagerCaseInsensitive() {
        val user = RealmUser()
        user.rolesList = mutableListOf("MaNaGeR")
        user.userAdmin = false
        assertTrue(user.isManager())
    }
}
