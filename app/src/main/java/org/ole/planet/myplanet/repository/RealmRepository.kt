package org.ole.planet.myplanet.repository

import io.realm.Realm
import io.realm.RealmChangeListener
import io.realm.RealmObject
import io.realm.RealmQuery
import io.realm.RealmResults
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.datamanager.applyEqualTo
import org.ole.planet.myplanet.datamanager.findCopyByField
import org.ole.planet.myplanet.datamanager.queryList

open class RealmRepository(private val databaseService: DatabaseService) {
    protected suspend fun <T : RealmObject> queryList(
        clazz: Class<T>,
        builder: RealmQuery<T>.() -> Unit = {},
    ): List<T> =
        databaseService.withRealmAsync { realm ->
            realm.queryList(clazz, builder)
        }

    protected suspend fun <T : RealmObject> count(
        clazz: Class<T>,
        builder: RealmQuery<T>.() -> Unit = {},
    ): Long =
        databaseService.withRealmAsync { realm ->
            realm.where(clazz).apply(builder).count()
        }

    protected fun <T : RealmObject> queryListFlow(
        clazz: Class<T>,
        builder: RealmQuery<T>.() -> Unit = {},
    ): Flow<List<T>> =
        withRealmFlow { realm, scope ->
            val results = realm.where(clazz).apply(builder).findAllAsync()
            val listener =
                RealmChangeListener<RealmResults<T>> { updatedResults ->
                    if (updatedResults.isLoaded && updatedResults.isValid) {
                        scope.trySend(realm.copyFromRealm(updatedResults))
                    }
                }
            results.addChangeListener(listener)
            if (results.isLoaded && results.isValid) {
                scope.trySend(realm.copyFromRealm(results))
            }
            return@withRealmFlow { results.removeChangeListener(listener) }
        }

    protected suspend fun <T : RealmObject, V : Any> findByField(
        clazz: Class<T>,
        fieldName: String,
        value: V,
    ): T? =
        databaseService.withRealmAsync { realm ->
            realm.findCopyByField(clazz, fieldName, value)
        }

    protected suspend fun <T : RealmObject, V : Any> findFirstByFields(
        clazz: Class<T>,
        value: V,
        vararg fieldNames: String,
    ): T? = findFirstByFields(clazz, value, fieldNames.asList())

    protected suspend fun <T : RealmObject, V : Any> findFirstByFields(
        clazz: Class<T>,
        value: V,
        fieldNames: Collection<String>,
    ): T? {
        require(fieldNames.isNotEmpty()) { "At least one field name must be provided" }
        return databaseService.withRealmAsync { realm ->
            var match: T? = null
            for (fieldName in fieldNames) {
                if (fieldName.isBlank()) continue

                val result = realm.findCopyByField(clazz, fieldName, value)
                if (result != null) {
                    match = result
                    break
                }
            }
            match
        }
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

    protected suspend fun <T> withRealmAsync(operation: (Realm) -> T): T {
        return databaseService.withRealmAsync(operation)
    }

    protected fun <T> withRealmFlow(
        block: suspend (Realm, ProducerScope<T>) -> (() -> Unit),
    ): Flow<T> =
        callbackFlow {
            val realm = Realm.getDefaultInstance()
            val cleanup = try {
                block(realm, this)
            } catch (throwable: Throwable) {
                realm.close()
                throw throwable
            }
            awaitClose {
                cleanup()
                if (!realm.isClosed) {
                    realm.close()
                }
            }
        }

    protected suspend fun executeTransaction(transaction: (Realm) -> Unit) {
        databaseService.executeTransactionAsync(transaction)
    }
}
