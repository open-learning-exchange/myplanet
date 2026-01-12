package org.ole.planet.myplanet.data

import android.content.Context
import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.RealmModel
import io.realm.RealmQuery
import io.realm.log.LogLevel
import io.realm.log.RealmLog
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.BuildConfig
import org.ole.planet.myplanet.di.DispatcherProvider

class DatabaseService(context: Context, private val dispatcherProvider: DispatcherProvider) {
    val ioDispatcher: CoroutineDispatcher = dispatcherProvider.io

    init {
        Realm.init(context)
        val targetLogLevel = if (BuildConfig.DEBUG) LogLevel.DEBUG else LogLevel.ERROR
        if (RealmLog.getLevel() != targetLogLevel) {
            RealmLog.setLevel(targetLogLevel)
        }
        val config = RealmConfiguration.Builder()
            .name(Realm.DEFAULT_REALM_NAME)
            .schemaVersion(5)
            .migration(RealmMigrations())
            .build()
        Realm.setDefaultConfiguration(config)
    }

    @Deprecated("Use withRealm/withRealmAsync instead")
    val realmInstance: Realm
        get() = Realm.getDefaultInstance()

    private inline fun <T> withRealmInstance(block: (Realm) -> T): T {
        val realm = Realm.getDefaultInstance()
        return try {
            block(realm)
        } finally {
            if (!realm.isClosed) {
                realm.close()
            }
        }
    }

    fun <T> withRealm(operation: (Realm) -> T): T {
        return withRealmInstance(operation)
    }

    suspend fun <T> withRealmAsync(operation: (Realm) -> T): T {
        return withContext(ioDispatcher) {
            withRealmInstance(operation)
        }
    }

    suspend fun executeTransactionAsync(transaction: (Realm) -> Unit) {
        withContext(ioDispatcher) {
            Realm.getDefaultInstance().use { realm ->
                realm.executeTransaction { transactionRealm ->
                    transaction(transactionRealm)
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
