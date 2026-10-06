package org.ole.planet.myplanet.utils

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import java.math.BigDecimal
import kotlin.random.Random
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import org.ole.planet.myplanet.utils.KJson.GsonNumbers

/**
 * Property-style proof that [KJson.toGsonString] / [KJson.gsonToJson] write exactly Gson's bytes:
 * every generated tree is written by both and the texts compared char for char.
 */
class KJsonGsonWriterTest {

    private val gson = Gson()
    private val htmlUnsafeGson = GsonBuilder().disableHtmlEscaping().serializeNulls().create()
    private val random = Random(20261006)

    /** Every char class Gson's writer treats differently, plus plain text around them. */
    private val specialChars: List<Char> = buildList {
        for (c in 0..0x7f) add(c.toChar())
        addAll(listOf('\u0080', '\u009f', ' ', 'é', '‧', ' ', ' ', '‪', '​', '﻿', '�', '￿', 'ا', 'न', '漢', '—'))
        addAll(listOf('\ud83c', '\udf0d', '\ud800', '\udfff'))
    }

    private fun randomChar(): Char = when (random.nextInt(4)) {
        0 -> specialChars[random.nextInt(specialChars.size)]
        1 -> random.nextInt(0x20, 0x7f).toChar()
        2 -> random.nextInt(0, 0x10000).toChar()
        else -> HTML_AND_ESCAPES.random(random)
    }

    private fun randomString(): String = when (random.nextInt(6)) {
        0 -> ""
        1 -> "🌍" + randomChar() + "🌍"
        else -> buildString { repeat(random.nextInt(1, 24)) { append(randomChar()) } }
    }

    private val literalNumbers = listOf(
        "0", "-0", "1", "-1", "5", "1.0", "1.50", "-0.0", "0.1", "1e2", "1E2", "1e+2", "1E-7", "2.5e-3", "1.5e1",
        "3000000000", "9007199254740993", "9223372036854775807", "9223372036854775808", "-9223372036854775809",
        "1e400", "-1e400", "123456789012345678901234567890", "0.000001", "0.0000001", "12345678.9", "1.0E7", "4.9E-324",
    )

    private fun randomNumber(): JsonPrimitive = when (random.nextInt(9)) {
        0 -> JsonPrimitive(random.nextInt())
        1 -> JsonPrimitive(random.nextLong())
        2 -> JsonPrimitive(listOf(0L, 1L, -1L, Long.MAX_VALUE, Long.MIN_VALUE, Int.MAX_VALUE.toLong(), 9_007_199_254_740_993L)[random.nextInt(7)])
        3 -> JsonPrimitive(random.nextDouble() * Math.pow(10.0, random.nextInt(-12, 24).toDouble()) * (if (random.nextBoolean()) 1 else -1))
        4 -> JsonPrimitive(listOf(0.0, -0.0, 1.0, 0.1, 1e7, 1e-3, 9.99e-4, 1e21, Double.MAX_VALUE, Double.MIN_VALUE, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)[random.nextInt(13)])
        5 -> JsonPrimitive(random.nextFloat() * Math.pow(10.0, random.nextInt(-8, 12).toDouble()).toFloat())
        6 -> JsonPrimitive(listOf(0f, 0.1f, 1.5f, 36.6f, 1e10f, 1e-5f, Float.MAX_VALUE)[random.nextInt(7)])
        7 -> JsonPrimitive(BigDecimal(literalNumbers[random.nextInt(literalNumbers.size)]))
        else -> Json.parseToJsonElement(literalNumbers[random.nextInt(literalNumbers.size)]) as JsonPrimitive
    }

