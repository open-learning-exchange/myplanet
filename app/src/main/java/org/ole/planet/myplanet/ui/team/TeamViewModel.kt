package org.ole.planet.myplanet.ui.team

import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import io.realm.Realm
import io.realm.RealmResults
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmMyTeam.Companion.getMyTeamsByUserId
import org.ole.planet.myplanet.model.RealmUserModel

class TeamViewModel : ViewModel() {
    private lateinit var realm: Realm
    private var teamResults: RealmResults<RealmMyTeam>? = null

    private val _isGuestUser = MutableLiveData<Boolean>()
    val isGuestUser: LiveData<Boolean> = _isGuestUser

    private val _teams = MutableStateFlow<List<RealmMyTeam>>(emptyList())
    val teams: StateFlow<List<RealmMyTeam>> = _teams.asStateFlow()

    fun init(realm: Realm, user: RealmUserModel?) {
        this.realm = realm
        _isGuestUser.value = user?.isGuest() == true
    }

    fun loadTeams(fromDashboard: Boolean, type: String?, settings: SharedPreferences?) {
        teamResults?.removeAllChangeListeners()
        teamResults = if (fromDashboard) {
            getMyTeamsByUserId(realm, settings)
        } else {
            val query = realm.where(RealmMyTeam::class.java)
                .isEmpty("teamId")
                .notEqualTo("status", "archived")
            if (type.isNullOrEmpty() || type == "team") {
                query.notEqualTo("type", "enterprise")
            } else {
                query.equalTo("type", "enterprise")
            }
            query.findAllAsync()
        }
        teamResults?.let { results ->
            _teams.value = results.toList()
            results.addChangeListener { updated ->
                _teams.value = updated.toList()
            }
        }
    }

    override fun onCleared() {
        teamResults?.removeAllChangeListeners()
        if (::realm.isInitialized && !realm.isClosed) {
            realm.close()
        }
        super.onCleared()
    }
}

