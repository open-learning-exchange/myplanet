package org.ole.planet.myplanet.data.team

import android.content.SharedPreferences
import io.realm.Case
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmMyTeam.Companion.getMyTeamsByUserId

interface TeamRepository {
    suspend fun getTeams(fromDashboard: Boolean, type: String?, settings: SharedPreferences?): List<RealmMyTeam>
    suspend fun searchTeams(query: String, type: String?): List<RealmMyTeam>
}

class TeamRepositoryImpl(private val databaseService: DatabaseService) : TeamRepository {
    override suspend fun getTeams(fromDashboard: Boolean, type: String?, settings: SharedPreferences?): List<RealmMyTeam> {
        return withContext(Dispatchers.IO) {
            databaseService.realmInstance.use { realm ->
                val results = if (fromDashboard) {
                    getMyTeamsByUserId(realm, settings)
                } else {
                    var queryRealm = realm.where(RealmMyTeam::class.java)
                        .isEmpty("teamId")
                        .notEqualTo("status", "archived")
                    queryRealm = if (type.isNullOrEmpty() || type == "team") {
                        queryRealm.notEqualTo("type", "enterprise")
                    } else {
                        queryRealm.equalTo("type", "enterprise")
                    }
                    queryRealm.findAll()
                }
                realm.copyFromRealm(results)
            }
        }
    }

    override suspend fun searchTeams(query: String, type: String?): List<RealmMyTeam> {
        return withContext(Dispatchers.IO) {
            databaseService.realmInstance.use { realm ->
                var queryRealm = realm.where(RealmMyTeam::class.java)
                    .isEmpty("teamId")
                    .notEqualTo("status", "archived")
                    .contains("name", query, Case.INSENSITIVE)
                queryRealm = if (type.isNullOrEmpty() || type == "team") {
                    queryRealm.notEqualTo("type", "enterprise")
                } else {
                    queryRealm.equalTo("type", "enterprise")
                }
                val results = queryRealm.findAll()
                realm.copyFromRealm(results)
            }
        }
    }
}
