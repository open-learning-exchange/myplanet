package org.ole.planet.myplanet.services.upload

import android.content.Context
import android.text.TextUtils
import android.util.Log
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.MainApplication
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
    private val userRepository: UserRepository = mockk(relaxed = true)
    private val voicesRepository: VoicesRepository = mockk(relaxed = true)
    private val uploadRepository: UploadRepository = mockk(relaxed = true)
    private val retryQueue: RetryQueue = mockk(relaxed = true)

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var voicesUploader: VoicesUploader

    @Before
    fun setup() {
        MainApplication.testContext = context
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0

        mockkStatic(TextUtils::class)
        every { TextUtils.isEmpty(any()) } answers { firstArg<CharSequence?>().isNullOrEmpty() }

        mockkObject(NetworkUtils)
        every { NetworkUtils.getDeviceName() } returns "deviceName"
        every { NetworkUtils.getCustomDeviceName(any()) } returns "customDeviceName"

        mockkObject(UrlUtils)
        every { UrlUtils.header } returns "mockHeader"
        every { UrlUtils.getUrl() } returns "http://mock.url"

        voicesUploader = VoicesUploader(
            userRepository = userRepository,
            voicesRepository = voicesRepository,
            uploadRepository = uploadRepository,
            retryQueue = retryQueue,
            timeProvider = TestTimeProvider(),
            dispatcherProvider = TestDispatcherProvider(testDispatcher)
        )
    }

    @After
    fun tearDown() {
        MainApplication.testContext = null
        unmockkAll()
    }

    @Test
    fun `uploadNews derives mimeType from filename and passes to header map`() = testScope.runTest {
        mockkObject(FileUtils)
        every { FileUtils.getFileNameFromUrl("http://example.com/test_image.png") } returns "test_image.png"
        every { FileUtils.getMimeType("test_image.png") } returns "image/png"

        val imgObj = buildJsonObject {
            put("fileName", "test_image.png")
            put("imageUrl", "http://example.com/test_image.png")
        }
        val imgJsonString = imgObj.toString()

        val newsJson = buildJsonObject {
            put("message", "Hello World")
        }
        val newsItem = NewsUploadData(
            id = "news1",
            _id = "news1_id",
            message = "Hello World",
            imageUrls = listOf(imgJsonString),
            newsJson = newsJson
        )

        coEvery { voicesRepository.getNewsForUpload() } returns listOf(newsItem)
        coEvery { userRepository.getUserModel() } returns null

        val imageResponseJson = buildJsonObject {
            put("id", "res123")
            put("rev", "rev123")
        }
        coEvery { uploadRepository.postJson("http://mock.url/resources", any()) } returns Response.success(imageResponseJson)
        coEvery { uploadRepository.uploadResourceJson(any(), any(), any()) } returns Response.success(JsonObject(emptyMap()))

        val bulkResponse = buildJsonArray {
            add(buildJsonObject {
                put("id", "news1_id")
                put("rev", "rev2")
            })
        }
        coEvery { uploadRepository.postJsonArray("http://mock.url/news/_bulk_docs", any()) } returns Response.success(bulkResponse)

        voicesUploader.uploadNews()
        advanceUntilIdle()

        coVerify(exactly = 1) {
            uploadRepository.uploadResourceJson(
                match { headers -> headers["Content-Type"] == "image/png" && headers["If-Match"] == "rev123" },
                "http://mock.url/resources/res123/test_image.png",
                any()
            )
        }
    }
}
