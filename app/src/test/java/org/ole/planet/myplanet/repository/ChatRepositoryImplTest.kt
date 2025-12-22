package org.ole.planet.myplanet.repository

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatRepositoryImplTest {
    private fun createRepository(): ChatRepositoryImpl {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val field = unsafeClass.getDeclaredField("theUnsafe")
        try {
            field.isAccessible = true
        } catch (exception: Exception) {
            throw IllegalStateException("Unable to access Unsafe", exception)
        }
        val unsafe = field.get(null)
        val allocateInstance = unsafeClass.getMethod("allocateInstance", Class::class.java)
        return allocateInstance.invoke(unsafe, ChatRepositoryImpl::class.java) as ChatRepositoryImpl
    }

    @Test
    fun getChatHistoryForUserReturnsEmptyWhenUserNameIsNull() = runBlocking {
        val repository = createRepository()
        val result = repository.getChatHistoryForUser(null)
        assertTrue(result.isEmpty())
    }

    @Test
    fun getPlanetNewsMessagesReturnsEmptyWhenPlanetCodeIsNull() = runBlocking {
        val repository = createRepository()
        val result = repository.getPlanetNewsMessages(null)
        assertTrue(result.isEmpty())
    }
}
