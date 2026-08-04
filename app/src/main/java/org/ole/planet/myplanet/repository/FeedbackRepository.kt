package org.ole.planet.myplanet.repository

import com.google.gson.JsonObject
import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.model.Feedback
import org.ole.planet.myplanet.model.UserEntity

interface FeedbackRepository {
    fun createFeedback(
        user: String?,
        urgent: String,
        type: String,
        message: String,
        item: String? = null,
        state: String? = null,
    ): Feedback
    suspend fun getFeedback(userModel: UserEntity?): Flow<List<Feedback>>
    suspend fun getPendingFeedback(): List<Feedback>
    suspend fun getFeedbackById(id: String?): Feedback?
    suspend fun closeFeedback(id: String?)
    suspend fun addReply(id: String?, message: String, user: String?)
    suspend fun saveFeedback(feedback: Feedback)
    suspend fun insertFromJson(jsonObject: JsonObject)
    suspend fun insertFeedbackList(jsonObjects: List<JsonObject>)
    suspend fun markFeedbackUploaded(id: String): Boolean
}
