package org.ole.planet.myplanet.model

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.core.net.toUri
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import java.io.ByteArrayInputStream
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.MainApplication

class UserEntityEncodeImageTest {

    @MockK
    lateinit var mockContext: Context

    @MockK
    lateinit var mockContentResolver: ContentResolver

    private var originalContext: Context? = null
    private lateinit var realmUser: UserEntity

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        try {
            originalContext = MainApplication.context
        } catch (e: Exception) {
            // UninitializedPropertyAccessException if context was never set
        }
        MainApplication.testContext = mockContext
        every { mockContext.contentResolver } returns mockContentResolver

        mockkStatic(Base64::class)
        // Mock toUri extension function
        mockkStatic(Uri::class)
        mockkStatic("androidx.core.net.UriKt")

        realmUser = UserEntity()
    }

    @After
    fun tearDown() {
        try {
            MainApplication.testContext = originalContext
        } finally {
            originalContext = null
            unmockkAll()
        }
    }

    @Test
    fun testEncodeImageToBase64NullOrEmpty() {
        assertNull(realmUser.encodeImageToBase64(null))
        assertNull(realmUser.encodeImageToBase64(""))
    }

    @Test
    fun testEncodeImageToBase64ContentUri() {
        val imagePath = "content://media/external/images/media/1"
        val mockUri = mockk<Uri>()
        val mockInputStream = ByteArrayInputStream("test image data".toByteArray())

        every { imagePath.toUri() } returns mockUri
        every { mockContentResolver.openInputStream(mockUri) } returns mockInputStream
        val expectedBase64 = java.util.Base64.getEncoder().encodeToString("test image data".toByteArray())

        val result = realmUser.encodeImageToBase64(imagePath)

        assertEquals(expectedBase64, result)
        verify { imagePath.toUri() }
        verify { mockContentResolver.openInputStream(mockUri) }
    }

    @Test
    fun testEncodeImageToBase64File() {
        // Create a temporary file
        val tempFile = File.createTempFile("test_image", ".jpg")
        tempFile.writeText("test file data")

        try {
            val imagePath = tempFile.absolutePath
            val expectedBase64 = java.util.Base64.getEncoder().encodeToString("test file data".toByteArray())

            val result = realmUser.encodeImageToBase64(imagePath)

            assertEquals(expectedBase64, result)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testEncodeImageToBase64Exception() {
        val imagePath = "content://media/external/images/media/1"
        val mockUri = mockk<Uri>()

        every { imagePath.toUri() } returns mockUri
        every { mockContentResolver.openInputStream(mockUri) } throws RuntimeException("Test Exception")

        val result = realmUser.encodeImageToBase64(imagePath)

        assertNull(result)
    }
}
