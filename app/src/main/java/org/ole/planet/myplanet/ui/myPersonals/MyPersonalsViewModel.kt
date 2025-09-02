package org.ole.planet.myplanet.ui.myPersonals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.RealmMyPersonal
import org.ole.planet.myplanet.repository.MyPersonalRepository

@HiltViewModel
class MyPersonalsViewModel @Inject constructor(
    private val myPersonalRepository: MyPersonalRepository
) : ViewModel() {

    private val _personalItems = MutableStateFlow<List<RealmMyPersonal>>(emptyList())
    val personalItems: StateFlow<List<RealmMyPersonal>> = _personalItems.asStateFlow()

    fun loadPersonalItems(userId: String?) {
        viewModelScope.launch {
            _personalItems.value = myPersonalRepository.getPersonalItems(userId)
        }
    }
}
