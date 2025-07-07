package org.ole.planet.myplanet.ui.team

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.realm.Realm
import io.realm.RealmResults
import java.util.Date
import javax.inject.Inject
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmMyTeam.Companion.getMyTeamsByUserId
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.utilities.AndroidDecrypter

@HiltViewModel
class TeamViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val databaseService: DatabaseService,
    private val prefs: SharedPreferences
) : ViewModel() {

    private val realm: Realm = databaseService.realmInstance
    private val userHandler = UserProfileDbHandler(context)
    private var teamResults: RealmResults<RealmMyTeam>? = null

    private val _teams = MutableLiveData<List<RealmMyTeam>>()
    val teams: LiveData<List<RealmMyTeam>> = _teams

    fun loadTeams(fromDashboard: Boolean, type: String?) {
        teamResults?.removeAllChangeListeners()
        teamResults = if (fromDashboard) {
            getMyTeamsByUserId(realm, prefs)
        } else {
            var query = realm.where(RealmMyTeam::class.java)
                .isEmpty("teamId")
                .notEqualTo("status", "archived")
            query = if (type.isNullOrEmpty() || type == "team") {
                query.notEqualTo("type", "enterprise")
            } else {
                query.equalTo("type", "enterprise")
            }
            query.findAllAsync()
        }
        teamResults?.addChangeListener { results ->
            publish(results)
        }
        teamResults?.let { publish(it) }
    }

    private fun publish(results: RealmResults<RealmMyTeam>) {
        val list = realm.copyFromRealm(results)
        val sorted = sortTeams(list)
        _teams.postValue(sorted)
    }

    private fun sortTeams(list: List<RealmMyTeam>): List<RealmMyTeam> {
        val userId = userHandler.userModel?.id
        return list.sortedWith(compareByDescending<RealmMyTeam> { team ->
            when {
                RealmMyTeam.isTeamLeader(team.teamId, userId, realm) -> 3
                team.isMyTeam(userId, realm) -> 2
                else -> 1
            }
        }.thenBy { it.name })
    }

    fun createTeam(
        name: String?,
        teamType: String?,
        map: Map<String, String>,
        isPublic: Boolean,
        typeParam: String?
    ) {
        val user = userHandler.userModel ?: return
        if (!realm.isInTransaction) realm.beginTransaction()
        val teamId = AndroidDecrypter.generateIv()
        val team = realm.createObject(RealmMyTeam::class.java, teamId)
        team.status = "active"
        team.createdDate = Date().time
        if (typeParam == "enterprise") {
            team.type = "enterprise"
            team.services = map["services"]
            team.rules = map["rules"]
        } else {
            team.type = "team"
            team.teamType = teamType
        }
        team.name = name
        team.description = map["desc"]
        team.createdBy = user._id
        team.teamId = ""
        team.isPublic = isPublic
        team.userId = user.id
        team.parentCode = user.parentCode
        team.teamPlanetCode = user.planetCode
        team.updated = true

        val member = realm.createObject(RealmMyTeam::class.java, AndroidDecrypter.generateIv())
        member.userId = user._id
        member.teamId = teamId
        member.teamPlanetCode = user.planetCode
        member.userPlanetCode = user.planetCode
        member.docType = "membership"
        member.isLeader = true
        member.teamType = teamType
        member.updated = true

        realm.commitTransaction()
    }

    fun getRealm(): Realm = realm

    fun getUserId(): String? = userHandler.userModel?.id

    fun isGuest(): Boolean {
        return userHandler.userModel?.isGuest() == true
    }

    override fun onCleared() {
        super.onCleared()
        if (!realm.isClosed) realm.close()
    }
}
