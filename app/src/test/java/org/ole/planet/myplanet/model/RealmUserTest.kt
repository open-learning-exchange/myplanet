package org.ole.planet.myplanet.model

import android.content.Context
import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import io.realm.Realm
import io.realm.RealmList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.utils.Utilities

class RealmUserTest {

    @MockK
    lateinit var mockRealm: Realm

    @MockK
    lateinit var mockContext: Context

    private var originalContext: Context? = null

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        Dispatchers.setMain(Dispatchers.Unconfined)
        MainApplication.applicationScope = CoroutineScope(Dispatchers.Unconfined)
        mockkStatic(Utilities::class)
        every { Utilities.toast(any(), any()) } returns Unit
        every { Utilities.toast(any(), any(), any()) } returns Unit
        try {
            originalContext = MainApplication.context
        } catch (e: Exception) {
            // UninitializedPropertyAccessException if context was never set
        }
        MainApplication.context = mockContext
    }

    @After
    fun tearDown() {
        if (originalContext != null) {
            MainApplication.context = originalContext!!
        }
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun testIsManagerWithManagerRole() {
        val user = RealmUser()
        val roles = RealmList<String?>()
        roles.add("manager")
        user.rolesList = roles
        user.userAdmin = false
        assertTrue(user.isManager())
    }

    @Test
    fun testIsManagerWithUserAdminTrue() {
        val user = RealmUser()
        user.rolesList = RealmList<String?>()
        user.userAdmin = true
        assertTrue(user.isManager())
    }

    @Test
    fun testIsManagerFalse() {
        val user = RealmUser()
        user.rolesList = RealmList<String?>()
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
        val roles = RealmList<String?>()
        roles.add("MaNaGeR")
        user.rolesList = roles
        user.userAdmin = false
        assertTrue(user.isManager())
    }
}
