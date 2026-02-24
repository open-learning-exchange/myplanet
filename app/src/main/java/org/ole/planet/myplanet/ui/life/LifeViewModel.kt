package org.ole.planet.myplanet.ui.life

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.RealmMyLife
import org.ole.planet.myplanet.repository.LifeRepository
import org.ole.planet.myplanet.services.UserSessionManager

@HiltViewModel
class LifeViewModel @Inject constructor(
    private val lifeRepository: LifeRepository,
    private val userSessionManager: UserSessionManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _myLifeList = MutableStateFlow<List<RealmMyLife>>(emptyList())
    val myLifeList: StateFlow<List<RealmMyLife>> = _myLifeList.asStateFlow()

    private val _message = MutableSharedFlow<String>()
    val message: SharedFlow<String> = _message.asSharedFlow()

    fun getAllMyLife() {
        viewModelScope.launch(Dispatchers.IO) {
            val user = userSessionManager.getUserModel()
            val list = lifeRepository.getMyLifeByUserId(user?.id)
            _myLifeList.value = list
        }
    }

    fun updateVisibility(myLife: RealmMyLife, isVisible: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            myLife._id?.let { id ->
                lifeRepository.updateVisibility(isVisible, id)
                val msg = if (!isVisible) {
                    "${myLife.title}${context.getString(R.string.is_now_hidden)}"
                } else {
                    "${myLife.title} ${context.getString(R.string.is_now_shown)}"
                }
                _message.emit(msg)
                getAllMyLife()
            }
        }
    }

    fun updateMyLifeListOrder(list: List<RealmMyLife>) {
        viewModelScope.launch(Dispatchers.IO) {
            lifeRepository.updateMyLifeListOrder(list)
        }
    }
}
