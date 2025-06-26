package org.ole.planet.myplanet.utilities

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.ole.planet.myplanet.model.User
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME

class SharedPrefManager(context: Context) {
    private var privateMode = 0
    private var pref: SharedPreferences = context.getSharedPreferences(PREFS_NAME, privateMode)
    private var editor: SharedPreferences.Editor = pref.edit()

    private var savedUsers = "savedUsers"
    private var repliedNewsId = "repliedNewsId"
    var manualConfig = "manualConfig"
    private var selectedTeamId = "selectedTeamId"
    var firstLaunch = "firstLaunch"
    private var teamName = "teamName"

    private var chatHistorySynced = "chat_history_synced"
    private var teamsSynced = "teams_synced"
    private var feedbackSynced = "feedback_synced"
    private var achievementsSynced = "achievements_synced"
    private var healthSynced = "health_synced"
    private var coursesSynced = "courses_synced"
    private var resourcesSynced = "resources_synced"

    fun getSavedUsers(): List<User> {
        val usersJson = pref.getString(savedUsers, null)
        return if (usersJson != null) {
            try {
                val jsonArray = JSONArray(usersJson)
                val userList = mutableListOf<User>()
                for (i in 0 until jsonArray.length()) {
                    val userJson = jsonArray.getJSONObject(i)
                    val fullName = userJson.getString("fullName")
                    val name = userJson.getString("name")
                    val password = userJson.getString("password")
                    val image = userJson.getString("image")
                    val source = userJson.getString("source")
                    val user = User(fullName, name, password, image, source)
                    userList.add(user)
                }
                userList
            } catch (e: JSONException) {
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    fun setSavedUsers(users: List<User>) {
        val jsonArray = JSONArray()
        for (user in users) {
            val userJson = JSONObject()
            userJson.put("fullName", user.fullName)
            userJson.put("name", user.name)
            userJson.put("password", user.password)
            userJson.put("image", user.image)
            userJson.put("source", user.source)
            jsonArray.put(userJson)
        }
        editor.putString(savedUsers, jsonArray.toString())
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

    fun isChatHistorySynced(): Boolean {
        return pref.getBoolean(chatHistorySynced, false)
    }

    fun setChatHistorySynced(synced: Boolean) {
        editor.putBoolean(chatHistorySynced, synced)
        if (synced) {
            editor.putLong("${chatHistorySynced}_time", System.currentTimeMillis())
        }
        editor.apply()
    }

    // Teams Sync
    fun isTeamsSynced(): Boolean {
        return pref.getBoolean(teamsSynced, false)
    }

    fun setTeamsSynced(synced: Boolean) {
        editor.putBoolean(teamsSynced, synced)
        if (synced) {
            editor.putLong("${teamsSynced}_time", System.currentTimeMillis())
        }
        editor.apply()
    }

    // Feedback Sync
    fun isFeedbackSynced(): Boolean {
        return pref.getBoolean(feedbackSynced, false)
    }

    fun setFeedbackSynced(synced: Boolean) {
        editor.putBoolean(feedbackSynced, synced)
        if (synced) {
            editor.putLong("${feedbackSynced}_time", System.currentTimeMillis())
        }
        editor.apply()
    }

    // Achievements Sync
    fun isAchievementsSynced(): Boolean {
        return pref.getBoolean(achievementsSynced, false)
    }

    fun setAchievementsSynced(synced: Boolean) {
        editor.putBoolean(achievementsSynced, synced)
        if (synced) {
            editor.putLong("${achievementsSynced}_time", System.currentTimeMillis())
        }
        editor.apply()
    }

    // Health Sync
    fun isHealthSynced(): Boolean {
        return pref.getBoolean(healthSynced, false)
    }

    fun setHealthSynced(synced: Boolean) {
        editor.putBoolean(healthSynced, synced)
        if (synced) {
            editor.putLong("${healthSynced}_time", System.currentTimeMillis())
        }
        editor.apply()
    }

    // Courses Sync
    fun isCoursesSynced(): Boolean {
        return pref.getBoolean(coursesSynced, false)
    }

    fun setCoursesSynced(synced: Boolean) {
        editor.putBoolean(coursesSynced, synced)
        if (synced) {
            editor.putLong("${coursesSynced}_time", System.currentTimeMillis())
        }
        editor.apply()
    }

    // Resources Sync
    fun isResourcesSynced(): Boolean {
        return pref.getBoolean(resourcesSynced, false)
    }

    fun setResourcesSynced(synced: Boolean) {
        editor.putBoolean(resourcesSynced, synced)
        if (synced) {
            editor.putLong("${resourcesSynced}_time", System.currentTimeMillis())
        }
        editor.apply()
    }

    // Utility methods
    fun getSyncTime(syncType: String): Long {
        return pref.getLong("${syncType}_time", 0)
    }

    fun resetSyncStatus(syncType: String) {
        editor.remove(syncType)
        editor.remove("${syncType}_time")
        editor.apply()
    }

    fun resetAllSyncStatuses() {
        editor.remove(chatHistorySynced)
        editor.remove(teamsSynced)
        editor.remove(feedbackSynced)
        editor.remove(achievementsSynced)
        editor.remove(healthSynced)
        editor.remove(coursesSynced)
        editor.remove(resourcesSynced)
        // Remove timestamps too
        editor.remove("${chatHistorySynced}_time")
        editor.remove("${teamsSynced}_time")
        editor.remove("${feedbackSynced}_time")
        editor.remove("${achievementsSynced}_time")
        editor.remove("${healthSynced}_time")
        editor.remove("${coursesSynced}_time")
        editor.remove("${resourcesSynced}_time")
        editor.apply()
    }
}
