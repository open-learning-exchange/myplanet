package org.ole.planet.myplanet.data

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import io.realm.Realm
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DatabaseServiceClearAllTest {

    @Test
    fun `clearAll calls executeTransactionAsync with deleteAll`() = runTest {
        // Mocking DatabaseService fully avoids calling Realm.init() in the constructor,
        // which crashes JVM tests that lack a native Realm library.
        val databaseService = mockk<DatabaseService>()
        val mockRealm = mockk<Realm>(relaxed = true)

        coEvery { databaseService.clearAll() } answers { callOriginal() }

        // When clearAll calls executeTransactionAsync, immediately invoke the passed lambda
        // with our mocked Realm to verify its contents.
        coEvery { databaseService.executeTransactionAsync(any()) } answers {
            val transaction = firstArg<(Realm) -> Unit>()
            transaction(mockRealm)
        }

        databaseService.clearAll()

        coVerify { databaseService.executeTransactionAsync(any()) }
        verify { mockRealm.deleteAll() }
    }
}
