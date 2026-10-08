package org.ole.planet.myplanet.data.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room replacement for the Realm `RealmDictionary` model.
 *
 * Dictionary data is downloaded from a bundled JSON file and queried directly by the dictionary
 * screen; it is never synced to or uploaded from the server, which makes it the first fully
 * self-contained domain migrated off Realm.
 */
@Entity(tableName = "dictionary")
data class DictionaryEntity(
    @PrimaryKey
    var id: String = "",
    var word: String = "",
    var meaning: String = "",
    var synonym: String = "",
    var advanceCode: String = "",
    var code: String = "",
    var definition: String = "",
    var language: String = "",
    var antonym: String = ""
)
