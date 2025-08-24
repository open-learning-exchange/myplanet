package org.ole.planet.myplanet.repository

import com.google.gson.Gson
import com.google.gson.JsonObject
import io.realm.Case
import io.realm.Sort
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.datamanager.queryList
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmTeamLog
import org.ole.planet.myplanet.model.RealmTeamTask
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.utilities.AndroidDecrypter

class TeamRepositoryImpl @Inject constructor(
    databaseService: DatabaseService,
) : RealmRepository(databaseService), TeamRepository {

    override suspend fun getTeamResources(teamId: String): List<RealmMyLibrary> {
        return withRealm { realm ->
            val resourceIds = RealmMyTeam.getResourceIds(teamId, realm)
            if (resourceIds.isEmpty()) emptyList() else
                realm.queryList(RealmMyLibrary::class.java) {
                    `in`("id", resourceIds.toTypedArray())
                }
        }
    }

    override suspend fun getTeams(userId: String, type: String?): List<RealmMyTeam> {
        return withRealm { realm ->
            val query = realm.where(RealmMyTeam::class.java)
                .isEmpty("teamId")
                .notEqualTo("status", "archived")
            if (!type.isNullOrEmpty() && type != "team") {
                query.equalTo("type", "enterprise")
            } else {
                query.notEqualTo("type", "enterprise")
            }
            query.findAll()
        }
    }

    override suspend fun searchTeams(query: String, type: String?): List<RealmMyTeam> {
        return withRealm { realm ->
            val realmQuery = realm.where(RealmMyTeam::class.java)
                .isEmpty("teamId")
                .notEqualTo("status", "archived")
                .contains("name", query, Case.INSENSITIVE)
            if (!type.isNullOrEmpty() && type != "team") {
                realmQuery.equalTo("type", "enterprise")
            } else {
                realmQuery.notEqualTo("type", "enterprise")
            }
            realmQuery.findAll()
        }
    }

    override suspend fun createTeam(name: String, description: String, type: String, teamType: String, services: String, rules: String, isPublic: Boolean, createdBy: String, parentCode: String, planetCode: String, userId: String) {
        withRealm { realm ->
            realm.executeTransaction {
                val teamId = AndroidDecrypter.generateIv()
                val team = it.createObject(RealmMyTeam::class.java, teamId)
                team.status = "active"
                team.createdDate = Date().time
                if (type == "enterprise") {
                    team.type = "enterprise"
                    team.services = services
                    team.rules = rules
                } else {
                    team.type = "team"
                    team.teamType = teamType
                }
                team.name = name
                team.description = description
                team.createdBy = createdBy
                team.teamId = ""
                team.isPublic = isPublic
                team.userId = userId
                team.parentCode = parentCode
                team.teamPlanetCode = planetCode
                team.updated = true

                val teamMemberObj = it.createObject(RealmMyTeam::class.java, AndroidDecrypter.generateIv())
                teamMemberObj.userId = createdBy
                teamMemberObj.teamId = teamId
                teamMemberObj.teamPlanetCode = planetCode
                teamMemberObj.userPlanetCode = planetCode
                teamMemberObj.docType = "membership"
                teamMemberObj.isLeader = true
                teamMemberObj.teamType = teamType
                teamMemberObj.updated = true
            }
        }
    }

    override suspend fun updateTeam(teamId: String, name: String, description: String, services: String, rules: String, userId: String) {
        withRealm { realm ->
            realm.executeTransaction {
                val team = it.where(RealmMyTeam::class.java).equalTo("_id", teamId).findFirst()
                team?.let {
                    it.name = name
                    it.description = description
                    it.services = services
                    it.rules = rules
                    it.createdBy = userId
                    it.updated = true
                }
            }
        }
    }

    override suspend fun isTeamLeader(teamId: String, userId: String): Boolean {
        return withRealm { realm ->
            realm.where(RealmMyTeam::class.java)
                .equalTo("teamId", teamId)
                .equalTo("userId", userId)
                .equalTo("isLeader", true)
                .findFirst() != null
        }
    }

    override suspend fun isMyTeam(teamId: String, userId: String): Boolean {
        return withRealm { realm ->
            realm.where(RealmMyTeam::class.java)
                .equalTo("teamId", teamId)
                .equalTo("userId", userId)
                .findFirst() != null
        }
    }

    override suspend fun getVisitCountForTeam(teamId: String): Long {
        return withRealm { realm ->
            realm.where(RealmTeamLog::class.java)
                .equalTo("type", "visit")
                .equalTo("teamId", teamId)
                .count()
        }
    }

    override suspend fun getTeamLeaderId(teamId: String): String? {
        return withRealm { realm ->
            realm.where(RealmMyTeam::class.java)
                .equalTo("teamId", teamId)
                .equalTo("isLeader", true)
                .findFirst()?.userId
        }
    }

    override suspend fun hasPendingRequest(teamId: String, userId: String): Boolean {
        return withRealm { realm ->
            realm.where(RealmMyTeam::class.java)
                .equalTo("teamId", teamId)
                .equalTo("userId", userId)
                .findFirst()?.requested ?: false
        }
    }

    override suspend fun leaveTeam(teamId: String, userId: String) {
        withRealm { realm ->
            realm.executeTransaction {
                it.where(RealmMyTeam::class.java)
                    .equalTo("teamId", teamId)
                    .equalTo("userId", userId)
                    .findFirst()?.deleteFromRealm()
            }
        }
    }

    override suspend fun requestToJoin(teamId: String, userId: String, teamType: String) {
        withRealm { realm ->
            realm.executeTransaction {
                val team = it.where(RealmMyTeam::class.java).equalTo("_id", teamId).findFirst()
                team?.let {
                    val request = it.requests.find { r -> r == userId }
                    if(request == null) {
                        it.requests.add(userId)
                    }
                }
            }
        }
    }

    override suspend fun getTasks(teamId: String, filter: TaskFilter, userId: String): List<RealmTeamTask> {
        return withRealm { realm ->
            val query = realm.where(RealmTeamTask::class.java)
                .equalTo("teamId", teamId)
                .notEqualTo("status", "archived")
            when (filter) {
                is TaskFilter.MY_TASKS -> query.equalTo("completed", false).equalTo("assignee", userId).sort("deadline", Sort.DESCENDING)
                is TaskFilter.COMPLETED -> query.equalTo("completed", true).sort("deadline", Sort.DESCENDING)
                else -> query.sort("completed", Sort.ASCENDING)
            }
            query.findAll()
        }
    }

    override suspend fun createOrUpdateTask(taskId: String?, title: String, description: String, deadline: Long, teamId: String, planetCode: String, parentCode: String) {
        withRealm { realm ->
            realm.executeTransaction {
                val task = if (taskId.isNullOrEmpty()) {
                    it.createObject(RealmTeamTask::class.java, "${UUID.randomUUID()}")
                } else {
                    it.where(RealmTeamTask::class.java).equalTo("_id", taskId).findFirst()
                }
                task?.let {
                    it.title = title
                    it.description = description
                    it.deadline = deadline
                    it.teamId = teamId
                    it.isUpdated = true
                    val ob = JsonObject()
                    ob.addProperty("teams", teamId)
                    it.link = Gson().toJson(ob)
                    val obSync = JsonObject()
                    obSync.addProperty("type", "local")
                    obSync.addProperty("planetCode", planetCode)
                    it.sync = Gson().toJson(obSync)
                }
            }
        }
    }

    override suspend fun setTaskCompleted(taskId: String, isCompleted: Boolean) {
        withRealm { realm ->
            realm.executeTransaction {
                val task = it.where(RealmTeamTask::class.java).equalTo("_id", taskId).findFirst()
                task?.let {
                    it.completed = isCompleted
                    it.isUpdated = true
                    it.completedTime = Date().time
                }
            }
        }
    }

    override suspend fun deleteTask(taskId: String) {
        withRealm { realm ->
            realm.executeTransaction {
                it.where(RealmTeamTask::class.java).equalTo("_id", taskId).findFirst()?.deleteFromRealm()
            }
        }
    }

    override suspend fun assignTask(taskId: String, assigneeUserId: String) {
        withRealm { realm ->
            realm.executeTransaction {
                val task = it.where(RealmTeamTask::class.java).equalTo("_id", taskId).findFirst()
                task?.assignee = assigneeUserId
            }
        }
    }

    override suspend fun getJoinedMembers(teamId: String): List<RealmUserModel> {
        return withRealm { realm ->
            val userIds = realm.where(RealmMyTeam::class.java)
                .equalTo("teamId", teamId)
                .findAll()
                .map { it.userId }
            if (userIds.isEmpty()) emptyList() else
                realm.queryList(RealmUserModel::class.java) {
                    `in`("id", userIds.toTypedArray())
                }
        }
    }

    override suspend fun getUserById(userId: String): RealmUserModel? {
        return withRealm { realm ->
            realm.where(RealmUserModel::class.java).equalTo("id", userId).findFirst()
        }
    }

    override suspend fun getMyTeamsByUserId(userId: String, type: String?): List<RealmMyTeam> {
        return withRealm { realm ->
            val teamIds = realm.where(RealmMyTeam::class.java)
                .equalTo("userId", userId)
                .findAll()
                .map { it.teamId }

            val query = realm.where(RealmMyTeam::class.java)
                .`in`("_id", teamIds.toTypedArray())
                .isEmpty("teamId")
                .notEqualTo("status", "archived")

            if (!type.isNullOrEmpty() && type != "team") {
                query.equalTo("type", "enterprise")
            } else {
                query.notEqualTo("type", "enterprise")
            }
            query.findAll()
        }
    }
}

