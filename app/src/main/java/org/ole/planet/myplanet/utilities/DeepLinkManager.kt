package org.ole.planet.myplanet.utilities

import android.content.Context
import org.json.JSONArray
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME

class DeepLinkManager(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveAllowedHosts(hosts: List<String>) {
        val json = JSONArray(hosts).toString()
        prefs.edit().putString("deep_link_hosts", json).apply()
    }

    fun getAllowedHosts(): List<String> {
        val json = prefs.getString("deep_link_hosts", "[]") ?: "[]"
        val jsonArray = JSONArray(json)
        val list = mutableListOf<String>()
        for (i in 0 until jsonArray.length()) {
            list.add(jsonArray.getString(i))
        }
        return list
    }
}