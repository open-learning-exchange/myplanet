package org.ole.planet.myplanet.utils

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class CrashLogStoreTest {

    private lateinit var context: Context
    private lateinit var pendingDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        pendingDir = File(context.filesDir, "pending_logs")
        pendingDir.deleteRecursively()
    }

    @Test
    fun `save writes a file and loadPendingLogs returns it`() {
        val file = CrashLogStore.save(context, "anr", "stack trace body")

        assertNotNull(file)
        assertTrue(file!!.exists())

        val pending = CrashLogStore.loadPendingLogs(context)
        assertEquals(1, pending.size)
        assertEquals("anr", pending[0].type)
        assertEquals("stack trace body", pending[0].error)
        assertNotNull(pending[0].time.toLongOrNull())
    }

    @Test
    fun `type containing underscores survives the filename round trip`() {
        CrashLogStore.save(context, "my_custom_type", "err")

        val pending = CrashLogStore.loadPendingLogs(context)
        assertEquals(1, pending.size)
        assertEquals("my_custom_type", pending[0].type)
    }

    @Test
    fun `loadPendingLogs skips malformed files`() {
        pendingDir.mkdirs()
        File(pendingDir, "not-a-log.txt").writeText("junk")
        File(pendingDir, "noseparator.log").writeText("junk")
        File(pendingDir, "nottime_crash.log").writeText("junk")

        assertTrue(CrashLogStore.loadPendingLogs(context).isEmpty())
    }

    @Test
    fun `save stops accepting files once the cap is reached`() {
        pendingDir.mkdirs()
        repeat(20) { i ->
            File(pendingDir, "${1000L + i}_crash.log").writeText("e$i")
        }

        assertNull(CrashLogStore.save(context, "crash", "one too many"))
        assertEquals(20, CrashLogStore.loadPendingLogs(context).size)
    }

    @Test
    fun `loadPendingLogs on missing directory returns empty list`() {
        assertTrue(CrashLogStore.loadPendingLogs(context).isEmpty())
    }
}
