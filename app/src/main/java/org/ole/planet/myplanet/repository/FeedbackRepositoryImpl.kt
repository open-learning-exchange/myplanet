package org.ole.planet.myplanet.repository

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.realm.RealmChangeListener
import io.realm.RealmQuery
import io.realm.RealmResults
import io.realm.Sort
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmFeedback
import org.ole.planet.myplanet.model.RealmUserModel

class FeedbackRepositoryImpl @Inject constructor(
    private val databaseService: DatabaseService,
    private val gson: Gson
) : RealmRepository(databaseService), FeedbackRepository {

    override fun createFeedback(
        user: String?,
        urgent: String,
        type: String,
        message: String,
        item: String?,
        state: String?,
    ): RealmFeedback {
        val feedback = RealmFeedback()
        feedback.id = UUID.randomUUID().toString()
        if (state != null) {
            feedback.title = "Question regarding /$state"
            feedback.url = "/$state"
            feedback.state = state
            feedback.item = item
        } else {
            feedback.title = "Question regarding /"
            feedback.url = "/"
        }
        feedback.openTime = Date().time
        feedback.owner = user
        feedback.source = user
        feedback.status = "Open"
        feedback.priority = urgent
        feedback.type = type
        feedback.parentCode = "dev"
        val obj = JsonObject().apply {
            addProperty("message", message)
            addProperty("time", Date().time.toString() + "")
            addProperty("user", user + "")
        }
        val msgArray = JsonArray().apply { add(obj) }
        feedback.setMessages(msgArray)
        return feedback
    }

    override fun getFeedback(userModel: RealmUserModel?): Flow<List<RealmFeedback>> =
        callbackFlow {
            val realm = try {
                databaseService.realmInstance
            } catch (error: Throwable) {
                close(error)
                return@callbackFlow
            }

            val builder: RealmQuery<RealmFeedback>.() -> Unit = {
                if (userModel?.isManager() == true) {
                    sort("openTime", Sort.DESCENDING)
                } else {
                    equalTo("owner", userModel?.name)
                    sort("openTime", Sort.DESCENDING)
                }
            }

            val listener =
                RealmChangeListener<RealmResults<RealmFeedback>> { realmResults ->
                    if (realmResults.isLoaded && realmResults.isValid) {
                        trySend(realm.copyFromRealm(realmResults))
                    }
                }

            val results =
                try {
                    realm.where(RealmFeedback::class.java).apply(builder).findAllAsync()
                } catch (error: Throwable) {
                    realm.close()
                    close(error)
                    return@callbackFlow
                }
            if (results.isLoaded && results.isValid) {
                trySend(realm.copyFromRealm(results))
            }
            results.addChangeListener(listener)

            awaitClose {
                try {
                    results.removeChangeListener(listener)
                } finally {
                    realm.close()
                }
            }
        }

    override suspend fun getFeedbackById(id: String?): RealmFeedback? {
        return id?.let { findByField(RealmFeedback::class.java, "id", it) }
    }

    override suspend fun closeFeedback(id: String?) {
        id?.let {
            update(RealmFeedback::class.java, "id", it) { feedback ->
                feedback.status = "Closed"
            }
        }
    }

    override suspend fun addReply(id: String?, obj: JsonObject) {
        id?.let {
            update(RealmFeedback::class.java, "id", it) { feedback ->
                val msgArray = gson.fromJson(feedback.messages, JsonArray::class.java)
                msgArray.add(obj)
                feedback.setMessages(msgArray)
            }
        }
    }

    override suspend fun saveFeedback(feedback: RealmFeedback) {
        save(feedback)
    }
}
