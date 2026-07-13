package org.ole.planet.myplanet.ui.enterprises

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.Transaction
import org.ole.planet.myplanet.repository.TeamsRepository

@HiltViewModel
class EnterprisesFinancesViewModel @Inject constructor(
    private val teamsRepository: TeamsRepository
) : ViewModel() {

    private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())
    val transactions: StateFlow<List<Transaction>> = _transactions.asStateFlow()

    private var transactionsJob: Job? = null

    fun getTeamTransactions(
        teamId: String,
        sortAscending: Boolean,
        startDate: Long?,
        endDate: Long?
    ) {
        transactionsJob?.cancel()
        transactionsJob = viewModelScope.launch {
            teamsRepository.getTeamTransactionsWithBalance(
                teamId = teamId,
                startDate = startDate,
                endDate = endDate,
                sortAscending = sortAscending
            ).collectLatest { results ->
                _transactions.value = results
            }
        }
    }
}
