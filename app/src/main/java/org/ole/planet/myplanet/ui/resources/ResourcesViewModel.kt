package org.ole.planet.myplanet.ui.resources

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonObject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.MainApplication.Companion.isServerReachable
import org.ole.planet.myplanet.callback.SyncListener
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.di.AppPreferences
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmRating.Companion.getRatings
import org.ole.planet.myplanet.service.SyncManager
import org.ole.planet.myplanet.utilities.ServerUrlMapper
import org.ole.planet.myplanet.utilities.SharedPrefManager

@HiltViewModel
class ResourcesViewModel @Inject constructor(
    private val syncManager: SyncManager,
    private val databaseService: DatabaseService,
    @AppPreferences private val settings: SharedPreferences,
    @ApplicationContext context: Context
) : ViewModel() {
    private val prefManager = SharedPrefManager(context)
    private val serverUrlMapper = ServerUrlMapper()

    private val _libraryList = MutableLiveData<List<RealmMyLibrary>>()
    val libraryList: LiveData<List<RealmMyLibrary>> = _libraryList

    private val _ratingMap = MutableLiveData<HashMap<String?, JsonObject>>()
    val ratingMap: LiveData<HashMap<String?, JsonObject>> = _ratingMap

    private val _syncing = MutableLiveData<Boolean>()
    val syncing: LiveData<Boolean> = _syncing

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val serverUrl: String
        get() = settings.getString("serverURL", "") ?: ""

    fun startResourcesSync(isMyCourseLib: Boolean) {
        val isFastSync = settings.getBoolean("fastSync", false)
        if (isFastSync && !prefManager.isResourcesSynced()) {
            checkServerAndStartSync(isMyCourseLib)
        } else {
            refreshResourcesData(isMyCourseLib)
        }
    }

    private fun checkServerAndStartSync(isMyCourseLib: Boolean) {
        val mapping = serverUrlMapper.processUrl(serverUrl)
        viewModelScope.launch(Dispatchers.IO) {
            updateServerIfNecessary(mapping)
            startSyncManager(isMyCourseLib)
        }
    }

    private fun startSyncManager(isMyCourseLib: Boolean) {
        syncManager.start(object : SyncListener {
            override fun onSyncStarted() {
                _syncing.postValue(true)
            }

            override fun onSyncComplete() {
                _syncing.postValue(false)
                prefManager.setResourcesSynced(true)
                refreshResourcesData(isMyCourseLib)
            }

            override fun onSyncFailed(msg: String?) {
                _syncing.postValue(false)
                _error.postValue(msg)
            }
        }, "full", listOf("resources"))
    }

    private suspend fun updateServerIfNecessary(mapping: ServerUrlMapper.UrlMapping) {
        serverUrlMapper.updateServerIfNecessary(mapping, settings) { url ->
            isServerReachable(url)
        }
    }

    fun refreshResourcesData(isMyCourseLib: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            databaseService.withRealm { realm ->
                val userId = settings.getString("userId", "--")
                val ratings = getRatings(realm, "resource", userId)
                val libs = realm.where(RealmMyLibrary::class.java).findAll().toList()
                val libraryList = if (isMyCourseLib) {
                    RealmMyLibrary.getMyLibraryByUserId(userId, libs, realm)
                } else {
                    val publicLibs = realm.where(RealmMyLibrary::class.java)
                        .equalTo("isPrivate", false)
                        .findAll()
                        .toList()
                    RealmMyLibrary.getOurLibrary(userId, publicLibs)
                }
                _ratingMap.postValue(ratings)
                _libraryList.postValue(libraryList)
            }
        }
    }
}

