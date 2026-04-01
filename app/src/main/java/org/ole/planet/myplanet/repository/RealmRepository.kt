package org.ole.planet.myplanet.repository

import io.realm.Realm
import io.realm.RealmChangeListener
import io.realm.RealmObject
import io.realm.RealmQuery
import io.realm.RealmResults
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.data.applyEqualTo
import org.ole.planet.myplanet.data.findCopyByField
import org.ole.planet.myplanet.data.queryList

open class RealmRepository(
    protected val databaseService: DatabaseService,
    protected val realmDispatcher: CoroutineDispatcher = Dispatchers.Main
) {
    protected suspend fun <T : RealmObject> queryList(
        clazz: Class<T>,
        builder: RealmQuery<T>.() -> Unit = {},
    ): List<T> = queryList(clazz, false, builder)

    protected suspend fun <T : RealmObject> queryList(
        clazz: Class<T>,
        ensureLatest: Boolean,
        builder: RealmQuery<T>.() -> Unit = {},
    ): List<T> =
        withRealm(ensureLatest) { realm ->
            realm.queryList(clazz, builder)
        }

    protected suspend fun <T : RealmObject> count(
        clazz: Class<T>,
        builder: RealmQuery<T>.() -> Unit = {},
    ): Long = count(clazz, false, builder)

    protected suspend fun <T : RealmObject> count(
        clazz: Class<T>,
        ensureLatest: Boolean,
        builder: RealmQuery<T>.() -> Unit = {},
    ): Long =
        withRealm(ensureLatest) { realm ->
            realm.where(clazz).apply(builder).count()
        }

    protected suspend fun <T : RealmObject> queryListFlow(
        clazz: Class<T>,
        builder: RealmQuery<T>.() -> Unit = {},
    ): Flow<List<T>> = callbackFlow {
        val isClosed = AtomicBoolean(false)
        var realm: Realm? = null
        var results: RealmResults<T>? = null
        var listener: RealmChangeListener<RealmResults<T>>? = null

        val channel = Channel<RealmResults<T>>(Channel.CONFLATED)

        fun safeCloseRealm() {
            if (isClosed.compareAndSet(false, true)) {
                try {
                    results?.let { res ->
                        listener?.let { l ->
                            if (res.isValid) {
                                res.removeChangeListener(l)
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                try {
                    realm?.let { r ->
                        if (!r.isClosed) {
                            r.close()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // Single serialized path to copy and send downstream
        launch(databaseService.ioDispatcher) {
            for (frozenResults in channel) {
                if (isClosed.get()) break
                try {
                    val frozenRealm = frozenResults.realm
                    val copiedList = frozenRealm.copyFromRealm(frozenResults)
                    if (!isClosed.get()) {
                        send(copiedList)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        try {
            realm = databaseService.createManagedRealmInstance()

            val initialResults = realm.where(clazz).apply(builder).findAll()
            if (initialResults.isValid && initialResults.isLoaded) {
                val frozenInitial = initialResults.freeze()
                channel.trySend(frozenInitial)
            }

            results = realm.where(clazz).apply(builder).findAllAsync()
            listener = RealmChangeListener<RealmResults<T>> { changedResults ->
                if (!isClosed.get() && changedResults.isLoaded && changedResults.isValid) {
                    try {
                        val frozenResults = changedResults.freeze()
                        channel.trySend(frozenResults)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            results.addChangeListener(listener)

            awaitClose {
                channel.close()
                safeCloseRealm()
            }
        } catch (e: Exception) {
            channel.close()
            safeCloseRealm()
            throw e
        }
    }.flowOn(realmDispatcher)

    protected suspend fun <T : RealmObject, V : Any> findByField(
        clazz: Class<T>,
        fieldName: String,
        value: V,
    ): T? = findByField(clazz, fieldName, value, false)

    protected suspend fun <T : RealmObject, V : Any> findByField(
        clazz: Class<T>,
        fieldName: String,
        value: V,
        ensureLatest: Boolean,
    ): T? =
        withRealm(ensureLatest) { realm ->
            realm.findCopyByField(clazz, fieldName, value)
        }

    protected suspend fun <T : RealmObject> save(item: T) {
        executeTransaction { realm ->
            realm.copyToRealmOrUpdate(item)
        }
    }

    protected suspend fun <T : RealmObject, V : Any> update(
        clazz: Class<T>,
        fieldName: String,
        value: V,
        updater: (T) -> Unit,
    ) {
        executeTransaction { realm ->
            realm.where(clazz)
                .applyEqualTo(fieldName, value)
                .findFirst()?.let { updater(it) }
        }
    }

    protected suspend fun <T : RealmObject, V : Any> delete(
        clazz: Class<T>,
        fieldName: String,
        value: V,
    ) {
        executeTransaction { realm ->
            realm.where(clazz)
                .applyEqualTo(fieldName, value)
                .findFirst()?.deleteFromRealm()
        }
    }

    protected suspend fun <T> withRealm(
        ensureLatest: Boolean = false,
        operation: (Realm) -> T,
    ): T {
        return databaseService.withRealmAsync { realm ->
            if (ensureLatest) {
                realm.refresh()
            }
            operation(realm)
        }
    }

    protected suspend fun <T> withRealmAsync(operation: (Realm) -> T): T {
        return withRealm(false, operation)
    }


    protected suspend fun executeTransaction(transaction: (Realm) -> Unit) {
        databaseService.executeTransactionAsync(transaction)
    }

    open fun bulkInsertFromSync(realm: Realm, jsonArray: com.google.gson.JsonArray, table: String) {
        val documentList = mutableListOf<com.google.gson.JsonObject>()
        for (j in jsonArray) {
            var jsonDoc = j.asJsonObject
            jsonDoc = org.ole.planet.myplanet.utils.JsonUtils.getJsonObject("doc", jsonDoc)
            val id = org.ole.planet.myplanet.utils.JsonUtils.getString("_id", jsonDoc)
            if (!id.startsWith("_design")) {
                documentList.add(jsonDoc)
            }
        }
        documentList.forEach { jsonDoc ->
            when (table) {
                "tags" -> org.ole.planet.myplanet.model.RealmTag.insert(realm, jsonDoc)
                "login_activities" -> org.ole.planet.myplanet.model.RealmOfflineActivity.insert(realm, jsonDoc)
                "ratings" -> org.ole.planet.myplanet.model.RealmRating.insert(realm, jsonDoc)
                "submissions" -> org.ole.planet.myplanet.model.RealmSubmission.insert(realm, jsonDoc)
                "achievements" -> org.ole.planet.myplanet.model.RealmAchievement.insert(realm, jsonDoc)
                "teams" -> org.ole.planet.myplanet.model.RealmMyTeam.insert(realm, jsonDoc)
                "tasks" -> org.ole.planet.myplanet.model.RealmTeamTask.insert(realm, jsonDoc)
                "meetups" -> org.ole.planet.myplanet.model.RealmMeetup.insert(realm, jsonDoc)
                "health" -> org.ole.planet.myplanet.model.RealmHealthExamination.insert(realm, jsonDoc)
                "certifications" -> org.ole.planet.myplanet.model.RealmCertification.insert(realm, jsonDoc)
                "team_activities" -> org.ole.planet.myplanet.model.RealmTeamLog.insert(realm, jsonDoc)
                "courses_progress" -> org.ole.planet.myplanet.model.RealmCourseProgress.insert(realm, jsonDoc)
                "notifications" -> org.ole.planet.myplanet.model.RealmNotification.insert(realm, jsonDoc)
            }
        }
    }
}
