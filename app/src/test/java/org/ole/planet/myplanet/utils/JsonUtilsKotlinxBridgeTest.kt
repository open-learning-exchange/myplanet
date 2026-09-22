package org.ole.planet.myplanet.utils

import com.google.gson.JsonArray as GsonJsonArray
import com.google.gson.JsonNull as GsonJsonNull
import com.google.gson.JsonObject as GsonJsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonUtilsKotlinxBridgeTest {
    @Test
    fun `toGson converts primitives, nulls and nested arrays into an equivalent Gson tree`() {
        val kotlinxObject = buildJsonObject {
            put("name", "value")
            put("count", 3)
            put("missing", null as String?)
            putJsonArray("tags") {
                add("a")
                add("b")
            }
        }

        val gsonObject = kotlinxObject.toGson()

        assertEquals("value", gsonObject.get("name").asString)
        assertEquals(3, gsonObject.get("count").asInt)
        assertTrue(gsonObject.get("missing").isJsonNull)
        assertEquals(2, gsonObject.getAsJsonArray("tags").size())
        assertEquals("a", gsonObject.getAsJsonArray("tags")[0].asString)
    }

    @Test
    fun `JsonArray toGson converts primitives and nested objects`() {
        val kotlinxArray = buildJsonArray {
            add(1)
            add("two")
            addJsonObject { put("nested", true) }
        }

        val gsonArray = kotlinxArray.toGson()

        assertEquals(3, gsonArray.size())
        assertEquals(1, gsonArray[0].asInt)
        assertEquals("two", gsonArray[1].asString)
        assertTrue(gsonArray[2].asJsonObject.get("nested").asBoolean)
    }

    @Test
    fun `toGsonElement dispatches nulls, primitives, objects and arrays`() {
        assertTrue(JsonNull.toGsonElement().isJsonNull)
        assertEquals(42, JsonPrimitive(42).toGsonElement().asInt)
        assertEquals("v", buildJsonObject { put("k", "v") }.toGsonElement().asJsonObject.get("k").asString)
        assertEquals(1, buildJsonArray { add(1) }.toGsonElement().asJsonArray.size())
    }

    @Test
    fun `toKotlinx converts a Gson tree into an equivalent kotlinx tree`() {
        val gsonObject = GsonJsonObject().apply {
            addProperty("name", "value")
            addProperty("count", 3)
            addProperty("active", true)
            add("missing", GsonJsonNull.INSTANCE)
            add(
                "tags",
                GsonJsonArray().apply {
                    add("a")
                    add("b")
                }
            )
        }

        val kotlinxObject = gsonObject.toKotlinx().jsonObject

        assertEquals("value", kotlinxObject.getValue("name").jsonPrimitive.content)
        assertEquals(3, kotlinxObject.getValue("count").jsonPrimitive.int)
        assertTrue(kotlinxObject.getValue("active").jsonPrimitive.boolean)
        assertTrue(kotlinxObject.getValue("missing") is JsonNull)
        assertEquals(2, kotlinxObject.getValue("tags").jsonArray.size)
        assertEquals("a", kotlinxObject.getValue("tags").jsonArray[0].jsonPrimitive.content)
    }

    @Test
    fun `toGson and toKotlinx round-trip preserves structure`() {
        val original = buildJsonObject {
            put("str", "hi")
            put("num", 7)
            put("flag", false)
            putJsonArray("list") {
                add(1)
                add(2)
            }
        }

        val roundTripped = original.toGson().toKotlinx().jsonObject

        assertEquals(original.getValue("str").jsonPrimitive.content, roundTripped.getValue("str").jsonPrimitive.content)
        assertEquals(original.getValue("num").jsonPrimitive.int, roundTripped.getValue("num").jsonPrimitive.int)
        assertEquals(original.getValue("flag").jsonPrimitive.boolean, roundTripped.getValue("flag").jsonPrimitive.boolean)
        assertEquals(original.getValue("list").jsonArray.size, roundTripped.getValue("list").jsonArray.size)
    }
}
