package org.ole.planet.myplanet.ui.mylife

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.di.AppPreferences
import org.ole.planet.myplanet.model.RealmMyLife
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.service.UserProfileDbHandler

@HiltViewModel
class MyLifeViewModel @Inject constructor(
    private val databaseService: DatabaseService,
    private val userProfileDbHandler: UserProfileDbHandler,
    @AppPreferences private val preferences: SharedPreferences
) : ViewModel() {

    private val _myLifeData = MutableStateFlow<List<MyLifeData>>(emptyList())
    val myLifeData: StateFlow<List<MyLifeData>> = _myLifeData

    fun loadMyLife() {
        viewModelScope.launch {
            val myLifeDataList = databaseService.withRealmAsync { realm ->
                val myLifeItems = RealmMyLife.getMyLifeByUserId(realm, preferences)
                val surveyCount = realm.where(RealmSubmission::class.java)
                    .equalTo("userId", userProfileDbHandler.userModel?.id)
                    .equalTo("status", "pending")
                    .count()

                // Create unmanaged copies for the UI thread
                val unmanagedMyLifeItems = realm.copyFromRealm(myLifeItems)

                unmanagedMyLifeItems
                    .filter { it.isVisible }
                    .map { myLife ->
                        MyLifeData(realmMyLife = myLife, surveyCount = surveyCount)
                    }
            }
            _myLifeData.value = myLifeDataList
        }
    }
}
