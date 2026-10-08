package org.ole.planet.myplanet.ui.enterprises

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
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
import org.ole.planet.myplanet.di.ApplicationScope
import org.ole.planet.myplanet.model.Transaction
import org.ole.planet.myplanet.repository.TeamsFinancesRepository
import org.ole.planet.myplanet.utils.AttachmentReader
import org.ole.planet.myplanet.utils.UriAttachment

data class FinanceSummaryUiState(
    val debit: Int = 0,
    val credit: Int = 0,
    val total: Int = 0,
    val isCautionVisible: Boolean = false
) {
    companion object {
        fun from(transactions: List<Transaction>): FinanceSummaryUiState {
            var debit = 0
            var credit = 0
            for (transaction in transactions) {
                if ("credit".equals(transaction.type, ignoreCase = true)) {
                    credit += transaction.amount
                } else {
                    debit += transaction.amount
                }
            }
            val total = credit - debit
            return FinanceSummaryUiState(
                debit = debit,
                credit = credit,
                total = total,
                isCautionVisible = total < 0
            )
        }
    }
}

@HiltViewModel
class EnterprisesFinancesViewModel @Inject constructor(
    private val teamsRepository: TeamsFinancesRepository,
    @ApplicationScope private val appScope: CoroutineScope,
    private val attachmentReader: AttachmentReader
) : ViewModel() {

    private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())
    val transactions: StateFlow<List<Transaction>> = _transactions.asStateFlow()

    val financeSummary: StateFlow<FinanceSummaryUiState> = transactions
        .map { FinanceSummaryUiState.from(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = FinanceSummaryUiState()
        )

    private val _transactionCreated = MutableSharedFlow<Result<Unit>>(extraBufferCapacity = 1)
    val transactionCreated: SharedFlow<Result<Unit>> = _transactionCreated.asSharedFlow()

    private data class TransactionQuery(
        val teamId: String,
        val sortAscending: Boolean,
        val startDate: Long?,
        val endDate: Long?
    )

    private var lastQuery: TransactionQuery? = null
    private var transactionsJob: Job? = null

    fun getTeamTransactions(
        teamId: String,
        sortAscending: Boolean,
        startDate: Long?,
        endDate: Long?
    ) {
        val query = TransactionQuery(teamId, sortAscending, startDate, endDate)
        if (lastQuery == query && transactionsJob?.isActive == true) {
            return
        }
        lastQuery = query
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
        imageUri: Uri? = null,
        imageName: String? = null,
        imageData: ByteArray? = null
    ) {
        appScope.launch {
            try {
                val (resolvedName, resolvedData) = if (imageUri != null) {
                    attachmentReader.read(imageUri)
                } else {
                    UriAttachment(imageName, imageData)
                }
                val result = teamsRepository.createTransaction(
                    teamId = teamId,
                    type = type,
                    note = note,
                    amount = amount,
                    date = date,
                    parentCode = parentCode,
                    planetCode = planetCode,
                    imageName = resolvedName,
                    imageData = resolvedData
                )
                _transactionCreated.emit(result)
            } catch (e: Exception) {
                _transactionCreated.emit(Result.failure(e))
            }
        }
    }
}
