package org.ole.planet.myplanet.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.datamanager.ApiInterface
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmCommunity
import org.ole.planet.myplanet.utilities.JsonUtils
import javax.inject.Inject

class PlanetRepositoryImpl @Inject constructor(
    private val apiInterface: ApiInterface,
    private val databaseService: DatabaseService,
    @ApplicationContext private val context: Context
) : PlanetRepository {
    override suspend fun syncPlanetServers(): String {
        return try {
            val response = withContext(Dispatchers.IO) {
                apiInterface.getJsonObject("", "https://planet.earth.ole.org/db/communityregistrationrequests/_all_docs?include_docs=true").execute()
            }

            if (response.isSuccessful && response.body() != null) {
                val arr = JsonUtils.getJsonArray("rows", response.body())
                val transactionResult = runCatching {
                    withContext(Dispatchers.IO) {
                        databaseService.withRealm { backgroundRealm ->
                            backgroundRealm.executeTransaction { realm1 ->
                                realm1.delete(RealmCommunity::class.java)
                                for (j in arr) {
                                    var jsonDoc = j.asJsonObject
                                    jsonDoc = JsonUtils.getJsonObject("doc", jsonDoc)
                                    val id = JsonUtils.getString("_id", jsonDoc)
                                    val community = realm1.createObject(RealmCommunity::class.java, id)
                                    if (JsonUtils.getString("name", jsonDoc) == "learning") {
                                        community.weight = 0
                                    }
                                    community.localDomain = JsonUtils.getString("localDomain", jsonDoc)
                                    community.name = JsonUtils.getString("name", jsonDoc)
                                    community.parentDomain = JsonUtils.getString("parentDomain", jsonDoc)
                                    community.registrationRequest = JsonUtils.getString("registrationRequest", jsonDoc)
                                }
                            }
                        }
                    }
                }
                if (transactionResult.isSuccess) {
                    context.getString(R.string.server_sync_successfully)
                } else {
                    transactionResult.exceptionOrNull()?.printStackTrace()
                    context.getString(R.string.server_sync_has_failed)
                }
            } else {
                context.getString(R.string.server_sync_has_failed)
            }
        } catch (t: Throwable) {
            t.printStackTrace()
            context.getString(R.string.server_sync_has_failed)
        }
    }
}
