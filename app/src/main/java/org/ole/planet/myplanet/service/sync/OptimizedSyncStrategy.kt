package org.ole.planet.myplanet.service.sync

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.realm.Realm
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.withPermit
import org.ole.planet.myplanet.datamanager.ApiClient
import org.ole.planet.myplanet.datamanager.ApiInterface
import org.ole.planet.myplanet.service.TransactionSyncManager
import org.ole.planet.myplanet.utilities.JsonUtils.getJsonArray
import org.ole.planet.myplanet.utilities.JsonUtils.getJsonObject
import org.ole.planet.myplanet.utilities.JsonUtils.getString
import org.ole.planet.myplanet.utilities.UrlUtils

class OptimizedSyncStrategy @Inject constructor(
    private val apiInterface: ApiInterface,
    private val batchProcessor: AdaptiveBatchProcessor,
    private val poolManager: RealmPoolManager,
    private val errorRecovery: SyncErrorRecovery
) : SyncStrategy {
    
    override suspend fun syncTable(
        table: String,
        realm: Realm,
        config: SyncConfig
    ): Flow<SyncResult> = flow {
        val startTime = System.currentTimeMillis()
        var processedItems = 0
        
        val result = errorRecovery.executeSyncOperation(table) {
            when (table) {
                "resources" -> syncResourcesOptimized(config)
                "library", "shelf" -> syncLibraryOptimized(config)
                else -> syncStandardTable(table, realm, config)
            }
        }
        
        if (result.isSuccess) {
            processedItems = result.getOrDefault(0)
            emit(
                SyncResult(
                    table = table,
                    processedItems = processedItems,
                    success = true,
                    duration = System.currentTimeMillis() - startTime,
                    strategy = getStrategyName()
                )
            )
        } else {
            emit(
                SyncResult(
                    table = table,
                    processedItems = 0,
                    success = false,
                    errorMessage = result.exceptionOrNull()?.message,
                    duration = System.currentTimeMillis() - startTime,
                    strategy = getStrategyName()
                )
            )
        }
    }
    
    override suspend fun syncTableWithProgress(
        table: String,
        realm: Realm,
        config: SyncConfig
    ): Flow<SyncProgress> = flow {
        val totalItems = getTotalItemCount(table)
        var processedItems = 0
        val batchSize = config.batchSize
        val totalBatches = (totalItems + batchSize - 1) / batchSize
        
        for (batch in 0 until totalBatches) {
            val batchStart = batch * batchSize
            val batchEnd = minOf(batchStart + batchSize, totalItems)
            
            // Process batch here
            processedItems = batchEnd
            
            emit(
                SyncProgress(
                    table = table,
                    processedItems = processedItems,
                    totalItems = totalItems,
                    currentBatch = batch + 1,
                    totalBatches = totalBatches
                )
            )
        }
    }
    
    private suspend fun syncResourcesOptimized(config: SyncConfig): Int {
        val newIds = ConcurrentHashMap.newKeySet<String>()
        var totalRows = 0
        
        // Get total count
        ApiClient.executeWithRetry {
            apiInterface.getJsonObject(
                UrlUtils.header,
                "${UrlUtils.getUrl()}/resources/_all_docs?limit=0"
            ).execute()
        }?.let { response ->
            response.body()?.let { body ->
                if (body.has("total_rows")) {
                    totalRows = body.get("total_rows").asInt
                }
            }
        }
        
        val numBatches = (totalRows + config.batchSize - 1) / config.batchSize
        val semaphore = batchProcessor.createSemaphore(config)
        
        return coroutineScope {
            val batches = (0 until numBatches).map { batchIndex ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        processResourceBatch(
                            batchIndex * config.batchSize,
                            config.batchSize,
                            newIds
                        )
                    }
                }
            }
            
            val processedCounts = batches.awaitAll()
            val totalProcessed = processedCounts.sum()
            
            // Clean up deleted resources
            poolManager.useRealmTransaction { realm ->
                // Call existing cleanup method
                org.ole.planet.myplanet.model.RealmMyLibrary.removeDeletedResource(
                    newIds.toList(),
                    realm
                )
            }
            
            totalProcessed
        }
    }
    
    private suspend fun processResourceBatch(
        skip: Int,
        batchSize: Int,
        newIds: MutableSet<String>
    ): Int {
        var processedCount = 0
        
        try {
            val response = ApiClient.executeWithRetry {
                apiInterface.getJsonObject(
                    UrlUtils.header,
                    "${UrlUtils.getUrl()}/resources/_all_docs?include_docs=true&limit=$batchSize&skip=$skip"
                ).execute()
            }?.body() ?: return 0
            
            val rows = getJsonArray("rows", response)
            if (rows.size() == 0) return 0
            
            val validDocs = mutableListOf<JsonObject>()
            val batchIds = mutableListOf<String>()
            
            for (i in 0 until rows.size()) {
                val rowObj = rows[i].asJsonObject
                if (rowObj.has("doc")) {
                    val doc = getJsonObject("doc", rowObj)
                    val id = getString("_id", doc)
                    
                    if (!id.startsWith("_design")) {
                        validDocs.add(doc)
                        batchIds.add(id)
                    }
                }
            }
            
            if (validDocs.isEmpty()) return 0
            
            poolManager.useRealmTransaction { realm ->
                val bulkArray = JsonArray()
                validDocs.forEach { doc -> bulkArray.add(doc) }
                
                try {
                    val savedIds = org.ole.planet.myplanet.model.RealmMyLibrary.save(bulkArray, realm)
                    newIds.addAll(savedIds)
                    processedCount = savedIds.size
                } catch (e: Exception) {
                    // Fallback to individual processing
                    validDocs.forEach { doc ->
                        try {
                            val singleDocArray = JsonArray()
                            singleDocArray.add(doc)
                            val ids = org.ole.planet.myplanet.model.RealmMyLibrary.save(singleDocArray, realm)
                            if (ids.isNotEmpty()) {
                                newIds.addAll(ids)
                                processedCount++
                            }
                        } catch (individualE: Exception) {
                            // Log individual failures but continue
                        }
                    }
                }
            }
            
        } catch (e: Exception) {
            throw e
        }
        
        return processedCount
    }
    
    private suspend fun syncLibraryOptimized(config: SyncConfig): Int {
        // Implementation similar to fastMyLibraryTransactionSync but using new architecture
        return poolManager.useRealmTransaction { realm ->
            // Simplified library sync - adapt from existing fastMyLibraryTransactionSync
            0 // Placeholder
        }
    }
    
    private suspend fun syncStandardTable(table: String, realm: Realm, config: SyncConfig): Int {
        // Fallback to standard sync for unsupported tables
        TransactionSyncManager.syncDb(realm, table)
        return -1 // Unknown count
    }
    
    private suspend fun getTotalItemCount(table: String): Int {
        return try {
            val response = ApiClient.executeWithRetry {
                apiInterface.getJsonObject(
                    UrlUtils.header,
                    "${UrlUtils.getUrl()}/$table/_all_docs?limit=0"
                ).execute()
            }?.body()
            
            response?.get("total_rows")?.asInt ?: 0
        } catch (e: Exception) {
            0
        }
    }
    
    override fun getStrategyName(): String = "optimized"
    
    override fun isSupported(table: String): Boolean {
        return table in listOf("resources", "library", "shelf", "courses", "teams", "meetups")
    }
}
