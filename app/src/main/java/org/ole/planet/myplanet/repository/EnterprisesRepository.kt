package org.ole.planet.myplanet.repository

import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.model.FinanceReport
import org.ole.planet.myplanet.model.FinanceReportParams

interface EnterprisesRepository {
    suspend fun addReport(report: FinanceReportParams)
    suspend fun updateReport(reportId: String, payload: FinanceReportParams)
    suspend fun archiveReport(reportId: String)
    fun getReportsFlow(teamId: String): Flow<List<FinanceReport>>
    suspend fun exportReportsAsCsv(teamId: String, teamName: String): String
}
