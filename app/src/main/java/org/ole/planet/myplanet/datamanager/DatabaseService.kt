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

class DatabaseService(context: Context) {
    init {
        Realm.init(context)
        val targetLogLevel = if (BuildConfig.DEBUG) LogLevel.DEBUG else LogLevel.ERROR
        if (RealmLog.getLevel() != targetLogLevel) {
            RealmLog.setLevel(targetLogLevel)
        }
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
        return withContext(Dispatchers.IO) {
            withRealmInstance(operation)
        }
    }

    suspend fun executeTransactionAsync(transaction: (Realm) -> Unit) {
        withContext(Dispatchers.IO) {
            // Get the actual caller (skip DatabaseService and coroutine frames)
            val caller = Thread.currentThread().stackTrace
                .firstOrNull {
                    !it.className.startsWith("org.ole.planet.myplanet.datamanager.DatabaseService") &&
                    !it.className.startsWith("kotlin.coroutines") &&
                    !it.className.startsWith("kotlinx.coroutines") &&
                    !it.className.startsWith("dalvik.system") &&
                    !it.className.startsWith("java.lang.Thread")
                }
                ?.let { "${it.className}.${it.methodName}:${it.lineNumber}" }
                ?: "Unknown"

            val getInstanceTime = System.currentTimeMillis()
            android.util.Log.d("RatingPerformance", "[${getInstanceTime}] TX from: $caller")

            Realm.getDefaultInstance().use { realm ->
                val transactionStartTime = System.currentTimeMillis()
                realm.executeTransaction { transactionRealm ->
                    val waitTime = System.currentTimeMillis() - transactionStartTime
                    if (waitTime > 100) {
                        android.util.Log.w("RatingPerformance", "[${transactionStartTime}] ⚠️ SLOW: $caller waited ${waitTime}ms!")
                    }
                    val operationStartTime = System.currentTimeMillis()
                    transaction(transactionRealm)
                    val operationTime = System.currentTimeMillis() - operationStartTime
                    if (operationTime > 5000) {
                        android.util.Log.e("RatingPerformance", "[${operationStartTime}] 🔥 BLOCKING: $caller took ${operationTime}ms to execute!")
                    }
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
