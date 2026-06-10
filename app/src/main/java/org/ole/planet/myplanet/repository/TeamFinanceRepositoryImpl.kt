package org.ole.planet.myplanet.repository

import com.google.gson.JsonObject
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.di.RealmDispatcher
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.Transaction
import org.ole.planet.myplanet.utils.JsonUtils
import org.ole.planet.myplanet.utils.TimeUtils

class TeamFinanceRepositoryImpl @Inject constructor(
    databaseService: DatabaseService,
    @RealmDispatcher realmDispatcher: CoroutineDispatcher,
) : RealmRepository(databaseService, realmDispatcher), TeamFinanceRepository {

    override suspend fun getTeamTransactionsWithBalance(
        teamId: String,
        startDate: Long?,
        endDate: Long?,
        sortAscending: Boolean,
    ): Flow<List<Transaction>> {
        return queryTransactions(teamId, startDate, endDate, true).map { transactions ->
            val transactionDataList = mapTransactionsToPresentationModel(transactions)
            if (!sortAscending) transactionDataList.reversed() else transactionDataList
        }
    }

    private fun queryTransactions(
        teamId: String,
        startDate: Long?,
        endDate: Long?,
        sortAscending: Boolean
    ): Flow<List<RealmMyTeam>> {
        return queryListFlow(RealmMyTeam::class.java) {
            equalTo("teamId", teamId)
            equalTo("docType", "transaction")
            notEqualTo("status", "archived")
            startDate?.let { greaterThanOrEqualTo("date", it) }
            endDate?.let { lessThanOrEqualTo("date", it) }
        }.map { transactions ->
            if (sortAscending) transactions.sortedBy { it.date }
            else transactions.sortedByDescending { it.date }
        }
    }

    private fun mapTransactionsToPresentationModel(transactions: List<RealmMyTeam>): List<Transaction> {
        val transactionDataList = mutableListOf<Transaction>()
        var balance = 0
        for (team in transactions) {
            val id = team._id ?: continue
            balance += if ("debit".equals(team.type, ignoreCase = true)) -team.amount else team.amount
            transactionDataList.add(
                Transaction(
                    id = id,
                    date = team.date,
                    description = team.description,
                    type = team.type,
                    amount = team.amount,
                    balance = balance
                )
            )
        }
        return transactionDataList
    }

    override suspend fun createTransaction(
        teamId: String,
        type: String,
        note: String,
        amount: Int,
        date: Long,
        parentCode: String?,
        planetCode: String?,
    ): Result<Unit> {
        if (teamId.isBlank()) {
            return Result.failure(IllegalArgumentException("teamId cannot be blank"))
        }
        return runCatching {
            val transaction = RealmMyTeam().apply {
                _id = UUID.randomUUID().toString()
                status = "active"
                this.date = date
                this.type = type
                description = note
                this.teamId = teamId
                this.amount = amount
                this.parentCode = parentCode
                teamPlanetCode = planetCode
                teamType = "sync"
                docType = "transaction"
                updated = true
            }
            save(transaction)
        }
    }

    override suspend fun getReportsFlow(teamId: String): Flow<List<RealmMyTeam>> {
        return queryListFlow(RealmMyTeam::class.java) {
            equalTo("teamId", teamId)
            equalTo("docType", "report")
            notEqualTo("status", "archived")
            sort("createdDate", io.realm.Sort.DESCENDING)
        }
    }

    override suspend fun exportReportsAsCsv(reports: List<RealmMyTeam>, teamName: String): String {
        val csvBuilder = StringBuilder()
        csvBuilder.append("$teamName Financial Report Summary\n\n")
        csvBuilder.append("Start Date, End Date, Created Date, Updated Date, Beginning Balance, Sales, Other Income, Wages, Other Expenses, Profit/Loss, Ending Balance\n")
        for (report in reports) {
            val totalIncome = report.sales + report.otherIncome
            val totalExpenses = report.wages + report.otherExpenses
            val profitLoss = totalIncome - totalExpenses
            val endingBalance = profitLoss + report.beginningBalance
            csvBuilder.append("${TimeUtils.formatDateForCsv(report.startDate)}, ${TimeUtils.formatDateForCsv(report.endDate)}, ${TimeUtils.formatDateForCsv(report.createdDate)}, ${TimeUtils.formatDateForCsv(report.updatedDate)}, ${report.beginningBalance}, ${report.sales}, ${report.otherIncome}, ${report.wages}, ${report.otherExpenses}, $profitLoss, $endingBalance\n")
        }
        return csvBuilder.toString()
    }

    override suspend fun addReport(report: JsonObject) {
        executeTransaction { realm ->
            val reportId = JsonUtils.getString("_id", report)
            val reportEntry = realm.where(RealmMyTeam::class.java)
                .equalTo("_id", reportId)
                .findFirst()
                ?: realm.createObject(RealmMyTeam::class.java, reportId)
            RealmMyTeam.populateTeamFields(report, reportEntry)
        }
    }

    override suspend fun updateReport(reportId: String, payload: JsonObject) {
        if (reportId.isBlank()) return
        update(RealmMyTeam::class.java, "_id", reportId) { report ->
            RealmMyTeam.populateReportFields(payload, report)
            report.updated = true
            if (report.updatedDate == 0L) {
                report.updatedDate = System.currentTimeMillis()
            }
        }
    }

    override suspend fun archiveReport(reportId: String) {
        if (reportId.isBlank()) return
        update(RealmMyTeam::class.java, "_id", reportId) { report ->
            report.status = "archived"
            report.updated = true
        }
    }
}
