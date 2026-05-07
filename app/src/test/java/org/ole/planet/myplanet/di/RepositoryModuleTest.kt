package org.ole.planet.myplanet.di

import org.junit.Assert.assertEquals
import org.junit.Test
import org.ole.planet.myplanet.repository.UserRepositoryImpl

class RepositoryModuleTest {

    @Test
    fun `verify UserRepository dual-binding intent`() {
        val moduleClass = RepositoryModule::class.java

        val bindUserRepoMethod = moduleClass.getDeclaredMethod("bindUserRepository", UserRepositoryImpl::class.java)
        val bindUserSyncRepoMethod = moduleClass.getDeclaredMethod("bindUserSyncRepository", UserRepositoryImpl::class.java)

        // Assert both bindings target the same concrete implementation class
        assertEquals(UserRepositoryImpl::class.java, bindUserRepoMethod.parameterTypes[0])
        assertEquals(UserRepositoryImpl::class.java, bindUserSyncRepoMethod.parameterTypes[0])
    }
}
