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
import org.ole.planet.myplanet.data.room.AppDatabase
import org.ole.planet.myplanet.BuildConfig
import org.ole.planet.myplanet.utils.DispatcherProvider

class DatabaseService(
    context: Context,
    private val dispatcherProvider: DispatcherProvider,
    private val appDatabase: AppDatabase? = null,
) {
    val ioDispatcher: CoroutineDispatcher = dispatcherProvider.io
    private val realmDispatcher: CoroutineDispatcher = dispatcherProvider.io.limitedParallelism(4)

    init {
        Realm.init(context)
        val targetLogLevel = if (BuildConfig.DEBUG) LogLevel.DEBUG else LogLevel.ERROR
        if (RealmLog.getLevel() != targetLogLevel) {
            RealmLog.setLevel(targetLogLevel)
        }
        val currentConfig = Realm.getDefaultConfiguration()
        if (currentConfig == null || currentConfig.realmDirectory.name == Realm.DEFAULT_REALM_NAME) {
            val config = RealmConfiguration.Builder()
                .name(Realm.DEFAULT_REALM_NAME)
                .schemaVersion(17)
                // Realm -> Room migration transition: as each model is converted to a Room entity
                // it leaves the Realm schema. Rather than hand-writing a Realm migration to drop
                // each removed table, the (drop-and-resync) strategy recreates the Realm file on any
                // schema mismatch; Room data is repopulated from the server. Realm is removed
                // entirely once every domain is migrated.
                .deleteRealmIfMigrationNeeded()
                .compactOnLaunch()
                .build()
            Realm.setDefaultConfiguration(config)
        }
    }

    fun createManagedRealmInstance(): Realm = openRealm()

    fun roomDatabase(): AppDatabase {
        return appDatabase ?: error("Room database is not configured for DatabaseService")
    }

    private fun openRealm(): Realm {
        return try {
            Realm.getDefaultInstance()
        } catch (e: RuntimeException) {
            if (!isUnsupportedSchemaVersion(e)) {
                throw e
            }
            val config = Realm.getDefaultConfiguration() ?: throw e
            Realm.deleteRealm(config)
            Realm.getDefaultInstance()
        }
    }

    private fun isUnsupportedSchemaVersion(e: Throwable): Boolean {
        var cause: Throwable? = e
        while (cause != null) {
            if (cause is RealmMigrations.UnsupportedSchemaVersionException) {
                return true
            }
            cause = cause.cause
        }
        return false
    }

    private inline fun <T> withRealmInstance(block: (Realm) -> T): T {
        val realm = openRealm()
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
        withContext(realmDispatcher) {
            val realm = openRealm()
            try {
                realm.executeTransaction { r ->
                    transaction(r)
                }
            } finally {
                if (!realm.isClosed) {
                    realm.close()
                }
            }
        }
    }

    suspend fun <T> withRoomAsync(operation: suspend (AppDatabase) -> T): T {
        return withContext(ioDispatcher) {
            operation(roomDatabase())
        }
    }

    suspend fun <T> executeRoomTransactionAsync(operation: (AppDatabase) -> T): T {
        return withContext(ioDispatcher) {
            roomDatabase().runInTransaction<T> {
                operation(roomDatabase())
            }
        }
    }

    suspend fun clearAll() {
        appDatabase?.let { database ->
            withContext(ioDispatcher) {
                database.clearAllTables()
            }
        }
        executeTransactionAsync { it.deleteAll() }
    }

}

fun <T : RealmModel> Realm.queryList(
    clazz: Class<T>,
    builder: RealmQuery<T>.() -> Unit = {}
): List<T> {
    return where(clazz).apply(builder).findAll().let { copyFromRealm(it) }
}

fun <T : RealmModel> Realm.queryList(clazz: Class<T>, maxDepth: Int, builder: RealmQuery<T>.() -> Unit = {}): List<T> {
    return where(clazz).apply(builder).findAll().let { copyFromRealm(it, maxDepth) }
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
