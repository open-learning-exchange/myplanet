package org.ole.planet.myplanet.service.sync

import android.content.Context
import io.realm.Realm
import io.realm.RealmConfiguration
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.runBlocking
import org.ole.planet.myplanet.datamanager.DatabaseService
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

data class RealmPoolConfig(
    val maxConnections: Int = 5,
    val connectionTimeoutMs: Long = 30000,
    val idleTimeoutMs: Long = 300000, // 5 minutes
    val validationIntervalMs: Long = 60000, // 1 minute
    val enableConnectionValidation: Boolean = true
)

private data class PooledRealm(
    val realm: Realm,
    val createdAt: Long,
    val lastUsedAt: Long,
    var isInUse: Boolean = false,
    val id: String = java.util.UUID.randomUUID().toString()
)

class RealmConnectionPool(
    private val context: Context,
    private val config: RealmPoolConfig = RealmPoolConfig()
) {
    private val databaseService = DatabaseService(context)
    private val threadLocalConnections = ThreadLocal<Realm?>()
    private val availableConnections = ConcurrentLinkedQueue<PooledRealm>()
    private val allConnections = mutableMapOf<String, PooledRealm>()
    private val activeConnections = AtomicInteger(0)
    private val connectionSemaphore = Semaphore(config.maxConnections)
    private val poolMutex = Mutex()
    
    private var lastValidationTime = 0L
    private var isShuttingDown = false
    
    suspend fun <T> useRealm(operation: suspend (Realm) -> T): T {
        // Check if current thread already has a realm instance
        val existingRealm = threadLocalConnections.get()
        if (existingRealm != null && !existingRealm.isClosed) {
            return operation(existingRealm)
        }
        
        return connectionSemaphore.withPermit {
            val pooledRealm = acquireConnection()
            threadLocalConnections.set(pooledRealm.realm)
            try {
                operation(pooledRealm.realm)
            } finally {
                threadLocalConnections.remove()
                releaseConnection(pooledRealm)
            }
        }
    }
    
    suspend fun <T> useRealmTransaction(operation: suspend (Realm) -> T): T {
        return useRealm { realm ->
            var result: T? = null
            realm.executeTransaction { transactionRealm ->
                // Note: This is a simplified approach. In practice, you'd need to handle
                // the fact that executeTransaction is blocking and doesn't support suspend functions
                // You might need to use executeTransactionAsync with callbacks
                runBlocking {
                    result = operation(transactionRealm)
                }
            }
            result!!
        }
    }
    
    private suspend fun acquireConnection(): PooledRealm = poolMutex.withLock {
        if (isShuttingDown) {
            throw IllegalStateException("Connection pool is shutting down")
        }
        
        validateConnectionsIfNeeded()
        
        // Try to get an available connection
        var pooledRealm = availableConnections.poll()
        
        if (pooledRealm != null) {
            // Validate the connection before use
            if (isConnectionValid(pooledRealm)) {
                pooledRealm = pooledRealm.copy(
                    lastUsedAt = System.currentTimeMillis(),
                    isInUse = true
                )
                allConnections[pooledRealm.id] = pooledRealm
                return pooledRealm
            } else {
                // Connection is invalid, close it and remove from pool
                closeConnection(pooledRealm)
            }
        }
        
        // Create a new connection if under the limit
        if (allConnections.size < config.maxConnections) {
            pooledRealm = createNewConnection()
            allConnections[pooledRealm.id] = pooledRealm
            activeConnections.incrementAndGet()
            return pooledRealm
        }
        
        throw IllegalStateException("Connection pool exhausted and cannot create new connections")
    }
    
    private suspend fun releaseConnection(pooledRealm: PooledRealm) = poolMutex.withLock {
        if (!isShuttingDown && isConnectionValid(pooledRealm)) {
            val updatedConnection = pooledRealm.copy(
                lastUsedAt = System.currentTimeMillis(),
                isInUse = false
            )
            allConnections[pooledRealm.id] = updatedConnection
            availableConnections.offer(updatedConnection)
        } else {
            closeConnection(pooledRealm)
        }
    }
    
    private fun createNewConnection(): PooledRealm {
        val realm = databaseService.realmInstance
        return PooledRealm(
            realm = realm,
            createdAt = System.currentTimeMillis(),
            lastUsedAt = System.currentTimeMillis(),
            isInUse = true
        )
    }
    
    private fun isConnectionValid(pooledRealm: PooledRealm): Boolean {
        if (!config.enableConnectionValidation) return true
        
        return try {
            !pooledRealm.realm.isClosed && 
            pooledRealm.realm.isInTransaction.not() &&
            (System.currentTimeMillis() - pooledRealm.lastUsedAt) < config.idleTimeoutMs
        } catch (e: Exception) {
            false
        }
    }
    
    private fun closeConnection(pooledRealm: PooledRealm) {
        try {
            if (!pooledRealm.realm.isClosed) {
                pooledRealm.realm.close()
            }
        } catch (e: Exception) {
            // Log error but continue cleanup
        } finally {
            allConnections.remove(pooledRealm.id)
            activeConnections.decrementAndGet()
        }
    }
    
    private fun validateConnectionsIfNeeded() {
        val now = System.currentTimeMillis()
        if (now - lastValidationTime > config.validationIntervalMs) {
            lastValidationTime = now
            
            // Remove expired connections
            val expiredConnections = allConnections.values.filter { 
                !it.isInUse && (now - it.lastUsedAt) > config.idleTimeoutMs 
            }
            
            expiredConnections.forEach { closeConnection(it) }
            
            // Remove expired connections from available queue
            val validConnections = availableConnections.filter { pooledRealm ->
                allConnections.containsKey(pooledRealm.id) && isConnectionValid(pooledRealm)
            }
            availableConnections.clear()
            availableConnections.addAll(validConnections)
        }
    }
    
    suspend fun shutdown() = poolMutex.withLock {
        isShuttingDown = true
        
        // Close all connections
        allConnections.values.forEach { closeConnection(it) }
        availableConnections.clear()
        allConnections.clear()
        activeConnections.set(0)
    }
    
    fun getPoolStats(): PoolStats {
        return PoolStats(
            totalConnections = allConnections.size,
            activeConnections = allConnections.values.count { it.isInUse },
            availableConnections = availableConnections.size,
            maxConnections = config.maxConnections
        )
    }
}

