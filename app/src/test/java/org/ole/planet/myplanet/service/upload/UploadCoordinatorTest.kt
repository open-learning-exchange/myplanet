package org.ole.planet.myplanet.service.upload

import android.content.Context
import com.google.gson.JsonObject
import io.mockk.*
import io.realm.Realm
import io.realm.RealmObject
import io.realm.RealmQuery
import io.realm.RealmResults
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.data.ApiInterface
import org.ole.planet.myplanet.data.DatabaseService
import retrofit2.Response
import kotlin.reflect.KClass

/**
 * Comprehensive unit tests for UploadCoordinator.
 *
 * Tests cover:
 * - Successful uploads with database updates
 * - Failed uploads with error tracking
 * - Partial success scenarios
 * - Guest user filtering
 * - POST vs PUT logic
 * - Batch transaction optimization
 * - Hook invocation (beforeUpload/afterUpload/additionalUpdates)
 * - Custom response handlers
 */
class UploadCoordinatorTest {

    private lateinit var uploadCoordinator: UploadCoordinator
    private lateinit var databaseService: DatabaseService
    private lateinit var apiInterface: ApiInterface
    private lateinit var context: Context
    private lateinit var realm: Realm

    @Before
    fun setup() {
        // Mock dependencies
        databaseService = mockk(relaxed = true)
        apiInterface = mockk(relaxed = true)
        context = mockk(relaxed = true)
        realm = mockk(relaxed = true)

        // Create coordinator instance
        uploadCoordinator = UploadCoordinator(databaseService, apiInterface, context)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    /**
     * Test 1: Successful upload updates database correctly
     *
     * Verifies that:
     * - Items are queried from database
     * - Items are uploaded via API
     * - Database is updated with remote IDs
     * - Success result is returned
     */
    @Test
    fun `upload successful - updates database with remote IDs`() = runTest {
        // Arrange
        val testItem = TestRealmObject().apply {
            id = "local-1"
            title = "Test Item"
        }

        val responseJson = JsonObject().apply {
            addProperty("id", "remote-1")
            addProperty("rev", "1-abc")
        }

        // Mock database query
        val mockQuery = mockk<RealmQuery<TestRealmObject>>(relaxed = true)
        val mockResults = mockk<RealmResults<TestRealmObject>>(relaxed = true)

        every { mockResults.iterator() } returns mutableListOf(testItem).iterator()
        every { mockQuery.findAll() } returns mockResults

        coEvery { databaseService.withRealmAsync<Any>(any()) } coAnswers {
            val block = firstArg<suspend (Realm) -> Any>()

            every { realm.where(TestRealmObject::class.java) } returns mockQuery
            every { realm.copyFromRealm(testItem) } returns testItem

            runBlocking { block(realm) }
        }

        // Mock successful API response
        coEvery {
            apiInterface.postDocSuspend(any(), any(), any(), any())
        } returns Response.success(responseJson)

        // Mock database transaction
        coEvery { databaseService.executeTransactionAsync(any()) } answers {
            val block = firstArg<(Realm) -> Unit>()
            block(realm)
        }

        val config = createTestConfig()

        // Act
        val result = uploadCoordinator.upload(config)

        // Assert
        assertTrue("Result should be Success", result is UploadResult.Success)
        val successResult = result as UploadResult.Success
        assertEquals("Uploaded item count", 1, successResult.data)
        assertEquals("Items size", 1, successResult.items.size)
        assertEquals("Remote ID", "remote-1", successResult.items[0].remoteId)
        assertEquals("Remote Rev", "1-abc", successResult.items[0].remoteRev)

        // Verify database transaction was called
        coVerify { databaseService.executeTransactionAsync(any()) }
    }

    /**
     * Test 2: Failed upload preserves original data
     *
     * Verifies that:
     * - Failed API calls return Failure result
     * - Original database items are not modified
     * - Error information is captured
     */
    @Test
    fun `upload fails - returns failure result and preserves data`() = runTest {
        // Arrange
        val testItem = TestRealmObject().apply {
            id = "local-1"
            title = "Test Item"
        }

        // Mock database query
        val mockQuery = mockk<RealmQuery<TestRealmObject>>(relaxed = true)
        val mockResults = mockk<RealmResults<TestRealmObject>>(relaxed = true)

        every { mockResults.iterator() } returns mutableListOf(testItem).iterator()
        every { mockQuery.findAll() } returns mockResults

        coEvery { databaseService.withRealmAsync<Any>(any()) } coAnswers {
            val block = firstArg<suspend (Realm) -> Any>()

            every { realm.where(TestRealmObject::class.java) } returns mockQuery
            every { realm.copyFromRealm(testItem) } returns testItem

            runBlocking { block(realm) }
        }

        // Mock failed API response (500 server error)
        coEvery {
            apiInterface.postDocSuspend(any(), any(), any(), any())
        } returns Response.error(500, "".toResponseBody())

        val config = createTestConfig()

        // Act
        val result = uploadCoordinator.upload(config)

        // Assert
        assertTrue("Result should be Failure", result is UploadResult.Failure)
        val failureResult = result as UploadResult.Failure
        assertEquals("Size", 1, failureResult.errors.size)
        assertTrue("Should be true", failureResult.errors[0].retryable) // 500 errors are retryable
        assertEquals("Equal", 500, failureResult.errors[0].httpCode)

        // Verify database transaction was NOT called
        coVerify(exactly = 0) { databaseService.executeTransactionAsync(any()) }
    }

    /**
     * Test 3: Partial success tracks both succeeded and failed items
     *
     * Verifies that:
     * - Some items succeed, some fail
     * - PartialSuccess result is returned
     * - Only successful items update database
     * - Failed items are tracked with errors
     */
    @Test
    fun `upload partial success - tracks succeeded and failed separately`() = runTest {
        // Arrange
        val item1 = TestRealmObject().apply { id = "local-1"; title = "Item 1" }
        val item2 = TestRealmObject().apply { id = "local-2"; title = "Item 2" }
        val item3 = TestRealmObject().apply { id = "local-3"; title = "Item 3" }

        // Mock database query
        val mockQuery = mockk<RealmQuery<TestRealmObject>>(relaxed = true)
        val mockResults = mockk<RealmResults<TestRealmObject>>(relaxed = true)

        every { mockResults.iterator() } returns mutableListOf(item1, item2, item3).iterator()
        every { mockQuery.findAll() } returns mockResults

        coEvery { databaseService.withRealmAsync<Any>(any()) } coAnswers {
            val block = firstArg<suspend (Realm) -> Any>()

            every { realm.where(TestRealmObject::class.java) } returns mockQuery
            every { realm.copyFromRealm(any<TestRealmObject>()) } answers { firstArg() }

            runBlocking { block(realm) }
        }

        // Mock API responses: item1 success, item2 fails, item3 success
        coEvery {
            apiInterface.postDocSuspend(any(), any(), any(), match {
                it.get("title").asString == "Item 1"
            })
        } returns Response.success(JsonObject().apply {
            addProperty("id", "remote-1")
            addProperty("rev", "1-abc")
        })

        coEvery {
            apiInterface.postDocSuspend(any(), any(), any(), match {
                it.get("title").asString == "Item 2"
            })
        } returns Response.error(400, "".toResponseBody())

        coEvery {
            apiInterface.postDocSuspend(any(), any(), any(), match {
                it.get("title").asString == "Item 3"
            })
        } returns Response.success(JsonObject().apply {
            addProperty("id", "remote-3")
            addProperty("rev", "1-xyz")
        })

        coEvery { databaseService.executeTransactionAsync(any()) } answers {
            val block = firstArg<(Realm) -> Unit>()
            block(realm)
        }

        val config = createTestConfig()

        // Act
        val result = uploadCoordinator.upload(config)

        // Assert
        assertTrue("Result should be PartialSuccess", result is UploadResult.PartialSuccess)
        val partialResult = result as UploadResult.PartialSuccess
        assertEquals("Size", 2, partialResult.succeeded.size)
        assertEquals("Size", 1, partialResult.failed.size)
        val succeededIds = partialResult.succeeded.map { it.localId }.sorted()
        assertEquals("Succeeded IDs", listOf("local-1", "local-3"), succeededIds)
        assertEquals("Equal", "local-2", partialResult.failed[0].itemId)
    }

    /**
     * Test 4: Guest user filtering works correctly
     *
     * Verifies that:
     * - Items with guest userIds are filtered out
     * - Regular users are processed normally
     */
    @Test
    fun `upload filters guest users when configured`() = runTest {
        // Arrange
        val guestItem = TestRealmObject().apply {
            id = "local-1"
            userId = "guest123"
            title = "Guest Item"
        }
        val regularItem = TestRealmObject().apply {
            id = "local-2"
            userId = "user456"
            title = "Regular Item"
        }

        // Mock database query
        val mockQuery = mockk<RealmQuery<TestRealmObject>>(relaxed = true)
        val mockResults = mockk<RealmResults<TestRealmObject>>(relaxed = true)

        every { mockResults.iterator() } returns mutableListOf(guestItem, regularItem).iterator()
        every { mockQuery.findAll() } returns mockResults

        coEvery { databaseService.withRealmAsync<Any>(any()) } coAnswers {
            val block = firstArg<suspend (Realm) -> Any>()

            every { realm.where(TestRealmObject::class.java) } returns mockQuery
            every { realm.copyFromRealm(any<TestRealmObject>()) } answers { firstArg() }

            runBlocking { block(realm) }
        }

        // Mock API response
        coEvery {
            apiInterface.postDocSuspend(any(), any(), any(), any())
        } returns Response.success(JsonObject().apply {
            addProperty("id", "remote-2")
            addProperty("rev", "1-abc")
        })

        coEvery { databaseService.executeTransactionAsync(any()) } answers {
            val block = firstArg<(Realm) -> Unit>()
            block(realm)
        }

        // Config with guest filtering enabled
        val config = createTestConfig(
            filterGuests = true,
            guestUserIdExtractor = { it.userId }
        )

        // Act
        val result = uploadCoordinator.upload(config)

        // Assert - only 1 item uploaded (guest filtered out)
        assertTrue("Result should be Success", result is UploadResult.Success)
        val successResult = result as UploadResult.Success
        assertEquals("Uploaded item count", 1, successResult.data)

        // Verify API was called only once (for non-guest user)
        coVerify(exactly = 1) { apiInterface.postDocSuspend(any(), any(), any(), any()) }
    }

    /**
     * Test 5: POST vs PUT logic based on dbId
     *
     * Verifies that:
     * - Items without dbId use POST
     * - Items with dbId use PUT
     */
    @Test
    fun `upload uses POST when dbId is null`() = runTest {
        // Arrange
        val testItem = TestRealmObject().apply {
            id = "local-1"
            dbId = null // No dbId = POST
            title = "New Item"
        }

        setupBasicMocks(listOf(testItem))

        coEvery {
            apiInterface.postDocSuspend(any(), any(), any(), any())
        } returns Response.success(JsonObject().apply {
            addProperty("id", "remote-1")
            addProperty("rev", "1-abc")
        })

        val config = createTestConfig(dbIdExtractor = { it.dbId })

        // Act
        uploadCoordinator.upload(config)

        // Assert
        coVerify(exactly = 1) { apiInterface.postDocSuspend(any(), any(), any(), any()) }
        coVerify(exactly = 0) { apiInterface.putDocSuspend(any(), any(), any(), any()) }
    }

    @Test
    fun `upload uses PUT when dbId exists`() = runTest {
        // Arrange
        val testItem = TestRealmObject().apply {
            id = "local-1"
            dbId = "existing-doc-id" // Has dbId = PUT
            title = "Updated Item"
        }

        setupBasicMocks(listOf(testItem))

        coEvery {
            apiInterface.putDocSuspend(any(), any(), any(), any())
        } returns Response.success(JsonObject().apply {
            addProperty("id", "existing-doc-id")
            addProperty("rev", "2-xyz")
        })

        val config = createTestConfig(dbIdExtractor = { it.dbId })

        // Act
        uploadCoordinator.upload(config)

        // Assert
        coVerify(exactly = 1) { apiInterface.putDocSuspend(any(), any(), match { it.contains("existing-doc-id") }, any()) }
        coVerify(exactly = 0) { apiInterface.postDocSuspend(any(), any(), any(), any()) }
    }

    /**
     * Test 6: Batch transaction optimization
     *
     * Verifies that:
     * - Multiple items are processed
     * - Database transaction is called only ONCE per batch
     * - This is the key performance optimization
     */
    @Test
    fun `upload uses single transaction for batch of items`() = runTest {
        // Arrange - 5 items to verify batch processing
        val items = (1..5).map { i ->
            TestRealmObject().apply {
                id = "local-$i"
                title = "Item $i"
            }
        }

        setupBasicMocks(items)

        coEvery {
            apiInterface.postDocSuspend(any(), any(), any(), any())
        } returns Response.success(JsonObject().apply {
            addProperty("id", "remote-id")
            addProperty("rev", "1-abc")
        })

        coEvery { databaseService.executeTransactionAsync(any()) } answers {
            val block = firstArg<(Realm) -> Unit>()
            block(realm)
        }

        val config = createTestConfig()

        // Act
        uploadCoordinator.upload(config)

        // Assert - transaction called exactly ONCE for all 5 items
        coVerify(exactly = 1) { databaseService.executeTransactionAsync(any()) }

        // But API called 5 times (once per item)
        coVerify(exactly = 5) { apiInterface.postDocSuspend(any(), any(), any(), any()) }
    }

    /**
     * Test 7: beforeUpload and afterUpload hooks are called
     *
     * Verifies that:
     * - beforeUpload is called before API request
     * - afterUpload is called after successful upload
     * - Hooks receive correct parameters
     */
    @Test
    fun `upload calls beforeUpload and afterUpload hooks`() = runTest {
        // Arrange
        val testItem = TestRealmObject().apply {
            id = "local-1"
            title = "Test Item"
        }

        setupBasicMocks(listOf(testItem))

        coEvery {
            apiInterface.postDocSuspend(any(), any(), any(), any())
        } returns Response.success(JsonObject().apply {
            addProperty("id", "remote-1")
            addProperty("rev", "1-abc")
        })

        var beforeUploadCalled = false
        var afterUploadCalled = false
        var afterUploadItem: UploadedItem? = null

        val config = createTestConfig(
            beforeUpload = { item ->
                beforeUploadCalled = true
                assertEquals("Equal", "local-1", item.id)
            },
            afterUpload = { item, uploadedItem ->
                afterUploadCalled = true
                assertEquals("Equal", "local-1", item.id)
                afterUploadItem = uploadedItem
            }
        )

        // Act
        uploadCoordinator.upload(config)

        // Assert
        assertTrue("Should be true", beforeUploadCalled)
        assertTrue("Should be true", afterUploadCalled)
        assertEquals("Equal", "remote-1", afterUploadItem?.remoteId)
    }

    /**
     * Test 8: additionalUpdates hook is called in database transaction
     *
     * Verifies that:
     * - additionalUpdates is invoked during database transaction
     * - Can modify items beyond standard _id/_rev updates
     */
    @Test
    fun `upload calls additionalUpdates during transaction`() = runTest {
        // Arrange
        val testItem = TestRealmObject().apply {
            id = "local-1"
            title = "Test Item"
            isUpdated = true
        }

        setupBasicMocks(listOf(testItem))

        coEvery {
            apiInterface.postDocSuspend(any(), any(), any(), any())
        } returns Response.success(JsonObject().apply {
            addProperty("id", "remote-1")
            addProperty("rev", "1-abc")
        })

        var additionalUpdatesCalled = false

        coEvery { databaseService.executeTransactionAsync(any()) } answers {
            val block = firstArg<(Realm) -> Unit>()
            block(realm)
        }

        val config = createTestConfig(
            additionalUpdates = { realm, item, uploadedItem ->
                additionalUpdatesCalled = true
                assertEquals("Equal", "local-1", item.id)
                assertEquals("Equal", "remote-1", uploadedItem.remoteId)
                item.isUpdated = false // Custom update
            }
        )

        // Act
        uploadCoordinator.upload(config)

        // Assert
        assertTrue("Should be true", additionalUpdatesCalled)
    }

    /**
     * Test 9: Custom response handler works
     *
     * Verifies that:
     * - Custom field names for ID and revision can be specified
     * - Response parsing uses custom fields
     */
    @Test
    fun `upload uses custom response handler fields`() = runTest {
        // Arrange
        val testItem = TestRealmObject().apply {
            id = "local-1"
            title = "Test Item"
        }

        setupBasicMocks(listOf(testItem))

        // Response with custom field names (not "id"/"rev")
        val responseJson = JsonObject().apply {
            addProperty("meetupId", "custom-remote-id")
            addProperty("meetupRev", "custom-rev")
        }

        coEvery {
            apiInterface.postDocSuspend(any(), any(), any(), any())
        } returns Response.success(responseJson)

        coEvery { databaseService.executeTransactionAsync(any()) } answers {
            val block = firstArg<(Realm) -> Unit>()
            block(realm)
        }

        val config = createTestConfig(
            responseHandler = ResponseHandler.Custom("meetupId", "meetupRev")
        )

        // Act
        val result = uploadCoordinator.upload(config)

        // Assert
        assertTrue("Result should be Success", result is UploadResult.Success)
        val successResult = result as UploadResult.Success
        assertEquals("Equal", "custom-remote-id", successResult.items[0].remoteId)
        assertEquals("Equal", "custom-rev", successResult.items[0].remoteRev)
    }

    /**
     * Test 10: Empty query returns Empty result
     *
     * Verifies that:
     * - When no items match query, Empty result is returned
     * - No API calls are made
     */
    @Test
    fun `upload returns Empty when no items to upload`() = runTest {
        // Arrange - empty results
        setupBasicMocks(emptyList())

        val config = createTestConfig()

        // Act
        val result = uploadCoordinator.upload(config)

        // Assert
        assertEquals("Result should be Empty", UploadResult.Empty, result)

        // Verify no API calls
        coVerify(exactly = 0) { apiInterface.postDocSuspend(any(), any(), any(), any()) }
        coVerify(exactly = 0) { databaseService.executeTransactionAsync(any()) }
    }

    // ==================== Helper Methods ====================

    private fun setupBasicMocks(items: List<TestRealmObject>) {
        val mockQuery = mockk<RealmQuery<TestRealmObject>>(relaxed = true)
        val mockResults = mockk<RealmResults<TestRealmObject>>(relaxed = true)

        every { mockResults.iterator() } returns items.toMutableList().iterator()
        every { mockQuery.findAll() } returns mockResults

        coEvery { databaseService.withRealmAsync<Any>(any()) } coAnswers {
            val block = firstArg<suspend (Realm) -> Any>()

            every { realm.where(TestRealmObject::class.java) } returns mockQuery
            every { realm.copyFromRealm(any<TestRealmObject>()) } answers { firstArg() }

            runBlocking { block(realm) }
        }

        coEvery { databaseService.executeTransactionAsync(any()) } answers {
            val block = firstArg<(Realm) -> Unit>()
            block(realm)
        }

        every { realm.where(TestRealmObject::class.java) } returns mockQuery
    }

    private fun createTestConfig(
        filterGuests: Boolean = false,
        guestUserIdExtractor: ((TestRealmObject) -> String?)? = null,
        dbIdExtractor: ((TestRealmObject) -> String?)? = null,
        beforeUpload: (suspend (TestRealmObject) -> Unit)? = null,
        afterUpload: (suspend (TestRealmObject, UploadedItem) -> Unit)? = null,
        additionalUpdates: ((Realm, TestRealmObject, UploadedItem) -> Unit)? = null,
        responseHandler: ResponseHandler = ResponseHandler.Standard
    ): UploadConfig<TestRealmObject> {
        return UploadConfig(
            modelClass = TestRealmObject::class,
            endpoint = "test_endpoint",
            queryBuilder = { query -> query },
            serializer = UploadSerializer.Simple { item ->
                JsonObject().apply {
                    addProperty("id", item.id)
                    addProperty("title", item.title)
                    item.userId?.let { addProperty("userId", it) }
                }
            },
            idExtractor = { it.id },
            dbIdExtractor = dbIdExtractor,
            filterGuests = filterGuests,
            guestUserIdExtractor = guestUserIdExtractor,
            beforeUpload = beforeUpload,
            afterUpload = afterUpload,
            additionalUpdates = additionalUpdates,
            responseHandler = responseHandler
        )
    }

    // ==================== Test Data Classes ====================
    // TestRealmObject is defined in a separate file to avoid nested class issues with Realm annotation processor
}
