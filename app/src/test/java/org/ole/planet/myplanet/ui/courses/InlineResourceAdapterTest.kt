package org.ole.planet.myplanet.ui.courses

import android.app.Application
import android.content.Context
import android.view.View
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.ole.planet.myplanet.model.MyLibrary
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.ResourcesPreviewLoader
import org.ole.planet.myplanet.utils.UrlUtils
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class InlineResourceAdapterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val mainTestDispatcher = StandardTestDispatcher()
    private val ioTestDispatcher = StandardTestDispatcher()

    private val testDispatcherProvider = object : DispatcherProvider {
        override val main: CoroutineDispatcher = mainTestDispatcher
        override val mainImmediate: CoroutineDispatcher = mainTestDispatcher
        override val io: CoroutineDispatcher = ioTestDispatcher
        override val default: CoroutineDispatcher = ioTestDispatcher
        override val unconfined: CoroutineDispatcher = ioTestDispatcher
    }

    private lateinit var previewLoader: ResourcesPreviewLoader
    private lateinit var adapter: InlineResourceAdapter
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        context.setTheme(com.google.android.material.R.style.Theme_MaterialComponents)

        mockkObject(UrlUtils)
        every { UrlUtils.getUrl(any()) } returns "http://example.com/test.pdf"

        previewLoader = mockk(relaxed = true)
        adapter = InlineResourceAdapter(
            previewLoader = previewLoader,
            dispatcherProvider = testDispatcherProvider,
            onResourceClick = {}
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `test bind text resource calls previewLoader on IO dispatcher and updates UI`() = runTest {
        val oleDir = tempFolder.newFolder("ole", "res1")
        val file = File(oleDir, "sample.txt")
        file.writeText("Hello world preview")

        coEvery { previewLoader.getTextPreview(any()) } returns "Sample text preview content"

        val resource = MyLibrary().apply {
            id = "res1"
            _id = "res1"
            _rev = "1"
            downloadedRev = "1"
            title = "Test Resource"
            resourceLocalAddress = "sample.txt"
            resourceOffline = true
            mediaType = "text/plain"
        }

        val adapterExternalFilesDir = InlineResourceAdapter::class.java.getDeclaredField("externalFilesDir")
        adapterExternalFilesDir.isAccessible = true
        adapterExternalFilesDir.set(adapter, tempFolder.root)

        adapter.submitList(listOf(resource))
        mainTestDispatcher.scheduler.advanceUntilIdle()

        val parent = LinearLayout(context)
        val holder = adapter.onCreateViewHolder(parent, 0)

        adapter.onBindViewHolder(holder, 0)

        assertEquals("Test Resource", holder.binding.tvResourceTitle.text.toString())

        // Step 1: Advance main dispatcher -> coroutine launches up to withContext(dispatcherProvider.io)
        mainTestDispatcher.scheduler.advanceUntilIdle()

        // Verify previewLoader has NOT been called yet because IO dispatcher has not run
        coVerify(exactly = 0) { previewLoader.getTextPreview(any()) }

        // Step 2: Advance IO dispatcher -> file stat executes on IO
        ioTestDispatcher.scheduler.advanceUntilIdle()

        // Step 3: Advance main dispatcher -> coroutine resumes on main thread, calls previewLoader and updates UI
        mainTestDispatcher.scheduler.advanceUntilIdle()

        // Verify previewLoader was invoked
        coVerify(exactly = 1) { previewLoader.getTextPreview(any()) }

        assertEquals(View.VISIBLE, holder.binding.tvTextPreview.visibility)
        assertEquals("Sample text preview content", holder.binding.tvTextPreview.text.toString())
    }

    @Test
    fun `onViewRecycled cancels previous preview job`() = runTest {
        val oleDir = tempFolder.newFolder("ole", "res2")
        val file = File(oleDir, "sample.mp4")
        file.writeText("video data")

        val resource = MyLibrary().apply {
            id = "res2"
            _id = "res2"
            _rev = "1"
            downloadedRev = "1"
            title = "Video Resource"
            resourceLocalAddress = "sample.mp4"
            resourceOffline = true
            mediaType = "video/mp4"
        }

        val adapterExternalFilesDir = InlineResourceAdapter::class.java.getDeclaredField("externalFilesDir")
        adapterExternalFilesDir.isAccessible = true
        adapterExternalFilesDir.set(adapter, tempFolder.root)

        adapter.submitList(listOf(resource))
        mainTestDispatcher.scheduler.advanceUntilIdle()

        val parent = LinearLayout(context)
        val holder = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(holder, 0)

        mainTestDispatcher.scheduler.advanceUntilIdle()

        val job = holder.previewJob
        assertNotNull("previewJob should be set when binding", job)
        assertEquals(false, job?.isCancelled)

        adapter.onViewRecycled(holder)

        assertEquals(true, job?.isCancelled)
        assertNull(holder.previewJob)
    }

    @Test
    fun `isResourceOffline short-circuits and does not evaluate UrlUtils getUrl or disk check`() = runTest {
        val resource = MyLibrary().apply {
            id = "res3"
            resourceLocalAddress = "sample.pdf"
            resourceOffline = true
        }

        adapter.submitList(listOf(resource))
        mainTestDispatcher.scheduler.advanceUntilIdle()

        val parent = LinearLayout(context)
        val holder = adapter.onCreateViewHolder(parent, 0)

        // Bind holder
        adapter.onBindViewHolder(holder, 0)

        // Run main dispatcher (coroutine launches and checks resourceOffline)
        mainTestDispatcher.scheduler.advanceUntilIdle()

        // UrlUtils.getUrl should NOT be called at all because resourceOffline is true
        coVerify(exactly = 0) { UrlUtils.getUrl(any()) }

        // Download status updated to completed/downloaded
        assertEquals(View.GONE, holder.binding.pbDownload.visibility)
        assertEquals(View.VISIBLE, holder.binding.ivStatus.visibility)
    }

    @Test
    fun `online resource checks file existence on IO dispatcher`() = runTest {
        val resource = MyLibrary().apply {
            id = "res4"
            resourceLocalAddress = "sample.pdf"
            resourceOffline = false
        }

        adapter.submitList(listOf(resource))
        mainTestDispatcher.scheduler.advanceUntilIdle()

        val parent = LinearLayout(context)
        val holder = adapter.onCreateViewHolder(parent, 0)

        // Immediately after bind, holder is set synchronously to loading / not-downloaded state
        adapter.onBindViewHolder(holder, 0)
        assertEquals(View.VISIBLE, holder.binding.pbDownload.visibility)
        assertEquals(View.GONE, holder.binding.ivStatus.visibility)

        // Advance main dispatcher -> coroutine executes up to withContext(dispatcherProvider.io)
        mainTestDispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = 0) { UrlUtils.getUrl(any()) }

        // Advance IO dispatcher -> UrlUtils.getUrl / FileUtils.checkFileExist executes on IO
        ioTestDispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = 1) { UrlUtils.getUrl(resource) }
    }
}
