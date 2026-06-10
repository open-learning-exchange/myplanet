package org.ole.planet.myplanet.utils

import android.app.Application
import android.content.Context
import android.content.res.AssetManager
import android.os.Environment
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
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
    private lateinit var osmdroidDir: File

    @Before
    fun setUp() {
        ShadowEnvironment.setExternalStorageState(Environment.MEDIA_MOUNTED)

        context = mockk<Context>()
        assetManager = mockk<AssetManager>()
        every { context.assets } returns assetManager

        osmdroidDir = File(Environment.getExternalStorageDirectory(), "osmdroid")
        if (osmdroidDir.exists()) {
            osmdroidDir.deleteRecursively()
        }
    }

    @After
    fun tearDown() {
        if (osmdroidDir.exists()) {
            osmdroidDir.deleteRecursively()
        }
    }

    @Test
    fun copyAssets_failsSilentlyWhenDirectoryDoesNotExist() {
        val dhulikhelContent = "dhulikhel content".toByteArray()
        val somaliaContent = "somalia content".toByteArray()

        every { assetManager.open("dhulikhel.mbtiles") } returns ByteArrayInputStream(dhulikhelContent)
        every { assetManager.open("somalia.mbtiles") } returns ByteArrayInputStream(somaliaContent)

        // Ensure the directory does NOT exist to trigger the FileNotFoundException when creating FileOutputStream
        assertFalse(osmdroidDir.exists())

        MapTileUtils.copyAssets(context)

        // Due to the try/catch around the entire loop, the exception is caught silently and no files are written
        val dhulikhelFile = File(osmdroidDir, "dhulikhel.mbtiles")
        val somaliaFile = File(osmdroidDir, "somalia.mbtiles")

        assertFalse(dhulikhelFile.exists())
        assertFalse(somaliaFile.exists())
    }

    @Test
    fun copyAssets_copiesFilesSuccessfullyWhenDirectoryExists() {
        val dhulikhelContent = "dhulikhel content".toByteArray()
        val somaliaContent = "somalia content".toByteArray()

        every { assetManager.open("dhulikhel.mbtiles") } returns ByteArrayInputStream(dhulikhelContent)
        every { assetManager.open("somalia.mbtiles") } returns ByteArrayInputStream(somaliaContent)

        // Simulate production properly initializing the directory before calling this util, or the directory existing already
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
    fun copyAssets_skipsRemainingFilesOnPartialFailure() {
        val dhulikhelContent = "dhulikhel content".toByteArray()
        val somaliaContent = "somalia content".toByteArray()

        // The first file throws an exception
        every { assetManager.open("dhulikhel.mbtiles") } throws IOException("Mocked exception")
        // The second file would succeed if the loop continued
        every { assetManager.open("somalia.mbtiles") } returns ByteArrayInputStream(somaliaContent)

        osmdroidDir.mkdirs()

        MapTileUtils.copyAssets(context)

        val dhulikhelFile = File(osmdroidDir, "dhulikhel.mbtiles")
        val somaliaFile = File(osmdroidDir, "somalia.mbtiles")

        // Neither file should exist because the loop breaks on the first exception (caught outside the loop)
        assertFalse(dhulikhelFile.exists())
        assertFalse(somaliaFile.exists())
    }
}
