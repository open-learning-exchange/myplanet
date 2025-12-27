package org.ole.planet.myplanet.repository

import java.util.HashMap
import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.model.RealmVoices

interface VoicesRepository {
    suspend fun getLibraryResource(resourceId: String): RealmMyLibrary?
    suspend fun getCommunityVoices(userIdentifier: String): Flow<List<RealmVoices>>
    suspend fun getVoiceWithReplies(voiceId: String): Pair<RealmVoices?, List<RealmVoices>>
    suspend fun getCommunityVisibleVoices(userIdentifier: String): List<RealmVoices>
    suspend fun getVoicesByTeamId(teamId: String): List<RealmVoices>
    suspend fun createVoice(map: HashMap<String?, String>, user: RealmUserModel?): RealmVoices
    suspend fun getDiscussionsByTeamIdFlow(teamId: String): Flow<List<RealmVoices>>
    suspend fun shareVoiceToCommunity(voiceId: String, userId: String, planetCode: String, parentCode: String, teamName: String): Result<Unit>
    suspend fun updateTeamNotification(teamId: String, count: Int)
    suspend fun getFilteredVoices(teamId: String): List<RealmVoices>
    suspend fun getReplies(voiceId: String?): List<RealmVoices>
}
