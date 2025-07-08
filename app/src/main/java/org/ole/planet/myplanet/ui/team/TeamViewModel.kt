package org.ole.planet.myplanet.ui.team

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import io.realm.Case
import io.realm.Realm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.utilities.AndroidDecrypter
import org.ole.planet.myplanet.utilities.Constants
import java.util.Date

class TeamViewModel(application: Application) : AndroidViewModel(application) {
    private val realm: Realm = DatabaseService(application).realmInstance
    private val settings = application.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
    private val user = UserProfileDbHandler(application).userModel

    private val _teams = MutableLiveData<List<RealmMyTeam>>()
    val teams: LiveData<List<RealmMyTeam>> = _teams

    private val _toastMessage = MutableLiveData<String>()
    val toastMessage: LiveData<String> = _toastMessage

    val realmInstance: Realm
        get() = realm

    fun getUserId(): String? = user?.id

    private var fromDashboard: Boolean = false
    private var type: String? = null

    fun init(fromDashboard: Boolean, type: String?) {
        this.fromDashboard = fromDashboard
        this.type = type
        loadTeams()
    }

    fun isGuest(): Boolean = user?.isGuest() == true

    fun loadTeams() {
        val results = if (fromDashboard) {
            RealmMyTeam.getMyTeamsByUserId(realm, settings)
        } else {
            var query = realm.where(RealmMyTeam::class.java)
                .isEmpty("teamId")
                .notEqualTo("status", "archived")
            query = if (type.isNullOrEmpty() || type == "team") {
                query.notEqualTo("type", "enterprise")
            } else {
                query.equalTo("type", "enterprise")
            }
            query.findAll()
        }
        _teams.value = realm.copyFromRealm(results)
    }

    fun searchTeams(queryStr: String) {
        if (queryStr.isBlank()) {
            loadTeams()
            return
        }
        val results = if (fromDashboard) {
            RealmMyTeam.getMyTeamsByUserId(realm, settings)
                .where()
                .contains("name", queryStr, Case.INSENSITIVE)
                .findAll()
        } else {
            var q = realm.where(RealmMyTeam::class.java)
                .isEmpty("teamId")
                .notEqualTo("status", "archived")
                .contains("name", queryStr, Case.INSENSITIVE)
            q = if (type.isNullOrEmpty() || type == "team") {
                q.notEqualTo("type", "enterprise")
            } else {
                q.equalTo("type", "enterprise")
            }
            q.findAll()
        }
        _teams.value = realm.copyFromRealm(results)
    }

    fun createTeam(name: String, map: HashMap<String, String>, isPublic: Boolean) {
        val currentUser = user ?: return
        val teamType = type
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                realm.executeTransaction { r ->
                    val teamId = AndroidDecrypter.generateIv()
                    r.createObject(RealmMyTeam::class.java, teamId).apply {
                        status = "active"
                        createdDate = Date().time
                        if (teamType == "enterprise") {
                            this.type = "enterprise"
                            this.services = map["services"]
                            this.rules = map["rules"]
                        } else {
                            this.type = "team"
                            this.teamType = teamType
                        }
                        this.name = name
                        this.description = map["desc"]
                        this.createdBy = currentUser._id
                        this.teamId = ""
                        this.isPublic = isPublic
                        this.userId = currentUser.id
                        this.parentCode = currentUser.parentCode
                        this.teamPlanetCode = currentUser.planetCode
                        this.updated = true
                    }
                    r.createObject(RealmMyTeam::class.java, AndroidDecrypter.generateIv()).apply {
                        userId = currentUser._id
                        this.teamId = teamId
                        teamPlanetCode = currentUser.planetCode
                        userPlanetCode = currentUser.planetCode
                        docType = "membership"
                        isLeader = true
                        this.teamType = teamType
                        updated = true
                    }
                }
            }
            _toastMessage.value = "Team created"
            loadTeams()
        }
    }

    override fun onCleared() {
        if (!realm.isClosed) realm.close()
        super.onCleared()
    }
}

