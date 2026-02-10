package org.ole.planet.myplanet.model.dto

data class Member(
    val id: String?,
    val _id: String?,
    val name: String?,
    val firstName: String?,
    val lastName: String?,
    val middleName: String?,
    val email: String?,
    val phoneNumber: String?,
    val joinDate: Long,
    val userImage: String?,
    val dob: String?,
    val language: String?,
    val level: String?,
    val roles: List<String>?,
    val planetCode: String?,
    val parentCode: String?
) {
    fun getFullName(): String {
        return "$firstName $lastName"
    }

    fun getRoleAsString(): String {
        return roles?.joinToString(",") ?: ""
    }

    override fun toString(): String {
        return name ?: ""
    }
}
