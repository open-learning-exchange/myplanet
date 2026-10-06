package org.ole.planet.myplanet.utils

import com.google.gson.Gson
import com.google.gson.JsonElement as GsonJsonElement
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Property-style proof that [KJson.parseOrNull] reads text exactly as `Gson().fromJson(text,
 * JsonElement::class.java)` does: the same tree (numbers compared by their text), `null` for the
 * same empty documents, and a throw for the same malformed ones.
 */
class KJsonLenientParserTest {

    private val gson = Gson()
    private val random = Random(17)

    private fun outcome(gsonSide: Boolean, text: String): String = try {
        if (gsonSide) {
            gson.fromJson(text, GsonJsonElement::class.java)?.toKotlinx()?.toString() ?: "<null>"
        } else {
            KJson.parseOrNull(text)?.toString() ?: "<null>"
        }
    } catch (_: Exception) {
        "<throws>"
    }

    private fun check(text: String) {
        assertEquals("input: $text", outcome(true, text), outcome(false, text))
    }

    private val atoms = listOf(
        "0", "-0", "1", "-1", "12", "007", "1.5", "1.", ".5", "1e2", "1E+2", "1e-2", "1e", "-", "+1", "1.5.2",
        "9223372036854775807", "9223372036854775808", "-9223372036854775808", "123456789012345678901234567890",
        "true", "TRUE", "True", "tRuE", "false", "FALSE", "null", "NULL", "Null", "nullx", "truex", "NaN", "Infinity",
        "abc", "a-b", "a.b", "x_y", "ünï", "\"s\"", "'s'", "\"a\\nb\"", "'it\\'s'", "\"q\\\"\"", "\"\\u0041\\u00e9\"",
        "\"tab\\tx\"", "\"sl\\/x\"", "'mix\"ed'", "\"raw\nline\"", "\"bad\\x\"", "\"\\u12\"", "''", "\"\"",
    )

    private fun ws(): String = when (random.nextInt(8)) {
        0 -> " "
        1 -> "\n"
        2 -> "\t \r\n"
        3 -> "/* c */"
        4 -> "// line\n"
        5 -> "# hash\n"
        else -> ""
    }

    private fun name(): String = when (random.nextInt(5)) {
        0 -> "\"k${random.nextInt(4)}\""
        1 -> "'k${random.nextInt(4)}'"
        2 -> "k${random.nextInt(4)}"
        3 -> "\"<&='${random.nextInt(3)}\""
        else -> "\"\""
    }

    private fun sep(): String = listOf(":", ":", "=", "=>", " : ")[random.nextInt(5)]
    private fun comma(): String = listOf(",", ",", ";", " , ")[random.nextInt(4)]

    private fun value(depth: Int): String = when (if (depth > 3) 0 else random.nextInt(4)) {
        0, 1 -> atoms[random.nextInt(atoms.size)]
        2 -> buildString {
            append('[')
            val n = random.nextInt(4)
            repeat(n) { i ->
                if (i > 0) append(comma())
                append(ws())
                if (random.nextInt(6) != 0) append(value(depth + 1))
                append(ws())
            }
            if (random.nextInt(5) == 0) append(comma())
            append(']')
        }
        else -> buildString {
            append('{')
            val n = random.nextInt(4)
            repeat(n) { i ->
                if (i > 0) append(comma())
                append(ws()).append(name()).append(ws()).append(sep()).append(ws()).append(value(depth + 1))
            }
            append(ws()).append('}')
        }
    }

    @Test
    fun `generated lenient documents parse like Gson`() {
        repeat(20000) {
            val prefix = if (random.nextInt(20) == 0) ")]}'\n" else ""
            check(prefix + ws() + value(0) + ws())
        }
    }

    @Test
    fun `corrupted documents fail or succeed like Gson`() {
        val noise = listOf("", "x", "]", "}", ",", ":", "\"", "'", "/", "/*", "#", "\\", "=", ";", "\u000c", " 1", "[", "{")
        repeat(20000) {
            val doc = value(0)
            val at = random.nextInt(doc.length + 1)
            val cut = if (random.nextBoolean()) doc.substring(0, at) else doc
            check(cut + noise[random.nextInt(noise.size)] + (if (random.nextBoolean()) doc.substring(at) else ""))
        }
    }

    @Test
    fun `empty and edge documents`() {
        listOf("", " ", "\n\t", "// only\n", "/* only */", "# only", ")]}'\n", ")]}'\n[1]", "\u000c", "null", "NULL", "[,]", "[1,]",
            "[,1]", "[;]", "{}", "{,}", "{a:1,}", "{'a'=>1;b=2}", "[1] [2]", "1 2", "/", "/*", "{a:1}x", "[\"a\"\n]")
            .forEach(::check)
    }
}
