package org.ole.planet.myplanet.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "users", indices = [Index("_id"), Index("name"), Index("planetCode")])
open class UserEntity(
    @PrimaryKey @JvmField var id: String = "",
    @JvmField var _id: String? = null,
    @JvmField var _rev: String? = null,
    var name: String? = null,
    var rolesList: List<String>? = null,
    var userAdmin: Boolean? = null,
    var joinDate: Long = 0,
    var firstName: String? = null,
    var lastName: String? = null,
    var middleName: String? = null,
    var email: String? = null,
    var planetCode: String? = null,
    var parentCode: String? = null,
    var phoneNumber: String? = null,
    var password_scheme: String? = null,
    var iterations: String? = null,
    var derived_key: String? = null,
    var level: String? = null,
    var language: String? = null,
    var gender: String? = null,
    var salt: String? = null,
    var dob: String? = null,
    var age: String? = null,
    var birthPlace: String? = null,
    var userImage: String? = null,
    var key: String? = null,
    var iv: String? = null,
    var password: String? = null,
    var isUpdated: Boolean = false,
    var isShowTopbar: Boolean = false,
    var isArchived: Boolean = false
) {
    fun setRoles(roles: List<String>?) {
        rolesList = roles
    }

    fun getFullName(): String {
        return "$firstName $lastName"
    }

    fun getFullNameWithMiddleName(): String {
        return "$firstName ${middleName ?: ""} $lastName"
    }

    fun isManager(): Boolean {
        val hasManagerRole = rolesList?.any { it.equals("manager", ignoreCase = true) } == true
        return hasManagerRole || userAdmin ?: false
    }

    fun isLeader(): Boolean {
        return rolesList?.any { it.equals("leader", ignoreCase = true) } == true
    }

    fun isGuest(): Boolean {
        val hasGuestId = _id?.startsWith("guest_") == true
        val hasGuestRole = rolesList?.any { it.equals("guest", ignoreCase = true) } == true
        return hasGuestId || (hasGuestRole && rolesList?.any { it.equals("learner", ignoreCase = true) } != true)
    }

    override fun toString(): String {
        return "$name"
    }

    companion object
}

val UserEntity.effectiveId: String? get() = _id?.takeIf { it.isNotEmpty() } ?: id
