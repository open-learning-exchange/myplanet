package org.ole.planet.myplanet.service.upload

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.gson.Gson
import com.google.gson.JsonObject
import io.realm.Realm
import io.realm.RealmConfiguration
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.data.ApiInterface
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.model.RealmNewsLog
import org.ole.planet.myplanet.utilities.UrlUtils
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Integration tests for UploadCoordinator.
 *
 * These tests use:
 * - Real Realm database (in-memory configuration)
 * - MockWebServer for API responses
 * - Real UploadCoordinator implementation
 *
 * This provides much higher confidence than mocked unit tests
 * while still being fast and reliable.
 */
@RunWith(AndroidJUnit4::class)
class UploadCoordinatorIntegrationTest {

    private lateinit var realm: Realm
    private lateinit var mockWebServer: MockWebServer
    private lateinit var apiInterface: ApiInterface
    private lateinit var databaseService: DatabaseService
    private lateinit var uploadCoordinator: UploadCoordinator
    private lateinit var context: Context

    @Before
    fun setup() {
        // Initialize Realm with in-memory configuration
        Realm.init(ApplicationProvider.getApplicationContext())
        val config = RealmConfiguration.Builder()
            .name("test.realm")
            .inMemory()
            .deleteRealmIfMigrationNeeded()
            .build()
        Realm.setDefaultConfiguration(config)
        realm = Realm.getInstance(config)

        // Setup MockWebServer
        mockWebServer = MockWebServer()
        mockWebServer.start()

        // Create Retrofit with mock server URL
        val retrofit = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        apiInterface = retrofit.create(ApiInterface::class.java)

        // Setup UrlUtils for upload coordinator
        UrlUtils.header = "Bearer test-token"
        UrlUtils.setUrl(mockWebServer.url("/").toString().removeSuffix("/"))

        // Create real dependencies
        context = ApplicationProvider.getApplicationContext()
        databaseService = DatabaseService(context)
        uploadCoordinator = UploadCoordinator(databaseService, apiInterface, context)
    }

    @After
    fun tearDown() {
        realm.close()
        mockWebServer.shutdown()
    }

    /**
     * Test 1: Successful upload updates database with remote IDs
     *
     * Scenario:
     * 1. Insert news log item without _id (not synced)
     * 2. Upload via coordinator
     * 3. Verify API received correct data
     * 4. Verify database updated with remote _id and _rev
     */
    @Test
    fun testSuccessfulUpload_updatesDatabase() = runBlocking {
        // Arrange - Insert test item into Realm
        val localId = "local-news-1"
        realm.executeTransaction { realm ->
            val newsLog = realm.createObject(RealmNewsLog::class.java, localId)
            newsLog.title = "Test News"
            newsLog.description = "Test Description"
            newsLog.userId = "test-user"
            // No _id means not synced yet
        }

        // Mock successful API response
        val responseJson = JsonObject().apply {
            addProperty("id", "remote-news-1")
            addProperty("rev", "1-abc123")
        }
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(Gson().toJson(responseJson))
        )

        // Act - Upload using coordinator
        val result = uploadCoordinator.upload(UploadConfigs.NewsActivities)

        // Assert - Verify upload succeeded
        assertTrue("Upload should succeed", result is UploadResult.Success)
        val successResult = result as UploadResult.Success
        assertEquals("Should upload 1 item", 1, successResult.data)

        // Verify database was updated
        val updatedItem = realm.where(RealmNewsLog::class.java)
            .equalTo("id", localId)
            .findFirst()

        assertNotNull("Item should exist", updatedItem)
        assertEquals("Remote ID should be set", "remote-news-1", updatedItem!!._id)
        assertEquals("Remote Rev should be set", "1-abc123", updatedItem._rev)

