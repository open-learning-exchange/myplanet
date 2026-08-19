package org.ole.planet.myplanet.utils

import android.app.Application
import android.content.Context
import android.content.res.AssetManager
import android.os.Environment
import io.mockk.every
import io.mockk.mockk
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
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
    fun copyAssets_createsDirectoryAndCopiesSuccessfully() {
        val dhulikhelContent = "dhulikhel content".toByteArray()
        val somaliaContent = "somalia content".toByteArray()

        every { assetManager.open("dhulikhel.mbtiles") } returns ByteArrayInputStream(dhulikhelContent)
        every { assetManager.open("somalia.mbtiles") } returns ByteArrayInputStream(somaliaContent)

        assertFalse(osmdroidDir.exists())

        MapTileUtils.copyAssets(context)

        val dhulikhelFile = File(osmdroidDir, "dhulikhel.mbtiles")
        val somaliaFile = File(osmdroidDir, "somalia.mbtiles")

        assertTrue(dhulikhelFile.exists())
        assertTrue(somaliaFile.exists())
        assertArrayEquals(dhulikhelContent, dhulikhelFile.readBytes())
        assertArrayEquals(somaliaContent, somaliaFile.readBytes())
    }

    @Test
    fun copyAssets_skipsCopyIfDestinationExistsAndIsNonEmpty() {
        val dhulikhelContent = "dhulikhel content".toByteArray()
        val somaliaContent = "somalia content".toByteArray()

        // Write existing content to the destination files
        osmdroidDir.mkdirs()
        val dhulikhelFile = File(osmdroidDir, "dhulikhel.mbtiles")
        dhulikhelFile.writeBytes("existing dhulikhel content".toByteArray())
        val somaliaFile = File(osmdroidDir, "somalia.mbtiles")
        somaliaFile.writeBytes("existing somalia content".toByteArray())

        // The mocked streams should never be read because the file already exists and is not empty
        every { assetManager.open("dhulikhel.mbtiles") } returns ByteArrayInputStream(dhulikhelContent)
        every { assetManager.open("somalia.mbtiles") } returns ByteArrayInputStream(somaliaContent)

        MapTileUtils.copyAssets(context)

        // The content should still be the old existing content
        assertArrayEquals("existing dhulikhel content".toByteArray(), dhulikhelFile.readBytes())
        assertArrayEquals("existing somalia content".toByteArray(), somaliaFile.readBytes())
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
    fun copyAssets_continuesOnPartialFailure() {
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

        // First file should not exist, but second one should
        assertFalse(dhulikhelFile.exists())
        assertTrue(somaliaFile.exists())
        assertArrayEquals(somaliaContent, somaliaFile.readBytes())
    }
}
