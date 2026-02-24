package org.ole.planet.myplanet.repository

import com.google.gson.JsonObject
import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.model.RealmFeedback
import org.ole.planet.myplanet.model.RealmUser

interface FeedbackRepository {
    fun createFeedback(
        user: String?,
        urgent: String,
        type: String,
        message: String,
        item: String? = null,
        state: String? = null,
    ): RealmFeedback
    suspend fun getFeedback(userModel: RealmUser?): Flow<List<RealmFeedback>>
    suspend fun getFeedbackById(id: String?): RealmFeedback?
    suspend fun closeFeedback(id: String?)
    suspend fun addReply(id: String?, obj: JsonObject)
    suspend fun saveFeedback(feedback: RealmFeedback)
    suspend fun insertFromJson(jsonObject: JsonObject)
    suspend fun insertFeedbackList(jsonObjects: List<JsonObject>)
}
