package org.ole.planet.myplanet.repository

import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.model.Feedback

interface FeedbackRepository {
    suspend fun createAndSaveFeedback(
        user: String?,
        urgent: String,
        type: String,
        message: String,
        item: String? = null,
        state: String? = null,
    )
    fun getFeedback(ownerName: String?, isManager: Boolean): Flow<List<Feedback>>
    suspend fun getPendingFeedback(): List<Feedback>
    suspend fun getFeedbackById(id: String?): Feedback?
    suspend fun closeFeedback(id: String?)
    suspend fun addReply(id: String?, message: String, user: String?)
    suspend fun markFeedbackUploaded(id: String): Boolean
}
