package org.ole.planet.myplanet.repository

import com.google.gson.JsonObject
import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.model.RealmFeedback
import org.ole.planet.myplanet.model.RealmUserModel

interface FeedbackRepository {
    suspend fun submitFeedback(
        user: String?,
        urgent: String,
        type: String,
        message: String,
        item: String? = null,
        state: String? = null,
    )
    fun getFeedback(userModel: RealmUserModel?): Flow<List<RealmFeedback>>
    suspend fun getFeedbackById(id: String?): RealmFeedback?
    suspend fun closeFeedback(id: String?)
    suspend fun addReply(id: String?, obj: JsonObject)
}
