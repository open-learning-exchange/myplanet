package org.ole.planet.myplanet.model

data class Transaction(
    val id: String,
    val date: Long?,
    val description: String?,
    val type: String?,
    val amount: Int,
    val balance: Int
)
