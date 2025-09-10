package org.ole.planet.myplanet.repository

import android.content.SharedPreferences
import io.realm.Case
import javax.inject.Inject
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.di.AppPreferences

class TeamRepositoryImpl @Inject constructor(
    databaseService: DatabaseService,
    @AppPreferences private val settings: SharedPreferences,
) : RealmRepository(databaseService), TeamRepository {

    override suspend fun getTeamResources(teamId: String): List<RealmMyLibrary> {
        val resourceIds = getResourceIds(teamId)
        return if (resourceIds.isEmpty()) {
            emptyList()
        } else {
            queryList(RealmMyLibrary::class.java) {
                `in`("resourceId", resourceIds.toTypedArray())
            }
        }
    }

    private suspend fun getResourceIds(teamId: String): List<String> {
        return queryList(RealmMyTeam::class.java) {
            equalTo("teamId", teamId)
        }.mapNotNull { it.resourceId?.takeIf { id -> id.isNotBlank() } }
    }

    override suspend fun getTeams(type: String?, search: String?): List<RealmMyTeam> {
        val userId = settings.getString("userId", "--") ?: "--"
        return withRealm { realm ->
            if (type == "my") {
                val membershipIds = realm.where(RealmMyTeam::class.java)
                    .equalTo("userId", userId)
                    .equalTo("docType", "membership")
                    .findAll()
                    .mapNotNull { it.teamId }
                    .toTypedArray()
                var query = realm.where(RealmMyTeam::class.java)
                    .`in`("_id", membershipIds)
                if (!search.isNullOrBlank()) {
                    query = query.contains("name", search, Case.INSENSITIVE)
                }
                realm.copyFromRealm(query.findAll())
            } else {
                var query = realm.where(RealmMyTeam::class.java)
                    .isEmpty("teamId")
                    .notEqualTo("status", "archived")
                if (!search.isNullOrBlank()) {
                    query = query.contains("name", search, Case.INSENSITIVE)
                }
                query = if (type.isNullOrEmpty() || type == "team") {
                    query.notEqualTo("type", "enterprise")
                } else {
                    query.equalTo("type", "enterprise")
                }
                realm.copyFromRealm(query.findAll())
            }
        }
    }

    override suspend fun sortTeams(list: List<RealmMyTeam>): List<RealmMyTeam> {
        val userId = settings.getString("userId", null)
        return withRealm { realm ->
            list.sortedWith(compareByDescending<RealmMyTeam> { team ->
                when {
                    RealmMyTeam.isTeamLeader(team.teamId, userId, realm) -> 3
                    team.isMyTeam(userId, realm) -> 2
                    else -> 1
                }
            })
        }
    }
}

