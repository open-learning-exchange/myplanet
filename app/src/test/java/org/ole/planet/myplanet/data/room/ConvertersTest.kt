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

    // The following simulate JSON already sitting in an installed app's Room database, written
    // by the prior Gson-based converter (which omits null-valued reference fields and never
    // omits primitives, even at their zero/false default) - this locks in that the kotlinx
    // migration doesn't require a schema version bump to keep reading pre-existing rows.

    @Test
    fun testStringListDecodesPriorGsonOutputVerbatim() {
        val gsonWrittenJson = """["hello","world"]"""

        assertEquals(listOf("hello", "world"), converters.toStringList(gsonWrittenJson))
    }

    @Test
    fun testConversationListDecodesPriorGsonOutput_withNullFieldOmitted() {
        // Gson omits a null `response` field entirely rather than writing "response":null.
        val gsonWrittenJson = """[{"query":"msg1"},{"query":"msg2","response":"reply2"}]"""

        val restored = converters.toConversationList(gsonWrittenJson)

        assertEquals(2, restored?.size)
        assertEquals("msg1", restored?.get(0)?.query)
        assertNull(restored?.get(0)?.response)
        assertEquals("reply2", restored?.get(1)?.response)
    }

    @Test
    fun testAttachmentListDecodesPriorGsonOutput_withNullFieldsOmittedAndPrimitiveDefaultsPresent() {
        // Gson always writes primitive fields (even at their zero/false default) but omits
        // null reference fields - id/name/contentType/digest are all unset here.
        val gsonWrittenJson = """[{"length":0,"isStub":false,"revpos":0}]"""

        val restored = converters.toAttachmentList(gsonWrittenJson)

        assertEquals(1, restored?.size)
        assertNull(restored?.get(0)?.id)
        assertNull(restored?.get(0)?.name)
        assertEquals(0L, restored?.get(0)?.length)
        assertEquals(false, restored?.get(0)?.isStub)
    }

    @Test
    fun testAttachmentListDecodesPriorGsonOutput_withEveryFieldPopulated() {
        val gsonWrittenJson =
            """[{"id":"a1","name":"file1.pdf","contentType":"application/pdf","length":1024,"digest":"md5-abc","isStub":true,"revpos":2}]"""

        val restored = converters.toAttachmentList(gsonWrittenJson)?.single()

        assertEquals("a1", restored?.id)
        assertEquals("file1.pdf", restored?.name)
        assertEquals("application/pdf", restored?.contentType)
        assertEquals(1024L, restored?.length)
        assertEquals("md5-abc", restored?.digest)
        assertEquals(true, restored?.isStub)
        assertEquals(2, restored?.revpos)
    }
}
