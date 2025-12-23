package org.ole.planet.myplanet.repository

import java.util.HashMap
import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmUserModel

interface VoicesRepository {
    suspend fun getLibraryResource(resourceId: String): RealmMyLibrary?
    suspend fun getCommunityVoices(userIdentifier: String): Flow<List<RealmNews>>
    suspend fun getVoicesWithReplies(voicesId: String): Pair<RealmNews?, List<RealmNews>>
    suspend fun getCommunityVisibleVoices(userIdentifier: String): List<RealmNews>
    suspend fun getVoicesByTeamId(teamId: String): List<RealmNews>
    suspend fun createVoice(map: HashMap<String?, String>, user: RealmUserModel?): RealmNews
    suspend fun getDiscussionsByTeamIdFlow(teamId: String): Flow<List<RealmNews>>
    suspend fun shareVoiceToCommunity(voicesId: String, userId: String, planetCode: String, parentCode: String, teamName: String): Result<Unit>
    suspend fun updateTeamNotification(teamId: String, count: Int)
    suspend fun getFilteredVoices(teamId: String): List<RealmNews>
    suspend fun getReplies(voicesId: String?): List<RealmNews>
}
