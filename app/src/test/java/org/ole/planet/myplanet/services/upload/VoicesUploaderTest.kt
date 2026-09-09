package org.ole.planet.myplanet.services.upload

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.model.NewsLog
import org.ole.planet.myplanet.repository.NewsUploadData
import org.ole.planet.myplanet.repository.UploadRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.repository.VoicesRepository
import org.ole.planet.myplanet.services.retry.RetryQueue
import org.ole.planet.myplanet.utils.FileUtils
import org.ole.planet.myplanet.utils.NetworkUtils
import org.ole.planet.myplanet.utils.TestDispatcherProvider
import org.ole.planet.myplanet.utils.TestTimeProvider
import org.ole.planet.myplanet.utils.UrlUtils
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class VoicesUploaderTest {

    private val context: Context = mockk(relaxed = true)
    private val gson: Gson = mockk(relaxed = true)
    private val uploadRepository: UploadRepository = mockk(relaxed = true)
    private val voicesRepository: VoicesRepository = mockk(relaxed = true)
    private val userRepository: UserRepository = mockk(relaxed = true)
    private val uploadCoordinator: UploadCoordinator = mockk(relaxed = true)
    private val uploadConfigs: UploadConfigs = mockk(relaxed = true)
    private val retryQueue: RetryQueue = mockk(relaxed = true)

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var voicesUploader: VoicesUploader

    @Before
    fun setup() {
        MainApplication.testContext = context
        mockkStatic(Log::class)
        mockkStatic(android.text.TextUtils::class)
        every { android.text.TextUtils.isEmpty(any()) } answers { firstArg<CharSequence?>().isNullOrEmpty() }
        mockkObject(NetworkUtils)
        // createImage -> addDocumentOrigin() reads this; unstubbed it hits Settings.Secure
        every { NetworkUtils.getUniqueIdentifier() } returns "uniqueIdentifier"
        every { NetworkUtils.getDeviceName() } returns "deviceName"
        every { NetworkUtils.getCustomDeviceName(any()) } returns "customDeviceName"
        mockkObject(UrlUtils)
        every { UrlUtils.header } returns "mockHeader"
        every { UrlUtils.getUrl() } returns "http://mock.url"
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0

        voicesUploader = VoicesUploader(
            gson,
            uploadRepository,
            voicesRepository,
            userRepository,
            uploadCoordinator,
            uploadConfigs,
            retryQueue,
            TestDispatcherProvider(testDispatcher),
            TestTimeProvider()
        )
    }

    @After
    fun tearDown() {
        MainApplication.testContext = null
        unmockkAll()
    }

    private fun newsWithImage(): NewsUploadData {
        mockkObject(FileUtils)
        every { FileUtils.getFileNameFromUrl("http://example.com/test_image.png") } returns "test_image.png"
        every { FileUtils.getMimeType("test_image.png") } returns "image/png"

        val imgObj = JsonObject().apply {
            addProperty("fileName", "test_image.png")
            addProperty("imageUrl", "http://example.com/test_image.png")
        }
        every { gson.fromJson(imgObj.toString(), JsonObject::class.java) } returns imgObj

        return NewsUploadData(
            id = "news1",
            _id = "news1_id",
            message = "Hello World",
            imageUrls = listOf(imgObj.toString()),
            newsJson = JsonObject().apply { addProperty("message", "Hello World") }
        )
    }

    private fun stubImageUpload() {
        val imageResponseJson = JsonObject().apply {
            addProperty("id", "res123")
            addProperty("rev", "rev123")
        }
        coEvery { uploadRepository.postUpload("http://mock.url/resources", any()) } returns Response.success(imageResponseJson)
        coEvery { uploadRepository.uploadResource(any(), any(), any()) } returns Response.success(JsonObject())
    }

    private fun bulkResponse(vararg elements: JsonObject) = JsonArray().apply {
        elements.forEach { add(it) }
    }

    @Test
    fun `uploadNews derives mimeType from filename and passes to header map`() = testScope.runTest {
        // build the item (and its FileUtils/gson stubs) before opening another stubbing scope
        val news = newsWithImage()
        coEvery { voicesRepository.getNewsForUpload() } returns listOf(news)
        coEvery { userRepository.getUserModel() } returns null
        stubImageUpload()
        coEvery { uploadRepository.postUploadArray("http://mock.url/news/_bulk_docs", any()) } returns
            Response.success(bulkResponse(JsonObject().apply {
                addProperty("id", "news1_id")
                addProperty("rev", "rev2")
            }))

        voicesUploader.uploadNews()
        advanceUntilIdle()

        coVerify(exactly = 1) {
            uploadRepository.uploadResource(
                match { headers -> headers["Content-Type"] == "image/png" && headers["If-Match"] == "rev123" },
                "http://mock.url/resources/res123/test_image.png",
                any()
            )
        }
    }

    @Test
    fun `uploadNews marks accepted docs as uploaded`() = testScope.runTest {
        // build the item (and its FileUtils/gson stubs) before opening another stubbing scope
        val news = newsWithImage()
        coEvery { voicesRepository.getNewsForUpload() } returns listOf(news)
        coEvery { userRepository.getUserModel() } returns null
        stubImageUpload()
        coEvery { uploadRepository.postUploadArray(any(), any()) } returns
            Response.success(bulkResponse(JsonObject().apply {
                addProperty("id", "news1_remote")
                addProperty("rev", "rev2")
            }))

        voicesUploader.uploadNews()
        advanceUntilIdle()

        coVerify(exactly = 1) {
            voicesRepository.markNewsUploaded(
                match { updates ->
                    updates.size == 1 &&
                        updates[0].id == "news1" &&
                        updates[0]._id == "news1_remote" &&
                        updates[0]._rev == "rev2"
                }
            )
        }
        coVerify(exactly = 0) { retryQueue.queueFailedOperation(any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `uploadNews queues a retry when the bulk request fails`() = testScope.runTest {
        // build the item (and its FileUtils/gson stubs) before opening another stubbing scope
        val news = newsWithImage()
        coEvery { voicesRepository.getNewsForUpload() } returns listOf(news)
        coEvery { userRepository.getUserModel() } returns null
        stubImageUpload()
        coEvery { uploadRepository.postUploadArray(any(), any()) } returns
            Response.error(500, "Server Error".toResponseBody(null))

        voicesUploader.uploadNews()
        advanceUntilIdle()

        coVerify(exactly = 1) {
            retryQueue.queueFailedOperation(
                uploadType = "News",
                error = match { it.itemId == "news1" && it.httpCode == 500 },
                payload = any(),
                endpoint = "news",
                httpMethod = "PUT",
                dbId = "news1_id",
                modelClassName = "News",
                userId = any()
            )
        }
        coVerify(exactly = 0) { voicesRepository.markNewsUploaded(any()) }
    }

    @Test
    fun `uploadNews uploads news activities once the batch is done`() = testScope.runTest {
        coEvery { voicesRepository.getNewsForUpload() } returns emptyList()
        coEvery { userRepository.getUserModel() } returns null

        voicesUploader.uploadNews()
        advanceUntilIdle()

        coVerify(exactly = 1) { uploadCoordinator.uploadRoom<NewsLog>(any()) }
    }
}
