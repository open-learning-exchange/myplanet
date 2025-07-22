package org.ole.planet.myplanet.ui.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.di.SyncRepository

@HiltViewModel
class SyncViewModel @Inject constructor(
    private val repository: SyncRepository
) : ViewModel() {

    private val _autoSyncEnabled = MutableStateFlow(true)
    val autoSyncEnabled: StateFlow<Boolean> = _autoSyncEnabled.asStateFlow()

    fun loadAutoSyncSettings() {
        viewModelScope.launch {
            _autoSyncEnabled.value = repository.isAutoSyncEnabled()
        }
    }

    suspend fun authenticateUser(
        username: String?,
        password: String?,
        isManagerMode: Boolean
    ) = repository.authenticateUser(username, password, isManagerMode)
}
