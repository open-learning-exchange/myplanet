package org.ole.planet.myplanet.repository

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.utils.DispatcherProvider

@ExperimentalCoroutinesApi
class HealthRepositoryImplTest {

    private lateinit var repository: HealthRepositoryImpl
    private val dispatcherProvider: DispatcherProvider = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val databaseService: DatabaseService = mockk(relaxed = true)

    @Before
    fun setUp() {
        every { dispatcherProvider.default } returns testDispatcher
        repository = HealthRepositoryImpl(
            databaseService,
            UnconfinedTestDispatcher(),
            dispatcherProvider
        )
    }

    @Test
    fun initHealth_uses_dispatcherProvider_default() = testScope.runTest {
        val result = repository.initHealth()
        advanceUntilIdle()
        assertNotNull(result)
        io.mockk.verify { dispatcherProvider.default }
    }
}
