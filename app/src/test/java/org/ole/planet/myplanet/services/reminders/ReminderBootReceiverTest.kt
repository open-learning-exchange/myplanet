package org.ole.planet.myplanet.services.reminders

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.impl.WorkManagerImpl
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.O], application = Application::class)
class ReminderBootReceiverTest {

    private lateinit var receiver: ReminderBootReceiver
    private lateinit var context: Context
    private val workManagerImpl: WorkManagerImpl = mockk(relaxed = true)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        mockkStatic(WorkManagerImpl::class)
        every { WorkManagerImpl.getInstance(any()) } returns workManagerImpl

        mockkStatic(WorkManager::class)
        every { WorkManager.getInstance(any()) } returns workManagerImpl

        receiver = ReminderBootReceiver()
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testOnReceive_bootCompleted_enqueuesWork() {
        val intent = Intent(Intent.ACTION_BOOT_COMPLETED)
        receiver.onReceive(context, intent)

        verify { workManagerImpl.enqueue(any<WorkRequest>()) }
    }

    @Test
    fun testOnReceive_packageReplaced_enqueuesWork() {
        val intent = Intent(Intent.ACTION_MY_PACKAGE_REPLACED)
        receiver.onReceive(context, intent)

        verify { workManagerImpl.enqueue(any<WorkRequest>()) }
    }

    @Test
    fun testOnReceive_unrelatedAction_doesNotEnqueueWork() {
        val intent = Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED)
        receiver.onReceive(context, intent)

        verify(exactly = 0) { workManagerImpl.enqueue(any<WorkRequest>()) }
    }
}
