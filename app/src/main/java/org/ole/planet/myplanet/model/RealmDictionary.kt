package org.ole.planet.myplanet.model

import io.realm.RealmObject
import io.realm.annotations.PrimaryKey

open class RealmDictionary(
    @PrimaryKey var id: String = "",
    var word: String = "",
    var meaning: String = "",
    var synonym: String = "",
    var advanceCode: String = "",
    var code: String = "",
    var definition: String = "",
    var language: String = "",
    var antonym: String = ""
) : RealmObject()
