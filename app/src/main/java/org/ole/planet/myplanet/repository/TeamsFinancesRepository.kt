package org.ole.planet.myplanet.repository

import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.model.FinanceReportParams
import org.ole.planet.myplanet.model.MyTeam
import org.ole.planet.myplanet.model.Transaction

interface TeamsFinancesRepository {
    suspend fun getReportsFlow(teamId: String): Flow<List<MyTeam>>
    suspend fun exportReportsAsCsv(reports: List<MyTeam>, teamName: String): String
    suspend fun addReport(report: FinanceReportParams)
    suspend fun updateReport(reportId: String, payload: FinanceReportParams)
    suspend fun archiveReport(reportId: String)
    suspend fun getTeamTransactionsWithBalance(
        teamId: String, startDate: Long? = null,
        endDate: Long? = null, sortAscending: Boolean = false
    ): Flow<List<Transaction>>
    suspend fun createTransaction(
        teamId: String, type: String, note: String, amount: Int, date: Long,
        parentCode: String?, planetCode: String?,
        imageName: String? = null, imageData: ByteArray? = null
    ): Result<Unit>
}
