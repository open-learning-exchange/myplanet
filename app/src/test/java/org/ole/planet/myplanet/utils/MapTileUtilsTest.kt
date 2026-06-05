package org.ole.planet.myplanet.utils

import android.app.Application
import android.content.Context
import android.content.res.AssetManager
import android.os.Environment
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowEnvironment
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = Application::class)
class MapTileUtilsTest {

    private lateinit var context: Context
    private lateinit var assetManager: AssetManager

    @Before
    fun setUp() {
        ShadowEnvironment.setExternalStorageState(Environment.MEDIA_MOUNTED)

        context = mockk<Context>()
        assetManager = mockk<AssetManager>()
        every { context.assets } returns assetManager
    }

    @After
    fun tearDown() {
        val osmdroidDir = File(Environment.getExternalStorageDirectory(), "osmdroid")
        if (osmdroidDir.exists()) {
            osmdroidDir.deleteRecursively()
        }
    }

    @Test
    fun copyAssets_copiesFilesSuccessfully() {
        val dhulikhelContent = "dhulikhel content".toByteArray()
        val somaliaContent = "somalia content".toByteArray()

        every { assetManager.open("dhulikhel.mbtiles") } returns ByteArrayInputStream(dhulikhelContent)
        every { assetManager.open("somalia.mbtiles") } returns ByteArrayInputStream(somaliaContent)

        val osmdroidDir = File(Environment.getExternalStorageDirectory(), "osmdroid")
        osmdroidDir.mkdirs()

        MapTileUtils.copyAssets(context)

        val dhulikhelFile = File(osmdroidDir, "dhulikhel.mbtiles")
        val somaliaFile = File(osmdroidDir, "somalia.mbtiles")

        assertTrue(dhulikhelFile.exists())
        assertTrue(somaliaFile.exists())
        assertArrayEquals(dhulikhelContent, dhulikhelFile.readBytes())
        assertArrayEquals(somaliaContent, somaliaFile.readBytes())
    }

    @Test
    fun copyAssets_handlesExceptionGracefully() {
        every { assetManager.open(any()) } throws IOException("Mocked exception")

        // This should not throw an exception as it is caught inside the method
        MapTileUtils.copyAssets(context)

        // We can verify that no files were written
        val osmdroidDir = File(Environment.getExternalStorageDirectory(), "osmdroid")
        assertTrue(!osmdroidDir.exists() || osmdroidDir.listFiles()?.isEmpty() == true)
    }
}
