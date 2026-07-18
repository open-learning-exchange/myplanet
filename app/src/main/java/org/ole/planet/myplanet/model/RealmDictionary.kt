package org.ole.planet.myplanet.model

/**
 * Legacy dictionary DTO kept for source compatibility while dictionary persistence lives in Room
 * via `DictionaryEntity`/`DictionaryDao`.
 */
data class RealmDictionary(
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
