package org.ole.planet.myplanet.ui.community.leaders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.domain.UsersRepository
import org.ole.planet.myplanet.ui.community.leaders.models.LeadersUIState
import javax.inject.Inject

@HiltViewModel
class LeadersViewModel @Inject constructor(
    private val usersRepository: UsersRepository
): ViewModel() {

    private val _leadersUIState: MutableStateFlow<LeadersUIState> =
            MutableStateFlow(LeadersUIState())
    val leadersUIState: StateFlow<LeadersUIState> get() = _leadersUIState.asStateFlow()
    init {
        getLeaders()
    }

    private fun getLeaders() {
        viewModelScope.launch {
            usersRepository.getLeaders()
                .onStart {
                    _leadersUIState.update { currentState ->
                        currentState.copy(isLoading = true)
                    }
                }.catch { cause: Throwable ->
                    _leadersUIState.update { currentState ->
                        currentState.copy(
                            errorMessage = cause.message,
                            isLoading = false
                        )
                    }
                }.collect { leaders ->
                    _leadersUIState.update { currentState ->
                        currentState.copy(
                            errorMessage = null,
                            isLoading = false,
                            leaders = leaders
                        )
                    }
                }
        }
    }
}