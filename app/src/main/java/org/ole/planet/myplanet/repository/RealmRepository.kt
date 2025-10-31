package org.ole.planet.myplanet.repository

import android.util.Log
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
            val flowStartTime = System.currentTimeMillis()
            Log.d("MyPersonalTiming", "[${flowStartTime}] queryListFlow setup starting for ${clazz.simpleName}")

            val results = realm.where(clazz).apply(builder).findAllAsync()
            val listener =
                RealmChangeListener<RealmResults<T>> { updatedResults ->
                    val listenerTime = System.currentTimeMillis()
                    Log.d("MyPersonalTiming", "[${listenerTime}] Realm change listener triggered for ${clazz.simpleName}, loaded=${updatedResults.isLoaded}, valid=${updatedResults.isValid}, count=${updatedResults.size}")
                    if (updatedResults.isLoaded && updatedResults.isValid) {
                        val beforeCopy = System.currentTimeMillis()
                        val copied = realm.copyFromRealm(updatedResults)
                        val afterCopy = System.currentTimeMillis()
                        Log.d("MyPersonalTiming", "[${afterCopy}] Copied ${copied.size} items (+${afterCopy - beforeCopy}ms)")
                        scope.trySend(copied)
                        val afterSend = System.currentTimeMillis()
                        Log.d("MyPersonalTiming", "[${afterSend}] Sent to flow (+${afterSend - afterCopy}ms)")
                    }
                }
            results.addChangeListener(listener)
            if (results.isLoaded && results.isValid) {
                val initialTime = System.currentTimeMillis()
                Log.d("MyPersonalTiming", "[${initialTime}] Initial data loaded, sending ${results.size} items (+${initialTime - flowStartTime}ms)")
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

    protected suspend fun <T : RealmObject> save(item: T) {
        val startTime = System.currentTimeMillis()
        Log.d("MyPersonalTiming", "[${startTime}] RealmRepository.save() starting async transaction")

        executeTransaction { realm ->
            realm.copyToRealmOrUpdate(item)
        }

        val endTime = System.currentTimeMillis()
        Log.d("MyPersonalTiming", "[${endTime}] RealmRepository.save() async transaction completed (+${endTime - startTime}ms)")
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
