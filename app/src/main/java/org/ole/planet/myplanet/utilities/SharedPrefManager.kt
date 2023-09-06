package org.ole.planet.myplanet.utilities

import android.content.Context
import android.content.SharedPreferences

class SharedPrefManager(var _context: Context) {
        var PRIVATE_MODE = 0
        var pref: SharedPreferences
        var editor: SharedPreferences.Editor
        var SHARED_PREF_NAME = "OLEmyPlanetPrefData"

    // Constructor
    init {
        pref = _context.getSharedPreferences(SHARED_PREF_NAME, PRIVATE_MODE)
        editor = pref.edit()
    }
}