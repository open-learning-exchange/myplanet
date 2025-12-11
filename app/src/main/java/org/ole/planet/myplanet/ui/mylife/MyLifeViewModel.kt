package org.ole.planet.myplanet.ui.mylife

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import org.ole.planet.myplanet.model.RealmMyLife
import org.ole.planet.myplanet.repository.LifeRepository

@HiltViewModel
class MyLifeViewModel @Inject constructor(
    private val lifeRepository: LifeRepository
) : ViewModel() {
    suspend fun getMyLifeList(userId: String?): StateFlow<List<RealmMyLife>> =
        lifeRepository.getMyLifeList(userId).stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )
}
