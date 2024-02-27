package org.ole.planet.myplanet.utilities

import com.google.gson.JsonArray
import com.google.gson.JsonParser

object JsonParserUtils {
    @JvmStatic
    fun getStringAsJsonArray(s: String?): JsonArray {
        val parser = JsonParser()
        val arrayElement = parser.parse(s)
        return arrayElement.asJsonArray
    }
}
