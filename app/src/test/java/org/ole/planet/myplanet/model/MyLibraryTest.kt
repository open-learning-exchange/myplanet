package org.ole.planet.myplanet.model

import android.app.Application
import android.content.Context
import com.google.gson.JsonObject
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.services.SharedPrefManager
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class MyLibraryTest {

    private lateinit var mockSpm: SharedPrefManager
    private lateinit var context: Context

    @Before
    fun setUp() {
        mockSpm = mockk(relaxed = true)
        context = RuntimeEnvironment.getApplication()
        every { mockSpm.getCouchdbUrl() } returns "http://localhost:5984"
    }

    private fun createDocWithAttachments(id: String, attachmentsMap: Map<String, JsonObject>): JsonObject {
        val attachmentsObj = JsonObject()
        attachmentsMap.forEach { (key, value) ->
            attachmentsObj.add(key, value)
        }
        return JsonObject().apply {
            addProperty("_id", id)
            addProperty("_rev", "1-abc")
            addProperty("title", "Test Resource")
            add("_attachments", attachmentsObj)
        }
    }

    private fun createAttachmentJson(contentType: String = "image/png", length: Long = 100): JsonObject {
        return JsonObject().apply {
            addProperty("content_type", contentType)
            addProperty("length", length)
            addProperty("digest", "md5-xyz")
            addProperty("stub", true)
            addProperty("revpos", 1)
        }
    }

    @Test
    fun `insertMyLibrary called twice with same doc does not grow attachments array`() {
        val doc = createDocWithAttachments(
            id = "res_1",
            attachmentsMap = mapOf(
                "cover.png" to createAttachmentJson(),
                "document.pdf" to createAttachmentJson("application/pdf", 200)
            )
        )

        val params1 = MyLibrary.Companion.InsertParams(
            doc = doc,
            spm = mockSpm,
            context = context
        )

        val result1 = MyLibrary.insertMyLibrary(params1)
        assertNotNull(result1)
        assertEquals(2, result1?.attachments?.size)
        val initialAttachments = result1?.attachments?.toList()

        val params2 = MyLibrary.Companion.InsertParams(
            doc = doc,
            spm = mockSpm,
            context = context,
            existing = result1
        )

        val result2 = MyLibrary.insertMyLibrary(params2)
        assertNotNull(result2)
        assertEquals(2, result2?.attachments?.size)
        assertEquals(initialAttachments?.map { it.name }, result2?.attachments?.map { it.name })
    }

    @Test
    fun `insertMyLibrary with second document adding new key appends exactly one entry`() {
        val initialDoc = createDocWithAttachments(
            id = "res_1",
            attachmentsMap = mapOf(
                "cover.png" to createAttachmentJson()
            )
        )

        val params1 = MyLibrary.Companion.InsertParams(
            doc = initialDoc,
            spm = mockSpm,
            context = context
        )

        val result1 = MyLibrary.insertMyLibrary(params1)
        assertNotNull(result1)
        assertEquals(1, result1?.attachments?.size)

        val updatedDoc = createDocWithAttachments(
            id = "res_1",
            attachmentsMap = mapOf(
                "cover.png" to createAttachmentJson(),
                "extra.txt" to createAttachmentJson("text/plain", 50)
            )
        )

        val params2 = MyLibrary.Companion.InsertParams(
            doc = updatedDoc,
            spm = mockSpm,
            context = context,
            existing = result1
        )

        val result2 = MyLibrary.insertMyLibrary(params2)
        assertNotNull(result2)
        assertEquals(2, result2?.attachments?.size)
        assertEquals(listOf("cover.png", "extra.txt"), result2?.attachments?.map { it.name })
    }

    @Test
    fun `insertMyLibrary updates resource address fields even when attachment key already exists`() {
        val doc = createDocWithAttachments(
            id = "res_1",
            attachmentsMap = mapOf(
                "file.pdf" to createAttachmentJson("application/pdf", 500)
            )
        )

        val params = MyLibrary.Companion.InsertParams(
            doc = doc,
            spm = mockSpm,
            context = context
        )

        val result = MyLibrary.insertMyLibrary(params)
        assertNotNull(result)
        assertEquals("http://localhost:5984/resources/res_1/file.pdf", result?.resourceRemoteAddress)
        assertEquals("file.pdf", result?.resourceLocalAddress)

        // Change base URL in SPM
        every { mockSpm.getCouchdbUrl() } returns "http://newserver:5984"

        val params2 = MyLibrary.Companion.InsertParams(
            doc = doc,
            spm = mockSpm,
            context = context,
            existing = result
        )

        val result2 = MyLibrary.insertMyLibrary(params2)
        assertNotNull(result2)
        assertEquals(1, result2?.attachments?.size)
        assertEquals("http://newserver:5984/resources/res_1/file.pdf", result2?.resourceRemoteAddress)
        assertEquals("file.pdf", result2?.resourceLocalAddress)
    }
}
