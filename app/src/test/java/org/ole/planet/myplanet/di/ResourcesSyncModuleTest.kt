package org.ole.planet.myplanet.di

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertSame
import org.junit.Test
import org.ole.planet.myplanet.repository.ResourcesRepository

class ResourcesSyncModuleTest {

    @Test
    fun provideResourcesSyncRepository_returnsSameInstance() = runTest {
        val mockRepo = mockk<ResourcesRepository>()
        coEvery { mockRepo.batchInsertMyLibrary(any(), any()) } returns 0

        val syncRepo = ResourcesSyncModule.provideResourcesSyncRepository(mockRepo)

        assertSame(mockRepo, syncRepo)

        syncRepo.batchInsertMyLibrary("s", emptyList())

        coVerify(exactly = 1) { mockRepo.batchInsertMyLibrary("s", emptyList()) }
    }
}
