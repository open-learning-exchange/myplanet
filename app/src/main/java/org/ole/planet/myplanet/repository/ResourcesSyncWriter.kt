package org.ole.planet.myplanet.repository

import com.google.gson.JsonObject

interface ResourcesSyncWriter {
    suspend fun removeDeletedResources(currentIds: List<String?>)
    suspend fun batchInsertResources(documents: List<JsonObject>): List<String>
    suspend fun batchInsertMyLibrary(shelfId: String?, documents: List<JsonObject>): Int
}
