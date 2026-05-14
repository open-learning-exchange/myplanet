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
}
