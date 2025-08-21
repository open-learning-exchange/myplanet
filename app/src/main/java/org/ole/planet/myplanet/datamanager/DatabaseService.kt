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

    private val realmInstance: Realm
        get() = Realm.getDefaultInstance()

    private inline fun <T> withRealmInstance(block: (Realm) -> T): T {
        return realmInstance.use(block)
    }

    fun <T> withRealm(operation: (Realm) -> T): T {
        return withRealmInstance(operation)
    }

    suspend fun <T> withRealmAsync(operation: (Realm) -> T): T {
        return withContext(Dispatchers.IO) {
            withRealmInstance(operation)
        }
    }

    suspend fun executeTransactionAsync(transaction: (Realm) -> Unit) {
        withContext(Dispatchers.IO) {
            withRealmInstance { realm ->
                realm.executeTransaction { transaction(it) }
            }
        }
    }

    suspend fun <T> executeTransactionWithResultAsync(transaction: (Realm) -> T): T {
        return withContext(Dispatchers.IO) {
            withRealmInstance { realm ->
                var result: T? = null
                realm.executeTransaction {
                    result = transaction(it)
                }
                result!!
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

fun <T : RealmModel, V> Realm.findCopyByField(
    clazz: Class<T>,
    fieldName: String,
    value: V,
): T? {
    val query = where(clazz)
    when (value) {
        is String -> query.equalTo(fieldName, value)
        is Boolean -> query.equalTo(fieldName, value)
        is Int -> query.equalTo(fieldName, value)
        is Long -> query.equalTo(fieldName, value)
        is Float -> query.equalTo(fieldName, value)
        is Double -> query.equalTo(fieldName, value)
        else -> throw IllegalArgumentException("Unsupported value type")
    }
    return query.findFirst()?.let { copyFromRealm(it) }
}
