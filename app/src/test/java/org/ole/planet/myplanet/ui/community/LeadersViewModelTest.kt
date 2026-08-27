package org.ole.planet.myplanet.ui.community

import android.app.Application
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.utils.MainDispatcherRule
import org.ole.planet.myplanet.utils.TestDispatcherProvider
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class LeadersViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    private val sharedPrefManager: SharedPrefManager = mockk(relaxed = true)

    @Test
    fun `loadLeaders sets an empty list when community leaders string is empty`() = runTest(testDispatcher) {
        every { sharedPrefManager.getCommunityLeaders() } returns ""

        val viewModel = LeadersViewModel(sharedPrefManager, TestDispatcherProvider(testDispatcher))
        advanceUntilIdle()

        val leaders = viewModel.leaders.value
        assertEquals(0, leaders?.size)
        assertTrue(leaders != null && leaders.isEmpty())
    }

    @Test
    fun `loadLeaders parses leaders json into user entities when the string is non-empty`() = runTest(testDispatcher) {
        val leadersJson = """
            {
              "docs": [
                {
                  "_id": "user123",
                  "name": "john_doe",
                  "firstName": "John",
                  "lastName": "Doe",
                  "email": "john@example.com"
                }
              ]
            }
        """.trimIndent()
        every { sharedPrefManager.getCommunityLeaders() } returns leadersJson

        val viewModel = LeadersViewModel(sharedPrefManager, TestDispatcherProvider(testDispatcher))
        advanceUntilIdle()

        val leaders = viewModel.leaders.value
        assertEquals(1, leaders?.size)
        val leader = leaders!!.first()
        assertEquals("user123", leader.id)
        assertEquals("john_doe", leader.name)
        assertEquals("John", leader.firstName)
        assertEquals("Doe", leader.lastName)
        assertEquals("john@example.com", leader.email)
    }
}
