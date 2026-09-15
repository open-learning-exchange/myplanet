package org.ole.planet.myplanet.repository

data class ProfileFieldsUpdate(
    val firstName: String? = null,
    val lastName: String? = null,
    val middleName: String? = null,
    val email: String? = null,
    val language: String? = null,
    val phoneNumber: String? = null,
    val birthDate: String? = null,
    val birthPlace: String? = null,
    val level: String? = null,
    val gender: String? = null,
    val age: String? = null
)
