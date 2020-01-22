package org.ole.planet.myplanet.model

import com.google.gson.Gson
import com.google.gson.JsonArray
import io.realm.Realm
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import org.ole.planet.myplanet.utilities.JsonUtils


open class RealmDictionary(
        @PrimaryKey var id : String = "",
        var word: String= "",
        var meaning: String= "",
        var synonym: String = "",
        var advance_code: String = "",
        var code: String = "",
        var definition: String = "",
        var language: String = "",
        var antonoym: String = ""
) : RealmObject()