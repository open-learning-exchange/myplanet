package org.ole.planet.myplanet.model

import io.realm.RealmList
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealmUserTest {

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
