package org.ole.planet.myplanet.model

data class FinanceHeaderState(
    val debit: Int = 0,
    val credit: Int = 0,
    val total: Int = 0,
    val isCautionVisible: Boolean = false
)
