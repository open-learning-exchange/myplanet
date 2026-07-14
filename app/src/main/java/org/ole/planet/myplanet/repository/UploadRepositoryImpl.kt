package org.ole.planet.myplanet.repository

import android.util.Log
import com.google.gson.JsonObject
import io.realm.RealmObject
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.di.RealmDispatcher
import org.ole.planet.myplanet.utils.RealmUtils
import org.ole.planet.myplanet.utils.UrlUtils
import retrofit2.Response

@Singleton
class UploadRepositoryImpl @Inject constructor(
    databaseService: DatabaseService,
    private val apiInterface: ApiInterface,
    @RealmDispatcher realmDispatcher: CoroutineDispatcher
) : RealmRepository(databaseService, realmDispatcher), UploadRepository {

    override suspend fun <T : RealmObject> queryPending(config: UploadQueryContract<T>): List<T> {
        return withRealmAsync { realm ->
            val query = realm.where(config.modelClass.java)
            val filteredQuery = config.queryBuilder(query)
            val results = filteredQuery.findAll()
            realm.copyFromRealm(results)
        }
    }

    override suspend fun <T : RealmObject> markUploaded(config: UploadUpdateContract<T>, succeeded: List<UploadedItemResult>): List<UploadedItemResult> {
        val failedLocally = mutableListOf<UploadedItemResult>()
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
                        RealmUtils.setRealmField(it, "_id", uploadedItem.remoteId)
                        RealmUtils.setRealmField(it, "_rev", uploadedItem.remoteRev)
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
}
