package org.ole.planet.myplanet.ui.chat

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import io.realm.RealmList
import org.ole.planet.myplanet.model.Conversation

class ChatViewModel : ViewModel() {
    private val selectedChatHistoryLiveData = MutableLiveData<RealmList<Conversation>>()
    private val selected_Id = MutableLiveData<String>()
    private val selected_rev = MutableLiveData<String>()

    fun setSelectedChatHistory(conversations: RealmList<Conversation>) {
        selectedChatHistoryLiveData.value = conversations
    }

    fun getSelectedChatHistory(): LiveData<RealmList<Conversation>> {
        return selectedChatHistoryLiveData
    }

    fun setSelected_id(_id: String) {
        selected_Id.value = _id
    }

    fun setSelected_rev(_rev: String) {
        selected_rev.value = _rev
    }

    fun getSelected_id(): LiveData<String> {
        return selected_Id
    }

    fun getSelected_rev(): LiveData<String> {
        return selected_rev
    }
}

