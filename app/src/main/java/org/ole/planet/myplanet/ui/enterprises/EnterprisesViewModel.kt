package org.ole.planet.myplanet.ui.enterprises

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.FinanceReportParams
import org.ole.planet.myplanet.model.MyTeam
import org.ole.planet.myplanet.repository.EnterprisesRepository

sealed class ReportEvent {
    object ReportAdded : ReportEvent()
    object ReportUpdated : ReportEvent()
    object ReportArchived : ReportEvent()
    data class Error(val message: String) : ReportEvent()
}

@HiltViewModel
class EnterprisesViewModel @Inject constructor(
    private val enterprisesRepository: EnterprisesRepository
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
        teamPlanetCode: String?,
        imageName: String? = null,
        imageData: ByteArray? = null
    ) {
        viewModelScope.launch {
            try {
                val params = FinanceReportParams(
                    description, beginningBalance, sales, otherIncome, wages,
                    otherExpenses, startDate, endDate, teamId, teamType, teamPlanetCode,
                    imageName, imageData
                )
                enterprisesRepository.addReport(params)
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
        endDate: Long,
        imageName: String? = null,
        imageData: ByteArray? = null
    ) {
        viewModelScope.launch {
            try {
                val params = FinanceReportParams(
                    description, beginningBalance, sales, otherIncome, wages,
                    otherExpenses, startDate, endDate, "", null, null,
                    imageName, imageData
                )
                enterprisesRepository.updateReport(reportId, params)
                _reportEvent.emit(ReportEvent.ReportUpdated)
            } catch (e: Exception) {
                _reportEvent.emit(ReportEvent.Error("Failed to update report. Please try again."))
            }
        }
    }

    fun archiveReport(reportId: String) {
        viewModelScope.launch {
            try {
                enterprisesRepository.archiveReport(reportId)
                _reportEvent.emit(ReportEvent.ReportArchived)
            } catch (e: Exception) {
                _reportEvent.emit(ReportEvent.Error("Failed to delete report."))
            }
        }
    }

    fun getReportsFlow(teamId: String): Flow<List<MyTeam>> {
        return enterprisesRepository.getReportsFlow(teamId)
    }

    suspend fun exportReportsAsCsv(teamId: String, teamName: String): String {
        return enterprisesRepository.exportReportsAsCsv(teamId, teamName)
    }
}
