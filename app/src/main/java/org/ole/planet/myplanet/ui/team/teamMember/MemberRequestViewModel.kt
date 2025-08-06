package org.ole.planet.myplanet.ui.team.teamMember

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.repository.TeamRepository
import org.ole.planet.myplanet.service.UploadManager

@HiltViewModel
class MemberRequestViewModel @Inject constructor(
    private val teamRepository: TeamRepository,
    private val uploadManager: UploadManager
) : ViewModel() {

    private val _result = MutableLiveData<Result<Unit>>()
    val result: LiveData<Result<Unit>> = _result

    fun acceptRequest(teamId: String, userId: String) {
        viewModelScope.launch {
            val res = runCatching {
                teamRepository.acceptRequest(teamId, userId)
                RealmMyTeam.syncTeamActivities(MainApplication.context, uploadManager)
            }
            _result.postValue(res)
        }
    }

    fun rejectRequest(teamId: String, userId: String) {
        viewModelScope.launch {
            val res = runCatching {
                teamRepository.rejectRequest(teamId, userId)
                RealmMyTeam.syncTeamActivities(MainApplication.context, uploadManager)
            }
            _result.postValue(res)
        }
    }
}

