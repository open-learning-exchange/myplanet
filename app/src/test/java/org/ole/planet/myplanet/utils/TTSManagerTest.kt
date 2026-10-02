package org.ole.planet.myplanet.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class TTSManagerTest {

    @Test
    fun testStripMarkdown_headers() {
        val input = "# Header 1\n## Header 2\n### Header 3"
        val expected = "Header 1\nHeader 2\nHeader 3"
        assertEquals(expected, TTSManager.stripMarkdown(input))
    }

    @Test
    fun testStripMarkdown_boldAndItalic() {
        val input = "**Bold** and *Italic* and ***Both***"
        val expected = "Bold and Italic and Both"
        assertEquals(expected, TTSManager.stripMarkdown(input))
    }

    @Test
    fun testStripMarkdown_codeBlocks() {
        val input = "Here is some `code` and a block:\n```\nval x = 1\n```"
        val expected = "Here is some  and a block:"
        // Note: The current implementation replaces code blocks with empty string and trims the result
        assertEquals(expected, TTSManager.stripMarkdown(input))
    }

    @Test
    fun testStripMarkdown_links() {
        val input = "[Link Text](https://example.com) and ![Image Alt](https://example.com/img.png)"
        val expected = "Link Text and Image Alt"
        assertEquals(expected, TTSManager.stripMarkdown(input))
    }

    @Test
    fun testStripMarkdown_lists() {
        val input = "- Item 1\n* Item 2\n+ Item 3\n1. Numbered Item"
        val expected = "Item 1\nItem 2\nItem 3\nNumbered Item"
        assertEquals(expected, TTSManager.stripMarkdown(input))
    }

    @Test
    fun testStripMarkdown_complex() {
        val input = "> Blockquote\n---\nHorizontal Rule"
        val expected = "Blockquote\n\nHorizontal Rule"
        assertEquals(expected, TTSManager.stripMarkdown(input))
    }

    @Test
    fun testFormatCsvForSpeech_empty() {
        val rows = emptyList<Array<String>>()
        assertEquals("", TTSManager.formatCsvForSpeech(rows))
    }

    @Test
    fun testFormatCsvForSpeech_basic() {
        val rows = listOf(
            arrayOf("Name", "Age", "City"),
            arrayOf("John", "30", "New York"),
            arrayOf("Jane", "25", "London")
        )
        val expected = "Row 1. Name: John, Age: 30, City: New York. Row 2. Name: Jane, Age: 25, City: London"
        assertEquals(expected, TTSManager.formatCsvForSpeech(rows))
    }

    @Test
    fun testFormatCsvForSpeech_missingHeaders() {
        val rows = listOf(
            arrayOf("Name"),
            arrayOf("John", "30", "New York")
        )
        // Note: The current implementation uses "column N" for missing headers
        val expected = "Row 1. Name: John, column 2: 30, column 3: New York"
        assertEquals(expected, TTSManager.formatCsvForSpeech(rows))
    }

    @Test
    fun testStripMarkdown_plainTextTrimmed() {
        val input = "   hello world   "
        val expected = "hello world"
        assertEquals(expected, TTSManager.stripMarkdown(input))
    }

    @Test
    fun testStripMarkdown_regexFamilyBypassFastPath() {
        // inline code
        assertEquals("a  c", TTSManager.stripMarkdown("a `b` c"))
        // numbered list
        assertEquals("item", TTSManager.stripMarkdown("1. item"))
        // table
        assertEquals("a b", TTSManager.stripMarkdown("a|b"))
        // blockquote
        assertEquals("q", TTSManager.stripMarkdown("> q"))
        // rule
        assertEquals("", TTSManager.stripMarkdown("---"))
        // + list item
        assertEquals("item", TTSManager.stripMarkdown("+ item"))
    }

    @Test
    fun testStripMarkdown_propertyCheckEqualsFullChain() {
        val testInputs = listOf(
            "Plain text with no markdown",
            "   leading and trailing spaces   ",
            "# Header test",
            "**Bold** and *Italic*",
            "Here is `inline code` and ```block code```",
            "[Link](https://example.com)",
            "- Bullet 1\n* Bullet 2\n+ Bullet 3",
            "1. First item\n2. Second item",
            "> Quote here",
            "---\n***\n___",
            "col1|col2|col3",
            "Mixed text with 123 numbers and regular words"
        )

        for (input in testInputs) {
            val expected = fullChainStripMarkdown(input)
            val actual = TTSManager.stripMarkdown(input)
            assertEquals("Failed for input: $input", expected, actual)
        }
    }

    private fun fullChainStripMarkdown(text: String): String {
        val codeBlockRegex = Regex("```[\\s\\S]*?```")
        val inlineCodeRegex = Regex("`[^`]*`")
        val headerRegex = Regex("^#{1,6}\\s+", RegexOption.MULTILINE)
        val linkRegex = Regex("!?\\[([^]]*)]\\([^)]*\\)")
        val boldItalicRegex = Regex("[*_]{1,3}([^*_]+)[*_]{1,3}")
        val listItemRegex = Regex("^[-*+]\\s+", RegexOption.MULTILINE)
        val numberedListRegex = Regex("^\\d+\\.\\s+", RegexOption.MULTILINE)
        val blockquoteRegex = Regex("^>+\\s?", RegexOption.MULTILINE)
        val horizontalRuleRegex = Regex("[-]{3,}|[*]{3,}|[_]{3,}")
        val tablePipeRegex = Regex("\\|")

        return text
            .replace(codeBlockRegex, "")
            .replace(inlineCodeRegex, "")
            .replace(headerRegex, "")
            .replace(linkRegex, "$1")
            .replace(boldItalicRegex, "$1")
            .replace(listItemRegex, "")
            .replace(numberedListRegex, "")
            .replace(blockquoteRegex, "")
            .replace(horizontalRuleRegex, "")
            .replace(tablePipeRegex, " ")
            .trim()
    }
}
