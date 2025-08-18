package org.ole.planet.myplanet.model

data class User(
    val fullName: String? = null,
    val name: String? = null,
    var password: String? = null,
    val image: String? = null,
    var source: String? = null
)