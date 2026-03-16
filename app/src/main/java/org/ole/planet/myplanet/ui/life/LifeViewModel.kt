package org.ole.planet.myplanet.ui.life

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.RealmMyLife
import org.ole.planet.myplanet.repository.LifeRepository
import org.ole.planet.myplanet.services.UserSessionManager

@HiltViewModel
class LifeViewModel @Inject constructor(
    private val lifeRepository: LifeRepository,
    private val userSessionManager: UserSessionManager
) : ViewModel() {

    private val _myLifeList = MutableStateFlow<List<RealmMyLife>>(emptyList())
    val myLifeList: StateFlow<List<RealmMyLife>> = _myLifeList.asStateFlow()

    fun loadMyLife(userId: String?) {
        viewModelScope.launch {
            val items = getMyLifeListBase(userId)
            lifeRepository.seedMyLifeIfEmpty(userId, items)
            _myLifeList.value = lifeRepository.getMyLifeByUserId(userId)
        }
    }

    private fun getMyLifeListBase(userId: String?): List<RealmMyLife> {
        val myLifeList: MutableList<RealmMyLife> = ArrayList()
        myLifeList.add(RealmMyLife("ic_myhealth", userId, "MyHealth"))
        myLifeList.add(RealmMyLife("my_achievement", userId, "Achievements"))
        myLifeList.add(RealmMyLife("ic_submissions", userId, "Submissions"))
        myLifeList.add(RealmMyLife("ic_my_survey", userId, "My Surveys"))
        myLifeList.add(RealmMyLife("ic_references", userId, "References"))
        myLifeList.add(RealmMyLife("ic_calendar", userId, "Calendar"))
        myLifeList.add(RealmMyLife("ic_mypersonals", userId, "MyPersonals"))
        return myLifeList
    }

    suspend fun updateVisibility(isVisible: Boolean, id: String) {
        lifeRepository.updateVisibility(isVisible, id)
    }

    suspend fun reorder(list: List<RealmMyLife>) {
        lifeRepository.updateMyLifeListOrder(list)
    }
}
