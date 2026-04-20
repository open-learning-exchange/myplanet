package org.ole.planet.myplanet.services

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import org.ole.planet.myplanet.model.User
import org.ole.planet.myplanet.utils.Constants.PREFS_NAME
import androidx.preference.PreferenceManager

@Singleton
class SharedPrefManager @Inject constructor(@ApplicationContext private val context: Context) {
    private var pref: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    val rawPreferences: SharedPreferences get() = pref

    companion object {
        private const val SAVED_USERS = "savedUsers"
        private const val REPLIED_NEWS_ID = "repliedNewsId"
        const val MANUAL_CONFIG = "manualConfig"
        private const val SELECTED_TEAM_ID = "selectedTeamId"
        const val FIRST_LAUNCH = "firstLaunch"
        private const val TEAM_NAME = "teamName"
        private const val SERVER_URL = "serverURL"
        private const val SERVER_PIN = "serverPin"
        private const val SERVER_PROTOCOL = "serverProtocol"
        private const val COMMUNITY_NAME = "communityName"
        private const val CONFIGURATION_ID = "configurationId"
        private const val COUCHDB_URL = "couchdbURL"
        private const val URL_USER = "url_user"
        private const val URL_PWD = "url_pwd"
        private const val URL_SCHEME = "url_Scheme"
        private const val URL_HOST = "url_Host"
        private const val URL_PORT = "url_Port"
        private const val ALTERNATIVE_URL = "alternativeUrl"
        private const val PROCESSED_ALTERNATIVE_URL = "processedAlternativeUrl"
        private const val IS_ALTERNATIVE_URL = "isAlternativeUrl"
        private const val PINNED_SERVER_URL = "pinnedServerUrl"
        private const val SWITCH_CLOUD_URL = "switchCloudUrl"
        private const val USER_ID = "userId"
        private const val PARENT_CODE = "parentCode"
        private const val PLANET_CODE = "planetCode"
        private const val CUSTOM_DEVICE_NAME = "customDeviceName"
        private const val PENDING_LANGUAGE_CHANGE = "pendingLanguageChange"
        private const val USER_NAME = "name"
        private const val COMMUNITY_LEADERS = "communityLeaders"
        private const val AUTO_SYNC = "autoSync"
        private const val FAST_SYNC = "fastSync"
        private const val USE_IMPROVED_SYNC = "useImprovedSync"
        private const val AUTO_SYNC_INTERVAL = "autoSyncInterval"
        private const val AUTO_SYNC_POSITION = "autoSyncPosition"
        private const val FIRST_RUN = "firstRun"
        private const val LAST_USAGE_UPLOADED = "lastUsageUploaded"
        private const val LAST_SYNC = "LastSync"
        private const val LAST_WIFI_ID = "LastWifiID"
        private const val LAST_WIFI_SSID = "LastWifiSSID"
        private const val HAS_SHOWN_CONGRATS = "has_shown_congrats"
        const val KEY_LOGIN = "isLoggedIn"
        private const val KEY_NOTIFICATION_SHOWN = "notification_shown"
        private const val VERSION_DETAIL = "versionDetail"
        private const val CONCATENATED_LINKS = "concatenated_links"
        private const val LAST_VISITED_COURSE_ID = "lastVisitedCourseId"
        private const val LAST_VISITED_COURSE_TITLE = "lastVisitedCourseTitle"
    }

    enum class SyncKey(val key: String) {
        CHAT_HISTORY("chat_history_synced"),
        TEAMS("teams_synced"),
        FEEDBACK("feedback_synced"),
        ACHIEVEMENTS("achievements_synced"),
        HEALTH("health_synced"),
        COURSES("courses_synced"),
        RESOURCES("resources_synced"),
        EXAMS("exams_synced")
    }

    fun getSavedUsers(): List<User> {
        val usersJson = pref.getString(SAVED_USERS, null)
        return if (usersJson != null) {
            val type = object : TypeToken<List<User>>() {}.type
            gson.fromJson(usersJson, type)
        } else {
            emptyList()
        }
    }

    fun setSavedUsers(users: List<User>) {
        pref.edit { putString(SAVED_USERS, gson.toJson(users)) }
    }

    fun getRepliedNewsId(): String? {
        return pref.getString(REPLIED_NEWS_ID, null)
    }

    fun setRepliedNewsId(repliedNewsId: String?) {
        pref.edit { putString(REPLIED_NEWS_ID, repliedNewsId) }
    }

