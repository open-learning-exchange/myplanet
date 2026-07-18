package org.ole.planet.myplanet.data

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.util.concurrent.Callable
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.ole.planet.myplanet.data.room.AppDatabase
import org.ole.planet.myplanet.utils.DispatcherProvider

@OptIn(ExperimentalCoroutinesApi::class)
class DatabaseServiceTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val dispatcherProvider = object : DispatcherProvider {
        override val main = testDispatcher
        override val io = testDispatcher
        override val default = testDispatcher
        override val unconfined = testDispatcher
    }

    @Test
    fun `withRoomAsync returns operation result`() = runTest(testDispatcher) {
        val room = mockk<AppDatabase>(relaxed = true)
        val service = DatabaseService(dispatcherProvider, room)

        val result = service.withRoomAsync { database ->
            assertEquals(room, database)
            "success"
        }

        assertEquals("success", result)
    }

    @Test
    fun `executeRoomTransactionAsync runs transaction`() = runTest(testDispatcher) {
        val room = mockk<AppDatabase>(relaxed = true)
        every { room.runInTransaction(any<Callable<String>>()) } answers { firstArg<Callable<String>>().call() }
        val service = DatabaseService(dispatcherProvider, room)

        val result = service.executeRoomTransactionAsync { database ->
            assertEquals(room, database)
            "done"
        }

        assertEquals("done", result)
        verify(exactly = 1) { room.runInTransaction(any<Callable<String>>()) }
    }

    @Test
    fun `clearAll delegates to room clearAllTables`() = runTest(testDispatcher) {
        val room = mockk<AppDatabase>(relaxed = true)
        val service = DatabaseService(dispatcherProvider, room)

        service.clearAll()

        verify(exactly = 1) { room.clearAllTables() }
    }
}
