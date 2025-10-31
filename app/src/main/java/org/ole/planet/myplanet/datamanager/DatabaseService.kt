package org.ole.planet.myplanet.datamanager

import android.content.Context
import android.util.Log
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
        val startTime = System.currentTimeMillis()
        Log.d("DatabaseService", "[${startTime}ms] executeTransactionAsync() called, switching to IO dispatcher")
        withContext(Dispatchers.IO) {
            val ioTime = System.currentTimeMillis()
            Log.d("DatabaseService", "[${ioTime}ms] (+${ioTime - startTime}ms) On IO dispatcher, getting Realm instance...")
            withRealmInstance { realm ->
                val realmTime = System.currentTimeMillis()
                Log.d("DatabaseService", "[${realmTime}ms] (+${realmTime - ioTime}ms) Realm instance obtained, checking if closed...")
                try {
                    if (realm.isClosed) {
                        Log.w("DatabaseService", "Realm instance is CLOSED, aborting transaction")
                        return@withRealmInstance
                    }
                    val preExecTime = System.currentTimeMillis()
                    Log.d("DatabaseService", "[${preExecTime}ms] (+${preExecTime - realmTime}ms) Realm is open, executing transaction...")
                    realm.executeTransaction {
                        val insideTransactionTime = System.currentTimeMillis()
                        Log.d("DatabaseService", "[${insideTransactionTime}ms] (+${insideTransactionTime - preExecTime}ms) Inside Realm.executeTransaction block, calling user transaction")
                        transaction(it)
                        val afterUserTransactionTime = System.currentTimeMillis()
                        Log.d("DatabaseService", "[${afterUserTransactionTime}ms] (+${afterUserTransactionTime - insideTransactionTime}ms) User transaction completed")
                    }
                    val postExecTime = System.currentTimeMillis()
                    Log.d("DatabaseService", "[${postExecTime}ms] (+${postExecTime - preExecTime}ms) Realm.executeTransaction completed successfully")
                } catch (e: IllegalStateException) {
                    Log.e("DatabaseService", "IllegalStateException caught: ${e.message}", e)
                    if (e.message?.contains("non-existing write transaction") == true ||
                        e.message?.contains("not currently in a transaction") == true) {
                        Log.w("DatabaseService", "Swallowing transaction-related IllegalStateException, returning")
                        return@withRealmInstance
                    }
                    Log.e("DatabaseService", "Re-throwing IllegalStateException")
                    throw e
                } catch (e: Exception) {
                    Log.e("DatabaseService", "Unexpected exception during transaction", e)
                    throw e
                }
            }
            val endTime = System.currentTimeMillis()
            Log.d("DatabaseService", "[${endTime}ms] (+${endTime - startTime}ms total) withRealmInstance block completed")
        }
        val completeTime = System.currentTimeMillis()
        Log.d("DatabaseService", "[${completeTime}ms] (+${completeTime - startTime}ms total) executeTransactionAsync() completed")
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
