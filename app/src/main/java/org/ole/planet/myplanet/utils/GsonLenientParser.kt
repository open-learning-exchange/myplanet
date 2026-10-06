package org.ole.planet.myplanet.utils

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Reads JSON text into a kotlinx tree with the grammar of Gson's lenient `JsonReader`, which is
 * what `Gson().fromJson(text, ...)` and `JsonParser.parseString` accept: single-quoted and
 * unquoted strings and names, `//`, `/* */` and `#` comments, `=`/`=>` name separators, `;`
 * element separators, empty array slots read as `null`, case-insensitive `true`/`false`/`null`
 * and the `)]}'` prefix; after the value only plain whitespace may follow, as `fromJson` checks. Number literals keep their text, like Gson's `LazilyParsedNumber`;
 * anything that is not a valid JSON number reads as a string, as Gson's unquoted literals do.
 * `KJsonLenientParserTest` checks it against Gson's own parser.
 */
internal class GsonLenientParser private constructor(private val text: String) {
    private var pos = 0

    companion object {
        /** `null` for an empty document (only whitespace or comments), as `Gson().fromJson` returns. */
        fun parse(text: String): JsonElement? {
            val parser = GsonLenientParser(text)
            parser.consumeNonExecutePrefix()
            if (parser.nextNonWhitespace() == -1) return null
            parser.pos--
            val value = parser.readValue(Scope.DOCUMENT)
            // Gson checks for trailing content after restoring its default (non-lenient) reader, so
            // only plain whitespace may follow the value; even a comment is rejected there.
            while (parser.pos < text.length && text[parser.pos] in " \n\r\t") parser.pos++
            if (parser.pos < text.length) parser.fail("Did not consume the entire document")
            return value
        }
    }

    private enum class Scope { DOCUMENT, EMPTY_ARRAY, NONEMPTY_ARRAY, OBJECT }

    private fun fail(message: String): Nothing = throw SerializationException("$message at offset $pos")

    private fun consumeNonExecutePrefix() {
        if (nextNonWhitespace() == -1) return
        pos--
        if (text.startsWith(")]}'\n", pos)) pos += 5
    }

    /** Gson's `nextNonWhitespace`: skips ' ', '\n', '\r', '\t' and comments; -1 at the end. */
    private fun nextNonWhitespace(): Int {
        while (pos < text.length) {
            val c = text[pos++]
            when {
                c == ' ' || c == '\n' || c == '\r' || c == '\t' -> continue
                c == '/' && pos < text.length && text[pos] == '*' -> {
                    val end = text.indexOf("*/", pos + 1)
                    if (end < 0) fail("Unterminated comment")
                    pos = end + 2
                }
                c == '/' && pos < text.length && text[pos] == '/' -> skipToEndOfLine()
                c == '#' -> skipToEndOfLine()
                else -> return c.code
            }
        }
        return -1
    }

    private fun skipToEndOfLine() {
        while (pos < text.length) {
            val c = text[pos++]
            if (c == '\n' || c == '\r') return
        }
    }

    private fun isLiteral(c: Char): Boolean = when (c) {
        '/', '\\', ';', '#', '=', '{', '}', '[', ']', ':', ',', ' ', '\t', '\u000c', '\r', '\n' -> false
        else -> true
    }

    private fun next(): Int = nextNonWhitespace().also { if (it == -1) fail("End of input") }

    private fun readValue(scope: Scope): JsonElement {
        val c = next().toChar()
        return when (c) {
            ']', ',', ';' -> {
                if (c == ']' && scope == Scope.EMPTY_ARRAY) fail("Unexpected end of array")
                if (scope != Scope.EMPTY_ARRAY && scope != Scope.NONEMPTY_ARRAY) fail("Unexpected value")
                pos--
                JsonNull
            }
            '\'', '"' -> JsonPrimitive(readQuoted(c))
            '[' -> readArray()
            '{' -> readObject()
            else -> {
                pos--
                readLiteral()
            }
        }
    }

