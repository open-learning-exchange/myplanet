package org.ole.planet.myplanet.repository

import android.util.Log
import com.google.gson.JsonObject
import retrofit2.Response
import io.realm.RealmObject
import java.lang.reflect.Field
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.di.RealmDispatcher
import org.ole.planet.myplanet.services.upload.UploadConfig
import org.ole.planet.myplanet.services.upload.UploadedItem
import org.ole.planet.myplanet.utils.JsonUtils
import org.ole.planet.myplanet.utils.UrlUtils

@Singleton
class UploadRepositoryImpl @Inject constructor(
    databaseService: DatabaseService,
    private val apiInterface: ApiInterface,
    @RealmDispatcher realmDispatcher: CoroutineDispatcher
) : RealmRepository(databaseService, realmDispatcher), UploadRepository {

    override suspend fun <T : RealmObject> queryPending(config: UploadConfig<T>): List<T> {
        return withRealmAsync { realm ->
            val query = realm.where(config.modelClass.java)
            val filteredQuery = config.queryBuilder(query)
            val results = filteredQuery.findAll()
            realm.copyFromRealm(results)
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
                        setRealmField(it, "_id", uploadedItem.remoteId)
                        setRealmField(it, "_rev", uploadedItem.remoteRev)
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

    override suspend fun postUpload(url: String, serializedData: JsonObject): Response<JsonObject> {
        return apiInterface.postDoc(UrlUtils.header, "application/json", url, serializedData)
    }

    override suspend fun putUpload(url: String, serializedData: JsonObject): Response<JsonObject> {
        return apiInterface.putDoc(UrlUtils.header, "application/json", url, serializedData)
    }

    override suspend fun fetchExistingDoc(url: String): Response<JsonObject> {
        return apiInterface.getJsonObject(UrlUtils.header, url)
    }

    private class FieldCacheEntry(val field: Field?)

    private val fieldCache = ConcurrentHashMap<Pair<Class<*>, String>, FieldCacheEntry>()

    private fun setRealmField(obj: RealmObject, fieldName: String, value: Any?) {
        try {
            val cacheKey = Pair(obj.javaClass, fieldName)
            var entry = fieldCache[cacheKey]

            if (entry == null) {
                var clazz: Class<*>? = obj.javaClass
                var field: Field? = null

                while (clazz != null && field == null) {
                    try {
                        field = clazz.getDeclaredField(fieldName)
                    } catch (e: NoSuchFieldException) {
                        clazz = clazz.superclass
                    }
                }

                if (field != null) {
                    field.isAccessible = true
                } else {
                    Log.w("UploadRepositoryImpl", "Field $fieldName not found in class hierarchy of ${obj.javaClass.simpleName}")
                }

                entry = FieldCacheEntry(field)
                fieldCache[cacheKey] = entry
            } else if (entry.field == null) {
                Log.w("UploadRepositoryImpl", "Field $fieldName not found in class hierarchy of ${obj.javaClass.simpleName}")
            }

            entry.field?.set(obj, value)
        } catch (e: Exception) {
            Log.w("UploadRepositoryImpl", "Failed to set field $fieldName: ${e.message}")
        }
    }
}
