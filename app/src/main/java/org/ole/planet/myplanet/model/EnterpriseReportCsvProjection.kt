package org.ole.planet.myplanet.model

data class EnterpriseReportCsvProjection(
    val startDate: Long,
    val endDate: Long,
    val createdDate: Long,
    val updatedDate: Long,
    val beginningBalance: Int,
    val sales: Int,
    val otherIncome: Int,
    val wages: Int,
    val otherExpenses: Int
)
