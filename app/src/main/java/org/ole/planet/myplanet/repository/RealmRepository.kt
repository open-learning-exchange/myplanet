package org.ole.planet.myplanet.repository

import io.realm.Realm
import io.realm.RealmModel
import io.realm.RealmQuery
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.datamanager.findCopyByField
import org.ole.planet.myplanet.datamanager.queryList

open class RealmRepository(private val databaseService: DatabaseService) {

    protected suspend fun <T : RealmModel> queryList(
        clazz: Class<T>,
        builder: RealmQuery<T>.() -> Unit = {}
    ): List<T> = databaseService.withRealmAsync { realm ->
        realm.queryList(clazz, builder)
    }

    protected suspend fun <T : RealmModel, V> findByField(
        clazz: Class<T>,
        fieldName: String,
        value: V
    ): T? = databaseService.withRealmAsync { realm ->
        realm.findCopyByField(clazz, fieldName, value)
    }

    protected suspend fun <T : RealmModel> save(item: T) {
        executeTransaction { realm ->
            realm.copyToRealmOrUpdate(item)
        }
    }

    protected suspend fun <T : RealmModel, V> update(
        clazz: Class<T>,
        fieldName: String,
        value: V,
        updater: (T) -> Unit
    ) {
        executeTransaction { realm ->
            val query = realm.where(clazz)
            when (value) {
                is String -> query.equalTo(fieldName, value)
                is Boolean -> query.equalTo(fieldName, value)
                is Int -> query.equalTo(fieldName, value)
                is Long -> query.equalTo(fieldName, value)
                is Float -> query.equalTo(fieldName, value)
                is Double -> query.equalTo(fieldName, value)
                else -> throw IllegalArgumentException("Unsupported value type")
            }
            query.findFirst()?.let { updater(it) }
        }
    }

    protected suspend fun <T : RealmModel, V> delete(
        clazz: Class<T>,
        fieldName: String,
        value: V
    ) {
        executeTransaction { realm ->
            val query = realm.where(clazz)
            when (value) {
                is String -> query.equalTo(fieldName, value)
                is Boolean -> query.equalTo(fieldName, value)
                is Int -> query.equalTo(fieldName, value)
                is Long -> query.equalTo(fieldName, value)
                is Float -> query.equalTo(fieldName, value)
                is Double -> query.equalTo(fieldName, value)
                else -> throw IllegalArgumentException("Unsupported value type")
            }
            query.findFirst()?.deleteFromRealm()
        }
    }

    protected suspend fun <T> withRealm(operation: (Realm) -> T): T {
        return databaseService.withRealmAsync(operation)
    }

    protected suspend fun executeTransaction(transaction: (Realm) -> Unit) {
        databaseService.executeTransactionAsync(transaction)
    }
}

