package org.ole.planet.myplanet.utilities;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

public class JsonUtils {
    public static String getString(String fieldName, JsonObject jsonObject) {
        if (jsonObject.has(fieldName)) {
            JsonElement el = jsonObject.get(fieldName);
            return el instanceof JsonNull ? "" : el.getAsString();
        }
        return "";
    }

    public static boolean getBoolean(String fieldName, JsonObject jsonObject) {
        if (jsonObject.has(fieldName)) {
            JsonElement el = jsonObject.get(fieldName);
            return !(el instanceof JsonNull) && el.getAsBoolean();
        }
        return false;
    }

    public static int getInt(String fieldName, JsonObject jsonObject) {
        if (jsonObject.has(fieldName)) {
            JsonElement el = jsonObject.get(fieldName);
            return el instanceof JsonNull ? 0 : el.getAsInt();
        }
        return 0;
    }

    public static JsonArray getJsonArray(String fieldName, JsonObject jsonObject) {
        if (jsonObject.has(fieldName)) {
            JsonElement el = jsonObject.get(fieldName);
            return el instanceof JsonNull ? new JsonArray() : el.getAsJsonArray();
        }
        return new JsonArray();
    }

    public static long getLong(String fieldName, JsonObject jsonObject) {
        if (jsonObject.has(fieldName)) {
            JsonElement el = jsonObject.get(fieldName);
            return el instanceof JsonNull ? 0L : el.getAsLong();
        }
        return 0L;
    }
}
