package org.ole.planet.myplanet.repository

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.realm.Realm
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
    suspend fun getFeedbackById(id: String?): RealmFeedback?
    suspend fun closeFeedback(id: String?)
    suspend fun addReply(id: String?, obj: JsonObject)
}

class FeedbackRepositoryImpl @Inject constructor(
    private val databaseService: DatabaseService,
) : FeedbackRepository {
    override fun getFeedback(userModel: RealmUserModel?): Flow<List<RealmFeedback>> =
        callbackFlow {
            val realm = Realm.getDefaultInstance()
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

    override suspend fun getFeedbackById(id: String?): RealmFeedback? =
        databaseService.withRealmAsync { realm ->
            realm.where(RealmFeedback::class.java)
                .equalTo("id", id)
                .findFirst()
                ?.let { realm.copyFromRealm(it) }
        }

    override suspend fun closeFeedback(id: String?) {
        databaseService.executeTransactionAsync { realm ->
            realm.where(RealmFeedback::class.java)
                .equalTo("id", id)
                .findFirst()?.status = "Closed"
        }
    }

    override suspend fun addReply(id: String?, obj: JsonObject) {
        databaseService.executeTransactionAsync { realm ->
            val feedback = realm.where(RealmFeedback::class.java)
                .equalTo("id", id)
                .findFirst()
            if (feedback != null) {
                val con = Gson()
                val msgArray = con.fromJson(feedback.messages, JsonArray::class.java)
                msgArray.add(obj)
                feedback.setMessages(msgArray)
            }
        }
    }
}