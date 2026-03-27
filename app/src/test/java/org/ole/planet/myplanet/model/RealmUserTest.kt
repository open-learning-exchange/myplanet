package org.ole.planet.myplanet.model

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.MainApplication
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.io.FileInputStream
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], manifest = Config.NONE, application = android.app.Application::class)
class RealmUserTest {

    private lateinit var realmUser: RealmUser
    private lateinit var mockContext: Context
    private lateinit var mockContentResolver: ContentResolver

    @Before
    fun setup() {
        mockContext = mockk(relaxed = true)
        mockContentResolver = mockk(relaxed = true)
        every { mockContext.contentResolver } returns mockContentResolver

        MainApplication.context = mockContext

        realmUser = RealmUser()
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testEncodeImageToBase64_NullPath() {
        assertNull(realmUser.encodeImageToBase64(null))
    }

    @Test
    fun testEncodeImageToBase64_EmptyPath() {
        assertNull(realmUser.encodeImageToBase64(""))
    }

    @Test
    fun testEncodeImageToBase64_ContentUri() {
        val testString = "test content data"
        val testBytes = testString.toByteArray()
        val inputStream = ByteArrayInputStream(testBytes)

        val uriString = "content://media/external/images/media/1"
        val uri = mockk<Uri>()
        mockkStatic(Uri::class)
        every { Uri.parse(uriString) } returns uri
        every { mockContentResolver.openInputStream(any()) } returns inputStream

        val expected = Base64.encodeToString(testBytes, Base64.NO_WRAP)

        val result = realmUser.encodeImageToBase64(uriString)

        assertEquals(expected, result)
    }

    @Test
    fun testEncodeImageToBase64_FilePath() {
        val testString = "test file data"
        val testBytes = testString.toByteArray()

        val tempFile = File.createTempFile("test_image", ".jpg")
        FileOutputStream(tempFile).use { it.write(testBytes) }

        val expected = Base64.encodeToString(testBytes, Base64.NO_WRAP)

        val result = realmUser.encodeImageToBase64(tempFile.absolutePath)

        assertEquals(expected, result)

        tempFile.delete()
    }

    @Test
    fun testEncodeImageToBase64_Exception() {
        val uriString = "content://media/external/images/media/1"
        val uri = mockk<Uri>()
        mockkStatic(Uri::class)
        every { Uri.parse(uriString) } returns uri
        every { mockContentResolver.openInputStream(any()) } throws RuntimeException("Test Exception")

        assertNull(realmUser.encodeImageToBase64(uriString))
    }
}
