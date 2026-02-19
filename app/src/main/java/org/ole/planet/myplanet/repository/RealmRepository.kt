package org.ole.planet.myplanet.repository

import io.realm.Realm
import io.realm.RealmChangeListener
import io.realm.RealmObject
import io.realm.RealmQuery
import io.realm.RealmResults
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.data.applyEqualTo
import org.ole.planet.myplanet.data.findCopyByField
import org.ole.planet.myplanet.data.queryList

open class RealmRepository(protected val databaseService: DatabaseService) {
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

        try {
            realm = databaseService.createManagedRealmInstance()

            val initialResults = realm.where(clazz).apply(builder).findAll()
            if (initialResults.isValid && initialResults.isLoaded) {
                val initialCopy = realm.copyFromRealm(initialResults)
                send(initialCopy)
            }
            
            results = realm.where(clazz).apply(builder).findAllAsync()
            listener = RealmChangeListener<RealmResults<T>> { changedResults ->
                if (!isClosed.get() && changedResults.isLoaded && changedResults.isValid) {
                    try {
                        val frozenResults = changedResults.freeze()
                        launch(databaseService.ioDispatcher) {
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
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            results.addChangeListener(listener)

            awaitClose {
                safeCloseRealm()
            }
        } catch (e: Exception) {
            safeCloseRealm()
            throw e
        }
    }.flowOn(Dispatchers.Main)

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
}
