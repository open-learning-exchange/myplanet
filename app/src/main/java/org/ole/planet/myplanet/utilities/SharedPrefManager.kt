package org.ole.planet.myplanet.utilities

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.ole.planet.myplanet.model.User

class SharedPrefManager(var _context: Context) {
    var PRIVATE_MODE = 0
    var pref: SharedPreferences
    var editor: SharedPreferences.Editor
    var SHARED_PREF_NAME = "OLEmyPlanetPrefData"

    var SERVERPIN = "serverPin"
    var URLUSER = "urlUser"
    var URLPWD = "urlPwd"
    var URLSCHEME = "urlScheme"
    var URLHOST = "urlHost"
    var USERID = "userId"
    var NAME = "name"
    var PASSWORD = "password"
    var FIRSTNAME = "firstName"
    var LASTNAME = "lastName"
    var MIDDLENAME = "middleName"
    var ISUSERADMIN = "isUserAdmin"
    var LASTLOGIN = "lastLogin"
    var URLPORT = "urlPort"
    var SERVERURL = "serverURL"
    var COUCHDBURL = "couchDbURL"
    var SAVEDUSERS = "savedUsers"

    init {
        pref = _context.getSharedPreferences(SHARED_PREF_NAME, PRIVATE_MODE)
        editor = pref.edit()
    }

    @JvmName("getSERVERPIN1")
    fun getSERVERPIN(): String? {
        return if (pref.getString(SERVERPIN, null) != null) pref.getString(
            SERVERPIN, null
        ) else null
    }
    @JvmName("setSERVERPIN1")
    fun setSERVERPIN(serverPin: String?) {
        editor.putString(SERVERPIN, serverPin)
        editor.apply()
    }

    @JvmName("getURLUSER1")
    fun getURLUSER(): String? {
        return if (pref.getString(URLUSER, null) != null) pref.getString(
            URLUSER, null
        ) else null
    }

    @JvmName("setURLUSER1")
    fun setURLUSER(urlUser: String?) {
        editor.putString(URLUSER, urlUser)
        editor.apply()
    }

    @JvmName("getURLPWD1")
    fun getURLPWD(): String? {
        return if (pref.getString(URLPWD, null) != null) pref.getString(
            URLPWD, null
        ) else null
    }

    @JvmName("setURLPWD1")
    fun setURLPWD(urlPwd: String?) {
        editor.putString(URLPWD, urlPwd)
        editor.apply()
    }

    @JvmName("getURLSCHEME1")
    fun getURLSCHEME(): String? {
        return if (pref.getString(URLSCHEME, null) != null) pref.getString(
            URLSCHEME, null
        ) else null
    }

    @JvmName("setURLSCHEME1")
    fun setURLSCHEME(urlScheme: String?) {
        editor.putString(URLSCHEME, urlScheme)
        editor.apply()
    }

    @JvmName("getURLHOST1")
    fun getURLHOST(): String? {
        return if (pref.getString(URLHOST, null) != null) pref.getString(
            URLHOST, null
        ) else null
    }

    @JvmName("setURLHOST1")
    fun setURLHOST(urlHost: String?) {
        editor.putString(URLHOST, urlHost)
        editor.apply()
    }

    @JvmName("getUSERID1")
    fun getUSERID(): String? {
        return if (pref.getString(USERID, null) != null) pref.getString(
            USERID, null
        ) else null
    }

    @JvmName("setUSERID1")
    fun setUSERID(userId: String?) {
        editor.putString(USERID, userId)
        editor.apply()
    }

    @JvmName("getNAME1")
    fun getNAME(): String? {
        return if (pref.getString(NAME, null) != null) pref.getString(
            NAME, null
        ) else null
    }

    @JvmName("setNAME1")
    fun setNAME(name: String?) {
        editor.putString(NAME, name)
        editor.apply()
    }

    @JvmName("getPASSWORD1")
    fun getPASSWORD(): String? {
        return if (pref.getString(PASSWORD, null) != null) pref.getString(
            PASSWORD, null
        ) else null
    }

    @JvmName("setPASSWORD1")
    fun setPASSWORD(password: String?) {
        editor.putString(PASSWORD, password)
        editor.apply()
    }
    @JvmName("getFIRSTNAME1")
    fun getFIRSTNAME(): String? {
        return if (pref.getString(FIRSTNAME, null) != null) pref.getString(
            FIRSTNAME, null
        ) else null
    }

    @JvmName("setFIRSTNAME1")
    fun setFIRSTNAME(firstName: String?) {
        editor.putString(FIRSTNAME, firstName)
        editor.apply()
    }

    @JvmName("getLASTNAME1")
    fun getLASTNAME(): String? {
        return if (pref.getString(LASTNAME, null) != null) pref.getString(
            LASTNAME, null
        ) else null
    }

    @JvmName("setLASTNAME1")
    fun setLASTNAME(lastName: String?) {
        editor.putString(LASTNAME, lastName)
        editor.apply()
    }

    @JvmName("getMIDDLENAME1")
    fun getMIDDLENAME(): String? {
        return if (pref.getString(MIDDLENAME, null) != null) pref.getString(
            MIDDLENAME, null
        ) else null
    }

    @JvmName("setMIDDLENAME1")
    fun setMIDDLENAME(middleName: String?) {
        editor.putString(MIDDLENAME, middleName)
        editor.apply()
    }

    @JvmName("getISUSERADMIN1")
    fun getISUSERADMIN(): Boolean {
        return pref.getBoolean(ISUSERADMIN, false)
    }

    @JvmName("setISUSERADMIN1")
    fun setISUSERADMIN(isUserAdmin: Boolean) {
        editor.putBoolean(ISUSERADMIN, isUserAdmin)
        editor.apply()
    }
    @JvmName("getLASTLOGIN1")
    fun getLASTLOGIN(): Long {
        return pref.getLong(LASTLOGIN, 0)
    }

    @JvmName("setLASTLOGIN1")
    fun setLASTLOGIN(lastLogin: Long) {
        editor.putLong(LASTLOGIN, lastLogin)
        editor.apply()
    }

    @JvmName("getURLPORT1")
    fun getURLPORT(): Int {
        return pref.getInt(URLPORT, 0)
    }

    @JvmName("setURLPORT1")
    fun setURLPORT(urlPort: Int) {
        editor.putInt(URLPORT, urlPort)
        editor.apply()
    }

    @JvmName("getSERVERURL1")
    fun getSERVERURL(): String? {
        return if (pref.getString(SERVERURL, null) != null) pref.getString(
            SERVERURL, null
        ) else null
    }

    @JvmName("setSERVERURL1")
    fun setSERVERURL(serverUrl: String?) {
        editor.putString(SERVERURL, serverUrl)
        editor.apply()
    }

    @JvmName("getCOUCHDBURL1")
    fun getCOUCHDBURL(): String? {
        return if (pref.getString(COUCHDBURL, null) != null) pref.getString(
            COUCHDBURL, null
        ) else null
    }
    @JvmName("setCOUCHDBURL1")
    fun setCOUCHDBURL(couchDbUrl: String?) {
        editor.putString(COUCHDBURL, couchDbUrl)
        editor.apply()
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
                    val user = User(fullName, name, password, image)
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
            jsonArray.put(userJson)
        }
        editor.putString(SAVEDUSERS, jsonArray.toString())
        editor.apply()
    }
}