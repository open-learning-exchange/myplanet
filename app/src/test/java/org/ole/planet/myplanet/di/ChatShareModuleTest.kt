package org.ole.planet.myplanet.di

import io.mockk.mockk
import org.junit.Assert.assertSame
import org.junit.Test
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.repository.VoicesRepository

class ChatShareModuleTest {

    @Test
    fun `provideTeamsShareTargetsRepository returns the same teamsRepository instance`() {
        val mockTeamsRepository = mockk<TeamsRepository>()
        val result = ChatShareModule.provideTeamsShareTargetsRepository(mockTeamsRepository)
        assertSame(mockTeamsRepository, result)
    }

    @Test
    fun `provideVoicesShareRepository returns the same voicesRepository instance`() {
        val mockVoicesRepository = mockk<VoicesRepository>()
        val result = ChatShareModule.provideVoicesShareRepository(mockVoicesRepository)
        assertSame(mockVoicesRepository, result)
    }
}
