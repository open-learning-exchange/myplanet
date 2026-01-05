package org.ole.planet.myplanet.ui.health

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.realm.Realm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.model.RealmHealthExamination
import org.ole.planet.myplanet.model.RealmMyHealth
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.utilities.AndroidDecrypter
import org.ole.planet.myplanet.utilities.JsonUtils
import javax.inject.Inject

@HiltViewModel
class AddExaminationViewModel @Inject constructor(private val databaseService: DatabaseService) : ViewModel() {

    private val _healthData = MutableLiveData<RealmMyHealth?>()
    val healthData: LiveData<RealmMyHealth?> = _healthData

    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    fun loadHealthData(userId: String) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val decryptedHealth = withContext(Dispatchers.IO) {
                    var health: RealmMyHealth? = null
                    databaseService.withRealm { realm ->
                        val pojo = realm.where(RealmHealthExamination::class.java).equalTo("_id", userId).findFirst()
                            ?: realm.where(RealmHealthExamination::class.java).equalTo("userId", userId).findFirst()
                        val user = realm.where(RealmUserModel::class.java).equalTo("id", userId).findFirst()
                        if (pojo != null && !pojo.data.isNullOrEmpty()) {
                            val decryptedData = withContext(Dispatchers.Default) {
                                AndroidDecrypter.decrypt(pojo.data, user?.key, user?.iv)
                            }
                            health = JsonUtils.gson.fromJson(decryptedData, RealmMyHealth::class.java)
                        }
                    }
                    health
                }
                _healthData.postValue(decryptedHealth)
            } catch (e: Exception) {
                _error.postValue("Failed to decrypt health data.")
            } finally {
                _isLoading.postValue(false)
            }
        }
    }
}
