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

    public static String getString(JsonArray array, int index) {
        JsonElement el = array.get(index);
        return el instanceof JsonNull ? "" : el.getAsString();
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
        JsonElement arry = getJsonElement(fieldName, jsonObject, JsonArray.class);
        return arry instanceof JsonNull ? new JsonArray() : arry.getAsJsonArray();
    }

    public static JsonObject getJsonObject(String fieldName, JsonObject jsonObject) {
        JsonElement el = getJsonElement(fieldName, jsonObject, JsonArray.class);
        return el instanceof JsonNull ? new JsonObject() : el.getAsJsonObject();
    }

    public static JsonElement getJsonElement(String fieldName, JsonObject jsonObject, Class type) {
        JsonElement jsonElement = type == JsonObject.class ? new JsonObject() : new JsonArray();
        if (jsonObject.has(fieldName)) {
            return jsonObject.get(fieldName);
        }
        return jsonElement;

    }

    public static long getLong(String fieldName, JsonObject jsonObject) {
        if (jsonObject.has(fieldName)) {
            JsonElement el = jsonObject.get(fieldName);
            return el instanceof JsonNull ? 0L : el.getAsLong();
        }
        return 0L;
    }
}
