package org.ole.planet.myplanet.repository

import io.realm.kotlin.asFlow
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmFeedback

interface FeedbackRepository {
    fun getFeedback(userName: String, isManager: Boolean): Flow<List<RealmFeedback>>
}

class FeedbackRepositoryImpl @Inject constructor(
    private val databaseService: DatabaseService
) : RealmRepository(databaseService), FeedbackRepository {

    override fun getFeedback(userName: String, isManager: Boolean): Flow<List<RealmFeedback>> {
        val query = databaseService.realmInstance.where(RealmFeedback::class.java)
        if (!isManager) {
            query.equalTo("owner", userName)
        }
        return query.findAllAsync().asFlow().map { realmResults ->
            realmResults.toList()
        }
    }
}
