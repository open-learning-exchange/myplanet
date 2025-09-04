package org.ole.planet.myplanet.repository

import android.content.Context
import androidx.core.net.toUri
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.datamanager.ApiClient.client
import org.ole.planet.myplanet.datamanager.ApiInterface
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.datamanager.queryList
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.service.UploadManager
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
import org.ole.planet.myplanet.utilities.ServerUrlMapper

class TeamRepositoryImpl @Inject constructor(
    private val databaseService: DatabaseService,
) : RealmRepository(databaseService), TeamRepository {

    override suspend fun getTeamResources(teamId: String): List<RealmMyLibrary> {
        return withRealm { realm ->
            val resourceIds = RealmMyTeam.getResourceIds(teamId, realm)
            if (resourceIds.isEmpty()) emptyList() else
                realm.queryList(RealmMyLibrary::class.java) {
                    `in`("id", resourceIds.toTypedArray())
                }
        }
    }

    override fun syncTeamActivities(context: Context, uploadManager: UploadManager) {
        val settings = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val updateUrl = "${settings.getString("serverURL", "")}"
        val serverUrlMapper = ServerUrlMapper()
        val mapping = serverUrlMapper.processUrl(updateUrl)

        CoroutineScope(Dispatchers.IO).launch {
            val primaryAvailable = MainApplication.isServerReachable(mapping.primaryUrl)
            val alternativeAvailable =
                mapping.alternativeUrl?.let { MainApplication.isServerReachable(it) } == true

            if (!primaryAvailable && alternativeAvailable) {
                mapping.alternativeUrl?.let { alternativeUrl ->
                    val uri = updateUrl.toUri()
                    val editor = settings.edit()
                    serverUrlMapper.updateUrlPreferences(editor, uri, alternativeUrl, mapping.primaryUrl, settings)
                }
            }

            withContext(Dispatchers.Main) {
                uploadTeamActivities(context, uploadManager)
            }
        }
    }

    private fun uploadTeamActivities(context: Context, uploadManager: UploadManager) {
        MainApplication.applicationScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    uploadManager.uploadTeams()
                }
                withContext(Dispatchers.IO) {
                    val apiInterface = client?.create(ApiInterface::class.java)
                    databaseService.withRealm { realm ->
                        realm.executeTransaction { transactionRealm ->
                            uploadManager.uploadTeamActivities(transactionRealm, apiInterface)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

