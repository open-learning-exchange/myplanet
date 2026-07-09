package org.ole.planet.myplanet.ui.user

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import org.ole.planet.myplanet.services.sync.RealtimeSyncManager

@HiltViewModel
class AchievementViewModel @Inject constructor(
    private val realtimeSyncManager: RealtimeSyncManager
) : ViewModel() {
    val dataUpdateFlow = realtimeSyncManager.dataUpdateFlow
}
