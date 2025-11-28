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
import org.ole.planet.myplanet.BuildConfig

class DatabaseService(private val context: Context) {
    private var isRealmInitialized = false
    private val initializationLock = Any()

    private fun ensureRealmInitialized() {
        synchronized(initializationLock) {
            if (isRealmInitialized) return
            Realm.init(context)
            val targetLogLevel = if (BuildConfig.DEBUG) LogLevel.DEBUG else LogLevel.ERROR
            if (RealmLog.getLevel() != targetLogLevel) {
                RealmLog.setLevel(targetLogLevel)
            }
            val config = RealmConfiguration.Builder()
                .name(Realm.DEFAULT_REALM_NAME)
                .addModule(Realm.getDefaultModule())
                .migration(AppRealmMigration())
                .schemaVersion(4)
                .build()
            Realm.setDefaultConfiguration(config)
            isRealmInitialized = true
        }
    }

    @Deprecated(
        "Use withRealm/withRealmAsync instead",
        replaceWith = ReplaceWith("withRealm { realm -> /* your code */ }")
    )
    val realmInstance: Realm
        get() {
            ensureRealmInitialized()
            return Realm.getDefaultInstance()
        }

    private inline fun <T> withRealmInstance(block: (Realm) -> T): T {
        ensureRealmInitialized()
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
        ensureRealmInitialized()
        return withRealmInstance(operation)
    }

    suspend fun <T> withRealmAsync(operation: (Realm) -> T): T {
        ensureRealmInitialized()
        return withContext(Dispatchers.IO) {
            withRealmInstance(operation)
        }
    }

    suspend fun executeTransactionAsync(transaction: (Realm) -> Unit) {
        ensureRealmInitialized()
        withContext(Dispatchers.IO) {
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
