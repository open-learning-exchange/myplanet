package org.ole.planet.myplanet.model

data class FinanceReport(
    val _id: String,
    val _rev: String?,
    val status: String?,
    val description: String?,
    val beginningBalance: Int,
    val sales: Int,
    val otherIncome: Int,
    val wages: Int,
    val otherExpenses: Int,
    val startDate: Long,
    val endDate: Long,
    val createdDate: Long,
    val updatedDate: Long,
    val updated: Boolean,
    val imageName: String?,
)
