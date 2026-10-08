package org.ole.planet.myplanet.model

data class RetryFailure(
    val itemId: String,
    val message: String,
    val httpCode: Int? = null
)