    private fun readLiteral(): JsonElement {
        keyword("true")?.let { return JsonPrimitive(true) }
        keyword("false")?.let { return JsonPrimitive(false) }
        keyword("null")?.let { return JsonNull }
        val start = pos
        while (pos < text.length && isLiteral(text[pos])) pos++
        if (pos == start) fail("Expected value")
        val literal = text.substring(start, pos)
        return if (isGsonNumber(literal)) Json.parseToJsonElement(literal) else JsonPrimitive(literal)
    }

    /** Gson's `peekKeyword`: each char may be lower or upper case, and the keyword must end the literal. */
    private fun keyword(keyword: String): Unit? {
        if (pos + keyword.length > text.length) return null
        for (i in keyword.indices) {
            val c = text[pos + i]
            if (c != keyword[i] && c != keyword[i].uppercaseChar()) return null
        }
        if (pos + keyword.length < text.length && isLiteral(text[pos + keyword.length])) return null
        pos += keyword.length
        return Unit
    }

    /**
     * Gson's `peekNumber`: JSON number syntax, no leading zero before another digit. Gson spots a
     * leading zero through its (wrapping) long accumulator being 0, so an integer part that
     * overflows back to exactly 0 mid-way also reads as an unquoted string; that quirk is kept.
     */
    private fun isGsonNumber(literal: String): Boolean {
        if (!gsonNumber.matches(literal)) return false
        var value = 0L
        var first = true
        for (c in literal.removePrefix("-")) {
            if (c !in '0'..'9') break
            if (!first && value == 0L) return false
            value = value * 10 - (c - '0')
            first = false
        }
        return true
    }

    private val gsonNumber = Regex("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][-+]?[0-9]+)?")

    private fun readQuoted(quote: Char): String {
        val out = StringBuilder()
        while (true) {
            if (pos >= text.length) fail("Unterminated string")
            val c = text[pos++]
            when (c) {
                quote -> return out.toString()
                '\\' -> out.append(readEscape())
                else -> out.append(c)
            }
        }
    }

    private fun readEscape(): Char {
        if (pos >= text.length) fail("Unterminated escape sequence")
        return when (val escaped = text[pos++]) {
            'u' -> {
                if (pos + 4 > text.length) fail("Unterminated escape sequence")
                var result = 0
                for (i in 0 until 4) {
                    val digit = Character.digit(text[pos + i], 16)
                    if (digit < 0 || text[pos + i] !in "0123456789abcdefABCDEF") fail("Malformed Unicode escape")
                    result = result * 16 + digit
                }
                pos += 4
                result.toChar()
            }
            't' -> '\t'
            'b' -> '\b'
            'n' -> '\n'
            'r' -> '\r'
            'f' -> '\u000c'
            '\n', '\'', '"', '\\', '/' -> escaped
            else -> fail("Invalid escape sequence")
        }
    }

    private fun readArray(): JsonArray {
        val items = mutableListOf<JsonElement>()
        var scope = Scope.EMPTY_ARRAY
        while (true) {
            if (scope == Scope.NONEMPTY_ARRAY) {
                when (next().toChar()) {
                    ']' -> return JsonArray(items)
                    ',', ';' -> Unit
                    else -> fail("Unterminated array")
                }
            } else {
                if (next().toChar() == ']') return JsonArray(items)
                pos--
            }
            items.add(readValue(scope))
            scope = Scope.NONEMPTY_ARRAY
        }
    }

    private fun readObject(): JsonObject {
        val members = LinkedHashMap<String, JsonElement>()
        var first = true
        while (true) {
            if (!first) {
                when (next().toChar()) {
                    '}' -> return JsonObject(members)
                    ',', ';' -> Unit
                    else -> fail("Unterminated object")
                }
            }
            val name = when (val c = next().toChar()) {
                '"', '\'' -> readQuoted(c)
                '}' -> if (first) return JsonObject(members) else fail("Expected name")
                else -> {
                    pos--
                    if (!isLiteral(c)) fail("Expected name")
                    val start = pos
                    while (pos < text.length && isLiteral(text[pos])) pos++
                    text.substring(start, pos)
                }
            }
            when (next().toChar()) {
                ':' -> Unit
                '=' -> if (pos < text.length && text[pos] == '>') pos++
                else -> fail("Expected ':'")
            }
            members[name] = readValue(Scope.OBJECT)
            first = false
        }
    }
}
