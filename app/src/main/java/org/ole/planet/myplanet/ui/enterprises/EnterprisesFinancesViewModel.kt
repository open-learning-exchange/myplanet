package org.ole.planet.myplanet.ui.enterprises

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.model.Transaction
import org.ole.planet.myplanet.repository.TeamsFinancesRepository
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.FileUtils
import org.ole.planet.myplanet.utils.TimeProvider
import dagger.hilt.android.qualifiers.ApplicationContext

@HiltViewModel
class EnterprisesFinancesViewModel @Inject constructor(
    private val teamsRepository: TeamsFinancesRepository,
    private val dispatcherProvider: DispatcherProvider,
    private val timeProvider: TimeProvider,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())
    val transactions: StateFlow<List<Transaction>> = _transactions.asStateFlow()

    private val _transactionCreated = Channel<Result<Unit>>(Channel.BUFFERED)
    val transactionCreated: Flow<Result<Unit>> = _transactionCreated.receiveAsFlow()

    private var transactionsJob: Job? = null

    fun createTransaction(
        teamId: String,
        type: String,
        note: String,
        amount: Int,
        date: Long,
        parentCode: String?,
        planetCode: String?,
        imageUri: Uri?
    ) {
        viewModelScope.launch {
            val (imageName, imageData) = withContext(dispatcherProvider.io) {
                val name = imageUri?.let { FileUtils.getDisplayName(context, it, timeProvider) }
                val data = imageUri?.let { FileUtils.readBytesFromUri(context, it) }
                name to data
            }

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
            _transactionCreated.send(result)
        }
    }

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
