package org.ole.planet.myplanet.model

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import io.realm.RealmList

class ChatViewModel : ViewModel() {
    private val selectedChatHistoryLiveData = MutableLiveData<RealmList<Conversation>>()
    private val selectedId = MutableLiveData<String>()
    private val selectedRev = MutableLiveData<String>()
    private val selectedAiProvider = MutableLiveData<String?>()

    fun setSelectedChatHistory(conversations: RealmList<Conversation>) {
        selectedChatHistoryLiveData.value = conversations
    }

    fun getSelectedChatHistory(): LiveData<RealmList<Conversation>> {
        return selectedChatHistoryLiveData
    }

    fun setSelectedId(id: String) {
        selectedId.value = id
    }

    fun setSelectedRev(rev: String) {
        selectedRev.value = rev
    }

    fun getSelectedId(): LiveData<String> {
        return selectedId
    }

    fun getSelectedRev(): LiveData<String> {
        return selectedRev
    }

    fun setSelectedAiProvider(aiProvider: String?) {
        selectedAiProvider.value = aiProvider
    }
    fun getSelectedAiProvider(): LiveData<String?> {
        return selectedAiProvider
    }
}

