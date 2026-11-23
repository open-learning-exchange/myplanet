package org.ole.planet.myplanet.model

data class EnterpriseReportCsvProjection(
    val startDate: Long,
    val endDate: Long,
    val createdDate: Long,
    val updatedDate: Long,
    val beginningBalance: Double,
    val sales: Double,
    val otherIncome: Double,
    val wages: Double,
    val otherExpenses: Double
)
