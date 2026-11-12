package org.ole.planet.myplanet.base

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.ole.planet.myplanet.ui.dashboard.DashboardUiState

class SimpleLifecycleOwner : LifecycleOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    fun setCurrentState(state: Lifecycle.State) {
        lifecycleRegistry.currentState = state
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class BaseDashboardFragmentTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `collection starts at STARTED, stops below it, resumes with the latest value, and exactly one collector per slice exists`() = testScope.runTest {
        val lifecycleOwner = SimpleLifecycleOwner()
        lifecycleOwner.setCurrentState(Lifecycle.State.INITIALIZED)

        var libraryCollections = 0
        var coursesCollections = 0
        var teamsCollections = 0
        var offlineLoginsCollections = 0
        var newsCollections = 0

        val uiStateFlow = MutableStateFlow(DashboardUiState())
        val privateImageUrlsFlow = MutableStateFlow<List<String>>(emptyList())

        // Simulating the observeUiState behavior
        val job = lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    uiStateFlow
                        .map { it.library }
                        .distinctUntilChanged()
                        .collect {
                            libraryCollections++
                        }
                }
                launch {
                    uiStateFlow
                        .map { it.courses }
                        .distinctUntilChanged()
                        .collect {
                            coursesCollections++
                        }
                }
                launch {
                    uiStateFlow
                        .map { it.teams }
                        .distinctUntilChanged()
                        .collect {
                            teamsCollections++
                        }
                }
                launch {
                    uiStateFlow
                        .map { it.fullName to it.offlineLogins }
                        .distinctUntilChanged()
                        .collect {
                            offlineLoginsCollections++
                        }
                }
                launch {
                    privateImageUrlsFlow.collect {
                        newsCollections++
                    }
                }
            }
        }

        advanceUntilIdle()

        // INITIALIZED state -> 0 collections
        assertEquals(0, libraryCollections)
        assertEquals(0, newsCollections)

        // Move to STARTED state -> Should collect 1 time
        lifecycleOwner.setCurrentState(Lifecycle.State.STARTED)
        advanceUntilIdle()

        assertEquals(1, libraryCollections)
        assertEquals(1, coursesCollections)
        assertEquals(1, teamsCollections)
        assertEquals(1, offlineLoginsCollections)
        assertEquals(1, newsCollections)

        // Emit new value while STARTED
        uiStateFlow.value = DashboardUiState(fullName = "Test")
        privateImageUrlsFlow.value = listOf("url")
        advanceUntilIdle()

        assertEquals(2, offlineLoginsCollections) // fullName changed
        assertEquals(1, libraryCollections) // didn't change
        assertEquals(2, newsCollections) // emitted

        // Move BELOW started -> should stop collecting
        lifecycleOwner.setCurrentState(Lifecycle.State.CREATED)
        advanceUntilIdle()

        // Emit new value while STOPPED
        uiStateFlow.value = DashboardUiState(fullName = "Test 2")
        privateImageUrlsFlow.value = listOf("url2")
        advanceUntilIdle()

        // Counts shouldn't change
        assertEquals(2, offlineLoginsCollections)
        assertEquals(2, newsCollections)

        // Move back to STARTED -> should resume with the latest value (which is exactly ONE collector per slice)
        lifecycleOwner.setCurrentState(Lifecycle.State.STARTED)
        advanceUntilIdle()

        // Resumes with latest values
        assertEquals(3, offlineLoginsCollections)
        assertEquals(2, libraryCollections) // Latest library value collected again upon restart
        assertEquals(2, coursesCollections) // Latest courses value collected again upon restart
        assertEquals(2, teamsCollections) // Latest teams value collected again upon restart
        assertEquals(3, newsCollections) // Latest url value collected again upon restart

        job.cancel()
    }
}
