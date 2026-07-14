package org.ole.planet.myplanet.repository

import io.realm.OrderedRealmCollectionChangeListener
import io.realm.Realm
import io.realm.RealmObject
import io.realm.RealmQuery
import io.realm.RealmResults
import io.realm.log.RealmLog
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.data.applyEqualTo
import org.ole.planet.myplanet.data.findCopyByField
import org.ole.planet.myplanet.data.queryList

open class RealmRepository(
    protected val databaseService: DatabaseService,
    protected val realmDispatcher: CoroutineDispatcher
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

    protected suspend fun <T : RealmObject> queryList(clazz: Class<T>, maxDepth: Int, builder: RealmQuery<T>.() -> Unit = {}): List<T> = withRealm(false) { realm ->
        realm.queryList(clazz, maxDepth, builder)
    }

    protected suspend fun <T : RealmObject> findFirstCopy(
        clazz: Class<T>,
        builder: RealmQuery<T>.() -> Unit = {},
    ): T? = withRealm { realm ->
        realm.where(clazz).apply(builder).findFirst()?.let { realm.copyFromRealm(it) }
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

    protected fun <T : RealmObject> queryListFlow(
        clazz: Class<T>,
        builder: RealmQuery<T>.() -> Unit = {},
    ): Flow<List<T>> = callbackFlow<List<T>> {
        val isClosed = AtomicBoolean(false)
        var realm: Realm? = null
        var results: RealmResults<T>? = null
        var listener: OrderedRealmCollectionChangeListener<RealmResults<T>>? = null

        var lastPrimaryKeys: List<Any>? = null

        fun safeCloseRealm() {
            if (isClosed.compareAndSet(false, true)) {
                val currentResults = results
                val currentListener = listener
                if (currentResults != null && currentListener != null && currentResults.isValid) {
                    try {
                        currentResults.removeChangeListener(currentListener)
                    } catch (e: Exception) {
                        RealmLog.error(e, "Error removing RealmChangeListener")
                    }
                }

                val currentRealm = realm
                if (currentRealm != null && !currentRealm.isClosed) {
                    try {
                        currentRealm.close()
                    } catch (e: Exception) {
                        RealmLog.error(e, "Error closing Realm")
                    }
                }
            }
        }

        fun emitResults(res: RealmResults<T>, errorMsg: String, hasContentChanges: Boolean) {
            if (!isClosed.get() && res.isValid && res.isLoaded) {
                try {
                    val frozen = res.freeze()
                    if (!isClosed.get()) {
                        val detachedList = if (frozen.isEmpty()) {
                            emptyList()
                        } else {
                            frozen.realm.copyFromRealm(frozen)
                        }

                        val pkField = frozen.realm.schema.get(clazz.simpleName)?.primaryKey
                        val currentPrimaryKeys = if (pkField != null && detachedList.isNotEmpty()) {
                            try {
                                val field = detachedList.first().javaClass.getDeclaredField(pkField)
                                field.isAccessible = true
                                detachedList.map { field.get(it) }
                            } catch (e: Exception) { null }
                        } else if (detachedList.isNotEmpty()) {
                            val idField = try { detachedList.first().javaClass.getDeclaredField("id") } catch(e: Exception) { null }
                                ?: try { detachedList.first().javaClass.getDeclaredField("_id") } catch(e: Exception) { null }
                            if (idField != null) {
                                idField.isAccessible = true
                                detachedList.map { idField.get(it) }
                            } else null
                        } else emptyList()

                        val isDuplicate = !hasContentChanges && lastPrimaryKeys != null && currentPrimaryKeys != null && currentPrimaryKeys == lastPrimaryKeys
                        if (!isDuplicate) {
                            lastPrimaryKeys = currentPrimaryKeys
                            trySend(detachedList)
                        }
                    }
                } catch (e: Exception) {
                    RealmLog.error(e, errorMsg)
                }
            }
        }

        try {
            realm = databaseService.createManagedRealmInstance()

            val initialResults = realm.where(clazz).apply(builder).findAll()
            emitResults(initialResults, "Error sending initial results", false)

            results = initialResults
            listener = OrderedRealmCollectionChangeListener<RealmResults<T>> { changedResults, changeSet ->
                if (changeSet == null || changeSet.insertions.isNotEmpty() || changeSet.deletions.isNotEmpty() || changeSet.changes.isNotEmpty()) {
                    val hasContentChanges = changeSet != null && changeSet.changes.isNotEmpty()
                    emitResults(changedResults, "Error sending changed results", hasContentChanges)
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
    }.flowOn(realmDispatcher)
        .conflate()

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
