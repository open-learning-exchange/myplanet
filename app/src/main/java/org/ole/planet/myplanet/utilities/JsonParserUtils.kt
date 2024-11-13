package org.ole.planet.myplanet.utilities

import com.google.gson.JsonArray
import com.google.gson.JsonParser.parseString

object JsonParserUtils {
    fun getStringAsJsonArray(s: String?): JsonArray {
        val arrayElement = parseString(s)
        return arrayElement.asJsonArray
    }
}
