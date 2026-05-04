package org.ole.planet.myplanet.base

import android.text.TextUtils
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.model.Download
import org.ole.planet.myplanet.utils.DialogUtils

@OptIn(ExperimentalCoroutinesApi::class)
class BaseResourceFragmentTest {

    private lateinit var fragment: BaseResourceFragment
    private lateinit var mockPrgDialog: DialogUtils.CustomProgressDialog
    private lateinit var mockLifecycleOwner: LifecycleOwner
    private lateinit var mockActivity: FragmentActivity
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fragment = spyk(object : BaseResourceFragment() {})
        mockPrgDialog = mockk(relaxed = true)
        fragment.prgDialog = mockPrgDialog

        mockLifecycleOwner = mockk(relaxed = true)
        val lifecycleRegistry = LifecycleRegistry(mockLifecycleOwner)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        every { mockLifecycleOwner.lifecycle } returns lifecycleRegistry

        every { fragment.viewLifecycleOwner } returns mockLifecycleOwner

        mockActivity = mockk(relaxed = true)

        every { fragment.activity } returns mockActivity
        every { fragment.requireActivity() } returns mockActivity
        every { fragment.isAdded } returns true
        every { mockActivity.isFinishing } returns false
        every { mockActivity.isDestroyed } returns false

        mockkStatic(TextUtils::class)
        every { TextUtils.isEmpty(any()) } answers {
            val str = it.invocation.args[0] as? CharSequence
            str == null || str.length == 0
        }
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `showProgressDialog shows dialog when fragment is active`() = runTest {
        fragment.showProgressDialog()

        verify(exactly = 1) { mockPrgDialog.setIndeterminateMode(true) }
        verify(exactly = 1) { mockPrgDialog.show() }
    }

    @Test
    fun `showProgressDialog does not show dialog when fragment is not added`() = runTest {
        every { fragment.isAdded } returns false

        fragment.showProgressDialog()

        verify(exactly = 0) { mockPrgDialog.setIndeterminateMode(any()) }
        verify(exactly = 0) { mockPrgDialog.show() }
    }

    @Test
    fun `showProgressDialog does not show dialog when activity is finishing`() = runTest {
        every { mockActivity.isFinishing } returns true

        fragment.showProgressDialog()

        verify(exactly = 0) { mockPrgDialog.setIndeterminateMode(any()) }
        verify(exactly = 0) { mockPrgDialog.show() }
    }

    @Test
    fun `showProgressDialog does not show dialog when activity is destroyed`() = runTest {
        every { mockActivity.isDestroyed } returns true

        fragment.showProgressDialog()

        verify(exactly = 0) { mockPrgDialog.setIndeterminateMode(any()) }
        verify(exactly = 0) { mockPrgDialog.show() }
    }

    @Test
    fun `showProgressDialog does not show dialog when activity is null`() = runTest {
        every { fragment.activity } returns null

        fragment.showProgressDialog()

        verify(exactly = 0) { mockPrgDialog.setIndeterminateMode(any()) }
        verify(exactly = 0) { mockPrgDialog.show() }
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
}
