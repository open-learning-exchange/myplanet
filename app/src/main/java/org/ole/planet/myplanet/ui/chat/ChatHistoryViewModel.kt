package org.ole.planet.myplanet.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.data.chat.ChatRepository
import org.ole.planet.myplanet.model.RealmChatHistory

@HiltViewModel
class ChatHistoryViewModel @Inject constructor(
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val _chatHistory = MutableStateFlow<List<RealmChatHistory>>(emptyList())
    val chatHistory: StateFlow<List<RealmChatHistory>> = _chatHistory.asStateFlow()

    fun loadChatHistory(userName: String?) {
        viewModelScope.launch {
            _chatHistory.value = chatRepository.getChatHistoryForUser(userName)
        }
    }
}
