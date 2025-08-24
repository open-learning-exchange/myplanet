package org.ole.planet.myplanet.repository

import io.realm.kotlin.Realm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.query
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.notifications.asFlow
import org.ole.planet.myplanet.datamanager.DatabaseService
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow

open class RealmRepository(private val databaseService: DatabaseService) {

    protected suspend fun <T : RealmObject> queryList(
        clazz: KClass<T>,
        query: String = "TRUEPREDICATE",
        vararg args: Any?
    ): List<T> = databaseService.withRealmAsync { realm ->
        realm.query(clazz, query, *args).find()
    }

    protected suspend fun <T : RealmObject, V : Any> findByField(
        clazz: KClass<T>,
        fieldName: String,
        value: V
    ): T? = databaseService.withRealmAsync { realm ->
        realm.query(clazz, "$fieldName == $0", value).first().find()
    }

    protected suspend fun <T : RealmObject> save(item: T) {
        databaseService.executeTransactionAsync {
            copyToRealm(item, updatePolicy = UpdatePolicy.ALL)
        }
    }

    protected suspend fun <T : RealmObject, V : Any> update(
        clazz: KClass<T>,
        fieldName: String,
        value: V,
        updater: (T) -> Unit
    ) {
        databaseService.executeTransactionAsync {
            val obj = query(clazz, "$fieldName == $0", value).first().find()
            if (obj != null) updater(obj)
        }
    }

    protected suspend fun <T : RealmObject, V : Any> delete(
        clazz: KClass<T>,
        fieldName: String,
        value: V
    ) {
        databaseService.executeTransactionAsync {
            val obj = query(clazz, "$fieldName == $0", value).first().find()
            obj?.let { delete(it) }
        }
    }

    protected fun <T : RealmObject> queryFlow(
        clazz: KClass<T>,
        query: String = "TRUEPREDICATE",
        vararg args: Any?
    ): Flow<ResultsChange<T>> = databaseService.withRealm { realm ->
        realm.query(clazz, query, *args).asFlow()
    }

    protected suspend fun <T> withRealm(operation: (Realm) -> T): T {
        return databaseService.withRealmAsync(operation)
    }

    protected suspend fun executeTransaction(transaction: MutableRealm.() -> Unit) {
        databaseService.executeTransactionAsync(transaction)
    }
}