    fun getManualConfig(): Boolean {
        return pref.getBoolean(MANUAL_CONFIG, false)
    }

    fun setManualConfig(manualConfig: Boolean) {
        pref.edit { putBoolean(MANUAL_CONFIG, manualConfig) }
    }

    fun getSelectedTeamId(): String? {
        return pref.getString(SELECTED_TEAM_ID, "").takeIf { !it.isNullOrEmpty() } ?: ""
    }

    fun setSelectedTeamId(selectedTeamId: String?) {
        pref.edit { putString(SELECTED_TEAM_ID, selectedTeamId) }
    }

    fun getFirstLaunch(): Boolean {
        return pref.getBoolean(FIRST_LAUNCH, false)
    }

    fun setFirstLaunch(firstLaunch: Boolean) {
        pref.edit { putBoolean(FIRST_LAUNCH, firstLaunch) }
    }

    fun getTeamName(): String? {
        return pref.getString(TEAM_NAME, "").takeIf { !it.isNullOrEmpty() } ?: ""
    }

    fun setTeamName(teamName: String?) {
        pref.edit { putString(TEAM_NAME, teamName) }
    }

    private fun isSynced(key: SyncKey): Boolean {
        return pref.getBoolean(key.key, false)
    }

    private fun setSynced(key: SyncKey, synced: Boolean) {
        pref.edit {
            putBoolean(key.key, synced)
            if (synced) {
                putLong("${key.key}_time", System.currentTimeMillis())
            }
        }
    }

    fun getSyncTime(key: SyncKey): Long {
        return pref.getLong("${key.key}_time", 0L)
    }

    fun isChatHistorySynced(): Boolean = isSynced(SyncKey.CHAT_HISTORY)
    fun setChatHistorySynced(synced: Boolean) = setSynced(SyncKey.CHAT_HISTORY, synced)

    fun isTeamsSynced(): Boolean = isSynced(SyncKey.TEAMS)
    fun setTeamsSynced(synced: Boolean) = setSynced(SyncKey.TEAMS, synced)

    fun isFeedbackSynced(): Boolean = isSynced(SyncKey.FEEDBACK)
    fun setFeedbackSynced(synced: Boolean) = setSynced(SyncKey.FEEDBACK, synced)

    fun isAchievementsSynced(): Boolean = isSynced(SyncKey.ACHIEVEMENTS)
    fun setAchievementsSynced(synced: Boolean) = setSynced(SyncKey.ACHIEVEMENTS, synced)

    fun isHealthSynced(): Boolean = isSynced(SyncKey.HEALTH)
    fun setHealthSynced(synced: Boolean) = setSynced(SyncKey.HEALTH, synced)

    fun isCoursesSynced(): Boolean = isSynced(SyncKey.COURSES)
    fun setCoursesSynced(synced: Boolean) = setSynced(SyncKey.COURSES, synced)

    fun isResourcesSynced(): Boolean = isSynced(SyncKey.RESOURCES)
    fun setResourcesSynced(synced: Boolean) = setSynced(SyncKey.RESOURCES, synced)

    fun isExamsSynced(): Boolean = isSynced(SyncKey.EXAMS)
    fun setExamsSynced(synced: Boolean) = setSynced(SyncKey.EXAMS, synced)

    fun getNewLoginUsername(): String? = pref.getString("new_login_username", null)
    fun setNewLoginUsername(username: String?) = pref.edit { putString("new_login_username", username) }

    fun getNewLoginPassword(): String? = pref.getString("new_login_password", null)
    fun setNewLoginPassword(password: String?) = pref.edit { putString("new_login_password", password) }

    fun getServerUrl(): String = pref.getString(SERVER_URL, "") ?: ""
    fun setServerUrl(url: String) = pref.edit { putString(SERVER_URL, url) }

    fun getServerPin(): String = pref.getString(SERVER_PIN, "") ?: ""
    fun setServerPin(pin: String) = pref.edit { putString(SERVER_PIN, pin) }

    fun getServerProtocol(): String = pref.getString(SERVER_PROTOCOL, "") ?: ""
    fun setServerProtocol(protocol: String) = pref.edit { putString(SERVER_PROTOCOL, protocol) }

    fun getCommunityName(): String = pref.getString(COMMUNITY_NAME, "") ?: ""
    fun setCommunityName(name: String) = pref.edit { putString(COMMUNITY_NAME, name) }

