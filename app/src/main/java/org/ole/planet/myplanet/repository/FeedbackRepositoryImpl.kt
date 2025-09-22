package org.ole.planet.myplanet.repository

import android.os.Handler
import android.os.HandlerThread
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.realm.Realm
import io.realm.RealmChangeListener
import io.realm.RealmQuery
import io.realm.RealmResults
import io.realm.Sort
import java.util.Date
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
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
            val realmRef = AtomicReference<Realm?>()
            val resultsRef = AtomicReference<RealmResults<RealmFeedback>?>(null)

            val realmThread = HandlerThread("FeedbackRealmThread").apply { start() }
            val handler = Handler(realmThread.looper)

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
                    val realm = realmRef.get()
                    if (realmResults.isLoaded && realmResults.isValid && realm != null) {
                        trySendBlocking(realm.copyFromRealm(realmResults))
                    }
                }

            handler.post {
                val realm = try {
                    databaseService.realmInstance
                } catch (error: Throwable) {
                    close(error)
                    return@post
                }
                realmRef.set(realm)

                val results =
                    try {
                        realm.where(RealmFeedback::class.java).apply(builder).findAllAsync()
                    } catch (error: Throwable) {
                        realm.close()
                        realmRef.set(null)
                        close(error)
                        return@post
                    }

                resultsRef.set(results)
                if (results.isLoaded && results.isValid) {
                    trySendBlocking(realm.copyFromRealm(results))
                }
                results.addChangeListener(listener)
            }

            awaitClose {
                handler.post {
                    resultsRef.getAndSet(null)?.removeChangeListener(listener)
                    realmRef.getAndSet(null)?.close()
                    realmThread.quitSafely()
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
