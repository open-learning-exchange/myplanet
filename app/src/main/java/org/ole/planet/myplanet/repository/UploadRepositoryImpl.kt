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
        val tempResults = withRealmAsync { realm ->
            val query = realm.where(config.modelClass.java)
            val filteredQuery = config.queryBuilder(query)
            val results = filteredQuery.findAll()
            results.map { realm.copyFromRealm(it) }
        }
        return tempResults
    }

    override suspend fun <T : RealmObject> markUploaded(config: UploadConfig<T>, succeeded: List<UploadedItem>) {
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
                        setRealmField(it, "_id", uploadedItem.remoteId)
                        setRealmField(it, "_rev", uploadedItem.remoteRev)
                        config.additionalUpdates?.invoke(realm, it, uploadedItem)
                    }
                } catch (e: Exception) {
                    Log.e("UploadRepositoryImpl", "Failed to update item ${uploadedItem.localId}", e)
                }
            }
        }
    }

    private fun setRealmField(obj: RealmObject, fieldName: String, value: Any?) {
        try {
            var clazz: Class<*>? = obj.javaClass
            var field: java.lang.reflect.Field? = null

            while (clazz != null && field == null) {
                try {
                    field = clazz.getDeclaredField(fieldName)
                } catch (e: NoSuchFieldException) {
                    clazz = clazz.superclass
                }
            }

            if (field != null) {
                field.isAccessible = true
                field.set(obj, value)
            } else {
                Log.w("UploadRepositoryImpl", "Field $fieldName not found in class hierarchy of ${obj.javaClass.simpleName}")
            }
        } catch (e: Exception) {
            Log.w("UploadRepositoryImpl", "Failed to set field $fieldName: ${e.message}")
        }
    }
}
