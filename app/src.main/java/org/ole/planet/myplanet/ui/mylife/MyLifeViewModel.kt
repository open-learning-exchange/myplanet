package org.ole.planet.myplanet.ui.mylife

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.di.AppPreferences
import org.ole.planet.myplanet.model.RealmMyLife
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.service.UserProfileDbHandler
import javax.inject.Inject

data class MyLifeData(
    val realmMyLife: RealmMyLife,
    val surveyCount: Int = 0
)

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
            val rawMylife: List<RealmMyLife> = databaseService.withRealmAsync { realm ->
                RealmMyLife.getMyLifeByUserId(realm, preferences)
            }
            _myLifeData.value = rawMylife.filter { it.isVisible }.map { myLife ->
                async {
                    val surveyCount = if (myLife.title == "mySurveys") {
                        databaseService.withRealmAsync { realm ->
                            RealmSubmission.getNoOfSurveySubmissionByUser(userProfileDbHandler.userModel?.id, realm)
                        }
                    } else {
                        0
                    }
                    MyLifeData(myLife, surveyCount)
                }
            }.awaitAll()
        }
    }
}
