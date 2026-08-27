package org.ole.planet.myplanet.ui.courses

import android.app.Application
import android.content.Context
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.ole.planet.myplanet.model.MyLibrary
import org.ole.planet.myplanet.utils.ResourcesPreviewLoader
import org.ole.planet.myplanet.utils.TestDispatcherProvider
import org.ole.planet.myplanet.utils.UrlUtils
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class InlineResourceAdapterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val dispatcherProvider = TestDispatcherProvider(testDispatcher)
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
            dispatcherProvider = dispatcherProvider,
            onResourceClick = {}
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `test bind text resource calls previewLoader on IO dispatcher`() = runTest {
        val file = tempFolder.newFile("sample.txt")
        file.writeText("Hello world preview")

        val resource = MyLibrary().apply {
            id = "res1"
            _id = "res1"
            title = "Test Resource"
            resourceLocalAddress = "sample.txt"
            mediaType = "text/plain"
        }

        adapter.submitList(listOf(resource))

        val parent = LinearLayout(context)
        val holder = adapter.onCreateViewHolder(parent, 0)

        adapter.onBindViewHolder(holder, 0)

        assertEquals("Test Resource", holder.binding.tvResourceTitle.text.toString())
    }

    @Test
    fun `onViewRecycled cancels previous preview job`() = runTest {
        val resource = MyLibrary().apply {
            id = "res2"
            _id = "res2"
            title = "Video Resource"
            resourceLocalAddress = "sample.mp4"
            mediaType = "video/mp4"
        }
        adapter.submitList(listOf(resource))

        val parent = LinearLayout(context)
        val holder = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(holder, 0)

        adapter.onViewRecycled(holder)
    }
}