    fun getConfigurationId(): String? = pref.getString(CONFIGURATION_ID, null)
    fun setConfigurationId(id: String) = pref.edit { putString(CONFIGURATION_ID, id) }

    fun getCouchdbUrl(): String = pref.getString(COUCHDB_URL, "") ?: ""
    fun setCouchdbUrl(url: String) = pref.edit { putString(COUCHDB_URL, url) }

    fun getUrlUser(): String = pref.getString(URL_USER, "") ?: ""
    fun setUrlUser(user: String) = pref.edit { putString(URL_USER, user) }

    fun getUrlPwd(): String = pref.getString(URL_PWD, "") ?: ""
    fun setUrlPwd(pwd: String) = pref.edit { putString(URL_PWD, pwd) }

    fun getUrlScheme(): String = pref.getString(URL_SCHEME, "") ?: ""
    fun setUrlScheme(scheme: String) = pref.edit { putString(URL_SCHEME, scheme) }

    fun getUrlHost(): String = pref.getString(URL_HOST, "") ?: ""
    fun setUrlHost(host: String) = pref.edit { putString(URL_HOST, host) }

    fun getUrlPort(): Int = pref.getInt(URL_PORT, 443)
    fun setUrlPort(port: Int) = pref.edit { putInt(URL_PORT, port) }

    fun getAlternativeUrl(): String = pref.getString(ALTERNATIVE_URL, "") ?: ""
    fun setAlternativeUrl(url: String) = pref.edit { putString(ALTERNATIVE_URL, url) }

    fun getProcessedAlternativeUrl(): String = pref.getString(PROCESSED_ALTERNATIVE_URL, "") ?: ""
    fun setProcessedAlternativeUrl(url: String) = pref.edit { putString(PROCESSED_ALTERNATIVE_URL, url) }

    fun isAlternativeUrl(): Boolean = pref.getBoolean(IS_ALTERNATIVE_URL, false)
    fun setIsAlternativeUrl(value: Boolean) = pref.edit { putBoolean(IS_ALTERNATIVE_URL, value) }

    fun getPinnedServerUrl(): String? = pref.getString(PINNED_SERVER_URL, null)
    fun setPinnedServerUrl(url: String) = pref.edit { putString(PINNED_SERVER_URL, url) }

    fun getSwitchCloudUrl(): Boolean = pref.getBoolean(SWITCH_CLOUD_URL, false)
    fun setSwitchCloudUrl(value: Boolean) = pref.edit { putBoolean(SWITCH_CLOUD_URL, value) }

    fun getUserId(): String = pref.getString(USER_ID, "") ?: ""
    fun setUserId(id: String) = pref.edit { putString(USER_ID, id) }

    fun getParentCode(): String = pref.getString(PARENT_CODE, "") ?: ""
    fun setParentCode(code: String) = pref.edit { putString(PARENT_CODE, code) }

    fun getPlanetCode(): String = pref.getString(PLANET_CODE, "") ?: ""
    fun setPlanetCode(code: String) = pref.edit { putString(PLANET_CODE, code) }

    fun getCustomDeviceName(): String = pref.getString(CUSTOM_DEVICE_NAME, "") ?: ""
    fun setCustomDeviceName(name: String) = pref.edit { putString(CUSTOM_DEVICE_NAME, name) }

    fun getPendingLanguageChange(): String? = pref.getString(PENDING_LANGUAGE_CHANGE, null)
    fun setPendingLanguageChange(language: String?) = pref.edit {
        if (language != null) putString(PENDING_LANGUAGE_CHANGE, language)
        else remove(PENDING_LANGUAGE_CHANGE)
    }

    fun getUserName(): String = pref.getString(USER_NAME, "") ?: ""
    fun setUserName(name: String) = pref.edit { putString(USER_NAME, name) }

    fun getCommunityLeaders(): String = pref.getString(COMMUNITY_LEADERS, "") ?: ""
    fun setCommunityLeaders(json: String) = pref.edit { putString(COMMUNITY_LEADERS, json) }

    fun getAutoSync(): Boolean = pref.getBoolean(AUTO_SYNC, true)
    fun setAutoSync(value: Boolean) = pref.edit { putBoolean(AUTO_SYNC, value) }

    fun getFastSync(): Boolean = pref.getBoolean(FAST_SYNC, false)
    fun setFastSync(value: Boolean) = pref.edit { putBoolean(FAST_SYNC, value) }

