package org.ole.planet.myplanet.datamanager

import android.content.Context
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.log.LogLevel
import io.realm.kotlin.log.RealmLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.model.RealmMyTeam

class DatabaseService(context: Context) {
    private val realm: Realm

    init {
        RealmLog.level = LogLevel.DEBUG
        val config = RealmConfiguration.Builder(schema = setOf(RealmMyTeam::class))
            .name("default.realm")
            .deleteRealmIfMigrationNeeded()
            .schemaVersion(4)
            .build()
        realm = Realm.open(config)
    }

    fun <T> withRealm(operation: Realm.() -> T): T {
        return realm.operation()
    }

    suspend fun <T> withRealmAsync(operation: Realm.() -> T): T {
        return withContext(Dispatchers.IO) { realm.operation() }
    }

    suspend fun executeTransactionAsync(transaction: MutableRealm.() -> Unit) {
        withContext(Dispatchers.IO) {
            realm.write { transaction() }
        }
    }

    suspend fun <T> executeTransactionWithResultAsync(transaction: MutableRealm.() -> T): T {
        return withContext(Dispatchers.IO) {
            realm.write { transaction() }
        }
    }
}
