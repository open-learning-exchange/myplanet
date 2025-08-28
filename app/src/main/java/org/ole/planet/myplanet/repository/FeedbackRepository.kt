package org.ole.planet.myplanet.repository

import io.realm.RealmChangeListener
import io.realm.RealmResults
import io.realm.Sort
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
    private val databaseService: DatabaseService,
) : FeedbackRepository {
    override fun getFeedback(userModel: RealmUserModel?): Flow<List<RealmFeedback>> =
        databaseService.withRealm { realm ->
            callbackFlow {
                val feedbackList: RealmResults<RealmFeedback> =
                    if (userModel?.isManager() == true) {
                        realm.where(RealmFeedback::class.java)
                            .sort("openTime", Sort.DESCENDING)
                            .findAllAsync()
                    } else {
                        realm.where(RealmFeedback::class.java).equalTo("owner", userModel?.name)
                            .sort("openTime", Sort.DESCENDING)
                            .findAllAsync()
                    }

                val listener = RealmChangeListener<RealmResults<RealmFeedback>> { results ->
                    trySend(realm.copyFromRealm(results))
                }

                feedbackList.addChangeListener(listener)

                awaitClose {
                    feedbackList.removeChangeListener(listener)
                    realm.close()
                }
            }
        }
}
