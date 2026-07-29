package org.ole.planet.myplanet.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.data.room.AppDatabase
import org.ole.planet.myplanet.utils.DispatcherProvider

class DatabaseService(
    private val dispatcherProvider: DispatcherProvider,
    val room: AppDatabase,
) {
    val ioDispatcher: CoroutineDispatcher = dispatcherProvider.io

    suspend fun <T> withRoomAsync(operation: suspend (AppDatabase) -> T): T {
        return withContext(ioDispatcher) {
            operation(room)
        }
    }

    suspend fun <T> executeRoomTransactionAsync(operation: (AppDatabase) -> T): T {
        return withContext(ioDispatcher) {
            room.runInTransaction<T> {
                operation(room)
            }
        }
    }

    suspend fun clearAll() {
        withContext(ioDispatcher) {
            room.clearAllTables()
        }
    }
}
