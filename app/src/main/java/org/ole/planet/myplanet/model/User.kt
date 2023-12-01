package org.ole.planet.myplanet.model

data class User(
    val fullName: String,
    val name: String,
    var password: String,
    val image: String,
    var source: String
)