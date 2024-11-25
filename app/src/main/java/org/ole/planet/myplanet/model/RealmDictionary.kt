package org.ole.planet.myplanet.model

import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey

class RealmDictionary : RealmObject {
    @PrimaryKey
    var id: String? = null
    var word: String? = null
    var meaning: String? = null
    var synonym: String? = null
    var advanceCode: String? = null
    var code: String? = null
    var definition: String? = null
    var language: String? = null
    var antonym: String? = null
}
