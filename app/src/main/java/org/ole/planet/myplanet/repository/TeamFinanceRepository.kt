package org.ole.planet.myplanet.repository

import com.google.gson.JsonObject
import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.Transaction

interface TeamFinanceRepository {
    suspend fun getTeamTransactionsWithBalance(
        teamId: String, startDate: Long? = null,
        endDate: Long? = null, sortAscending: Boolean = false
    ): Flow<List<Transaction>>
    suspend fun createTransaction(
        teamId: String, type: String, note: String, amount: Int, date: Long,
        parentCode: String?, planetCode: String?
    ): Result<Unit>
    suspend fun getReportsFlow(teamId: String): Flow<List<RealmMyTeam>>
    suspend fun exportReportsAsCsv(reports: List<RealmMyTeam>, teamName: String): String
    suspend fun addReport(report: JsonObject)
    suspend fun updateReport(reportId: String, payload: JsonObject)
    suspend fun archiveReport(reportId: String)
}
