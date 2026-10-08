package org.ole.planet.myplanet.utils

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class AttachmentReaderTest {

    @Test
    fun `null uri returns empty attachment and never calls FileUtils`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val timeProvider = mockk<TimeProvider>()
        val dispatcherProvider = TestDispatcherProvider(StandardTestDispatcher(testScheduler))

        mockkObject(FileUtils)
        try {
            val result = AttachmentReader(context, timeProvider, dispatcherProvider).read(null)
            assertEquals(UriAttachment(null, null), result)
            verify(exactly = 0) { FileUtils.getDisplayName(any(), any(), any()) }
            verify(exactly = 0) { FileUtils.readBytesFromUri(any(), any()) }
        } finally {
            unmockkObject(FileUtils)
        }
    }

    @Test
    fun `non-null uri returns name and bytes from FileUtils`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val uri = mockk<Uri>()
        val timeProvider = mockk<TimeProvider>()
        val dispatcherProvider = TestDispatcherProvider(StandardTestDispatcher(testScheduler))
        val expectedName = "test_image.png"
        val expectedBytes = byteArrayOf(1, 2, 3)

        mockkObject(FileUtils)
        try {
            every { FileUtils.getDisplayName(context, uri, timeProvider) } returns expectedName
            every { FileUtils.readBytesFromUri(context, uri) } returns expectedBytes

            val result = AttachmentReader(context, timeProvider, dispatcherProvider).read(uri)

            assertEquals(UriAttachment(expectedName, expectedBytes), result)
            verify(exactly = 1) { FileUtils.getDisplayName(context, uri, timeProvider) }
            verify(exactly = 1) { FileUtils.readBytesFromUri(context, uri) }
        } finally {
            unmockkObject(FileUtils)
        }
    }

    @Test
    fun `read runs on provider io dispatcher`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val uri = mockk<Uri>()
        val timeProvider = mockk<TimeProvider>()

        var capturedInterceptor: ContinuationInterceptor? = null
        val ioDispatcher = object : CoroutineDispatcher() {
            override fun isDispatchNeeded(context: CoroutineContext): Boolean = true
            override fun dispatch(context: CoroutineContext, block: Runnable) {
                capturedInterceptor = this
                block.run()
            }
        }

        val dispatcherProvider = object : DispatcherProvider {
            override val main: CoroutineDispatcher = StandardTestDispatcher(testScheduler)
            override val mainImmediate: CoroutineDispatcher = StandardTestDispatcher(testScheduler)
            override val io: CoroutineDispatcher = ioDispatcher
            override val default: CoroutineDispatcher = StandardTestDispatcher(testScheduler)
            override val unconfined: CoroutineDispatcher = StandardTestDispatcher(testScheduler)
        }

        val expectedName = "test_image.png"
        val expectedBytes = byteArrayOf(4, 5, 6)

        mockkObject(FileUtils)
        try {
            every { FileUtils.getDisplayName(context, uri, timeProvider) } returns expectedName
            every { FileUtils.readBytesFromUri(context, uri) } returns expectedBytes

            val result = AttachmentReader(context, timeProvider, dispatcherProvider).read(uri)

            assertEquals(UriAttachment(expectedName, expectedBytes), result)
            assertEquals(ioDispatcher, capturedInterceptor)
        } finally {
            unmockkObject(FileUtils)
        }
    }
}
