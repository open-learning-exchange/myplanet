package org.ole.planet.myplanet

import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.services.SharedPrefManager
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MainApplicationAutoSyncTest {

    private lateinit var mainApplication: MainApplication
    private lateinit var mockSharedPrefManager: SharedPrefManager

    @Before
    fun setup() {
        val app = ApplicationProvider.getApplicationContext<MainApplication>()
        mainApplication = spyk(app, recordPrivateCalls = true)

        mockSharedPrefManager = mockk(relaxed = true)
        mainApplication.sharedPrefManager = mockSharedPrefManager
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `applyAutoSyncSettings schedules work when auto sync is true`() {
        // Arrange
        val interval = 1000
        every { mockSharedPrefManager.getAutoSync() } returns true
        every { mockSharedPrefManager.getAutoSyncInterval() } returns interval
        every { mainApplication["scheduleAutoSyncWork"](any<Int>()) } returns Unit

        // Act
        mainApplication.applyAutoSyncSettings()

        // Assert
        verify { mainApplication["scheduleAutoSyncWork"](interval) }
    }

    @Test
    fun `applyAutoSyncSettings cancels work when auto sync is false`() {
        // Arrange
        every { mockSharedPrefManager.getAutoSync() } returns false
        every { mainApplication["cancelAutoSyncWork"]() } returns Unit

        // Act
        mainApplication.applyAutoSyncSettings()

        // Assert
        verify { mainApplication["cancelAutoSyncWork"]() }
    }
}
