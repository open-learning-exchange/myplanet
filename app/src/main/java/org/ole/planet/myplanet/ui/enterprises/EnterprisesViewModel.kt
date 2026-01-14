package org.ole.planet.myplanet.ui.enterprises

import androidx.lifecycle.ViewModel
import com.google.gson.JsonObject
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.repository.TeamsRepository

@HiltViewModel
class EnterprisesViewModel @Inject constructor(
    private val teamsRepository: TeamsRepository
) : ViewModel() {

    suspend fun addReport(doc: JsonObject) {
        withContext(Dispatchers.IO) {
            teamsRepository.addReport(doc)
        }
    }

    suspend fun updateReport(reportId: String, doc: JsonObject) {
        withContext(Dispatchers.IO) {
            teamsRepository.updateReport(reportId, doc)
        }
    }

    suspend fun archiveReport(reportId: String) {
        withContext(Dispatchers.IO) {
            teamsRepository.archiveReport(reportId)
        }
    }

    suspend fun getReportsFlow(teamId: String): Flow<List<RealmMyTeam>> {
        return teamsRepository.getReportsFlow(teamId)
    }

    suspend fun exportReportsAsCsv(reports: List<RealmMyTeam>, teamName: String): String {
        return withContext(Dispatchers.IO) {
            teamsRepository.exportReportsAsCsv(reports, teamName)
        }
    }
}
