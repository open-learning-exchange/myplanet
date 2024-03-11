package org.ole.planet.myplanet.utilities

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.ole.planet.myplanet.model.User

class SharedPrefManager(context: Context) {
    var PRIVATE_MODE = 0
    var pref: SharedPreferences
    var editor: SharedPreferences.Editor
    var SHARED_PREF_NAME = "OLEmyPlanetPrefData"

    var SAVEDUSERS = "savedUsers"
    var REPLIEDNEWSID = "repliedNewsId"
    var MANUALCONFIG = "manualConfig"
    var SELECTEDTEAMID = "selectedTeamId"
    var FIRSTLAUNCH = "firstLaunch"
    var TEAMNAME = "teamName"

    init {
        pref = context.getSharedPreferences(SHARED_PREF_NAME, PRIVATE_MODE)
        editor = pref.edit()
    }

    @JvmName("getSAVEDUSERS1")
    fun getSAVEDUSERS(): List<User> {
        val usersJson = pref.getString(SAVEDUSERS, null)
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

    @JvmName("setSAVEDUSERS1")
    fun setSAVEDUSERS(users: List<User>) {
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
        editor.putString(SAVEDUSERS, jsonArray.toString())
        editor.apply()
    }

    @JvmName("getREPLIEDNEWSID1")
    fun getREPLIEDNEWSID(): String? {
        return if (pref.getString(REPLIEDNEWSID, "") != "") pref.getString(
            REPLIEDNEWSID, "") else ""
    }

    @JvmName("setREPLIEDNEWSID1")
    fun setREPLIEDNEWSID(repliedNewsId: String?) {
        editor.putString(REPLIEDNEWSID, repliedNewsId)
        editor.apply()
    }

    @JvmName("getMANUALCONFIG1")
    fun getMANUALCONFIG(): Boolean {
        return pref.getBoolean(MANUALCONFIG, false)
    }

    @JvmName("setMANUALCONFIG1")
    fun setMANUALCONFIG(manualConfig: Boolean) {
        editor.putBoolean(MANUALCONFIG, manualConfig)
        editor.apply()
    }

    @JvmName("getSELECTEDTEAMID1")
    fun getSELECTEDTEAMID(): String? {
        return if (pref.getString(SELECTEDTEAMID, "") != "") pref.getString(
            SELECTEDTEAMID, "") else ""
    }

    @JvmName("setSELECTEDTEAMID1")
    fun setSELECTEDTEAMID(selectedTeamId: String?) {
        editor.putString(SELECTEDTEAMID, selectedTeamId)
        editor.apply()
    }

    @JvmName("getFIRSTLAUNCH1")
    fun getFIRSTLAUNCH(): Boolean {
        return pref.getBoolean(FIRSTLAUNCH, false)
    }

    @JvmName("setFIRSTLAUNCH1")
    fun setFIRSTLAUNCH(firstLaunch: Boolean) {
        editor.putBoolean(FIRSTLAUNCH, firstLaunch)
        editor.apply()
    }

    @JvmName("getTEAMNAME1")
    fun getTEAMNAME(): String? {
        return if (pref.getString(TEAMNAME, "") != "") {
            pref.getString(TEAMNAME, "")
        } else {
            ""
        }
    }

    @JvmName("setTEAMNAME1")
    fun setTEAMNAME(teamName: String?) {
        editor.putString(TEAMNAME, teamName)
        editor.apply()
    }
}