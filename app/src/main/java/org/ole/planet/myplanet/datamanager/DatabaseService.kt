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

    @Deprecated("Use withRealm/withRealmAsync instead")
    val realmInstance: Realm
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

    fun executeTransactionAsync(
        transaction: (Realm) -> Unit,
        onSuccess: (() -> Unit)? = null,
        onError: ((Throwable) -> Unit)? = null,
    ) {
        val realm = Realm.getDefaultInstance()
        realm.executeTransactionAsync({ backgroundRealm ->
            transaction(backgroundRealm)
        }, {
            realm.close()
            onSuccess?.invoke()
        }, { error ->
            realm.close()
            onError?.invoke(error)
        })
    }

    suspend fun executeTransactionAsync(transaction: (Realm) -> Unit) {
        withContext(Dispatchers.IO) {
            withRealmInstance { realm ->
                try {
                    if (realm.isClosed) {
                        return@withRealmInstance
                    }
                    realm.executeTransaction { transaction(it) }
                } catch (e: IllegalStateException) {
                    if (e.message?.contains("non-existing write transaction") == true ||
                        e.message?.contains("not currently in a transaction") == true) {
                        return@withRealmInstance
                    }
                    throw e
                }
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

fun <T : RealmModel, V : Any> Realm.findCopyByField(
    clazz: Class<T>,
    fieldName: String,
    value: V,
): T? {
    return where(clazz)
        .applyEqualTo(fieldName, value)
        .findFirst()
        ?.let { copyFromRealm(it) }
}

fun <T : RealmModel> RealmQuery<T>.applyEqualTo(field: String, value: Any): RealmQuery<T> {
    return when (value) {
        is String -> equalTo(field, value)
        is Boolean -> equalTo(field, value)
        is Int -> equalTo(field, value)
        is Long -> equalTo(field, value)
        is Float -> equalTo(field, value)
        is Double -> equalTo(field, value)
        else -> throw IllegalArgumentException("Unsupported value type")
    }
}
