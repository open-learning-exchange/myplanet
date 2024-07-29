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
    private var manualConfig = "manualConfig"
    private var selectedTeamId = "selectedTeamId"
    var firstLaunch = "firstLaunch"
    private var teamName = "teamName"

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
}
