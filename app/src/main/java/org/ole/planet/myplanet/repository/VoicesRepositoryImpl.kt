package org.ole.planet.myplanet.repository

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.realm.Case
import io.realm.Sort
import java.util.Calendar
import java.util.HashMap
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.data.findCopyByField
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.model.RealmVoices
import org.ole.planet.myplanet.model.RealmVoices.Companion.createVoices

class VoicesRepositoryImpl @Inject constructor(
    databaseService: DatabaseService,
    private val gson: Gson,
) : RealmRepository(databaseService), VoicesRepository {
    override suspend fun getLibraryResource(resourceId: String): RealmMyLibrary? {
        return withRealm { realm ->
            realm.findCopyByField(RealmMyLibrary::class.java, "_id", resourceId)
        }
    }

    override suspend fun getVoiceWithReplies(voiceId: String): Pair<RealmVoices?, List<RealmVoices>> {
        return withRealm(ensureLatest = true) { realm ->
            val voice = realm.findCopyByField(RealmVoices::class.java, "id", voiceId)
            val replies = realm.where(RealmVoices::class.java)
                .equalTo("replyTo", voiceId, Case.INSENSITIVE)
                .sort("time", Sort.DESCENDING)
                .findAll()
                .let { realm.copyFromRealm(it) }
            voice to replies
        }
    }

    override suspend fun getCommunityVisibleVoices(userIdentifier: String): List<RealmVoices> {
        val allVoices = queryList(RealmVoices::class.java) {
            isEmpty("replyTo")
            equalTo("docType", "message", Case.INSENSITIVE)
            sort("time", Sort.DESCENDING)
        }
        if (allVoices.isEmpty()) {
            return emptyList()
        }

        return allVoices.filter { voice ->
            isVisibleToUser(voice, userIdentifier)
        }
    }

    override suspend fun createVoice(map: HashMap<String?, String>, user: RealmUserModel?): RealmVoices {
        return withRealmAsync { realm ->
            val managedVoice = createVoices(map, realm, user, null)
            realm.copyFromRealm(managedVoice)
        }
    }

    override suspend fun getVoicesByTeamId(teamId: String): List<RealmVoices> {
        return withRealm { realm ->
            val allVoices = realm.where(RealmVoices::class.java)
                .isEmpty("replyTo")
                .sort("time", Sort.DESCENDING)
                .findAll()

            val filteredList = mutableListOf<RealmVoices>()
            for (voice in allVoices) {
                if (!voice.viewableBy.isNullOrEmpty() && voice.viewableBy.equals("teams", ignoreCase = true) && voice.viewableId.equals(teamId, ignoreCase = true)) {
                    filteredList.add(realm.copyFromRealm(voice))
                } else if (!voice.viewIn.isNullOrEmpty()) {
                    try {
                        val ar = gson.fromJson(voice.viewIn, JsonArray::class.java)
                        for (e in ar) {
                            val ob = e.asJsonObject
                            if (ob["_id"].asString.equals(teamId, ignoreCase = true)) {
                                filteredList.add(realm.copyFromRealm(voice))
                                break
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            filteredList
        }
    }

    private fun isVisibleToUser(voice: RealmVoices, userIdentifier: String): Boolean {
        if (voice.viewableBy.equals("community", ignoreCase = true)) {
            return true
        }

        val viewIn = voice.viewIn ?: return false
        if (viewIn.isEmpty()) {
            return false
        }

        return try {
            val array = gson.fromJson(viewIn, JsonArray::class.java)
            array?.any { element ->
                element != null && element.isJsonObject &&
                    element.asJsonObject.has("_id") &&
                    element.asJsonObject.get("_id").asString.equals(userIdentifier, ignoreCase = true)
            } == true
        } catch (throwable: Throwable) {
            false
        }
    }

    override suspend fun getCommunityVoices(userIdentifier: String): Flow<List<RealmVoices>> {
        val allVoicesFlow = queryListFlow(RealmVoices::class.java) {
            isEmpty("replyTo")
            equalTo("docType", "message", Case.INSENSITIVE)
            sort("time", Sort.DESCENDING)
        }
        .flowOn(Dispatchers.Main)

        return allVoicesFlow.map { allVoices ->
            allVoices.filter { voice ->
                isVisibleToUser(voice, userIdentifier)
            }.map { voice ->
                voice.sortDate = voice.calculateSortDate()
                voice
            }
        }.flowOn(Dispatchers.Default)
    }

    override suspend fun getDiscussionsByTeamIdFlow(teamId: String): Flow<List<RealmVoices>> {
        return queryListFlow(RealmVoices::class.java) {
            isEmpty("replyTo")
            sort("time", Sort.DESCENDING)
        }.map { discussions ->
            discussions.filter { voice ->
                val viewableByTeams = !voice.viewableBy.isNullOrEmpty() &&
                        voice.viewableBy.equals("teams", ignoreCase = true) &&
                        voice.viewableId.equals(teamId, ignoreCase = true)

                val viewInTeam = if (!voice.viewIn.isNullOrEmpty()) {
                    try {
                        val ar = gson.fromJson(voice.viewIn, JsonArray::class.java)
                        ar.any { e ->
                            val ob = e.asJsonObject
                            ob["_id"].asString.equals(teamId, ignoreCase = true)
                        }
                    } catch (e: Exception) {
                        false
                    }
                } else {
                    false
                }

                viewableByTeams || viewInTeam
            }
        }.flowOn(Dispatchers.Default)
    }

    override suspend fun shareVoiceToCommunity(voiceId: String, userId: String, planetCode: String, parentCode: String, teamName: String): Result<Unit> {
        return try {
            databaseService.executeTransactionAsync { realm ->
                val voice = realm.where(RealmVoices::class.java).equalTo("id", voiceId).findFirst()
                if (voice != null) {
                    val array = gson.fromJson(voice.viewIn, JsonArray::class.java)
                    if (array != null && array.size() > 0) {
                        val firstElement = array.get(0)
                        if (firstElement.isJsonObject) {
                            val obj = firstElement.asJsonObject
                            if (!obj.has("name")) {
                                obj.addProperty("name", teamName)
                            }
                        }
                    }

                    val ob = JsonObject()
                    ob.addProperty("section", "community")
                    ob.addProperty("_id", "$planetCode@$parentCode")
                    ob.addProperty("sharedDate", Calendar.getInstance().timeInMillis)
                    array?.add(ob)

                    voice.sharedBy = userId
                    voice.viewIn = gson.toJson(array)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateTeamNotification(teamId: String, count: Int) {
        withRealm { realm ->
            realm.executeTransaction {
                var notification = it.where(org.ole.planet.myplanet.model.RealmTeamNotification::class.java)
                    .equalTo("type", "chat")
                    .equalTo("parentId", teamId)
                    .findFirst()

                if (notification == null) {
                    notification = it.createObject(org.ole.planet.myplanet.model.RealmTeamNotification::class.java, UUID.randomUUID().toString())
                    notification.parentId = teamId
                    notification.type = "chat"
                }
                notification.lastCount = count
            }
        }
    }

    override suspend fun getFilteredVoices(teamId: String): List<RealmVoices> {
        return withRealm { realm ->
            val query = realm.where(RealmVoices::class.java)
                .isEmpty("replyTo")
                .beginGroup()
                .equalTo("viewableBy", "teams", Case.INSENSITIVE)
                .equalTo("viewableId", teamId, Case.INSENSITIVE)
                .endGroup()
                .or()
                .contains("viewIn", "\"_id\":\"$teamId\"", Case.INSENSITIVE)
                .sort("time", Sort.DESCENDING)

            realm.copyFromRealm(query.findAll())
        }
    }

    override suspend fun getReplies(voiceId: String?): List<RealmVoices> {
        return withRealm { realm ->
            realm.where(RealmVoices::class.java)
                .sort("time", Sort.DESCENDING)
                .equalTo("replyTo", voiceId, Case.INSENSITIVE)
                .findAll()
                .let { realm.copyFromRealm(it) }
        }
    }
}
