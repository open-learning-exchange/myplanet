package org.ole.planet.myplanet.ui.team

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import io.realm.Realm
import java.util.Date
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.utilities.AndroidDecrypter

@HiltViewModel
class TeamViewModel @Inject constructor(
    private val databaseService: DatabaseService
) : ViewModel() {

    private val realm: Realm
        get() = databaseService.realmInstance

    fun createTeam(
        currentUser: RealmUserModel,
        name: String?,
        fragmentType: String?,
        teamType: String?,
        details: Map<String, String>,
        isPublic: Boolean
    ) {
        if (!realm.isInTransaction) realm.beginTransaction()
        val teamId = AndroidDecrypter.generateIv()
        val team = realm.createObject(RealmMyTeam::class.java, teamId)
        team.status = "active"
        team.createdDate = Date().time
        if (fragmentType == "enterprise") {
            team.type = "enterprise"
            team.services = details["services"]
            team.rules = details["rules"]
        } else {
            team.type = "team"
            team.teamType = teamType
        }
        team.name = name
        team.description = details["desc"]
        team.createdBy = currentUser._id
        team.teamId = ""
        team.isPublic = isPublic
        team.userId = currentUser.id
        team.parentCode = currentUser.parentCode
        team.teamPlanetCode = currentUser.planetCode
        team.updated = true

        val teamMemberObj = realm.createObject(RealmMyTeam::class.java, AndroidDecrypter.generateIv())
        teamMemberObj.userId = currentUser._id
        teamMemberObj.teamId = teamId
        teamMemberObj.teamPlanetCode = currentUser.planetCode
        teamMemberObj.userPlanetCode = currentUser.planetCode
        teamMemberObj.docType = "membership"
        teamMemberObj.isLeader = true
        teamMemberObj.teamType = teamType
        teamMemberObj.updated = true

        realm.commitTransaction()
    }

    fun updateTeam(team: RealmMyTeam, name: String, details: Map<String, String>, userId: String?) {
        val r = team.realm
        if (!r.isInTransaction) {
            r.beginTransaction()
        }
        team.name = name
        team.services = details["services"]
        team.rules = details["rules"]
        team.limit = 12
        team.description = details["desc"]
        team.createdBy = userId
        team.updated = true
        r.commitTransaction()
    }
}
