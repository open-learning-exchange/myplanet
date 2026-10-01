package org.ole.planet.myplanet.di

import io.mockk.mockk
import org.junit.Assert.assertSame
import org.junit.Test
import org.ole.planet.myplanet.repository.UserRepository

class UserLookupModuleTest {
    @Test
    fun provideUserLookupRepository_returnsSameUserRepositoryInstance() {
        val mockUserRepository = mockk<UserRepository>()
        val result = UserLookupModule.provideUserLookupRepository(mockUserRepository)
        assertSame(mockUserRepository, result)
    }
}
