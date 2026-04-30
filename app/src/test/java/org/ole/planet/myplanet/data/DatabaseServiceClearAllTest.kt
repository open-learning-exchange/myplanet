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
    fun `clearAll calls deleteAll on Realm`() = runTest {
        val databaseService = mockk<DatabaseService>()
        val mockRealm = mockk<Realm>(relaxed = true)

        coEvery { databaseService.clearAll() } answers { callOriginal() }

        coEvery { databaseService.executeTransactionAsync(any()) } answers {
            val transaction = firstArg<(Realm) -> Unit>()
            transaction(mockRealm)
        }

        databaseService.clearAll()

        verify { mockRealm.deleteAll() }
    }
}
