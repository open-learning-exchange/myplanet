package org.ole.planet.myplanet.data.room.entity.legacy

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Room mirror for legacy UserEntity rows during the Realm-to-Room migration. */
@Entity(tableName = "users", indices = [Index("_id"), Index("name"), Index("planetCode")])
data class RoomUserEntity(
    @PrimaryKey @JvmField val id: String,
    @JvmField val _id: String? = null,
    @JvmField val _rev: String? = null,
    val name: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val middleName: String? = null,
    val rolesList: List<String>? = null,
    val planetCode: String? = null,
    val parentCode: String? = null,
    val isUserAdmin: Boolean = false,
    val joined: Long = 0,
    val updated: Long = 0,
    val email: String? = null,
    val phoneNumber: String? = null,
    val password_scheme: String? = null,
    val iterations: String? = null,
    val derived_key: String? = null,
    val level: String? = null,
    val language: String? = null,
    val gender: String? = null,
    val salt: String? = null,
    val dob: String? = null,
    val age: String? = null,
    val birthPlace: String? = null,
    val userImage: String? = null,
    val key: String? = null,
    val iv: String? = null,
    val password: String? = null,
    val isUpdated: Boolean = false,
    val isShowTopbar: Boolean = false,
    val isArchived: Boolean = false,
)
