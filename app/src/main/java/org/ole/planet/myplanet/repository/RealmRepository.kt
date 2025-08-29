package org.ole.planet.myplanet.repository

import io.realm.Realm
import io.realm.RealmObject
import io.realm.RealmQuery
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.datamanager.applyEqualTo
import org.ole.planet.myplanet.datamanager.findCopyByField
import org.ole.planet.myplanet.datamanager.queryList

open class RealmRepository(private val databaseService: DatabaseService) {

    protected suspend fun <T : RealmObject> queryList(
        clazz: Class<T>,
        builder: RealmQuery<T>.() -> Unit = {}
    ): List<T> = databaseService.withRealmAsync { realm ->
        realm.queryList(clazz, builder)
    }

    protected suspend fun <T : RealmObject> queryInList(
        clazz: Class<T>,
        fieldName: String,
        ids: List<String>,
    ): List<T> {
        if (ids.isEmpty()) return emptyList()
        return databaseService.withRealmAsync { realm ->
            realm.queryList(clazz) {
                `in`(fieldName, ids.toTypedArray())
            }
        }
    }


    protected suspend fun <T : RealmObject, V : Any> findByField(
        clazz: Class<T>,
        fieldName: String,
        value: V
    ): T? = databaseService.withRealmAsync { realm ->
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
        updater: (T) -> Unit
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
        value: V
    ) {
        executeTransaction { realm ->
            realm.where(clazz)
                .applyEqualTo(fieldName, value)
                .findFirst()?.deleteFromRealm()
        }
    }

    protected suspend fun <T> withRealm(operation: (Realm) -> T): T {
        return databaseService.withRealmAsync(operation)
    }

    protected suspend fun executeTransaction(transaction: (Realm) -> Unit) {
        databaseService.executeTransactionAsync(transaction)
    }
}

