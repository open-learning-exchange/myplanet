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
}
