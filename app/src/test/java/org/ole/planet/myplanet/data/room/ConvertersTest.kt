package org.ole.planet.myplanet.data.room

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.ole.planet.myplanet.model.Attachment
import org.ole.planet.myplanet.model.Conversation

class ConvertersTest {

    private val converters = Converters()

    @Test
    fun testStringListRoundTrip() {
        val original = listOf("hello", "world")
        val json = converters.fromStringList(original)
        val restored = converters.toStringList(json)
        assertEquals(original, restored)
    }

    @Test
    fun testStringListNullOrBlank() {
        assertNull(converters.toStringList(null))
        assertNull(converters.toStringList(""))
        assertNull(converters.toStringList("   "))
    }

    @Test
    fun testConversationListRoundTrip() {
        val original = listOf(Conversation().apply { query = "msg1" }, Conversation().apply { query = "msg2" })
        val json = converters.fromConversationList(original)
        val restored = converters.toConversationList(json)
        assertEquals(original.size, restored?.size)
        assertEquals("msg1", restored?.get(0)?.query)
    }

    @Test
    fun testConversationListNullOrBlank() {
        assertNull(converters.toConversationList(null))
        assertNull(converters.toConversationList(""))
        assertNull(converters.toConversationList("   "))
    }

    @Test
    fun testAttachmentListRoundTrip() {
        val original = listOf(Attachment().apply { name = "file1.pdf" }, Attachment().apply { name = "file2.pdf" })
        val json = converters.fromAttachmentList(original)
        val restored = converters.toAttachmentList(json)
        assertEquals(original.size, restored?.size)
        assertEquals("file1.pdf", restored?.get(0)?.name)
    }

    @Test
    fun testAttachmentListNullOrBlank() {
        assertNull(converters.toAttachmentList(null))
        assertNull(converters.toAttachmentList(""))
        assertNull(converters.toAttachmentList("   "))
    }
}
