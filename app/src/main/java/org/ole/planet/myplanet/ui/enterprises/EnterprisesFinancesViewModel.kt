package org.ole.planet.myplanet.ui.enterprises

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.FinanceHeaderState
import org.ole.planet.myplanet.model.Transaction
import org.ole.planet.myplanet.repository.TeamsFinancesRepository

@HiltViewModel
class EnterprisesFinancesViewModel @Inject constructor(
    private val teamsRepository: TeamsFinancesRepository
) : ViewModel() {

    private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())
    val transactions: StateFlow<List<Transaction>> = _transactions.asStateFlow()

    val headerState: StateFlow<FinanceHeaderState> = transactions
        .map { Transaction.calculateTotals(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = FinanceHeaderState()
        )

    private val _transactionCreated = MutableSharedFlow<Result<Unit>>(extraBufferCapacity = 1)
    val transactionCreated: SharedFlow<Result<Unit>> = _transactionCreated.asSharedFlow()

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

    fun createTransaction(
        teamId: String,
        type: String,
        note: String,
        amount: Int,
        date: Long,
        parentCode: String?,
        planetCode: String?,
        imageName: String?,
        imageData: ByteArray?
    ) {
        viewModelScope.launch {
            val result = teamsRepository.createTransaction(
                teamId = teamId,
                type = type,
                note = note,
                amount = amount,
                date = date,
                parentCode = parentCode,
                planetCode = planetCode,
                imageName = imageName,
                imageData = imageData
            )
            _transactionCreated.emit(result)
        }
    }
}
