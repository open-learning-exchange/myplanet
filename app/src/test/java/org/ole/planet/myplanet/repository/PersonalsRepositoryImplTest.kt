package org.ole.planet.myplanet.repository

import android.content.Context
import com.google.gson.JsonObject
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.unmockkObject
import io.mockk.verify
import java.util.logging.Level
import java.util.logging.Logger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.data.room.dao.PersonalDao
import org.ole.planet.myplanet.model.Personal
import org.ole.planet.myplanet.utils.DeviceNameProvider
import org.ole.planet.myplanet.utils.FileUtils
import org.ole.planet.myplanet.utils.NetworkUtils
import org.ole.planet.myplanet.utils.UrlUtils
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class PersonalsRepositoryImplTest {

    private lateinit var personalDao: PersonalDao
    private lateinit var apiInterface: ApiInterface
    private lateinit var uploadRepository: UploadRepository
    private lateinit var deviceNameProvider: DeviceNameProvider
    private lateinit var repository: PersonalsRepositoryImpl

    @Before
    fun setup() {
        Logger.getLogger("io.mockk").level = Level.OFF
        personalDao = mockk(relaxed = true)
        apiInterface = mockk(relaxed = true)
        uploadRepository = mockk(relaxed = true)
        deviceNameProvider = mockk(relaxed = true)
        every { deviceNameProvider.getCustomDeviceName() } returns "mock-custom-device-name"

        mockkObject(UrlUtils)
        every { UrlUtils.header } returns "mock-header"
        every { UrlUtils.getUrl() } returns "mock-url"

        // NetworkUtils is no longer used by the repository, but keep its statics mocked in case
        // shared helpers (e.g. Personal.serialize) reach into it indirectly.
        mockkObject(NetworkUtils)
        every { NetworkUtils.getUniqueIdentifier() } returns "mock-unique-id"
        every { NetworkUtils.getDeviceName() } returns "mock-device-name"
        every { NetworkUtils.getCustomDeviceName(any<Context>()) } returns "mock-custom-device-name"

        mockkObject(FileUtils)
        every { FileUtils.getFileNameFromUrl(any()) } returns "test.txt"

        repository = PersonalsRepositoryImpl(personalDao, apiInterface, uploadRepository, deviceNameProvider)
    }

    @After
    fun tearDown() {
        unmockkObject(UrlUtils)
        unmockkObject(NetworkUtils)
        unmockkObject(FileUtils)
        unmockkAll()
    }

    @Test
    fun `personalTitleExists returns true when title and user match`() = runTest {
        coEvery { personalDao.countByTitle("My Title", "user1") } returns 1

        val result = repository.personalTitleExists("My Title", "user1")

        assertTrue(result)
        coVerify { personalDao.countByTitle("My Title", "user1") }
    }

    @Test
    fun `personalTitleExists returns false when title does not exist`() = runTest {
        coEvery { personalDao.countByTitle("Missing", null) } returns 0

        val result = repository.personalTitleExists("Missing", null)

        assertFalse(result)
        coVerify { personalDao.countByTitle("Missing", null) }
    }

    @Test
    fun `savePersonalResource sets id and properties before saving`() = runTest {
        val savedObjectSlot = slot<Personal>()
        coEvery { personalDao.insert(capture(savedObjectSlot)) } returns Unit

        repository.savePersonalResource(
            title = "Test Title",
            userId = "user1",
            userName = "Test User",
            path = "/path/to/file",
            description = "Test Desc"
        )

        val captured = savedObjectSlot.captured
        assertEquals("Test Title", captured.title)
        assertEquals("user1", captured.userId)
        assertEquals("Test User", captured.userName)
        assertEquals("/path/to/file", captured.path)
        assertEquals("Test Desc", captured.description)
        assertTrue(captured.id.isNotEmpty())
        assertEquals(captured.id, captured._id)
    }

    @Test
    fun `getPersonalResources returns empty flow for null or blank userId`() = runTest {
        val resultNull = repository.getPersonalResources(null).first()
        assertTrue(resultNull.isEmpty())

        val resultBlank = repository.getPersonalResources("   ").first()
        assertTrue(resultBlank.isEmpty())
    }

    @Test
    fun `getPersonalResources returns flow of personals for valid userId`() = runTest {
        val expectedList = listOf(Personal())
        coEvery { personalDao.getByUserIdFlow("user1") } returns flowOf(expectedList)

        val result = repository.getPersonalResources("user1").first()

        assertEquals(expectedList, result)
        coVerify { personalDao.getByUserIdFlow("user1") }
    }

    @Test
    fun `deletePersonalResource deletes by _id or id in a single statement`() = runTest {
        repository.deletePersonalResource("test-id")

        coVerify(exactly = 1) { personalDao.deleteByIdOrDocId("test-id") }
    }

    @Test
    fun `updatePersonalResource delegates to personalDao updateFields`() = runTest {
        val update = PersonalUpdate(title = "New Title", description = "New Desc")

        repository.updatePersonalResource("test-id", update)

        coVerify(exactly = 1) { personalDao.updateFields("test-id", "New Title", "New Desc") }
    }

    @Test
    fun `getPendingPersonalUploads queries correctly`() = runTest {
        coEvery { personalDao.getPendingUploads("user1") } returns listOf(Personal(), Personal())

        val results = repository.getPendingPersonalUploads("user1")

        assertEquals(2, results.size)
        coVerify { personalDao.getPendingUploads("user1") }
    }

    @Test
    fun `updatePersonalAfterSync updates fields properly`() = runTest {
        repository.updatePersonalAfterSync("test-id", "new-id", "rev-1")

        coVerify { personalDao.updateUploadedStatus("test-id", "new-id", "rev-1") }
    }

    @Test
    fun `uploadPersonalDocument returns Pair of id and rev on success`() = runTest {
        val personal = Personal().apply { id = "test-id" }

        val responseJson = JsonObject().apply {
            addProperty("id", "new-id")
            addProperty("rev", "rev-1")
        }
        coEvery { apiInterface.postDoc(any(), any(), any(), any()) } returns Response.success(responseJson)

        val result = repository.uploadPersonalDocument(personal)

        assertEquals("new-id", result?.first)
        assertEquals("rev-1", result?.second)
        coVerify { personalDao.updateUploadedStatus("test-id", "new-id", "rev-1") }
    }

    @Test
    fun `uploadPersonalDocument sources customDeviceName from DeviceNameProvider without Context`() = runTest {
        val personal = Personal().apply { id = "test-id" }
        every { deviceNameProvider.getCustomDeviceName() } returns "provider-device-name"

        val responseJson = JsonObject().apply {
            addProperty("id", "new-id")
            addProperty("rev", "rev-1")
        }
        val bodySlot = slot<JsonObject>()
        coEvery { apiInterface.postDoc(any(), any(), any(), capture(bodySlot)) } returns Response.success(responseJson)

        repository.uploadPersonalDocument(personal)

        assertEquals("provider-device-name", bodySlot.captured.get("customDeviceName").asString)
        verify(exactly = 1) { deviceNameProvider.getCustomDeviceName() }
    }

    @Test
    fun `uploadPersonalDocument returns null when response body is null`() = runTest {
        val personal = Personal().apply { id = "test-id" }
        coEvery { apiInterface.postDoc(any(), any(), any(), any()) } returns Response.success<JsonObject>(null)

        val result = repository.uploadPersonalDocument(personal)

        assertNull(result)
    }

    @Test
    fun `uploadPersonal returns already uploaded when personal is uploaded`() = runTest {
        val personal = Personal().apply { isUploaded = true }

        val result = repository.uploadPersonal(personal)

        assertEquals("Resource already uploaded", result)
    }

    @Test
    fun `uploadPersonal uploads doc and returns success when response is valid without path`() = runTest {
        val personal = Personal().apply {
            id = "test-id"
            isUploaded = false
            path = null
        }
        val mockResponseObject = JsonObject().apply {
            addProperty("rev", "new-rev")
            addProperty("id", "new-id")
        }
        val mockResponse = Response.success(mockResponseObject)
        coEvery { apiInterface.postDoc(any(), any(), any(), any()) } returns mockResponse

        val result = repository.uploadPersonal(personal)

        assertEquals("Personal resource uploaded successfully", result)
        coVerify { apiInterface.postDoc(any(), any(), any(), any()) }
        coVerify(exactly = 0) { uploadRepository.uploadAttachment(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `uploadPersonal uploads doc and attachment when path is provided`() = runTest {
        val personal = Personal().apply {
            id = "test-id"
            isUploaded = false
            path = "/local/path/to/test.txt"
        }
        val mockResponseObject = JsonObject().apply {
            addProperty("rev", "new-rev")
            addProperty("id", "new-id")
        }
        val mockResponse = Response.success(mockResponseObject)
        coEvery { apiInterface.postDoc(any(), any(), any(), any()) } returns mockResponse
        coEvery { uploadRepository.uploadAttachment(any(), any(), any(), any(), any()) } returns mockk()

        val result = repository.uploadPersonal(personal)

        assertEquals("Personal resource uploaded successfully", result)
        coVerify { apiInterface.postDoc(any(), any(), any(), any()) }
        coVerify(exactly = 1) {
            uploadRepository.uploadAttachment(
                file = any(),
                destinationFormat = "%s/resources/%s/%s",
                id = "new-id",
                rev = "new-rev",
                name = "test.txt"
            )
        }
    }

    @Test
    fun `uploadPersonal returns failure message when doc response is null`() = runTest {
        val personal = Personal().apply {
            id = "test-id"
            isUploaded = false
        }
        val mockResponse = Response.success<JsonObject>(null)
        coEvery { apiInterface.postDoc(any(), any(), any(), any()) } returns mockResponse

        val result = repository.uploadPersonal(personal)

        assertEquals("Failed to upload personal resource: No response", result)
    }

    @Test
    fun `getPersonalResources deduplicates byte-identical flow emissions`() = runTest {
        val p1 = Personal().apply { id = "p1"; _rev = "rev1"; isUploaded = true; title = "Title" }
        val p2 = Personal().apply { id = "p1"; _rev = "rev1"; isUploaded = true; title = "Title" }
        coEvery { personalDao.getByUserIdFlow("user1") } returns flowOf(listOf(p1), listOf(p2))

        val emissions = mutableListOf<List<Personal>>()
        repository.getPersonalResources("user1").collect { emissions.add(it) }

        assertEquals(1, emissions.size)
    }

    @Test
    fun `getPersonalResources emits when local properties like title change`() = runTest {
        val p1 = Personal().apply { id = "p1"; _rev = "rev1"; isUploaded = false; title = "Old Title" }
        val p2 = Personal().apply { id = "p1"; _rev = "rev1"; isUploaded = false; title = "New Title" }
        coEvery { personalDao.getByUserIdFlow("user1") } returns flowOf(listOf(p1), listOf(p2))

        val emissions = mutableListOf<List<Personal>>()
        repository.getPersonalResources("user1").collect { emissions.add(it) }

        assertEquals(2, emissions.size)
        assertEquals("Old Title", emissions[0][0].title)
        assertEquals("New Title", emissions[1][0].title)
    }
}
