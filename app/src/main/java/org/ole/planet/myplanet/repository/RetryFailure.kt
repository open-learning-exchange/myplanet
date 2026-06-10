package org.ole.planet.myplanet.repository

data class RetryFailure(
    val itemId: String,
    val message: String,
    val httpCode: Int? = null
)
