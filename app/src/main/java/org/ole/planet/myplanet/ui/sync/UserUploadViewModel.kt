package org.ole.planet.myplanet.ui.sync

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.repository.SyncRepository
import org.ole.planet.myplanet.repository.SyncUiState
import org.ole.planet.myplanet.repository.UserRepository

@HiltViewModel
class UserUploadViewModel @Inject constructor(
    private val syncRepository: SyncRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    fun uploadLoginData(): Flow<SyncUiState> {
        return syncRepository.uploadLoginData()
    }

    fun uploadBulkData(): Flow<SyncUiState> {
        return syncRepository.uploadBulkData()
    }

    suspend fun fetchUserSecurityData(name: String) {
        userRepository.fetchUserSecurityData(name)
    }
}
