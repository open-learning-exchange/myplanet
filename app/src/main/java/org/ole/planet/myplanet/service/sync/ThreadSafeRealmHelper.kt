package org.ole.planet.myplanet.service.sync

import io.realm.Realm
import org.ole.planet.myplanet.data.DatabaseService

object ThreadSafeRealmHelper {
    
    private val threadLocalRealm = ThreadLocal<Realm?>()
    
    fun <T> withRealm(databaseService: DatabaseService, operation: (Realm) -> T): T? {
        return try {
            // Get or create Realm instance for current thread
            val realm = threadLocalRealm.get() ?: run {
                val newRealm = databaseService.realmInstance
                threadLocalRealm.set(newRealm)
                newRealm
            }
            
            if (realm.isClosed) {
                // If realm is closed, create a new one
                val newRealm = databaseService.realmInstance
                threadLocalRealm.set(newRealm)
                operation(newRealm)
            } else {
                operation(realm)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    fun closeThreadRealm() {
        try {
            val realm = threadLocalRealm.get()
            if (realm != null && !realm.isClosed) {
                realm.close()
            }
        } catch (e: Exception) {
            // Ignore close errors
        } finally {
            threadLocalRealm.remove()
        }
    }
    
}
