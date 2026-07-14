package org.ole.planet.myplanet.model

data class MemberInfo(
    val username: String,
    var password: String,
    val rePassword: String,
    val fName: String,
    val lName: String,
    val mName: String,
    val email: String,
    val language: String,
    val level: String,
    val phoneNumber: String,
    val birthDate: String,
    val gender: String?
)