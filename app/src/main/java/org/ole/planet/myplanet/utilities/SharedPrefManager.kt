package org.ole.planet.myplanet.utilities

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import org.ole.planet.myplanet.model.User
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME

class SharedPrefManager @Inject constructor(@ApplicationContext context: Context) {
    private var privateMode = 0
    private var pref: SharedPreferences = context.getSharedPreferences(PREFS_NAME, privateMode)
    private var editor: SharedPreferences.Editor = pref.edit()
    private val gson = Gson()

    private var savedUsers = "savedUsers"
    private var repliedNewsId = "repliedNewsId"
    var manualConfig = "manualConfig"
    private var selectedTeamId = "selectedTeamId"
    var firstLaunch = "firstLaunch"
    private var teamName = "teamName"

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
        val usersJson = pref.getString(savedUsers, null)
        return if (usersJson != null) {
            val type = object : TypeToken<List<User>>() {}.type
            gson.fromJson(usersJson, type)
        } else {
            emptyList()
        }
    }

    fun setSavedUsers(users: List<User>) {
        editor.putString(savedUsers, gson.toJson(users))
        editor.apply()
    }

    fun setRepliedNewsId(repliedNewsId: String?) {
        editor.putString(this.repliedNewsId, repliedNewsId)
        editor.apply()
    }

    fun getManualConfig(): Boolean {
        return pref.getBoolean(manualConfig, false)
    }

    fun setManualConfig(manualConfig: Boolean) {
        editor.putBoolean(this.manualConfig, manualConfig)
        editor.apply()
    }

    fun getSelectedTeamId(): String? {
        return if (pref.getString(selectedTeamId, "") != "") pref.getString(
            selectedTeamId, "") else ""
    }

    fun setSelectedTeamId(selectedTeamId: String?) {
        editor.putString(this.selectedTeamId, selectedTeamId)
        editor.apply()
    }

    fun getFirstLaunch(): Boolean {
        return pref.getBoolean(firstLaunch, false)
    }

    fun setFirstLaunch(firstLaunch: Boolean) {
        editor.putBoolean(this.firstLaunch, firstLaunch)
        editor.apply()
    }

    fun getTeamName(): String? {
        return if (pref.getString(teamName, "") != "") {
            pref.getString(teamName, "")
        } else {
            ""
        }
    }

    fun setTeamName(teamName: String?) {
        editor.putString(this.teamName, teamName)
        editor.apply()
    }

    private fun isSynced(key: SyncKey): Boolean {
        return pref.getBoolean(key.key, false)
    }

    private fun setSynced(key: SyncKey, synced: Boolean) {
        editor.putBoolean(key.key, synced)
        if (synced) {
            editor.putLong("${key.key}_time", System.currentTimeMillis())
        }
        editor.apply()
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
}
