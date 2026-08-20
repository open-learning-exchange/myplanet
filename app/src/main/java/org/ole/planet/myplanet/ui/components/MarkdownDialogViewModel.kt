package org.ole.planet.myplanet.ui.components

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import org.ole.planet.myplanet.repository.UserRepository

@HiltViewModel
class MarkdownDialogViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {
    suspend fun hasActiveUserSyncAction(): Boolean {
        return userRepository.hasActiveUserSyncAction()
    }
}
