package org.ole.planet.myplanet.data

import android.content.Context
import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.RealmModel
import io.realm.RealmQuery
import io.realm.RealmResults
import io.realm.log.LogLevel
import io.realm.log.RealmLog
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.BuildConfig

class DatabaseService(context: Context) {
    val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

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
            val realm = Realm.getDefaultInstance()
            try {
                realm.beginTransaction()
                transaction(realm)
                if (realm.isInTransaction) {
                    realm.commitTransaction()
                }
            } catch (e: Exception) {
                if (realm.isInTransaction) {
                    realm.cancelTransaction()
                }
                throw e
            } finally {
                if (!realm.isClosed) {
                    realm.close()
                }
            }
        }
    }

}

/**
 * Returns a detached copy of the list. Safe to use across threads.
 */
fun <T : RealmModel> Realm.queryList(
    clazz: Class<T>,
    builder: RealmQuery<T>.() -> Unit = {}
): List<T> {
    return where(clazz).apply(builder).findAll().let { copyFromRealm(it) }
}

/**
 * Returns a managed RealmResults. faster, but MUST NOT be used across threads or after Realm is closed.
 */
fun <T : RealmModel> Realm.queryListAttached(
    clazz: Class<T>,
    builder: RealmQuery<T>.() -> Unit = {}
): RealmResults<T> {
    return where(clazz).apply(builder).findAll()
}

/**
 * Returns a detached copy of the object. Safe to use across threads.
 */
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

/**
 * Returns a managed Realm object. faster, but MUST NOT be used across threads or after Realm is closed.
 */
fun <T : RealmModel, V : Any> Realm.findAttachedByField(
    clazz: Class<T>,
    fieldName: String,
    value: V,
): T? {
    return where(clazz)
        .applyEqualTo(fieldName, value)
        .findFirst()
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
