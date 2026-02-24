package org.ole.planet.myplanet.repository

import android.content.SharedPreferences
import org.ole.planet.myplanet.callback.OnSuccessListener
import org.ole.planet.myplanet.model.MyPlanet

interface ConfigurationsRepository {
    fun checkHealth(listener: OnSuccessListener)
    fun checkVersion(callback: CheckVersionCallback, settings: SharedPreferences)
    suspend fun checkServerAvailability(): Boolean
    suspend fun checkServerAvailability(url: String): Boolean
    suspend fun checkCheckSum(path: String): Boolean
    suspend fun clearAllData()
    suspend fun getMinApk(url: String, pin: String): ConfigurationResult

    interface CheckVersionCallback {
        fun onUpdateAvailable(info: MyPlanet?, cancelable: Boolean)
        fun onCheckingVersion()
        fun onError(msg: String, blockSync: Boolean)
    }

    sealed class ConfigurationResult {
        data class Success(val id: String, val code: String, val url: String, val defaultUrl: String, val isAlternativeUrl: Boolean) : ConfigurationResult()
        data class Failure(val errorMessage: String, val url: String) : ConfigurationResult()
    }
}
