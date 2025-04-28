package org.ole.planet.myplanet.utilities

import android.util.Log
import io.realm.Realm
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.atomics.AtomicBoolean

object RealmTransactionManager {
    @OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)
    private val transactionMap = ConcurrentHashMap<Realm, AtomicBoolean>()

    fun executeInTransaction(realm: Realm, operations: (Realm) -> Unit) {
        if (realm.isClosed) {
            Log.e("RealmTransactionManager", "Cannot execute transaction on closed Realm instance")
            return
        }

        val wasInTransaction = realm.isInTransaction

        try {
            if (!wasInTransaction) {
                try {
                    SyncTimingLogger.logOperation("transaction_begin")
                    realm.beginTransaction()
                } catch (e: Exception) {
                    Log.e("RealmTransactionManager", "Failed to begin transaction: ${e.message}")
                    throw e
                }
            }

            operations(realm)

            if (!wasInTransaction) {
                try {
                    if (realm.isInTransaction) {
                        SyncTimingLogger.logOperation("transaction_commit")
                        realm.commitTransaction()
                    } else {
                        Log.w("RealmTransactionManager", "Attempted to commit a transaction that wasn't active")
                    }
                } catch (e: Exception) {
                    Log.e("RealmTransactionManager", "Failed to commit transaction: ${e.message}")
                    throw e
                }
            }
        } catch (e: Exception) {
            if (!wasInTransaction) {
                try {
                    if (realm.isInTransaction) {
                        SyncTimingLogger.logOperation("transaction_cancel")
                        realm.cancelTransaction()
                    }
                } catch (cancelError: Exception) {
                    Log.e("RealmTransactionManager", "Failed to cancel transaction: ${cancelError.message}")
                }
            }
            throw e
        }
    }

    /**
     * Safely executes a batch operation, suitable for bulk operations
     */
    fun executeBatchOperation(realm: Realm, items: List<*>, operation: (Realm, Any?) -> Unit) {
        if (realm.isClosed) {
            Log.e("RealmTransactionManager", "Cannot execute batch operation on closed Realm instance")
            return
        }

        val wasInTransaction = realm.isInTransaction

        try {
            if (!wasInTransaction) {
                try {
                    realm.beginTransaction()
                } catch (e: Exception) {
                    Log.e("RealmTransactionManager", "Failed to begin batch transaction: ${e.message}")
                    throw e
                }
            }

            items.forEach { item ->
                try {
                    operation(realm, item)
                } catch (e: Exception) {
                    Log.e("RealmTransactionManager", "Error processing batch item: ${e.message}")
                    // Continue with other items
                }
            }

            if (!wasInTransaction) {
                try {
                    if (realm.isInTransaction) {
                        realm.commitTransaction()
                    } else {
                        Log.w("RealmTransactionManager", "Attempted to commit a batch transaction that wasn't active")
                    }
                } catch (e: Exception) {
                    Log.e("RealmTransactionManager", "Failed to commit batch transaction: ${e.message}")
                    throw e
                }
            }
        } catch (e: Exception) {
            if (!wasInTransaction) {
                try {
                    if (realm.isInTransaction) {
                        realm.cancelTransaction()
                    }
                } catch (cancelError: Exception) {
                    Log.e("RealmTransactionManager", "Failed to cancel batch transaction: ${cancelError.message}")
                }
            }
            throw e
        }
    }
}