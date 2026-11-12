package org.ole.planet.myplanet.model

data class FinanceReportParams(
    val description: String,
    val beginningBalance: Double,
    val sales: Double,
    val otherIncome: Double,
    val wages: Double,
    val otherExpenses: Double,
    val startDate: Long,
    val endDate: Long,
    val teamId: String,
    val teamType: String?,
    val teamPlanetCode: String?,
    val imageName: String? = null,
    val imageData: ByteArray? = null
)
