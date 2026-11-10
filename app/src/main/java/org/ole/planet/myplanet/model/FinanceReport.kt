package org.ole.planet.myplanet.model

data class FinanceReport(
    val _id: String,
    val _rev: String?,
    val status: String?,
    val description: String?,
    val beginningBalance: Double,
    val sales: Double,
    val otherIncome: Double,
    val wages: Double,
    val otherExpenses: Double,
    val startDate: Long,
    val endDate: Long,
    val createdDate: Long,
    val updatedDate: Long,
    val updated: Boolean,
    val imageName: String?,
)
