package org.ole.planet.myplanet.ui.enterprises

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonObject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.repository.TeamsRepository

@HiltViewModel
class EnterprisesViewModel @Inject constructor(
    private val teamsRepository: TeamsRepository
) : ViewModel() {

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
        teamPlanetCode: String?,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
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
                onSuccess()
            } catch (e: Exception) {
                onError(e)
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
        endDate: Long,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
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
                onSuccess()
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    fun archiveReport(reportId: String, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        viewModelScope.launch {
            try {
                teamsRepository.archiveReport(reportId)
                onSuccess()
            } catch (e: Exception) {
                onError(e)
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
