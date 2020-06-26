package org.ole.planet.myplanet.utilities;

import android.text.TextUtils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import io.realm.RealmList;

public class JsonUtils {
    public static String getString(String fieldName, JsonObject jsonObject) {
        try {

            if (jsonObject.has(fieldName)) {
                JsonElement el = jsonObject.get(fieldName);
                return el instanceof JsonNull ? "" : el.getAsString();
            }
        } catch (Exception e) {
        }
        return "";
    }

    public static String getString(JsonArray array, int index) {
        try {
            JsonElement el = array.get(index);
            return el instanceof JsonNull ? "" : el.getAsString();
        } catch (Exception e) {
        }
        return "";
    }

    public static JsonArray getAsJsonArray(RealmList<String> list) {
        JsonArray array = new JsonArray();
        for (String s : list) {
            array.add(s);
        }
        return array;
    }

    public static boolean getBoolean(String fieldName, JsonObject jsonObject) {
        try {
            if (jsonObject.has(fieldName)) {
                JsonElement el = jsonObject.get(fieldName);
                return !(el instanceof JsonNull) && el.getAsBoolean();
            }
        } catch (Exception e) {

        }
        return false;
    }

    public static void addString(JsonObject object, String fieldName, String value) {
        if (!TextUtils.isEmpty(value))
            object.addProperty(fieldName, value);
    }

    public static void addLong(JsonObject object, String fieldName, long value) {
        if (value > 0)
            object.addProperty(fieldName, value);
    }

    public static void addInteger(JsonObject object, String fieldName, int value) {
        if (value != 0)
            object.addProperty(fieldName, value);
    }

    public static void addFloat(JsonObject object, String fieldName, float value) {
        if (value != 0)
            object.addProperty(fieldName, value);
    }

    public static void addJson(JsonObject object, String fieldName, JsonObject value) {
        if (value != null && value.keySet().size() > 0)
            object.add(fieldName, value);
    }

    public static int getInt(String fieldName, JsonObject jsonObject) {
        try {
            if (jsonObject.has(fieldName)) {
                JsonElement el = jsonObject.get(fieldName);
                return el instanceof JsonNull ? 0 : el.getAsInt();
            }
        } catch (Exception e) {

        }
        return 0;
    }

    public static float getFloat(String fieldName, JsonObject jsonObject) {
        try {
            if (jsonObject.has(fieldName)) {
                JsonElement el = jsonObject.get(fieldName);

                return el instanceof JsonNull ? 0 : el.getAsFloat();
            }
        } catch (Exception e) {

        }
        return getInt(fieldName, jsonObject);
    }

    public static JsonArray getJsonArray(String fieldName, JsonObject jsonObject) {
        try {
            JsonElement arry = getJsonElement(fieldName, jsonObject, JsonArray.class);
            return arry instanceof JsonNull || !(arry instanceof JsonArray) ? new JsonArray() : arry.getAsJsonArray();
        } catch (Exception e) {
        }
        return new JsonArray();
    }

    public static JsonObject getJsonObject(String fieldName, JsonObject jsonObject) {
        try {
            JsonElement el = getJsonElement(fieldName, jsonObject, JsonArray.class);
            return el instanceof JsonObject ? el.getAsJsonObject() : new JsonObject();
        } catch (Exception e) {
        }
        return new JsonObject();
    }

    public static JsonElement getJsonElement(String fieldName, JsonObject jsonObject, Class type) {
        try {
            JsonElement jsonElement = type == JsonObject.class ? new JsonObject() : new JsonArray();
            if (jsonObject.has(fieldName)) {
                return jsonObject.get(fieldName);
            }
            return jsonElement;
        } catch (Exception e) {
        }
        return new JsonObject();

    }

    public static long getLong(String fieldName, JsonObject jsonObject) {
        try {
            if (jsonObject.has(fieldName)) {
                JsonElement el = jsonObject.get(fieldName);
                return el instanceof JsonNull ? 0L : el.getAsLong();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0L;
    }
}
