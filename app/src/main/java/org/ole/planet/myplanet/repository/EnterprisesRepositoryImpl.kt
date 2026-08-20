package org.ole.planet.myplanet.repository

import com.google.gson.JsonObject
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.data.room.dao.TeamDao
import org.ole.planet.myplanet.model.FinanceReportParams
import org.ole.planet.myplanet.model.MyTeam
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.TimeProvider
import org.ole.planet.myplanet.utils.TimeUtils

class EnterprisesRepositoryImpl @Inject constructor(
    private val teamDao: TeamDao,
    private val timeProvider: TimeProvider,
    private val dispatcherProvider: DispatcherProvider,
    private val teamsRepository: TeamsRepository
) : EnterprisesRepository {

    override suspend fun addReport(report: FinanceReportParams) {
        val reportId = UUID.randomUUID().toString()
        val doc = JsonObject().apply {
            addProperty("_id", reportId)
            addProperty("createdDate", timeProvider.now())
            addProperty("description", report.description)
            addProperty("beginningBalance", report.beginningBalance)
            addProperty("sales", report.sales)
            addProperty("otherIncome", report.otherIncome)
            addProperty("wages", report.wages)
            addProperty("otherExpenses", report.otherExpenses)
            addProperty("startDate", report.startDate)
            addProperty("endDate", report.endDate)
            addProperty("updatedDate", timeProvider.now())
            addProperty("teamId", report.teamId)
            addProperty("teamType", report.teamType)
            addProperty("teamPlanetCode", report.teamPlanetCode)
            addProperty("docType", "report")
            addProperty("updated", true)
        }
        val reportEntry = MyTeam().apply { _id = reportId }
        MyTeam.populateTeamFields(doc, reportEntry)
        teamDao.upsert(with(teamsRepository) { reportEntry.requireRoomEntity() })
        if (report.imageName != null && report.imageData != null) {
            teamsRepository.attachTeamImage(reportId, report.imageName, report.imageData)
        }
    }

    override suspend fun updateReport(reportId: String, payload: FinanceReportParams) {
        if (reportId.isBlank()) return
        val doc = JsonObject().apply {
            addProperty("description", payload.description)
            addProperty("beginningBalance", payload.beginningBalance)
            addProperty("sales", payload.sales)
            addProperty("otherIncome", payload.otherIncome)
            addProperty("wages", payload.wages)
            addProperty("otherExpenses", payload.otherExpenses)
            addProperty("startDate", payload.startDate)
            addProperty("endDate", payload.endDate)
            addProperty("updatedDate", timeProvider.now())
            addProperty("updated", true)
        }
        teamsRepository.updateTeamEntityById(reportId) { report ->
            MyTeam.populateReportFields(doc, report)
            report.updated = true
            if (report.updatedDate == 0L) {
                report.updatedDate = timeProvider.now()
            }
        }
        if (payload.imageName != null && payload.imageData != null) {
            teamsRepository.attachTeamImage(reportId, payload.imageName, payload.imageData)
        }
    }

    override suspend fun archiveReport(reportId: String) {
        if (reportId.isBlank()) return
        teamsRepository.updateTeamEntityById(reportId) { report ->
            report.status = "archived"
            report.updated = true
        }
    }

    override fun getReportsFlow(teamId: String): Flow<List<MyTeam>> {
        return teamDao.observeAll().map { entities ->
            entities.filter {
                it.teamId == teamId &&
                    it.docType == "report" &&
                    it.status != "archived"
            }.sortedByDescending { it.createdDate }
        }
    }

    override suspend fun exportReportsAsCsv(reports: List<MyTeam>, teamName: String): String {
        val csvBuilder = StringBuilder()
        csvBuilder.append(teamName).append(" Financial Report Summary\n\n")
        csvBuilder.append("Start Date, End Date, Created Date, Updated Date, Beginning Balance, Sales, Other Income, Wages, Other Expenses, Profit/Loss, Ending Balance\n")
        for (report in reports) {
            val totalIncome = report.sales + report.otherIncome
            val totalExpenses = report.wages + report.otherExpenses
            val profitLoss = totalIncome - totalExpenses
            val endingBalance = profitLoss + report.beginningBalance
            csvBuilder.append(TimeUtils.formatDateForCsv(report.startDate)).append(", ")
                .append(TimeUtils.formatDateForCsv(report.endDate)).append(", ")
                .append(TimeUtils.formatDateForCsv(report.createdDate)).append(", ")
                .append(TimeUtils.formatDateForCsv(report.updatedDate)).append(", ")
                .append(report.beginningBalance).append(", ")
                .append(report.sales).append(", ")
                .append(report.otherIncome).append(", ")
                .append(report.wages).append(", ")
                .append(report.otherExpenses).append(", ")
                .append(profitLoss).append(", ")
                .append(endingBalance).append('\n')
        }
        return csvBuilder.toString()
    }
}
