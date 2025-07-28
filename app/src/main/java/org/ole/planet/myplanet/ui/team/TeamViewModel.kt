package org.ole.planet.myplanet.ui.team

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import io.realm.Realm
import io.realm.RealmResults
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmMyTeam.Companion.getMyTeamsByUserId

@HiltViewModel
class TeamViewModel @Inject constructor(
    private val databaseService: DatabaseService
) : ViewModel() {
    private val _teams = MutableStateFlow<List<RealmMyTeam>>(emptyList())
    val teams: StateFlow<List<RealmMyTeam>> = _teams.asStateFlow()

    private lateinit var realm: Realm
    private var results: RealmResults<RealmMyTeam>? = null

    fun loadTeams(type: String?, fromDashboard: Boolean, settings: SharedPreferences) {
        realm = databaseService.realmInstance
        results = if (fromDashboard) {
            getMyTeamsByUserId(realm, settings)
        } else {
            val query = realm.where(RealmMyTeam::class.java)
                .isEmpty("teamId")
                .notEqualTo("status", "archived")
            if (type.isNullOrEmpty() || type == "team") {
                query.notEqualTo("type", "enterprise").findAllAsync()
            } else {
                query.equalTo("type", "enterprise").findAllAsync()
            }
        }
        results?.addChangeListener { r ->
            _teams.value = realm.copyFromRealm(r)
        }
        results?.let { _teams.value = realm.copyFromRealm(it) }
    }

    override fun onCleared() {
        results?.removeAllChangeListeners()
        if (this::realm.isInitialized && !realm.isClosed) {
            realm.close()
        }
        super.onCleared()
    }
}
