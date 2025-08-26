package org.ole.planet.myplanet.repository

import io.realm.Realm
import io.realm.RealmChangeListener
import io.realm.RealmResults
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmFeedback
import org.ole.planet.myplanet.model.RealmUserModel

interface FeedbackRepository {
    fun getFeedback(userModel: RealmUserModel?): Flow<List<RealmFeedback>>
}

class FeedbackRepositoryImpl @Inject constructor(
    private val databaseService: DatabaseService
) : FeedbackRepository {
    override fun getFeedback(userModel: RealmUserModel?): Flow<List<RealmFeedback>> = callbackFlow {
        val mRealm = databaseService.realmInstance
        val feedbackList: RealmResults<RealmFeedback> = if (userModel?.isManager() == true) {
            mRealm.where(RealmFeedback::class.java).findAllAsync()
        } else {
            mRealm.where(RealmFeedback::class.java).equalTo("owner", userModel?.name).findAllAsync()
        }

        val listener = RealmChangeListener<RealmResults<RealmFeedback>> { results ->
            trySend(mRealm.copyFromRealm(results))
        }

        feedbackList.addChangeListener(listener)

        awaitClose {
            feedbackList.removeChangeListener(listener)
            mRealm.close()
        }
    }
}
