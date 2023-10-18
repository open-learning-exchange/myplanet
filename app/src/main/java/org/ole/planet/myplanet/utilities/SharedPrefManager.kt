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
}