package org.ole.planet.myplanet.model

data class FinanceReportParams(
    val description: String,
    val beginningBalance: Int,
    val sales: Int,
    val otherIncome: Int,
    val wages: Int,
    val otherExpenses: Int,
    val startDate: Long,
    val endDate: Long,
    val teamId: String,
    val teamType: String?,
    val teamPlanetCode: String?,
    val imageName: String? = null,
    val imageData: ByteArray? = null
)