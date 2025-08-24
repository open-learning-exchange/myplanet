package org.ole.planet.myplanet.model

import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey

class RealmDictionary(
    @PrimaryKey var id: String = "",
    var word: String = "",
    var meaning: String = "",
    var synonym: String = "",
    var advanceCode: String = "",
    var code: String = "",
    var definition: String = "",
    var language: String = "",
    var antonym: String = ""
) : RealmObject
