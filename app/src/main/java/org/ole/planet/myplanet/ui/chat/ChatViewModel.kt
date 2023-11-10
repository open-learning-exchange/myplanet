package org.ole.planet.myplanet.ui.chat

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import io.realm.RealmList
import org.ole.planet.myplanet.model.Conversation

class ChatViewModel : ViewModel() {
    private val selectedChatHistoryLiveData = MutableLiveData<RealmList<Conversation>>()

    fun setSelectedChatHistory(conversations: RealmList<Conversation>) {
        selectedChatHistoryLiveData.value = conversations
    }

    fun getSelectedChatHistory(): LiveData<RealmList<Conversation>> {
        return selectedChatHistoryLiveData
    }
}