    fun getUseImprovedSync(): Boolean = pref.getBoolean(USE_IMPROVED_SYNC, false)
    fun setUseImprovedSync(value: Boolean) = pref.edit { putBoolean(USE_IMPROVED_SYNC, value) }

    fun getAutoSyncInterval(): Int = pref.getInt(AUTO_SYNC_INTERVAL, 60 * 60)
    fun setAutoSyncInterval(interval: Int) = pref.edit { putInt(AUTO_SYNC_INTERVAL, interval) }

    fun getAutoSyncPosition(): Int = pref.getInt(AUTO_SYNC_POSITION, 0)
    fun setAutoSyncPosition(position: Int) = pref.edit { putInt(AUTO_SYNC_POSITION, position) }

    fun getFirstRun(): Boolean = pref.getBoolean(FIRST_RUN, true)
    fun setFirstRun(value: Boolean) = pref.edit { putBoolean(FIRST_RUN, value) }

    fun getLastUsageUploaded(): Long = pref.getLong(LAST_USAGE_UPLOADED, 0L)
    fun setLastUsageUploaded(time: Long) = pref.edit { putLong(LAST_USAGE_UPLOADED, time) }

    fun getLastSync(): Long = pref.getLong(LAST_SYNC, 0L)
    fun setLastSync(time: Long) = pref.edit { putLong(LAST_SYNC, time) }

    fun getLastWifiId(): Int = pref.getInt(LAST_WIFI_ID, -1)
    fun setLastWifiId(id: Int) = pref.edit { putInt(LAST_WIFI_ID, id) }

    fun getLastWifiSsid(): String? = pref.getString(LAST_WIFI_SSID, null)
    fun setLastWifiSsid(ssid: String) = pref.edit { putString(LAST_WIFI_SSID, ssid) }

    fun getIsExamsSynced(): Boolean = isSynced(SyncKey.EXAMS)
    fun setIsExamsSynced(value: Boolean) = setSynced(SyncKey.EXAMS, value)

    fun getHasShownCongrats(): Boolean = pref.getBoolean(HAS_SHOWN_CONGRATS, false)
    fun setHasShownCongrats(value: Boolean) = pref.edit { putBoolean(HAS_SHOWN_CONGRATS, value) }

    fun isLoggedIn(): Boolean = pref.getBoolean(KEY_LOGIN, false)
    fun setLoggedIn(value: Boolean) = pref.edit { putBoolean(KEY_LOGIN, value) }

    fun isNotificationShown(): Boolean = pref.getBoolean(KEY_NOTIFICATION_SHOWN, false)
    fun setNotificationShown(value: Boolean) = pref.edit { putBoolean(KEY_NOTIFICATION_SHOWN, value) }

    fun getVersionDetail(): String? = pref.getString(VERSION_DETAIL, null)
    fun setVersionDetail(json: String) = pref.edit { putString(VERSION_DETAIL, json) }

    fun getConcatenatedLinks(): String? = pref.getString(CONCATENATED_LINKS, null)
    fun setConcatenatedLinks(json: String) = pref.edit { putString(CONCATENATED_LINKS, json) }

    fun getLastVisitedCourseId(): String? = pref.getString(LAST_VISITED_COURSE_ID, null)
    fun setLastVisitedCourseId(id: String?) = pref.edit { putString(LAST_VISITED_COURSE_ID, id) }

    fun getLastVisitedCourseTitle(): String? = pref.getString(LAST_VISITED_COURSE_TITLE, null)
    fun setLastVisitedCourseTitle(title: String?) = pref.edit { putString(LAST_VISITED_COURSE_TITLE, title) }

    fun getRawString(key: String, default: String = ""): String = pref.getString(key, default) ?: default
    fun setRawString(key: String, value: String) = pref.edit { putString(key, value) }
    fun getRawLong(key: String, default: Long = 0L): Long = pref.getLong(key, default)
    fun setRawLong(key: String, value: Long) = pref.edit { putLong(key, value) }
    fun removeKey(key: String) = pref.edit { remove(key) }
    fun clearPreferences() {
        val editor = pref.edit()
        val keysToKeep = setOf(FIRST_LAUNCH, MANUAL_CONFIG)
        val tempStorage = HashMap<String, Boolean>()
        for (key in keysToKeep) {
            tempStorage[key] = pref.getBoolean(key, false)
        }
        editor.clear().apply()
        for ((key, value) in tempStorage) {
            editor.putBoolean(key, value)
        }
        editor.commit()
        val defaultPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        defaultPreferences.edit { clear() }
    }

}
