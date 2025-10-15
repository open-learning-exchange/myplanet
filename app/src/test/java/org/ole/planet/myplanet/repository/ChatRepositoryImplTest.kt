package org.ole.planet.myplanet.repository

import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Test
import sun.misc.Unsafe

class ChatRepositoryImplTest {
    @Test
    fun getChatHistoryForUser_nullUsername_returnsEmptyList() = runTest {
        val repository = createUninitializedChatRepository()

        val result = repository.getChatHistoryForUser(null)

        assertTrue(result.isEmpty())
    }

    @Test
    fun getChatHistoryForUser_emptyUsername_returnsEmptyList() = runTest {
        val repository = createUninitializedChatRepository()

        val result = repository.getChatHistoryForUser("")

        assertTrue(result.isEmpty())
    }

    @Test
    fun getPlanetNewsMessages_nullPlanetCode_returnsEmptyList() = runTest {
        val repository = createUninitializedChatRepository()

        val result = repository.getPlanetNewsMessages(null)

        assertTrue(result.isEmpty())
    }

    private fun createUninitializedChatRepository(): ChatRepositoryImpl {
        val field = Unsafe::class.java.getDeclaredField("theUnsafe")
        field.isAccessible = true
        val unsafe = field.get(null) as Unsafe
        return unsafe.allocateInstance(ChatRepositoryImpl::class.java) as ChatRepositoryImpl
    }
}
