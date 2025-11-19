package org.ole.planet.myplanet.repository

import io.realm.Realm
import io.realm.RealmChangeListener
import io.realm.RealmObject
import io.realm.RealmQuery
import io.realm.RealmResults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.datamanager.applyEqualTo
import org.ole.planet.myplanet.datamanager.findCopyByField
import org.ole.planet.myplanet.datamanager.queryList
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.channelFlow

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
    ): Flow<List<T>> = withContext(Dispatchers.Main) {
        val realm = Realm.getDefaultInstance()
        callbackFlow {
            val results = realm.where(clazz).apply(builder).findAllAsync()
            val listener = RealmChangeListener<RealmResults<T>> { updatedResults ->
                if (updatedResults.isLoaded && updatedResults.isValid) {
                    trySend(realm.copyFromRealm(updatedResults))
                }
            }
            results.addChangeListener(listener)

            if (results.isLoaded && results.isValid) {
                trySend(realm.copyFromRealm(results))
            }

            awaitClose {
                if (!realm.isClosed) {
                    results.removeChangeListener(listener)
                    realm.close()
                }
            }
        }.flowOn(Dispatchers.IO)
    }

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
