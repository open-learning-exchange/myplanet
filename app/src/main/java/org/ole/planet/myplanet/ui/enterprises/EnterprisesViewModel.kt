package org.ole.planet.myplanet.ui.enterprises

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonObject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.repository.TeamsRepository

sealed class ReportEvent {
    object ReportAdded : ReportEvent()
    object ReportUpdated : ReportEvent()
    object ReportArchived : ReportEvent()
    data class Error(val message: String) : ReportEvent()
}

@HiltViewModel
class EnterprisesViewModel @Inject constructor(
    private val teamsRepository: TeamsRepository
) : ViewModel() {

    private val _reportEvent = MutableSharedFlow<ReportEvent>()
    val reportEvent: SharedFlow<ReportEvent> = _reportEvent.asSharedFlow()

    fun addReport(
        description: String,
        beginningBalance: Int,
        sales: Int,
        otherIncome: Int,
        wages: Int,
        otherExpenses: Int,
        startDate: Long,
        endDate: Long,
        teamId: String,
        teamType: String?,
        teamPlanetCode: String?
    ) {
        viewModelScope.launch {
            try {
                val doc = JsonObject().apply {
                    addProperty("_id", UUID.randomUUID().toString())
                    addProperty("createdDate", System.currentTimeMillis())
                    addProperty("description", description)
                    addProperty("beginningBalance", beginningBalance)
                    addProperty("sales", sales)
                    addProperty("otherIncome", otherIncome)
                    addProperty("wages", wages)
                    addProperty("otherExpenses", otherExpenses)
                    addProperty("startDate", startDate)
                    addProperty("endDate", endDate)
                    addProperty("updatedDate", System.currentTimeMillis())
                    addProperty("teamId", teamId)
                    addProperty("teamType", teamType)
                    addProperty("teamPlanetCode", teamPlanetCode)
                    addProperty("docType", "report")
                    addProperty("updated", true)
                }
                teamsRepository.addReport(doc)
                _reportEvent.emit(ReportEvent.ReportAdded)
            } catch (e: Exception) {
                _reportEvent.emit(ReportEvent.Error("Failed to add report. Please try again."))
            }
        }
    }

    fun updateReport(
        reportId: String,
        description: String,
        beginningBalance: Int,
        sales: Int,
        otherIncome: Int,
        wages: Int,
        otherExpenses: Int,
        startDate: Long,
        endDate: Long
    ) {
        viewModelScope.launch {
            try {
                val doc = JsonObject().apply {
                    addProperty("description", description)
                    addProperty("beginningBalance", beginningBalance)
                    addProperty("sales", sales)
                    addProperty("otherIncome", otherIncome)
                    addProperty("wages", wages)
                    addProperty("otherExpenses", otherExpenses)
                    addProperty("startDate", startDate)
                    addProperty("endDate", endDate)
                    addProperty("updatedDate", System.currentTimeMillis())
                    addProperty("updated", true)
                }
                teamsRepository.updateReport(reportId, doc)
                _reportEvent.emit(ReportEvent.ReportUpdated)
            } catch (e: Exception) {
                _reportEvent.emit(ReportEvent.Error("Failed to update report. Please try again."))
            }
        }
    }

    fun archiveReport(reportId: String) {
        viewModelScope.launch {
            try {
                teamsRepository.archiveReport(reportId)
                _reportEvent.emit(ReportEvent.ReportArchived)
            } catch (e: Exception) {
                _reportEvent.emit(ReportEvent.Error("Failed to delete report."))
            }
        }
    }

    suspend fun getReportsFlow(teamId: String): Flow<List<RealmMyTeam>> {
        return teamsRepository.getReportsFlow(teamId)
    }

    suspend fun exportReportsAsCsv(reports: List<RealmMyTeam>, teamName: String): String {
        return teamsRepository.exportReportsAsCsv(reports, teamName)
    }
}
