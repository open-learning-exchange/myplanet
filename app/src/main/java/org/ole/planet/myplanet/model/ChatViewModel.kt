package org.ole.planet.myplanet.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import io.realm.RealmList
import org.ole.planet.myplanet.chat.ChatRepository
import org.ole.planet.myplanet.model.RealmChatHistory

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository
) : ViewModel() {
    private val _selectedChatHistory = MutableStateFlow<RealmList<Conversation>?>(null)
    val selectedChatHistory: StateFlow<RealmList<Conversation>?> = _selectedChatHistory.asStateFlow()

    private val _selectedId = MutableStateFlow("")
    val selectedId: StateFlow<String> = _selectedId.asStateFlow()

    private val _selectedRev = MutableStateFlow("")
    val selectedRev: StateFlow<String> = _selectedRev.asStateFlow()

    private val _selectedAiProvider = MutableStateFlow<String?>(null)
    val selectedAiProvider: StateFlow<String?> = _selectedAiProvider.asStateFlow()

    private val _chatHistory = MutableStateFlow<List<RealmChatHistory>>(emptyList())
    val chatHistory: StateFlow<List<RealmChatHistory>> = _chatHistory.asStateFlow()

    fun loadChatHistory(user: String) {
        viewModelScope.launch {
            chatRepository.getChatHistory(user).collect { history ->
                _chatHistory.value = history
            }
        }
    }

    fun setSelectedChatHistory(conversations: RealmList<Conversation>) {
        _selectedChatHistory.value = conversations
    }

    fun setSelectedId(id: String) {
        _selectedId.value = id
    }

    fun setSelectedRev(rev: String) {
        _selectedRev.value = rev
    }

    fun setSelectedAiProvider(aiProvider: String?) {
        _selectedAiProvider.value = aiProvider
    }
}