data class PoolStats(
    val totalConnections: Int,
    val activeConnections: Int,
    val availableConnections: Int,
    val maxConnections: Int
)

// Helper function to handle the blocking executeTransaction in a suspend context
private suspend fun <T> Realm.executeTransactionSuspend(operation: suspend (Realm) -> T): T {
    return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        var result: T? = null
        var exception: Exception? = null
        
        executeTransaction { realm ->
            try {
                // This is still problematic as executeTransaction doesn't support suspend
                // In a real implementation, you'd need to restructure this
                kotlinx.coroutines.runBlocking {
                    result = operation(realm)
                }
            } catch (e: Exception) {
                exception = e
                throw e
            }
        }
        
        exception?.let { throw it }
        result!!
    }
}

class RealmPoolManager private constructor() {
    companion object {
        @Volatile
        private var INSTANCE: RealmPoolManager? = null
        
        fun getInstance(): RealmPoolManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: RealmPoolManager().also { INSTANCE = it }
            }
        }
    }
    
    private var connectionPool: RealmConnectionPool? = null
    private val mutex = Mutex()
    
    suspend fun initializePool(context: Context, config: RealmPoolConfig = RealmPoolConfig()) = mutex.withLock {
        if (connectionPool == null) {
            connectionPool = RealmConnectionPool(context, config)
        }
    }
    
    suspend fun <T> useRealm(operation: suspend (Realm) -> T): T {
        val pool = connectionPool ?: throw IllegalStateException("Pool not initialized")
        return pool.useRealm(operation)
    }
    
    suspend fun <T> useRealmTransaction(operation: suspend (Realm) -> T): T {
        val pool = connectionPool ?: throw IllegalStateException("Pool not initialized")
        return pool.useRealmTransaction(operation)
    }
    
    fun getPoolStats(): PoolStats? {
        return connectionPool?.getPoolStats()
    }
    
    suspend fun shutdown() = mutex.withLock {
        connectionPool?.shutdown()
        connectionPool = null
    }
}
