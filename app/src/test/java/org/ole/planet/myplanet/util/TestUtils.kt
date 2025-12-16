package org.ole.planet.myplanet.util

import io.realm.Realm
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmUserModel

fun createTeamAndMember(realm: Realm, teamId: String, userId: String): Pair<String, String> {
    var team = ""
    var user = ""
    realm.executeTransaction {
        val teamObject = it.createObject(RealmMyTeam::class.java, teamId).apply {
            name = "Test Team"
        }
        team = teamObject._id ?: ""
        val userObject = it.createObject(RealmUserModel::class.java, userId).apply {
            name = "Test User"
        }
        user = userObject.id ?: ""
    }
    return Pair(team, user)
}
