package org.ole.planet.myplanet.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test
import org.ole.planet.myplanet.model.ChatMessage

class ChatAdapterTest {

    @Test
    fun testChatMessage_properties() {
        val message1 = ChatMessage("Hello", ChatMessage.QUERY)
        val message2 = ChatMessage("Hi there", ChatMessage.RESPONSE)

        val list = listOf(message1, message2)
        assertEquals(2, list.size)
        assertEquals("Hello", list[0].message)
        assertEquals(ChatMessage.QUERY, list[0].viewType)
        assertEquals("Hi there", list[1].message)
        assertEquals(ChatMessage.RESPONSE, list[1].viewType)
    }
}
