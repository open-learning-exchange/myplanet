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
    private var examsSynced = "exams_synced"

    private fun isSynced(key: String): Boolean {
        return pref.getBoolean(key, false)
    }

    private fun setSynced(key: String, synced: Boolean) {
        editor.putBoolean(key, synced)
        if (synced) {
            editor.putLong("${key}_time", System.currentTimeMillis())
        }
        editor.apply()
    }

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
                e.printStackTrace()
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

    fun isChatHistorySynced(): Boolean = isSynced(chatHistorySynced)

    fun setChatHistorySynced(synced: Boolean) = setSynced(chatHistorySynced, synced)

    fun isTeamsSynced(): Boolean = isSynced(teamsSynced)

    fun setTeamsSynced(synced: Boolean) = setSynced(teamsSynced, synced)

    fun isFeedbackSynced(): Boolean = isSynced(feedbackSynced)

    fun setFeedbackSynced(synced: Boolean) = setSynced(feedbackSynced, synced)

    fun isAchievementsSynced(): Boolean = isSynced(achievementsSynced)

    fun setAchievementsSynced(synced: Boolean) = setSynced(achievementsSynced, synced)

    fun isHealthSynced(): Boolean = isSynced(healthSynced)

    fun setHealthSynced(synced: Boolean) = setSynced(healthSynced, synced)

    fun isCoursesSynced(): Boolean = isSynced(coursesSynced)

    fun setCoursesSynced(synced: Boolean) = setSynced(coursesSynced, synced)

    fun isResourcesSynced(): Boolean = isSynced(resourcesSynced)

    fun setResourcesSynced(synced: Boolean) = setSynced(resourcesSynced, synced)

    fun isExamsSynced(): Boolean = isSynced(examsSynced)

    fun setExamsSynced(synced: Boolean) = setSynced(examsSynced, synced)
}
