package org.ole.planet.myplanet.ui.sync

import com.afollestad.materialdialogs.MaterialDialog
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.ole.planet.myplanet.databinding.DialogServerUrlBinding
import org.ole.planet.myplanet.repository.ConfigurationsRepository
import org.ole.planet.myplanet.repository.ConfigurationsRepository.ConfigurationResult
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.utils.ServerConfigUtils

class SyncConfigurationCoordinatorTest {

    private val configurationsRepository: ConfigurationsRepository = mockk()
    private val prefData: SharedPrefManager = mockk(relaxed = true)
    private val callback: SyncConfigurationCoordinator.Callback = mockk(relaxed = true)
    private val dialog: MaterialDialog = mockk()
    private val binding: DialogServerUrlBinding = mockk()

    private val coordinator =
        SyncConfigurationCoordinator(configurationsRepository, prefData, callback)

    private val success = ConfigurationResult.Success(
        id = "config1",
        code = "community",
        url = "https://planet.example.org",
        defaultUrl = "https://planet.example.org",
        isAlternativeUrl = false
    )

    @After
    fun tearDown() {
        unmockkObject(ServerConfigUtils)
    }

    private fun stubResult(result: ConfigurationResult) {
        coEvery { configurationsRepository.getMinApk(any(), any()) } returns result
    }

    @Test
    fun `failure reports sync failed and shows the error`() = runTest {
        stubResult(ConfigurationResult.Failure("server unreachable", "https://planet.example.org"))

        coordinator.checkMinApk(this, "url", "pin", CallerContext.SYNC_ACTIVITY, "sync", dialog, binding)
        advanceUntilIdle()

        verifyOrder {
            callback.showProgressDialog()
            callback.dismissProgressDialog()
        }
        verify { callback.setSyncFailed(true) }
        verify { callback.showErrorDialog("server unreachable") }
    }

    @Test
    fun `login caller continues after successful version check`() = runTest {
        stubResult(success)

        coordinator.checkMinApk(this, "url", "pin", CallerContext.LOGIN_ACTIVITY, "sync", null, null)
        advanceUntilIdle()

        verify { callback.setSyncFailed(false) }
        verify { callback.onVersionCheckSuccess() }
        verify(exactly = 0) { callback.onClearDataDialog() }
    }

    @Test
    fun `login caller saves alternative url before continuing`() = runTest {
        mockkObject(ServerConfigUtils)
        every { ServerConfigUtils.saveAlternativeUrl(any(), any(), any()) } returns ""
        every { prefData.getServerPin() } returns "1234"
        stubResult(success.copy(isAlternativeUrl = true))

        coordinator.checkMinApk(this, "url", "pin", CallerContext.LOGIN_ACTIVITY, "sync", null, null)
        advanceUntilIdle()

        verify { ServerConfigUtils.saveAlternativeUrl(success.url, "1234", prefData) }
        verify { callback.onVersionCheckSuccess() }
    }

    @Test
    fun `first sync stores configuration identity and continues`() = runTest {
        every { prefData.getConfigurationId() } returns null
        stubResult(success)

        coordinator.checkMinApk(this, "url", "pin", CallerContext.SYNC_ACTIVITY, "sync", dialog, binding)
        advanceUntilIdle()

        verify { prefData.setConfigurationId("config1") }
        verify { prefData.setCommunityName("community") }
        verify { callback.onContinueSync(dialog, success.url, false, success.defaultUrl) }
    }

    @Test
    fun `sync against the already-configured server continues without re-saving`() = runTest {
        every { prefData.getConfigurationId() } returns "config1"
        stubResult(success)

        coordinator.checkMinApk(this, "url", "pin", CallerContext.SYNC_ACTIVITY, "sync", dialog, binding)
        advanceUntilIdle()

        verify(exactly = 0) { prefData.setConfigurationId(any()) }
        verify { callback.onContinueSync(dialog, success.url, false, success.defaultUrl) }
    }

    @Test
    fun `sync against a different server asks to clear data`() = runTest {
        every { prefData.getConfigurationId() } returns "otherConfig"
        stubResult(success)

        coordinator.checkMinApk(this, "url", "pin", CallerContext.SYNC_ACTIVITY, "sync", dialog, binding)
        advanceUntilIdle()

        verify { callback.onClearDataDialog() }
        verify(exactly = 0) { callback.onContinueSync(any(), any(), any(), any()) }
    }

    @Test
    fun `sync without a dialog does not invoke continue`() = runTest {
        every { prefData.getConfigurationId() } returns null
        stubResult(success)

        coordinator.checkMinApk(this, "url", "pin", CallerContext.SYNC_ACTIVITY, "sync", null, null)
        advanceUntilIdle()

        verify(exactly = 0) { callback.onContinueSync(any(), any(), any(), any()) }
    }

    @Test
    fun `save action with a matching configuration saves and continues`() = runTest {
        every { prefData.getConfigurationId() } returns "config1"
        stubResult(success)

        coordinator.checkMinApk(this, "url", "pin", CallerContext.SYNC_ACTIVITY, "save", dialog, binding)
        advanceUntilIdle()

        verify { callback.onSaveConfigAndContinue(dialog, binding, success.defaultUrl) }
    }

    @Test
    fun `save action against a different server asks to clear data`() = runTest {
        every { prefData.getConfigurationId() } returns "otherConfig"
        stubResult(success)

        coordinator.checkMinApk(this, "url", "pin", CallerContext.SYNC_ACTIVITY, "save", dialog, binding)
        advanceUntilIdle()

        verify { callback.onClearDataDialog() }
        verify(exactly = 0) { callback.onSaveConfigAndContinue(any(), any(), any()) }
    }

    @Test
    fun `save action without a binding does not invoke save`() = runTest {
        every { prefData.getConfigurationId() } returns "config1"
        stubResult(success)

        coordinator.checkMinApk(this, "url", "pin", CallerContext.SYNC_ACTIVITY, "save", dialog, null)
        advanceUntilIdle()

        verify(exactly = 0) { callback.onSaveConfigAndContinue(any(), any(), any()) }
    }
}
