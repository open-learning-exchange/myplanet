package org.ole.planet.myplanet.data

import android.content.Context
import android.content.SharedPreferences
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnSuccessListener
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.di.ApplicationScope
import org.ole.planet.myplanet.model.MyPlanet
import org.ole.planet.myplanet.repository.CommunityRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.services.UploadToShelfService
import org.ole.planet.myplanet.ui.sync.SyncActivity
import org.ole.planet.myplanet.utils.Constants
import org.ole.planet.myplanet.utils.JsonUtils
import org.ole.planet.myplanet.utils.UrlUtils

class DataService constructor(
    private val context: Context,
    private val retrofitInterface: ApiInterface,
    private val databaseService: DatabaseService,
    @param:ApplicationScope private val serviceScope: CoroutineScope,
    private val userRepository: UserRepository,
    private val uploadToShelfService: UploadToShelfService,
) {
    private val preferences: SharedPreferences = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
    private val serverAvailabilityCache = ConcurrentHashMap<String, Pair<Boolean, Long>>()
}