package org.ole.planet.myplanet.ui.chat

import org.junit.Test
import kotlin.test.assertFalse

class ChatDetailFragmentInjectionTest {
    @Test
    fun verifyNoDirectApiServiceDependency() {
        val fields = ChatDetailFragment::class.java.declaredFields
        val hasApiService = fields.any { it.type.simpleName == "ChatApiService" }
        assertFalse(hasApiService, "ChatDetailFragment should not directly depend on ChatApiService. Use ChatRepository instead.")
    }
}