    private fun randomTree(depth: Int): JsonElement = when (if (depth >= 4) random.nextInt(4) else random.nextInt(6)) {
        0 -> JsonPrimitive(randomString())
        1 -> randomNumber()
        2 -> if (random.nextBoolean()) JsonPrimitive(random.nextBoolean()) else JsonNull
        3 -> JsonPrimitive(randomString())
        4 -> JsonArray(List(random.nextInt(0, 5)) { randomTree(depth + 1) })
        else -> JsonObject(LinkedHashMap<String, JsonElement>().apply {
            repeat(random.nextInt(0, 6)) { put(randomString(), randomTree(depth + 1)) }
        })
    }

    @Test
    fun `bridged trees write exactly what Gson writes for the bridged copy`() {
        repeat(TREES) { i ->
            val tree = randomTree(0)
            val gsonTree = tree.toGsonElement()
            assertEquals("toString #$i", gsonTree.toString(), KJson.toGsonString(tree))
            assertEquals("toJson #$i", gson.toJson(gsonTree), KJson.gsonToJson(tree))
            assertEquals("html-unsafe toJson #$i", htmlUnsafeGson.toJson(gsonTree), KJson.toGsonString(tree))
        }
    }

    @Test
    fun `parsed trees in LITERAL mode write exactly what Gson writes for its own parse`() {
        repeat(TREES) { i ->
            val written = randomTree(0).toString()
            // kotlinx writes NaN/Infinity unquoted, which strict kotlinx parsing then rejects.
            val text = if ("NaN" in written || "Infinity" in written) "[]" else written
            val gsonTree = JsonParser.parseString(text)
            val tree = Json.parseToJsonElement(text)
            assertEquals("toString #$i $text", gsonTree.toString(), KJson.toGsonString(tree, numbers = GsonNumbers.LITERAL))
            assertEquals("toJson #$i $text", gson.toJson(gsonTree), KJson.gsonToJson(tree, numbers = GsonNumbers.LITERAL))
        }
    }

    @Test
    fun `every single char escapes like Gson in both modes`() {
        for (code in 0 until 0x10000) {
            val s = "a${code.toChar()}b"
            val tree = JsonObject(mapOf(s to JsonArray(listOf(JsonPrimitive(s)))))
            assertEquals("U+%04X".format(code), tree.toGsonElement().toString(), KJson.toGsonString(tree))
            assertEquals("U+%04X html".format(code), gson.toJson(tree.toGsonElement()), KJson.gsonToJson(tree))
        }
    }

    @Test
    fun `strings, lists and maps match Gson reflection output`() {
        repeat(TREES) { i ->
            val strings = List(random.nextInt(0, 5)) { randomString() }
            assertEquals("list #$i", gson.toJson(strings), KJson.gsonToJson(KJson.getAsJsonArray(strings)))
            assertEquals("array #$i", gson.toJson(strings.toTypedArray()), KJson.gsonToJson(KJson.getAsJsonArray(strings)))
            val map = HashMap<String, String?>().apply { strings.forEach { put(it, if (random.nextInt(4) == 0) null else randomString()) } }
            val asObject = JsonObject(LinkedHashMap<String, JsonElement>().apply { map.forEach { (k, v) -> put(k, JsonPrimitive(v)) } })
            assertEquals("map #$i", gson.toJson(map), KJson.gsonToJson(asObject))
            val one = randomString()
            assertEquals("string #$i", gson.toJson(one), KJson.gsonToJson(JsonPrimitive(one)))
        }
        assertEquals(gson.toJson(null), KJson.gsonToJson(null))
    }

    @Test
    fun `lenient unquoted tokens in LITERAL mode print as strings, as Gson's lenient parse reads them`() {
        val text = "[abc, 1, true, x.y]"
        val gsonTree = JsonParser.parseString(text)
        val tree = KJson.lenientJson.parseToJsonElement(text)
        assertEquals(gsonTree.toString(), KJson.toGsonString(tree, numbers = GsonNumbers.LITERAL))
    }

    private companion object {
        const val TREES = 5000
        const val HTML_AND_ESCAPES = "<>&='\"\\/\u2028\u2029\n\t\u0000\u001f"
    }
}
