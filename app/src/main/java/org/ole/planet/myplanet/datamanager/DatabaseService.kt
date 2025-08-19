package org.ole.planet.myplanet.datamanager

import android.content.Context
import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.RealmModel
import io.realm.RealmQuery
import io.realm.log.LogLevel
import io.realm.log.RealmLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DatabaseService(context: Context) {
    init {
        Realm.init(context)
        RealmLog.setLevel(LogLevel.DEBUG)
        val config = RealmConfiguration.Builder()
            .name(Realm.DEFAULT_REALM_NAME)
            .deleteRealmIfMigrationNeeded()
            .schemaVersion(4)
            .build()
        Realm.setDefaultConfiguration(config)
    }

    val realmInstance: Realm
        get() = Realm.getDefaultInstance()
    
    fun <T> withRealm(operation: (Realm) -> T): T {
        return Realm.getDefaultInstance().use { realm ->
            operation(realm)
        }
    }
    
    suspend fun <T> withRealmAsync(operation: (Realm) -> T): T {
        return withContext(Dispatchers.IO) {
            Realm.getDefaultInstance().use { realm ->
                operation(realm)
            }
        }
    }
    
    suspend fun executeTransactionAsync(transaction: (Realm) -> Unit) {
        withContext(Dispatchers.IO) {
            Realm.getDefaultInstance().use { realm ->
                realm.executeTransaction { transaction(it) }
            }
        }
    }
    
    suspend fun <T> executeTransactionWithResultAsync(transaction: (Realm) -> T): T? {
        return withContext(Dispatchers.IO) {
            Realm.getDefaultInstance().use { realm ->
                var result: T? = null
                realm.executeTransaction {
                    result = transaction(it)
                }
                result
            }
        }
    }
}

fun <T : RealmModel> Realm.queryList(
    clazz: Class<T>,
    builder: RealmQuery<T>.() -> Unit = {}
): List<T> {
    return where(clazz).apply(builder).findAll().let { copyFromRealm(it) }
}
