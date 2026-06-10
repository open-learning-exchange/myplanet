package org.ole.planet.myplanet.repository

import android.util.Log
import io.realm.RealmObject
import javax.inject.Inject
import javax.inject.Singleton
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.services.upload.UploadConfig
import org.ole.planet.myplanet.services.upload.UploadedItem
import org.ole.planet.myplanet.utils.DispatcherProvider

@Singleton
class UploadRepositoryImpl @Inject constructor(
    databaseService: DatabaseService,
    dispatcherProvider: DispatcherProvider
) : RealmRepository(databaseService, dispatcherProvider.io), UploadRepository {

    override suspend fun <T : RealmObject> queryPending(config: UploadConfig<T>): List<T> {
        return withRealmAsync { realm ->
            val query = realm.where(config.modelClass.java)
            val filteredQuery = config.queryBuilder(query)
            val results = filteredQuery.findAll()
            results.map { realm.copyFromRealm(it) }
        }
    }

    override suspend fun <T : RealmObject> markUploaded(config: UploadConfig<T>, succeeded: List<UploadedItem>): List<UploadedItem> {
        val failedLocally = mutableListOf<UploadedItem>()
        executeTransaction { realm ->
            val localIds = succeeded.map { it.localId }
            val idFieldName = realm.schema.get(config.modelClass.java.simpleName)?.primaryKey ?: "id"

            val itemsById = mutableMapOf<String, T>()
            if (localIds.isNotEmpty()) {
                val query = realm.where(config.modelClass.java)
                localIds.chunked(1000).forEachIndexed { index, chunk ->
                    if (index > 0) query.or()
                    query.`in`(idFieldName, chunk.toTypedArray())
                }
                val results = query.findAll()
                results.forEach { item ->
                    val localId = config.idExtractor(item) ?: ""
                    itemsById[localId] = item
                }
            }

            succeeded.forEach { uploadedItem ->
                try {
                    val item = itemsById[uploadedItem.localId]

                    item?.let {
                        config.additionalUpdates?.invoke(realm, it, uploadedItem)
                    } ?: run {
                        failedLocally.add(uploadedItem)
                    }
                } catch (e: Exception) {
                    Log.e("UploadRepositoryImpl", "Failed to update item ${uploadedItem.localId}", e)
                    failedLocally.add(uploadedItem)
                }
            }
        }
        return failedLocally
    }


}
