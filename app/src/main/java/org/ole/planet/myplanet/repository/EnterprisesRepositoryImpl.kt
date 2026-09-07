package org.ole.planet.myplanet.repository

import android.content.Context
import com.google.gson.JsonObject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.data.room.dao.TeamDao
import org.ole.planet.myplanet.model.FinanceReportParams
import org.ole.planet.myplanet.model.MyTeam
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.TimeProvider
import org.ole.planet.myplanet.utils.TimeUtils
import org.ole.planet.myplanet.utils.distinctByContent

class EnterprisesRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val teamDao: TeamDao,
    private val timeProvider: TimeProvider,
    private val dispatcherProvider: DispatcherProvider
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
        teamDao.upsert(reportEntry)
        if (report.imageName != null && report.imageData != null) {
            attachTeamImage(reportId, report.imageName, report.imageData)
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
        updateTeamEntityById(reportId) { report ->
            MyTeam.populateReportFields(doc, report)
            report.updated = true
            if (report.updatedDate == 0L) {
                report.updatedDate = timeProvider.now()
            }
        }
        if (payload.imageName != null && payload.imageData != null) {
            attachTeamImage(reportId, payload.imageName, payload.imageData)
        }
    }

    override suspend fun archiveReport(reportId: String) {
        if (reportId.isBlank()) return
        updateTeamEntityById(reportId) { report ->
            report.status = "archived"
            report.updated = true
        }
    }

    override fun getReportsFlow(teamId: String): Flow<List<MyTeam>> {
        return teamDao.observeNonArchivedReportsByTeamId(teamId)
            .distinctByContent { old, new ->
                old._id == new._id && old._rev == new._rev && old.status == new.status &&
                    old.description == new.description && old.beginningBalance == new.beginningBalance &&
                    old.sales == new.sales && old.otherIncome == new.otherIncome &&
                    old.wages == new.wages && old.otherExpenses == new.otherExpenses &&
                    old.startDate == new.startDate && old.endDate == new.endDate &&
                    old.updatedDate == new.updatedDate && old.updated == new.updated &&
                    old.imageName == new.imageName
            }
            .flowOn(dispatcherProvider.default)
    }

    override suspend fun exportReportsAsCsv(teamId: String, teamName: String): String {
        val reports = teamDao.getNonArchivedReportsByTeamId(teamId)
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


    private suspend fun attachTeamImage(teamId: String, imageName: String, imageData: ByteArray) {
        if (teamId.isBlank()) return
        val destFile = MyTeam.getAttachmentFile(context, teamId, imageName) ?: return
        withContext(dispatcherProvider.io) {
            destFile.parentFile?.mkdirs()
            destFile.writeBytes(imageData)
        }
        updateTeamEntityById(teamId) { team ->
            team.imageName = imageName
            team.updated = true
        }
    }

    private suspend fun updateTeamEntityById(id: String, updater: (MyTeam) -> Unit): Boolean {
        val entity = teamDao.getById(id) ?: return false
        val model = entity
        updater(model)
        teamDao.upsert(model)
        return true
    }
}
