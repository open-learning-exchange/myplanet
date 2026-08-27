package org.ole.planet.myplanet.ui.chat

import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import org.junit.Test

class ChatHistoryFragmentRefreshTest {
    @Test
    fun `onResume is not overridden so history loads only once on open`() {
        // onResume always follows onViewCreated, which already calls refreshChatHistory();
        // a resume-time override would run a duplicate full load on every screen open.
        assertFailsWith<NoSuchMethodException> {
            ChatHistoryFragment::class.java.getDeclaredMethod("onResume")
        }
    }

    @Test
    fun `refreshChatHistory entry point is kept for real-change triggers`() {
        val method = ChatHistoryFragment::class.java.getDeclaredMethod("refreshChatHistory")
        assertNotNull(method)
    }
}
