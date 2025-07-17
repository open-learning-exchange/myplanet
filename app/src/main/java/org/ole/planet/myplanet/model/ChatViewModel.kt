package org.ole.planet.myplanet.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import io.realm.RealmList

data class ChatUiState(
    val selectedChatHistory: RealmList<Conversation>? = null,
    val selectedId: String = "",
    val selectedRev: String = "",
    val selectedAiProvider: String? = null
)

class ChatViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    fun setSelectedChatHistory(conversations: RealmList<Conversation>) {
        _uiState.value = _uiState.value.copy(selectedChatHistory = conversations)
    }

    fun setSelectedId(id: String) {
        _uiState.value = _uiState.value.copy(selectedId = id)
    }

    fun setSelectedRev(rev: String) {
        _uiState.value = _uiState.value.copy(selectedRev = rev)
    }

    fun setSelectedAiProvider(aiProvider: String?) {
        _uiState.value = _uiState.value.copy(selectedAiProvider = aiProvider)
    }
}

