package org.ole.planet.myplanet.utilities;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

public class JsonParserUtils {
    public static JsonArray getStringAsJsonArray(String s) {
        JsonParser parser = new JsonParser();
        JsonElement arrayElement = parser.parse(s);
        return arrayElement.getAsJsonArray();
    }
}
