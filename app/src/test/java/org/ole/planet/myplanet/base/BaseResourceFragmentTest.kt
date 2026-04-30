package org.ole.planet.myplanet.base

import android.text.TextUtils
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test
import androidx.fragment.app.FragmentActivity
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.utils.Utilities
import org.ole.planet.myplanet.model.Download
import org.ole.planet.myplanet.utils.DialogUtils

class BaseResourceFragmentTest {

    private lateinit var fragment: BaseResourceFragment
    private lateinit var mockPrgDialog: DialogUtils.CustomProgressDialog

    @Before
    fun setup() {
        fragment = spyk(object : BaseResourceFragment() {})
        mockPrgDialog = mockk(relaxed = true)
        fragment.prgDialog = mockPrgDialog
        mockkStatic(TextUtils::class)
        every { TextUtils.isEmpty(any()) } answers {
            val str = it.invocation.args[0] as? CharSequence
            str == null || str.length == 0
        }
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `setProgress updates progress only when no fileName and not complete`() {
        val download = Download().apply {
            progress = 50
            fileName = ""
            completeAll = false
        }

        fragment.setProgress(download)

        verify { mockPrgDialog.setProgress(50) }
        verify(exactly = 0) { mockPrgDialog.setTitle(any()) }
        verify(exactly = 0) { fragment.onDownloadComplete() }
    }

    @Test
    fun `setProgress updates title when fileName is present`() {
        val download = Download().apply {
            progress = 75
            fileName = "test_file.txt"
            completeAll = false
        }

        fragment.setProgress(download)

        verify { mockPrgDialog.setProgress(75) }
        verify { mockPrgDialog.setTitle("test_file.txt") }
        verify(exactly = 0) { fragment.onDownloadComplete() }
    }

    @Test
    fun `setProgress calls onDownloadComplete when completeAll is true`() {
        val download = Download().apply {
            progress = 100
            fileName = "test_file.txt"
            completeAll = true
        }

        every { fragment.onDownloadComplete() } just Runs

        fragment.setProgress(download)

        verify { mockPrgDialog.setProgress(100) }
        verify { mockPrgDialog.setTitle("test_file.txt") }
        verify { fragment.onDownloadComplete() }
    }
    @Test
    fun `showNotConnectedToast shows toast when fragment is active`() {
        val mockActivity = mockk<FragmentActivity>(relaxed = true)
        every { fragment.isAdded } returns true
        every { fragment.activity } returns mockActivity
        every { fragment.requireActivity() } returns mockActivity
        every { fragment.context } returns mockActivity
        every { mockActivity.isFinishing } returns false
        every { mockActivity.isDestroyed } returns false

        every { fragment.requireContext() } returns mockActivity
        every { fragment.resources } returns mockk(relaxed = true)

        every { fragment.getString(R.string.device_not_connected_to_planet) } returns "Device not connected to planet."

        mockkStatic(Utilities::class)
        every { Utilities.toast(any(), any()) } just Runs

        fragment.showNotConnectedToast()

        verify { Utilities.toast(mockActivity, "Device not connected to planet.") }
    }

    @Test
    fun `showNotConnectedToast does nothing when fragment is not active`() {
        every { fragment.isAdded } returns false
        every { fragment.activity } returns null

        mockkStatic(Utilities::class)

        fragment.showNotConnectedToast()

        verify(exactly = 0) { Utilities.toast(any(), any()) }
    }
}