        // Verify correct data was sent to API
        val request = mockWebServer.takeRequest()
        assertEquals("Should POST to correct endpoint", "/myplanet_activities", request.path)
        assertTrue("Should contain title", request.body.readUtf8().contains("Test News"))
    }

    /**
     * Test 2: Failed upload preserves original data
     *
     * Scenario:
     * 1. Insert news log item
     * 2. Mock API failure (500 error)
     * 3. Verify upload returns Failure
     * 4. Verify database item remains unchanged
     */
    @Test
    fun testFailedUpload_preservesData() = runBlocking {
        // Arrange
        val localId = "local-news-2"
        realm.executeTransaction { realm ->
            val newsLog = realm.createObject(RealmNewsLog::class.java, localId)
            newsLog.title = "Test News 2"
            newsLog.userId = "test-user"
        }

        // Mock API failure
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error")
        )

        // Act
        val result = uploadCoordinator.upload(UploadConfigs.NewsActivities)

        // Assert - Verify upload failed
        assertTrue("Upload should fail", result is UploadResult.Failure)
        val failureResult = result as UploadResult.Failure
        assertEquals("Should have 1 error", 1, failureResult.errors.size)
        assertTrue("500 errors should be retryable", failureResult.errors[0].retryable)

        // Verify database item unchanged (no _id still)
        val item = realm.where(RealmNewsLog::class.java)
            .equalTo("id", localId)
            .findFirst()

        assertNotNull("Item should still exist", item)
        assertTrue("_id should still be null or empty", item!!._id.isNullOrEmpty())
    }

    /**
     * Test 3: Empty query returns Empty result
     *
     * Scenario:
     * 1. No items in database matching query
     * 2. Upload should return Empty
     * 3. No API calls should be made
     */
    @Test
    fun testEmptyQuery_returnsEmpty() = runBlocking {
        // Arrange - Realm is empty (no news items)

        // Act
        val result = uploadCoordinator.upload(UploadConfigs.NewsActivities)

        // Assert
        assertEquals("Should return Empty", UploadResult.Empty, result)

        // Verify no API calls were made
        assertEquals("Should not call API", 0, mockWebServer.requestCount)
    }

    /**
     * Test 4: Batch processing works correctly
     *
     * Scenario:
     * 1. Insert 3 items
     * 2. Mock 3 successful responses
     * 3. Verify all uploaded in single batch
     * 4. Verify single transaction updated all items
     */
    @Test
    fun testBatchProcessing_uploadsMultipleItems() = runBlocking {
        // Arrange - Insert 3 news items
        val localIds = listOf("local-news-3", "local-news-4", "local-news-5")
        realm.executeTransaction { realm ->
            localIds.forEachIndexed { index, id ->
                val newsLog = realm.createObject(RealmNewsLog::class.java, id)
                newsLog.title = "Test News ${index + 1}"
                newsLog.userId = "test-user"
            }
        }

        // Mock 3 successful responses
        repeat(3) { index ->
            val responseJson = JsonObject().apply {
                addProperty("id", "remote-news-${index + 3}")
                addProperty("rev", "1-xyz${index}")
            }
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(Gson().toJson(responseJson))
            )
        }

        // Act
        val result = uploadCoordinator.upload(UploadConfigs.NewsActivities)

        // Assert
        assertTrue("Upload should succeed", result is UploadResult.Success)
        val successResult = result as UploadResult.Success
        assertEquals("Should upload 3 items", 3, successResult.data)
        assertEquals("Should have 3 uploaded items", 3, successResult.items.size)

        // Verify all items were updated
        localIds.forEach { localId ->
            val item = realm.where(RealmNewsLog::class.java)
                .equalTo("id", localId)
                .findFirst()

            assertNotNull("Item $localId should exist", item)
            assertFalse("Item $localId should have remote _id", item!!._id.isNullOrEmpty())
            assertFalse("Item $localId should have remote _rev", item._rev.isNullOrEmpty())
        }

        // Verify 3 API calls were made
        assertEquals("Should make 3 API calls", 3, mockWebServer.requestCount)
    }
}
