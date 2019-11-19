package org.ole.planet.myplanet.utilities;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

public class JsonUtils {
    public static String getString(String fieldName, JsonObject jsonObject) {
        try {

        if (jsonObject.has(fieldName)) {
            JsonElement el = jsonObject.get(fieldName);
            return el instanceof JsonNull ? "" : el.getAsString();
        }
        }catch (Exception e){}
        return "";
    }

    public static String getString(JsonArray array, int index) {
        try {
            JsonElement el = array.get(index);
            return el instanceof JsonNull ? "" : el.getAsString();
        }catch (Exception e){}
        return "";
    }

    public static boolean getBoolean(String fieldName, JsonObject jsonObject) {
        try{
        if (jsonObject.has(fieldName)) {
            JsonElement el = jsonObject.get(fieldName);
            return !(el instanceof JsonNull) && el.getAsBoolean();
        }
        }catch (Exception e){}
        return false;
    }

    public static int getInt(String fieldName, JsonObject jsonObject) {
        try {
            if (jsonObject.has(fieldName)) {
                JsonElement el = jsonObject.get(fieldName);
                return el instanceof JsonNull ? 0 : el.getAsInt();
            }
        }catch (Exception e){}
        return 0;
    }

    public static JsonArray getJsonArray(String fieldName, JsonObject jsonObject) {
        try {
            JsonElement arry = getJsonElement(fieldName, jsonObject, JsonArray.class);
            return arry instanceof JsonNull || !(arry instanceof JsonArray) ? new JsonArray() : arry.getAsJsonArray();
        }catch (Exception e){}
        return new JsonArray();
    }

    public static JsonObject getJsonObject(String fieldName, JsonObject jsonObject) {
        try{
        JsonElement el = getJsonElement(fieldName, jsonObject, JsonArray.class);
        return el instanceof JsonObject ? el.getAsJsonObject() : new JsonObject();
        }catch (Exception e){}
        return new JsonObject();
    }

    public static JsonElement getJsonElement(String fieldName, JsonObject jsonObject, Class type) {
        try{
        JsonElement jsonElement = type == JsonObject.class ? new JsonObject() : new JsonArray();
        if (jsonObject.has(fieldName)) {
            return jsonObject.get(fieldName);
        }
        return jsonElement;
        }catch (Exception e){}
        return new JsonObject();

    }

    public static long getLong(String fieldName, JsonObject jsonObject) {
        try {
            if (jsonObject.has(fieldName)) {
                JsonElement el = jsonObject.get(fieldName);
                return el instanceof JsonNull ? 0L : el.getAsLong();
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        return 0L;
    }
}
