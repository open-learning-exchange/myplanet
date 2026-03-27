package org.ole.planet.myplanet.model

import io.realm.RealmList
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealmUserTest {

    @Test
    fun isGuest_idStartsWithGuest_returnsTrue() {
        val user = RealmUser()
        user._id = "guest_123"
        assertTrue(user.isGuest())
    }

    @Test
    fun isGuest_hasGuestRoleAndNoLearnerRole_returnsTrue() {
        val user = RealmUser()
        user._id = "user_123"
        val roles = RealmList<String?>()
        roles.add("guest")
        user.rolesList = roles
        assertTrue(user.isGuest())
    }

    @Test
    fun isGuest_hasGuestRoleAndLearnerRole_returnsFalse() {
        val user = RealmUser()
        user._id = "user_123"
        val roles = RealmList<String?>()
        roles.add("guest")
        roles.add("learner")
        user.rolesList = roles
        assertFalse(user.isGuest())
    }

    @Test
    fun isGuest_idStartsWithGuestAndHasLearnerRole_returnsTrue() {
        val user = RealmUser()
        user._id = "guest_123"
        val roles = RealmList<String?>()
        roles.add("learner")
        user.rolesList = roles
        assertTrue(user.isGuest())
    }

    @Test
    fun isGuest_noGuestIdAndNoGuestRole_returnsFalse() {
        val user = RealmUser()
        user._id = "user_123"
        val roles = RealmList<String?>()
        roles.add("manager")
        user.rolesList = roles
        assertFalse(user.isGuest())
    }

    @Test
    fun isGuest_caseInsensitiveRole_returnsTrue() {
        val user = RealmUser()
        user._id = "user_123"
        val roles = RealmList<String?>()
        roles.add("GUEST")
        user.rolesList = roles
        assertTrue(user.isGuest())
    }

    @Test
    fun isGuest_nullIdAndNullRoles_returnsFalse() {
        val user = RealmUser()
        user._id = null
        user.rolesList = null
        assertFalse(user.isGuest())
    }

    @Test
    fun isGuest_emptyRoles_returnsFalse() {
        val user = RealmUser()
        user._id = "user_123"
        user.rolesList = RealmList()
        assertFalse(user.isGuest())
    }

    @Test
    fun isGuest_onlyLearnerRole_returnsFalse() {
        val user = RealmUser()
        user._id = "user_123"
        val roles = RealmList<String?>()
        roles.add("learner")
        user.rolesList = roles
        assertFalse(user.isGuest())
    }
}
